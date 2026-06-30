# PersonalTV

A TiviGlass-inspired IPTV app for Android, built with Kotlin + Jetpack Compose + ExoPlayer Media3.

## Features

- **EPG / TV Guide** — XMLTV-based programme grid with hero banner, group tabs, channel list, progress bars
- **HLS / DASH / RTSP** — full adaptive streaming support via ExoPlayer Media3
- **DRM** — Widevine & PlayReady with license URLs parsed from M3U playlist (`#KODIPROP` and `EXT-X-KEY` attributes)
- **M3U Parser** — handles `#EXTINF`, `group-title`, `tvg-logo`, `tvg-id`, `tvg-name`, KODIPROP DRM attributes, VLC options
- **XMLTV Parser** — fetches and parses EPG data, maps by `tvg-id`
- **Apps screen** — grid of all installed apps with launch support
- **Group Options** — rename, hide, lock channel groups
- **Settings** — playlist URL, EPG URL, aspect ratio, buffer mode, deinterlacing, HW accel, subtitle config
- **Player controls** — transport buttons, audio/subtitle track selection, settings submenu (matches TiviGlass player screenshots)

## Build Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Gradle 8.4

## Build Steps

1. Open the project in Android Studio (`File > Open > PersonalTV/`)
2. Wait for Gradle sync to complete (downloads ~200MB of dependencies first time)
3. Connect a device or start an emulator (Android 5.0+ / API 21+)
4. Run: `Build > Build Bundle(s)/APK(s) > Build APK(s)`
5. APK will be at `app/build/outputs/apk/debug/app-debug.apk`

Or from command line:
```bash
./gradlew assembleDebug
```

## Using the App

1. Launch the app — you'll see the EPG screen with an empty state
2. Either tap "Load M3U Playlist" on the EPG screen, or go to **Settings** and enter your M3U URL
3. Tap "Reload Playlist" in Settings to fetch channels
4. EPG data will auto-load if your M3U header contains `url-tvg=`
5. Tap any channel in the EPG grid to start playback

## DRM Support

The M3U parser handles these DRM patterns automatically:

```
# Widevine (KODIPROP style)
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://license-server.com/widevine
#EXTINF:-1,Channel Name
http://stream.url/stream.mpd

# PlayReady (KODIPROP style)
#KODIPROP:inputstream.adaptive.license_type=com.microsoft.playready
#KODIPROP:inputstream.adaptive.license_key=https://license-server.com/playready
#EXTINF:-1,Channel Name
http://stream.url/stream.mpd
```

Note: PlayReady is only available on devices with OEM PlayReady support (Fire TV, some Samsung/LG TVs).

## Package

`com.personal.tv`
