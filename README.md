# QRKit

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

A small, dependency-free QR code library for Kotlin Multiplatform, plus a demo app that
generates and scans QR codes on Android and iOS.

The QR encoder and decoder are implemented from scratch in common Kotlin. Scanning uses the
platform camera stack (Camera2 on Android, AVFoundation + Vision on iOS) only to capture frames
and deliver them to the shared composable.

- **Generate** any text, link, Wi-Fi or contact payload with selectable error correction.
- **Render** symbols directly in Compose, or export them as PNG bytes.
- **Decode** QR codes from images with the built-in decoder (binarization, finder detection,
  perspective correction, Reed–Solomon error correction, bitstream parsing).
- **Scan** live camera frames with `QrScannerView`, available on Android and iOS.

Supported QR features: versions 1–40, all four error correction levels, numeric, alphanumeric,
byte (UTF-8 / ECI 26) modes, and all eight mask patterns.

## Project layout

| Module      | Description                                                    |
|-------------|----------------------------------------------------------------|
| `qrkit`     | Publishable KMP library (Android + iOS)                        |
| `shared`    | Compose Multiplatform demo UI used by both apps                |
| `androidApp`| Android entry point                                            |
| `iosApp`    | iOS entry point (SwiftUI + Compose)                            |

## Demo architecture

