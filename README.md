# Saqib IPTV

Android IPTV player that loads channels from an M3U playlist URL supplied by the user.

## Features
- Paste an M3U or M3U8 playlist URL and load channels.
- All, Pakistan, India, and Other channel filters.
- Search channels by name.
- Tap a channel to play it using AndroidX Media3.
- Remembers the last playlist URL on the device.
- GitHub Actions builds a debug APK on every push to main.

## Build
Run `gradle --no-daemon assembleDebug`.

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Playlist and content
The app does not bundle paid or copyrighted channel streams. Add an M3U playlist URL that you own or are authorized to use. Channel availability and playback depend on the playlist provider and stream format.
