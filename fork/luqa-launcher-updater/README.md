# Luqa launcher updater

This is the fork-specific updater installed alongside CleanYourTV on the Luqa box. It is deliberately separate from the network-free launcher.

The updater checks the fixed `luqa-launcher-latest` release in `Wiggli/cleanyourtvhomeL` every three days while the box has a network connection. A notification appears only when the published launcher has a higher Android version code than the installed launcher. Opening the app also performs an immediate check.

No launcher APK is downloaded during an automatic check. The owner must open the app and select **Download and install**, after which the updater verifies all of the following before Android's own installer is opened:

- the SHA-256 checksum published beside the release;
- the package name `com.gathin.tvhome`;
- the expected version code;
- the signing certificate against the launcher already installed on the box.

Android still shows its installation confirmation, so the updater cannot silently replace Home. Cancelling that screen leaves the current launcher installed.

The app contacts only the public GitHub release URLs under `Wiggli/cleanyourtvhomeL`. It has no analytics, account, token, advertisements or remote-control endpoint. Its requested permissions are network access, boot scheduling, update notifications and permission to hand a verified APK to Android's package installer.

Build it from the repository root with the launcher's Gradle wrapper:

```sh
launcher/gradlew -p fork/luqa-launcher-updater testReleaseUnitTest lintRelease assembleRelease
```

The first installation and the signing-certificate comparison still require verification on the real Luqa box before this system can be considered operational.
