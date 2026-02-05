# Meditation Timer (Android)

A minimal, reliable meditation timer app built with Kotlin and View Binding. It supports configurable durations, interval chimes, looping background music, and locally stored presets. The timer runs in a foreground service with a persistent notification and a wake lock for reliability.

## Features

- Duration and interval inputs (minutes, with 0 for no intervals)
- Start, pause/resume, and stop controls
- Foreground service with persistent notification
- Wake lock to keep the timer reliable with screen off
- Start/interval/end chimes selected from local audio files
- Optional looping background music from local storage
- Presets stored locally using SharedPreferences + Gson
- Material 3, calming lavender palette, scrollable layout

## Project Structure

```
app/src/main/
├── java/com/meditation/timer/
│   ├── MainActivity.kt
│   ├── MeditationTimerService.kt
│   ├── TimerConfig.kt
│   └── PresetManager.kt
├── res/
│   ├── layout/activity_main.xml
│   ├── values/{strings,colors,themes}.xml
│   └── raw/ (empty by default)
└── AndroidManifest.xml
```

## Build & Run

1. Open the project in Android Studio (Hedgehog or newer recommended).
2. Let Gradle sync.
3. Build and run on a device (Android 8.0+, target 34).

### Command line (optional)

```bash
./gradlew assembleDebug
```

## Usage

1. Enter duration and interval minutes.
2. Select chime audio files (start, interval, end).
3. Optional: select background music from local storage.
4. Tap **Start** to begin.
5. Use **Pause/Resume** or **Stop** as needed.
6. Save presets with a custom name and load later from the list.

## Notes & Reliability

- The timer uses a foreground service and a wake lock. This is important for keeping accurate time with the screen off.
- On some OEM devices, you may still need to disable battery optimization for best results.
- Music selection uses the system document picker and attempts to persist URI permissions; some providers might not allow persistable permissions.

## Permissions

- `READ_MEDIA_AUDIO` (or legacy storage access on Android 12 and lower) to pick music.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for the timer service.
- `POST_NOTIFICATIONS` for Android 13+ to show timer notifications.
- `WAKE_LOCK` to keep the timer running reliably.

No internet, location, or tracking permissions are used.
