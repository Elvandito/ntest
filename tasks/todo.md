# NetBand GUI Feature Set — TODO

## Phase 1
- [ ] Point `Gradle atAndroid SDK` existing toolchain for debug/release build.
- [ ] Add Compose dark theme, typography, shapes and reusable `StatusPill` / `TerminalIconButton` components.
- [ ] Extend `Device` with `hostname`, `watched`; persist in `devices.json`.
- [ ] Extend ViewModel with `scanRange`, `showAnalyze`, `watchSettings`, interface stats, watch functions, clear/quit.
- [ ] Build debug + release to validate foundation.

## Checkpoint: Foundation
- [ ] Build succeeds.

## Phase 2
- [ ] Hosts screen complete: scan/add/filter/KPIs/actions/detail dialog/analyze duration measure.
- [ ] Monitor screen complete: byte rate, totals, configurable interval.
- [ ] Watch screen complete: add/remove/watch all/range/interval controls, watch list.
- [ ] Settings screen complete: root/net/help mapping/clear/quit.
- [ ] One navigation rail/bottom bar; keyboard/focus/enabled states.

## Checkpoint: Core Screens
- [ ] Build succeeds and unit tests pass.

## Phase 3
- [ ] Commit code.
- [ ] Run GitHub Actions, wait for green result.
- [ ] Verify emulator smoke job and artifact link.
