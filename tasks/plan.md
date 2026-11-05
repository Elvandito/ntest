# Implementation Plan: NetBand GUI Feature Set

## Overview

Turn the existing command-line NetBand workflow into an Android Compose control panel for one authorized network. The app must expose scan, hosts, limits, blocks, status, manual add, monitor, analyze, watch, clear and quit as first-class GUI controls, without pretending unimplemented analysis is working.

## Architecture Decisions

- Keep the existing Android app and package `com.elvan.netband`; do not rewrite the app architecture.
- Use the `data-terminal` app style from the design pass: dark ground, 1px accent selection lines, dense rows, tabular figures, no decorative cards.
- Android phone shell: bottom nav with short labels; no sidebar on a phone.
- Figures use monospace (`FontFamily.Monospace`) as the dealt style's numeric face; UI copy uses the system sans as the bundled-font fallback.
- Implement the UI as screens inside the app: Hosts, Monitor, Watch, Settings; controls for Limit, Block, Add Host and Analyze live where the action belongs.
- Real data only: current root, interface, ARP/neighbour, local device store and Android network stats. Do not invent traffic or per-host octet counts when the device cannot provide them; show an honest unavailable state.
- Preserve the existing network logic (`Scanner`, `Limiter`, `ArpSpoofer`, `DeviceStore`) and extend it narrowly.

## Information Architecture

- **Hosts** — scan and manual add; KPI strip for online/blocked/limited/watched; dense host rows; each row actions: details, watch, limit, block/free; detail sheet exposes analyzer and full status.
- **Monitor** — configurable refresh interval (`250 ms`, `500 ms`, `1 s`, `2 s`); reads interface byte counters; current RX/TX; total counters; explicit note that per-host traffic cannot be measured without per-host TC/class stats.
- **Analyze** — within Hosts detail or Monitor: duration-based sample summary: packets/bytes per interface and host ARP state; no guessed host-level bandwidth.
- **Watch** — watch add/remove; `watch all`; range and interval fields; list watched hosts; detect IP churn between scans by MAC.
- **Settings** — root/interface/gateway/iface copy; command mapping/help; clear persisted hosts and watch entries; quit closes the app.

## Task List

### Phase 1: Foundation
- [ ] Add Compose dark theme tokens, font loading, navigation shell and reusable styled components.
- [ ] Extend device model/store for hostname, watch state and watch settings.
- [ ] Extend ViewModel/state for scan range, manual add, monitor stats, watch management, clear/quit actions.

### Checkpoint: Foundation
- [ ] Android build succeeds with no resource/code errors.

### Phase 2: Core Screens
- [ ] Hosts screen: scan range, add dialog, KPI strip, search/filter, dense rows, actions, empty/error states.
- [ ] Monitor screen: interface byte rate, counters, refresh interval control, analysis duration and honest unavailable/empty states.
- [ ] Watch screen: add/remove rows, set interval/range, watch-all/all removal.
- [ ] Settings/Help screen: root/net status, CLI-to-GUI mapping, clear data, quit.

### Checkpoint: Core Screens
- [ ] App builds debug and release; CI unit tests pass.

### Phase 3: Verification and Delivery
- [ ] Android CI passes build and emulator smoke job.
- [ ] Artifact uploaded by CI.
- [ ] Report design choices, real vs unavailable data, and CI result.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Per-host traffic cannot be measured reliably | Show interface-level stats and explicit unavailable state; never fabricate bytes |
| Root/tc/iptables differ across devices | Keep existing root failure handling; surface per-action failure messages |
| Scope too broad | Build one vertical slice: Hosts complete first, then Monitor/Watch/Settings |
| Android emulator cannot exercise root controls | Verify launch/smoke on CI; keep root controls inert but visible on non-rooted emulator |
| External network MITM misuse | Keep app scoped to own/authorized network; no stealth or exploit enablement |