The demo app (`shared`) is split into domain, data, and presentation layers and uses
[Koin](https://insert-koin.io) for dependency injection. The `qrkit` library itself stays
framework-free: it publishes plain Kotlin/Compose APIs, and the app adapts it behind a repository
interface.

```text
shared/src/commonMain/kotlin/com/mahbub/qrcodegeneratorandscanner
├── domain
│   ├── model        # GeneratedQr, ScannedQr, QrMatrix, QrErrorLevel
│   ├── repository   # QrCodeRepository abstraction
│   └── usecase      # GenerateQrCodeUseCase
├── data
│   ├── mapper       # qrkit <-> domain mapping
│   └── repository   # QrCodeRepositoryImpl delegating to qrkit
├── presentation
│   ├── generate     # GenerateViewModel + stateless GenerateScreen
│   ├── scan         # ScanViewModel + stateless ScanScreen
│   ├── components   # QR matrix renderer, shared cards
│   └── theme
└── di               # Koin module + initKoin()
```

Dependencies point inward. `domain` has no dependency on Compose or qrkit, `data` implements the
domain repository using qrkit, and `presentation` holds ViewModels and stateless composables.
Koin wires the graph:

| Definition | Scope |
|---|---|
| `QrCodeRepository` → `QrCodeRepositoryImpl` | `single` |
| `GenerateQrCodeUseCase` | `factory` |
| `GenerateViewModel`, `ScanViewModel` | `viewModel` |

`initKoin()` is idempotent and is called from `QrKitApplication` on Android and from
`MainViewController` on iOS. Pass extra modules to `initKoin(...)` for platform-specific bindings.
Screens are split into a `*Route` composable that resolves its ViewModel with `koinViewModel()` and
a stateless `*Screen` composable that only receives state and callbacks, which keeps previews and
tests free of DI.

## Using the library

Published artifacts use group `com.softvinax`, artifact `qrkit`, version `1.0.0`. Every release
publishes the Android, common metadata, and iOS variants together.

### JitPack (public, no consumer token)

JitPack is the simplest option for Android and JVM consumers. Add the repository in the root
`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then depend on a release tag:

```kotlin
implementation("com.github.Softvinax-Ltd.QRKit:qrkit:1.0.0")
```

JitPack builds on Linux, which cannot compile Apple/Kotlin-Native targets, so JitPack serves the
Android/JVM artifacts and common metadata but **not** the iOS klibs. For a single dependency that
works on both Android and iOS, use GitHub Packages below or publish to Maven Central.

### GitHub Packages (Android + iOS, requires a token)

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Softvinax-Ltd/QRKit")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: "<github-username>"
            password = System.getenv("GITHUB_TOKEN") ?: "<token-with-read:packages>"
        }
    }
}

dependencies {
    implementation("com.softvinax:qrkit:1.0.0")
}
```

GitHub Packages requires authentication even when the repository is public; the token needs the
`read:packages` scope.

### Publishing a release

1. Tag the commit in Android Studio (`Git > Tag...`, for example `1.0.0`) and push the tag, or run
   `git tag 1.0.0 && git push origin 1.0.0`.
2. The [publish workflow](./.github/workflows/publish.yml) runs on a macOS runner for every tag,
   builds the Android and iOS artifacts, and publishes them to GitHub Packages. The workflow reads
   `LIBRARY_VERSION` from the tag, so the published version always matches.
3. For JitPack, open `https://jitpack.io/#Softvinax-Ltd/QRKit/1.0.0` once and click **Get it** to
   trigger the first build.

To publish manually from a machine with the Android SDK and Xcode:

```bash
GITHUB_TOKEN=<token-with-write:packages> ./gradlew :qrkit:publish
```

The token needs the `write:packages` scope. The included [`jitpack.yml`](./jitpack.yml) selects
JDK 21 and publishes only the `qrkit` module. If JitPack's build image lacks `compileSdk 37`,
publish through GitHub Actions or from a local machine instead.

### Android setup

The library manifest already declares the camera permission:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

`QrScannerView` requests the runtime permission automatically. No additional manifest changes are
needed. `minSdk` is 24.

### iOS setup

Add a camera usage description to the app's `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>Scan QR codes with your camera.</string>
```

The library uses `AVCaptureSession` for capture and Vision for barcode detection, so no other
entitlements are required. The scanner is supported on iOS 13 and later.

## API

### Generate

```kotlin
import com.mahbub.qrkit.*

val qr = QrCodeGenerator.generate(
    data = "https://example.com",
    options = QrCodeOptions(
        errorCorrection = QrErrorCorrection.QUARTILE, // LOW, MEDIUM, QUARTILE, HIGH
        version = 0,                                   // 0 = pick the smallest that fits
        mask = -1,                                     // -1 = pick the lowest-penalty mask
    ),
)
```

`QrCode` exposes `version`, `size`, `errorCorrection`, `mask`, `isDark(x, y)` and
`toBooleanArray()`. To export an image:

```kotlin
val png: ByteArray = qr.toPngBytes(scale = 8, quietZone = 4)
val bitmap = qr.toImageBitmap(scale = 8, quietZone = 4)
```

### Render in Compose

```kotlin
QrCodeImage(qrCode = qr, modifier = Modifier.size(240.dp))
// or generate and render in one call
QrCodeImage(data = "Hello, world!", modifier = Modifier.size(240.dp))
```

### Decode an image

```kotlin
val result: QrScanResult? = QrCodeDecoder.decode(imageBitmap)
// or from raw 8-bit grayscale luminance (for example a camera frame)
val fromCamera = QrCodeDecoder.decodeLuminance(luminance, width, height)
```

```kotlin
data class QrScanResult(
    val text: String,
    val rawBytes: ByteArray,
    val symbology: String = "QR_CODE",
    val contentType: String = "TEXT", // URL, WIFI, EMAIL, PHONE, SMS, LOCATION, CONTACT, EVENT
    val version: Int? = null,
    val errorCorrection: QrErrorCorrection? = null,
)
```

### Scan with the camera

```kotlin
@Composable
fun ScannerScreen() {
    QrScannerView(
        modifier = Modifier.fillMaxSize(),
        onResult = { result -> println(result.text) },
        onError = { message -> println(message) },
    )
}
```

`QrScannerView` shows a live preview, handles camera permission, debounces repeated reads of the
same payload and reports results on the main thread. Set `isActive = false` to keep the preview
running while pausing decoding, or `showOverlay = false` to hide the decorative scan frame.
`QrScannerOverlay` is available separately if you want to compose your own layout.

## How decoding works

1. Frames are converted to 8-bit luminance and binarized with an integral-image adaptive
   threshold, falling back to a global Otsu threshold.
2. Finder patterns are located by scanning for the 1:1:3:1:1 ratio, cross-checked both axes and
   clustered.
3. A projective transform is fitted to the three finder centres and the bottom-right alignment
   pattern.
4. The module grid is sampled with majority voting, then format information (with Hamming
   correction), data codewords, Reed–Solomon blocks and the bitstream are decoded.

## Running the demo

- Android: `./gradlew :androidApp:assembleDebug`
- iOS: open [`iosApp`](./iosApp) in Xcode and run the `iosApp` scheme
- Android tests: `./gradlew :qrkit:testAndroidHostTest`
- iOS tests: `./gradlew :qrkit:iosSimulatorArm64Test`

The test suite includes round-trip tests across versions 1–40, every error correction level and
mask pattern, rotation/scale/quiet-zone variations, Reed–Solomon correction tests, and a
cross-validation suite that verifies the generated symbols with ZXing. ZXing is a test-only
dependency; the library itself has no third-party runtime dependency beyond Compose.

## License

Released under the [MIT License](./LICENSE) — free to use, modify, and redistribute, including
for commercial projects, as long as the copyright notice is kept.
