# Luqa box

This is the user's parents' Amino Android TV box in Luqa, one of three tracked devices. It uses the [family-gozo](../themes/family-gozo/) theme with Gozo wallpapers, an opt-in inline Victoria forecast and the separate Gozo Football add-on.

The theme name describes the photographs, not the box's location. The football add-on was merged in [PR #1](https://github.com/Wiggli/cleanyourtvhomeL/pull/1) and inline weather in [PR #7](https://github.com/Wiggli/cleanyourtvhomeL/pull/7). This does not establish that either APK has been installed on the physical television.

## Approved updates on the television

The fork-specific [`Launcher Updates`](../fork/luqa-launcher-updater/) app is designed to run on this box, and it replaces the computer-driven script as the normal update path. It checks the signed `luqa-launcher-latest` GitHub release every three days, notifies when the launcher version increases, and downloads the APK only after the owner selects **Download and install**. The updater verifies the checksum, package name, version and installed launcher's signing certificate before opening Android's own installation confirmation.

The updater does not silently install, uninstall or replace the launcher, and it does not require ADB, root access, a stored TV address or a remote-control endpoint. Keep the same protected signing key for every published launcher build because the updater refuses a certificate mismatch, and Android will also refuse an incompatible in-place update.

The existing `tools/luqa-update.sh` remains available as a supervised local recovery and maintenance path, but it is not the periodic updater. Before the first installation, back up the launcher configuration and retain the recovery instructions in `tools/restore.sh`; if a signature mismatch occurs, do not uninstall the working launcher without a tested recovery plan.

No successful installation or physical testing is claimed here until the Luqa box is powered on and verified. Check Home, the weather panel, football card, notifications, Android's installer prompt and remote navigation on the real television before treating the update system as complete.

The optional [Stremio content rows PR #4](https://github.com/Wiggli/cleanyourtvhomeL/pull/4) is intentionally excluded and remains unmerged.
