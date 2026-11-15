# Qantara VPN — Native Android Kotlin + Xray-core

A complete, production-structured native Android VPN client written in **Kotlin** using **Android `VpnService`**, **Xray-core (V2Ray)**, **Material Design 3 (Dark Mode)**, and full **Arabic (RTL) / English (LTR)** localization.

## Features

- **Protocols & Transports (Xray-core)**
  - Protocols: `VLESS`, `VMess`, `Trojan`, `Shadowsocks`
  - Security: `Reality` (`pbk`, `sid`, `spx`, `fp`), `TLS`, `None`
  - Transports: `gRPC`, `HTTP/2 (h2)`, `WebSocket (ws)`, `TCP`, `UDP` (Full-cone NAT / UDP over tunnel)
- **Android `VpnService` Tunnel**
  - Creates a local TUN interface (`10.0.0.2/30`, IPv6 `fd00::2/126`) with DNS routing (`1.1.1.1`, `8.8.8.8`)
  - Loop prevention via `addDisallowedApplication(packageName)`
  - Foreground service notification with live upload/download speed & quick disconnect action
- **Configuration Import**
  - **QR Code Scanner**: CameraX + ML Kit Barcode Scanning (`QrScannerActivity`)
  - **Share URI Links**: `vless://`, `vmess://`, `trojan://`, `ss://`
  - **Xray JSON Files**: Document picker (`ActivityResultContracts.OpenDocument`) for `.json` configs
- **Real-Time Telemetry**
  - Live **Ping (ms)** latency probe, **Upload speed**, **Download speed**, and **Packet Loss (%)**
- **11 Pre-Configured Country Regions**
  - Germany, Netherlands, France, UK, USA, Canada, Turkey, Singapore, Japan, UAE, Saudi Arabia
- **Modern Bilingual Material Design 3 Dark UI**
  - Automatic & manual Arabic (`ar`, RTL) and English (`en`, LTR) switching via `AppCompatDelegate.setApplicationLocales`

---

## Project Structure

```text
QantaraVPN/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/qantara/vpn/
        │   ├── QantaraApp.kt
        │   ├── data/ServerRepository.kt
        │   ├── model/VpnModels.kt
        │   ├── parser/UriConfigParser.kt
        │   ├── service/QantaraVpnService.kt
        │   ├── ui/
        │   │   ├── MainActivity.kt
        │   │   ├── HomeFragment.kt
        │   │   ├── ServersFragment.kt
        │   │   ├── ImportBottomSheetDialog.kt
        │   │   ├── QrScannerActivity.kt
        │   │   └── adapter/ServerAdapter.kt
        │   ├── util/
        │   │   ├── LocaleHelper.kt
        │   │   └── TrafficFormatter.kt
        │   └── xray/XrayCoreBridge.kt
        └── res/
            ├── layout/
            ├── values/ (strings, colors, themes)
            ├── values-ar/strings.xml
            ├── menu/bottom_nav_menu.xml
            ├── drawable/
            └── xml/
```

---

## Build Instructions (Android Studio)

### 1. Prerequisites
- **Android Studio Ladybug (2024.2.1)+** or newer
- **JDK 17**
- **Android SDK 35** (Build Tools `35.0.0`, NDK optional if building Go bindings locally)

### 2. Add Xray-core Android Library (`libv2ray.aar`)
Because Xray-core is compiled from Go (`XTLS/Xray-core` + `2dust/AndroidLibXrayLite` or `XTLS/libXray`), place the compiled `.aar` binary in:

```text
app/libs/libv2ray.aar
```

And place the `hev-socks5-tunnel` (tun2socks) shared library (`.so` files for `arm64-v8a`, `armeabi-v7a`, `x86_64`) into `app/src/main/jniLibs/<abi>/libhev-socks5-tunnel.so` (or use `AndroidLibXrayLite`, which bundles `Tun2socks` directly via `libv2ray.Libv2ray`).

`XrayCoreBridge.kt` uses reflection/wrapper invocation against `libv2ray.Libv2ray` so the Kotlin project compiles cleanly in Android Studio even before dropping in `libv2ray.aar`, and automatically binds to the real Xray-core native runtime when `libv2ray.aar` is present.

### 3. Bootstrap Gradle Wrapper JAR (if missing)
If `gradle/wrapper/gradle-wrapper.jar` is not present in your checkout, open the folder directly in Android Studio (which auto-downloads Gradle 8.9) or run:

```bash
gradle wrapper --gradle-version 8.9
```

### 4. Build Debug / Release APK

```bash
./gradlew assembleDebug
# Or release build:
./gradlew assembleRelease
```

### 5. Run on Device or Emulator
1. Connect an Android device (API 24+) with USB Debugging enabled.
2. Click **Run (`Shift+F10`)** in Android Studio.
3. Grant the Android **VPN Connection** system prompt when tapping **Connect**.
