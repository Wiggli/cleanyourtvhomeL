# Gozo Football

An Android TV match centre built for the Luqa parents' box and its `family-gozo` theme. Manchester United, AC Milan, England and Italy are selected initially, while the television settings screen allows each team and competition to be enabled or hidden without changing the launcher itself.

Installing this add-on is optional and it should only be installed on the Luqa Gozo-themed TV if the football experience is wanted there. Other TVs and themes are unaffected.

## What it shows

- Matches opens on the current local day, puts followed teams first and shows every competition with games in a filter rail.
- Use the directional pad to highlight a match and OK to open its timeline, lineups and team stats. Back returns to the selected match.
- Favourites shows upcoming fixtures and recent results for all followed teams or for a single team.
- Tables shows standings for the selected competition where ESPN supplies them.
- Settings groups followed teams and competitions in the same interface. Changes are saved immediately. The Gozo Home widget still shows the next selected fixture.

The app queries ESPN's public, undocumented football feeds and team crest CDN. Today's scores refresh once a minute while the match list is open and a fixture is live or about to start. Lineups, events, stats and tables depend on competition and match coverage. The ESPN interfaces can change without notice.

No ads, analytics, telemetry, accounts or background service are included.

## Permissions

- `INTERNET` - fetches football data from `site.api.espn.com` and team crests from `a.espncdn.com`.
- `com.wiggli.gozofootball.READ_INTERESTS` - signature permission that limits the read-only Home widget feed to the matching launcher signer.

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

All four teams are shown on first use. Open Settings to choose which teams appear, then enable or hide the Premier League, Serie A, Champions League, Europa League, domestic cups, World Cup, Nations League, European Championship and international friendlies. At least one team and one competition always remain selected.

## Tested on

The add-on targets Android API 34 and runs on the Luqa Amino GO Amigo7xGM with Android 12.
