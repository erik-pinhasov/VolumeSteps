# VolumeSteps

A minimal, open-source Android app that replaces the system's coarse ~15 volume levels with fine-grained control (e.g. 200 steps). Each hardware volume button press moves by a configurable step size.

No internet permission. No data collection. No analytics. 3 permissions total.

## Is this safe?

| Check | How to verify |
|---|---|
| **APK built from source** | Every release is compiled by GitHub Actions from this code. Click **Actions** tab to see the full public build log |
| **No INTERNET permission** | Read `AndroidManifest.xml` — or run `aapt d permissions VolumeSteps.apk` |
| **No network code** | The CI security scan checks every build and fails if any network code is found |
| **VirusTotal scan** | Each release includes a VirusTotal report link |
| **Build it yourself** | See build instructions below |

## Features

- Configurable total steps (15–1000, default 200)
- Configurable step size per key press (1–50, default 1)
- Vertical volume bar overlay on the right side, touch-draggable
- Screen-off volume control via MediaSession
- Hold-to-repeat with acceleration
- Haptic feedback
- No notification — runs inside AccessibilityService, no foreground service needed

## Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Volume bar overlay |
| `MODIFY_AUDIO_SETTINGS` | Read/write system volume |
| `VIBRATE` | Haptic tick |

## Setup

1. Download APK from [Releases](../../releases)
2. Install, open VolumeSteps
3. Grant overlay permission
4. Enable accessibility service
5. **Android 13+:** If grayed out, tap "Open App Info" button → ⋮ menu → Allow restricted settings → go back and enable
6. Set total steps and step size, tap Apply

## How it works

Maps N custom steps across the system's ~15 volume levels using `android.media.audiofx.Equalizer` gain offsets for sub-level granularity. A `MediaSession` with `VolumeProvider` handles screen-off volume keys.

## Architecture

```
src/com/volumesteps/
├── MainActivity.java          # UI
├── VolumeKeyService.java      # AccessibilityService: keys, MediaSession, overlay
├── VolumeStepController.java  # Core: step mapping, EQ gain offsets
├── VolumeOverlay.java         # Floating vertical bar
├── AudioSessionReceiver.java  # Attaches EQ to audio sessions
└── Compat.java                # Vibration API helpers
```

## Building from source

```bash
sudo apt install android-sdk-platform-23 dalvik-exchange aapt zipalign apksigner default-jdk

ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
mkdir -p build/gen build/classes

aapt package -f -m -S res -J build/gen -M AndroidManifest.xml -I $ANDROID_JAR
javac -source 1.8 -target 1.8 -bootclasspath $ANDROID_JAR -classpath $ANDROID_JAR \
  -d build/classes build/gen/com/volumesteps/R.java src/com/volumesteps/*.java
dx --dex --output=build/classes.dex build/classes/
aapt package -f -S res -M AndroidManifest.xml -I $ANDROID_JAR -F build/app.apk
(cd build && aapt add app.apk classes.dex)
zipalign -f 4 build/app.apk build/app-aligned.apk
apksigner sign --ks your-key.jks --out build/VolumeSteps.apk build/app-aligned.apk
```

## License

MIT
