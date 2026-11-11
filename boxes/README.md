# Box profiles

This fork tracks exactly three TV boxes. The profiles name each box without storing its IP address, serial number, Wi-Fi name or account details.

| Box | Profile | Theme recorded in this fork | Active device work |
|---|---|---|---|
| Friend's T9 box | [`t9.md`](t9.md) | No device theme recorded yet | None recorded here |
| Birżebbuġa box | [`birzebbuga.md`](birzebbuga.md) | No device theme recorded yet | None recorded here |
| Luqa box | [`luqa.md`](luqa.md) | [`family-gozo`](../themes/family-gozo/) | Football, inline weather and signed automatic launcher updates |

`main` contains the shared launcher and the three profiles above. Branches are temporary development work rather than device identities, which keeps upstream fixes and shared launcher work in one reviewable history.

## Release channels

The GitHub Releases count is the number of published device channels, not the number of boxes tracked in this repository. The current `luqa-launcher-latest` release is only for the Luqa box: it uses the signing identity verified against that installed launcher, and its updater has a Luqa-specific trust policy. It is not a universal three-box APK and must not be installed on the T9 or Birżebbuġa boxes.

Add a separate fixed release tag and updater policy for another box only after that physical box's package identity, signing certificate, configuration backup and recovery route have been verified. This can produce `t9-launcher-latest` and `birzebbuga-launcher-latest` channels later without splitting shared source into three long-lived branches.

Draft PR [#4](https://github.com/Wiggli/cleanyourtvhomeL/pull/4) is an opt-in Stremio content-row experiment for the shared launcher. Its target box has not been confirmed in this inventory, so it remains fork-wide development rather than being assigned to one of the three profiles.

