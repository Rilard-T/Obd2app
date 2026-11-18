# Distribution Guide: Google Play & GitHub Direct Editions

This project builds two compliant distribution configurations from the same verified Kotlin/Compose codebase via Gradle project properties (`-PdistributionChannel`), keeping the registered Firebase `applicationId` (`com.aistudio.obd2auto.vkmrtx`) and standard APK/AAB output paths intact.

---

## 1. Google Play Edition (`-PdistributionChannel=play`, default)

- **Monetization**: Uses **Google Play Billing** (`PlayBillingManager`) exclusively for `obd2_pro_monthly` and `obd2_pro_lifetime`. No external payment links, no debug Pro bypass switches in release builds, and no alternative update/sideloading links.
- **Permissions**:
  - Uses zero-permission Android Photo Picker (`PickVisualMedia`) — no `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`.
  - Requests only `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (API 31+) at startup; `ACCESS_FINE_LOCATION` is requested on demand only when launching GPS Speed Check (or BT scan on Android 11 and below).
  - Declares `android:foregroundServiceType="connectedDevice"` for the live ELM327 telemetry service.
  - Declares optional hardware features (`gps`, `wifi`, `bluetooth`, `usb.host` with `required="false"`) so tablets and car head units are supported.
- **Android Auto (Google Play compliant)**: Declares `androidx.car.app.category.IOT` without `NAVIGATION_TEMPLATES` or `ACCESS_SURFACE`. Runs exclusively with standard Car App Library templates (`TabTemplate`, `GridTemplate`, `ListTemplate`, `PaneTemplate`). Fullscreen `NavigationTemplate` / `SurfaceCallback` is completely disabled at compile/manifest level (`BuildConfig.ALLOW_AA_NAVIGATION_SURFACE = false`).
- **Pro Gating**: Free tier unlocks core OBD2 telemetry, Bluetooth/BLE/Wi-Fi/USB connection, DTC read/clear, Freeze Frame, I/M Readiness, Battery Test, basic Trip Computer, and Android Auto Templates. Purchasing `obd2_pro_monthly` or `obd2_pro_lifetime` via Google Play Billing unlocks AI Diagnostics (Fault Breakdown, Voice AI, Photo Scan, VIN Auto-Fill), Pre-Purchase Inspection + A4 PDF export, OEM & Custom Torque PIDs, CSV Datalogger, ELM327 Terminal, Virtual Dyno / 0-100 Timer, GPS Speed Check, Maintenance Tracker, HUD Projection, and Firebase Cloud Sync.

### Build Command (AAB for Google Play Console)
```bash
gradle :app:bundleRelease -PdistributionChannel=play
```
Output: `app/build/outputs/bundle/release/app-release.aab`

---

## 2. GitHub Direct Edition (`-PdistributionChannel=github`)

- **Open Access**: Unlocks all Pro telemetry, AI Diagnostic, Virtual Dyno, and OEM PID modules out of the box without requiring Google Play Billing (ideal for AOSP/car head units without Google Play billing support).
- **Android Auto Fullscreen Gauges**: Retains `androidx.car.app.category.NAVIGATION`, `NAVIGATION_TEMPLATES`, `ACCESS_SURFACE`, and the `SurfaceCallback` + `NavigationTemplate` fullscreen analog gauges cockpit (`src/github/AndroidManifest.xml`).
- **Safe Update Discovery**: Optionally links to the official GitHub Releases page via `Intent.ACTION_VIEW` when `-PgithubRepoUrl` is supplied in CI — uses zero restricted `REQUEST_INSTALL_PACKAGES` permissions and zero dynamic code loading (DCL).

### Build Command (APK for GitHub Releases)
```bash
gradle :app:assembleRelease \
  -PdistributionChannel=github \
  -PgithubRepoUrl="https://github.com/<owner>/<repo>/releases"
```
Output: `app/build/outputs/apk/release/app-release.apk`
