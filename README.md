# Saqib IPTV

Saqib IPTV is an Android WebView app that opens the website https://thugtv.st/.

## Branding
- App name: Saqib IPTV
- Launcher icon: supplied realistic portrait
- Opening screen: same portrait, shown while the website loads

## Build
Run `gradle --no-daemon assembleDebug`.

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
GitHub Actions builds and uploads the debug APK artifact after each push to `main`.

## Notes
This app is a website wrapper. Website features, sign-in, streams and availability are controlled by thugtv.st. The app does not host or provide streaming content itself.
