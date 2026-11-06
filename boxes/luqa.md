# Luqa box

This is the user's parents' Amino Android TV box in Luqa, one of three tracked devices. It uses the [family-gozo](../themes/family-gozo/) theme with Gozo wallpapers, an opt-in inline Victoria forecast and the separate Gozo Football add-on.

The theme name describes the photographs, not the box's location. The football add-on was merged in [PR #1](https://github.com/Wiggli/cleanyourtvhomeL/pull/1) and inline weather in [PR #7](https://github.com/Wiggli/cleanyourtvhomeL/pull/7). This does not establish that either APK has been installed on the physical television.

## Periodic manual updates from the synced fork

Use `tools/luqa-update.sh` from a Mac or Linux computer on the same network as the Luqa TV, with JDK 17, Android SDK 34, Python tools virtualenv and a stable local Android debug signing key. GitHub syncing the fork with upstream does **not** itself update the physical TV. The script fetches and fast-forwards **the fork's** `main` branch only, runs launcher and football Gradle tests/lint/release builds, installs both APKs and reapplies `family-gozo` only after a positive identity check and a separate explicit confirmation.

Do not store the TV's IP address, Android ID or credentials in this repository. The Luqa TV must have working ADB debugging and be reachable locally or through a separately managed trusted connection.

From the repository root, after checking the IP address on the TV:

```bash
export LUQA_TV_HOST=192.168.x.x
bash tools/luqa-update.sh --identify
# Compare the returned Android ID to the Luqa TV's real Android ID.
export LUQA_ANDROID_ID='<verified-ID-from-Luqa-box>'
export LUQA_CONFIRM=INSTALL_LUQA
bash tools/luqa-update.sh
```

The script refuses a different device ID, a dirty checkout, a branch other than `main`, and missing explicit confirmation. Check Home, the weather panel, football card and remote navigation after installation. Keep the same signing key on subsequent local builds or Android may refuse in-place updates. Back up the launcher configuration before the first upgrade and retain the existing recovery instructions in `tools/restore.sh`; if a signature mismatch happens, do not uninstall the installed launcher without a tested recovery plan.

No automatic remote ADB installation is configured, and no successful installation or physical testing is claimed here. Since the upstream sync can introduce incompatible changes, inspect the fork's tests and source changes before applying a new update.

The optional [Stremio content rows PR #4](https://github.com/Wiggli/cleanyourtvhomeL/pull/4) is intentionally excluded and remains unmerged.
