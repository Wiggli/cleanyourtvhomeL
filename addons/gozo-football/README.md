# Gozo Football

A small Android TV football dashboard built for the `family-gozo` theme. It follows Manchester United, AC Milan, England and Italy without changing the launcher itself.

The launcher only sees the normal module-card declaration. Installing this add-on is optional and it should only be installed on the Gozo TV if the football card is wanted there. Other TVs and themes are unaffected.

## What it shows

- Manchester United, AC Milan, England and Italy selectors
- next fixture with Malta/device-local kickoff time and countdown
- up to three upcoming fixtures
- most recent result
- translucent Apple-style panel designed to sit over the Gozo wallpaper
- cached data shown immediately when available, with a 15-minute refresh window
- D-pad Left/Right changes team, OK refreshes, Back closes

The app queries ESPN's public, undocumented schedule JSON endpoints for a small curated set of domestic, UEFA and FIFA competitions. Competition endpoints that are unavailable are ignored, and the last successful data remains cached on the TV. Because the ESPN interface is undocumented, it can change without notice.

No ads, analytics, telemetry, accounts or background service are included. No club or federation artwork is downloaded; the interface uses names, initials and simple colour accents only.

## Permissions

- `INTERNET` - fetches football fixture and result JSON from `site.api.espn.com` when the dashboard is opened or manually refreshed.

There are no other runtime permissions.

## Build

Use the same JDK 17 and Android SDK 34 setup as the launcher:

```sh
cd addons/gozo-football
./gradlew assembleRelease
```

Install the resulting APK only on the Gozo TV. Once installed, Home will discover the `Football` module card and ask once whether it should be shown. The decision is stored by the launcher against the add-on signing key, following the repository's normal module-card contract.

## Data configuration

The four teams are intentionally fixed for this family theme:

- Manchester United - ESPN team ID `360`
- AC Milan - ESPN team ID `103`
- England - ESPN team ID `448`
- Italy - ESPN team ID `162`

The app checks the relevant Premier League, Serie A, UEFA, cup and international schedule endpoints and merges duplicate event IDs before display.

## Tested on

Source prepared for the Amino GO Amigo7xGM running Android TV 12 used by the `family-gozo` theme. Real-TV installation and visual confirmation are still required before describing the add-on as device-tested.
