# Gozo Football

A small Android TV football dashboard built for the Luqa box and its `family-gozo` theme. Manchester United, AC Milan, England and Italy are selected initially, while the television settings screen allows each team and competition to be enabled or hidden without changing the launcher itself.

The launcher only sees the normal module-card declaration. Installing this add-on is optional and it should only be installed on the Gozo TV if the football card is wanted there. Other TVs and themes are unaffected.

## What it shows

- selectable Manchester United, AC Milan, England and Italy interests
- a remote-friendly competition menu covering domestic leagues and cups, UEFA competitions and international football
- next fixture with Malta/device-local kickoff time and countdown
- up to three upcoming fixtures
- most recent result
- translucent Apple-style panel designed to sit over the Gozo wallpaper
- cached data shown immediately when available, with a 15-minute refresh window
- D-pad Left/Right changes team, Down opens settings, OK refreshes and Back closes

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

The four initial team choices are:

- Manchester United - ESPN team ID `360`
- AC Milan - ESPN team ID `103`
- England - ESPN team ID `448`
- Italy - ESPN team ID `162`

All four teams are shown on first use. Open the dashboard and press Down to choose which teams appear, then enable or hide the Premier League, Serie A, Champions League, Europa League, domestic cups, World Cup, Nations League, European Championship and international friendlies. At least one team and one competition always remain selected, while the app only checks competitions that apply to each enabled team and merges duplicate event IDs before display.

## Tested on

Source prepared for the Amino GO Amigo7xGM running Android TV 12 used by the Luqa `family-gozo` setup. Real-TV installation, remote navigation and visual confirmation are still required before describing the add-on as device-tested.
