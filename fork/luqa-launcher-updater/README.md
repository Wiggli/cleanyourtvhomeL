# Luqa launcher updater

This is the fork-specific updater installed alongside CleanYourTV on the Luqa box. It is deliberately separate from the network-free launcher.

The updater checks the fixed `luqa-launcher-latest` release in `Wiggli/cleanyourtvhomeL` every day while the box has a network connection. A newer release is downloaded, verified and submitted to Android's package installer automatically. Opening the app also performs an immediate check.

Before any installation, the updater verifies all of the following:

- the SHA-256 checksum published beside the release;
- the package name `com.gathin.tvhome`;
- the expected version code;
- the signing certificate against the launcher already installed on the box.

Android TV 12 permits unattended updates only when this app is the launcher's installer of record and its unknown-source app-op is allowed. Provision those two properties once over ADB after installing version 2 of the updater:

```sh
adb shell appops set com.wiggli.launcherupdates REQUEST_INSTALL_PACKAGES allow
adb shell cmd package set-installer com.gathin.tvhome com.wiggli.launcherupdates
```

If either property is missing, Android refuses the silent path and the updater posts a one-time approval notice instead. The current launcher remains installed after any download, verification or installer failure.

The app contacts only the public GitHub release URLs under `Wiggli/cleanyourtvhomeL`. It has no analytics, account, token, advertisements or remote-control endpoint. Its requested permissions are network access, boot scheduling, update notifications and Android's package-update permissions.

Build it from the repository root with the launcher's Gradle wrapper:

```sh
launcher/gradlew -p fork/luqa-launcher-updater testReleaseUnitTest lintRelease assembleRelease
```

Version 1 and the signed launcher release were installed and verified on the real Luqa box. Version 2 still requires the one-time installer-of-record provisioning above before unattended installation can be claimed as verified.
