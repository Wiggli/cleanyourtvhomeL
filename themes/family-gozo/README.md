# Gozo family theme

This theme uses the CleanYourTV default launcher style, with a Gozo photo collection, the time-of-day sky backdrop and the Weather add-on shortcut. Stremio and GO TV remain at the front of the dock. Twenty licensed landscape photographs are supplied at 1920 × 1080; the home screen and clock photo frame share this collection, which advances sequentially every minute. The sky backdrop uses intensity 60.

The Weather add-on already lives in [`addons/weather/`](../../addons/weather/). The theme includes its app in the dock and leaves module cards enabled. Build and install the add-on separately, then open Weather and choose **Għarb** or **Victoria, Gozo** as the city. The add-on is a separate full-screen app and does not display a live forecast in the launcher's header.

Apply the theme to an installed launcher with:

```sh
TV_HOST=<parents-tv-ip> tools/theme.sh themes/family-gozo
```

After ten minutes without remote input on Home, the launcher enters a photo-frame screensaver using this same collection. A small picture-frame button beside Wi-Fi opens it immediately. It shows each image full-screen with its location caption, plus a clock and date that drift gently to reduce burn-in. The system screensaver picker calls the service **Clock**; select it in Android TV's screensaver settings to use the photo frame during system standby as well. The wallpaper collection is centred and cropped to fill 16:9. Its source files are adapted from Wikimedia Commons and resized to 1920 × 1080. Each is distributed under the stated Creative Commons licence; source links below provide the full attribution and licence terms. The photographers and Wikimedia Commons do not endorse this theme.

For iPhone casting, install the separate [AirPlay Server](https://github.com/jqssun/android-airplay-server) app on the TV, open it, then use Control Centre on the iPhone to choose **Screen Mirroring** and select the TV receiver. It supports screen mirroring, photos, video and audio on Android TV; DRM-protected video is not supported. Both devices need to be on the same local network. The receiver only advertises while its app is open.

## Wallpaper credits

- **01, Ta’ Pinu Basilica, Għarb** — Berthold Werner, [source photo](https://commons.wikimedia.org/wiki/File:Malta_Gozo_Ta_Pinu_BW_2011-10-08_10-18-54.JPG), [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
- **02, Dwejra Tower at sunset** — Marika Caruana, [source photo](https://commons.wikimedia.org/wiki/File:Dwejra_Tower_at_sunset,_Gozo.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **03, Ta’ Pinu from Għarb** — Diego Delso, [source photo](https://commons.wikimedia.org/wiki/File:Santuario_Nacional_de_la_Virgen_de_Ta_%27Pinu,_G%C4%A7arb,_isla_de_Gozo,_Malta,_2021-08-22,_DD_31.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **04, Ta’ Pinu Basilica and fields** — Simon Burchell, [source photo](https://commons.wikimedia.org/wiki/File:Basilica_of_the_National_Shrine_of_the_Blessed_Virgin_of_Ta%27_Pinu,_Gozo_02.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **05, Golden Cliffs of Dwejra** — Bjorn Spiteri, [source photo](https://commons.wikimedia.org/wiki/File:Dwejra%27s_Golden_Cliffs_Gozo.png), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **06, Dwejra Tower and coast** — Ethan Doyle White, [source photo](https://commons.wikimedia.org/wiki/File:Dwejra_Tower_in_landscape_context.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **07, Fungus Rock at dusk** — JosephAmodio, [source photo](https://commons.wikimedia.org/wiki/File:Fungus_Rock,_Dwejra_Bay,_Gozo_3.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **08, The Inland Sea, Dwejra** — Bellina 09, [source photo](https://commons.wikimedia.org/wiki/File:Inland_sea_at_Dwejra,_Gozo.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **09, Ramla Bay from Calypso Cave** — Diego Delso, [source photo](https://commons.wikimedia.org/wiki/File:Bah%C3%ADa_de_Ramla_desde_la_cueva_de_Calipso,_isla_de_Gozo,_Malta,_2021-08-22,_DD_02.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **10, Wied il-Mielaħ Window** — JosephAmodio, [source photo](https://commons.wikimedia.org/wiki/File:Wied_il-Miela%C4%A7_Window_2.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **11, Ta’ Pinu Basilica from the plaza** — Mika500, [source photo](https://commons.wikimedia.org/wiki/File:Basilica_of_the_National_Shrine_of_the_Blessed_Virgin_of_ta_Pinu.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **12, Ta’ Pinu from the countryside** — Petr Pelikan, [source photo](https://commons.wikimedia.org/wiki/File:Basilica_Ta%27Pinu.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **13, Ta’ Pinu and Għarb fields** — Berthold Werner, [source photo](https://commons.wikimedia.org/wiki/File:Malta_Gozo_Ta_Pinu_BW_2011-10-08_10-19-55.JPG), [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
- **14, Ta’ Pinu at sunset** — Godwin Borg, [source photo](https://commons.wikimedia.org/wiki/File:Ta%27_Pinu_Church_at_Sunset.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **15, Victoria from the Citadel** — Ethan Doyle White, [source photo](https://commons.wikimedia.org/wiki/File:Victoria,_Gozo,_as_seen_from_the_Citadel.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **16, Gozo salt pans at sunset** — Bellina 09, [source photo](https://commons.wikimedia.org/wiki/File:Gozo_Salt_Pans.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **17, Xwejni Bay salt pans** — Marika Caruana, [source photo](https://commons.wikimedia.org/wiki/File:Xwejni_Bay_salt_pans.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **18, Xlendi panorama** — Jmasini, [source photo](https://commons.wikimedia.org/wiki/File:Xlendi_panorama.jpg), [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
- **19, Xlendi Bay and cliffs** — VillageHero from Ulm, Germany, [source photo](https://commons.wikimedia.org/wiki/File:View_to_Xlendi,_Gozo_(25662992352).jpg), [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0/).
- **20, Kercem cliffs at sunset** — Slashmalta, [source photo](https://commons.wikimedia.org/wiki/File:Kercem_Cliffs.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).

## Device status

The twenty-image collection was applied to the Amino GO Amigo7xGM running Android 12 at 1920 × 1080, and Home shows the first captioned Ta’ Pinu image. Android has the Clock screensaver component selected, though automatic standby activation still needs on-device confirmation.
