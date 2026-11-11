# Gozo Football

An Android TV match centre built for the Luqa parents' box and its `family-gozo` theme. Manchester United, AC Milan, England and Italy are selected initially, while the television settings screen allows each team and competition to be enabled or hidden without changing the launcher itself.

Installing this add-on is optional and it should only be installed on the Luqa Gozo-themed TV if the football experience is wanted there. Other TVs and themes are unaffected.

## What it shows

- selectable Manchester United, AC Milan, England and Italy interests
- a remote-friendly competition menu covering domestic leagues and cups, UEFA competitions and international football
- separate Overview, Fixtures and Results views controlled with the directional pad
- next fixture with Malta/device-local kickoff time and countdown
- up to six upcoming fixtures and six recent results
- live score and match-status treatment when ESPN marks a fixture in progress
- translucent Apple-style panel designed to sit over the Gozo wallpaper
- cached data shown immediately when available, with a 15-minute refresh window
- a read-only interests bridge that lets the Gozo launcher show the next selected fixture on Home
- D-pad Left/Right changes team, Up/Down changes view, Menu opens settings, OK refreshes and Back closes

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

Install the resulting APK only on the Luqa Gozo-themed TV. Once installed, Home discovers the `Football` module card and asks once whether it should be shown. The Home match panel reads only the selected team names, public ESPN identifiers and enabled competitions; settings remain writable only inside the football app.

## Data configuration

The four initial team choices are:

- Manchester United - ESPN team ID `360`
- AC Milan - ESPN team ID `103`
- England - ESPN team ID `448`
- Italy - ESPN team ID `162`

All four teams are shown on first use. Open the dashboard and press Menu to choose which teams appear, then enable or hide the Premier League, Serie A, Champions League, Europa League, domestic cups, World Cup, Nations League, European Championship and international friendlies. At least one team and one competition always remain selected, while the app only checks competitions that apply to each enabled team and merges duplicate event IDs before display.

## Tested on

The release and unit builds are checked against Android TV API 34. Installation and D-pad verification of this expanded version still require the Luqa Amino GO Amigo7xGM to be powered on.
