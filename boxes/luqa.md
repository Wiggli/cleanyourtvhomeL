# Luqa box

This is the user's parents' Amino Android TV box in Luqa, one of three tracked devices. It uses the [family-gozo](../themes/family-gozo/) theme with Gozo wallpapers, an opt-in inline Victoria forecast and the separate Gozo Football add-on.

The theme name describes the photographs, not the box's location. The football add-on was merged in [PR #1](https://github.com/Wiggli/cleanyourtvhomeL/pull/1) and inline weather in [PR #7](https://github.com/Wiggli/cleanyourtvhomeL/pull/7). The launcher, updater and football add-on were installed and checked on the physical box in November 2026.

## Approved updates on the television

The fork-specific [`Launcher Updates`](../fork/luqa-launcher-updater/) app replaces the computer-driven script as the normal update path. It checks the signed `luqa-launcher-latest` GitHub release daily, downloads only a higher version, verifies the checksum, package name, version and installed launcher's signing certificate, then submits the update to Android automatically.

Unattended installation requires a one-time Android TV 12 setup: version 2 of the updater must be installed, allowed as an update source and recorded as the installer for `com.gathin.tvhome`, following the commands in the updater README. After that setup, ADB can remain off; no root access, stored TV address or remote-control endpoint is used. Keep the same protected signing key for every published launcher build because the updater refuses a certificate mismatch, and Android will also refuse an incompatible in-place update.

The existing `tools/luqa-update.sh` remains available as a supervised local recovery and maintenance path, but it is not the periodic updater. Before the first installation, back up the launcher configuration and retain the recovery instructions in `tools/restore.sh`; if a signature mismatch occurs, do not uninstall the working launcher without a tested recovery plan.

Home, the Gozo theme, football results and settings, updater checks, signing checks and D-pad navigation have been verified on the real box. A future higher launcher version must still be observed installing unattended after version 2 receives its one-time installer ownership before that final path is considered proven.

The optional [Stremio content rows PR #4](https://github.com/Wiggli/cleanyourtvhomeL/pull/4) is intentionally excluded and remains unmerged.
