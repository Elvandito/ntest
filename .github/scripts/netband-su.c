#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <signal.h>
#include <poll.h>
#include <errno.h>

#define SOCK_NAME "netband-sud"

static socklen_t address_length(const struct sockaddr_un *addr) {
    (void)addr;
    return (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(SOCK_NAME));
}

static int connect_daemon(void) {
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock < 0) return -1;
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, SOCK_NAME, sizeof(addr.sun_path) - 2);
    if (connect(sock, (struct sockaddr *)&addr, address_length(&addr)) < 0) {
        close(sock);
        return -1;
    }
    return sock;
}

static void write_all(int fd, const char *data, size_t len) {
    size_t off = 0;
    while (off < len) {
        ssize_t written = write(fd, data + off, len - off);
        if (written <= 0) return;
        off += (size_t)written;
    }
}

static int run_command(int sock, const char *command) {
    write_all(sock, command, strlen(command));
    write_all(sock, "\nexit\n", 6);
    shutdown(sock, SHUT_WR);
    char buf[4096];
    ssize_t n;
    while ((n = read(sock, buf, sizeof(buf))) > 0) {
        write_all(STDOUT_FILENO, buf, (size_t)n);
    }
    close(sock);
    return 0;
}

int main(int argc, char **argv) {
    signal(SIGPIPE, SIG_IGN);
    int sock = connect_daemon();
    if (sock < 0) return 1;
    if (argc >= 3 && (strcmp(argv[1], "-c") == 0 || strcmp(argv[1], "0") == 0)) {
        return run_command(sock, argv[2]);
    }

    struct pollfd fds[2];
    fds[0].fd = STDIN_FILENO;
    fds[0].events = POLLIN;
    fds[1].fd = sock;
    fds[1].events = POLLIN;
    char buf[4096];
    for (;;) {
        int ready = poll(fds, 2, -1);
        if (ready < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (fds[0].fd >= 0 && (fds[0].revents & (POLLIN | POLLHUP))) {
            ssize_t n = read(STDIN_FILENO, buf, sizeof(buf));
            if (n <= 0) {
                shutdown(sock, SHUT_WR);
                fds[0].fd = -1;
                fds[0].events = 0;
            } else {
                write_all(sock, buf, (size_t)n);
            }
        }
        if (fds[1].revents & (POLLIN | POLLHUP)) {
            ssize_t n = read(sock, buf, sizeof(buf));
            if (n <= 0) break;
            write_all(STDOUT_FILENO, buf, (size_t)n);
        }
    }
    close(sock);
    return 0;
}
