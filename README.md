# Blood Strike ESP — overlay panel

Floating ESP panel for Android. Draws boxes, snaplines, distance labels and an
FOV ring over Blood Strike using screen capture plus on-device person detection.
No root, no game modification.

## What it is not
Not a memory reader. It only sees what the game already renders, so enemies fully
behind cover produce no box. Latency is roughly 100-200 ms behind the frame.

## Build (GitHub Actions)
1. Create a public or private repo.
2. Upload every file in this project, keeping the folder structure.
3. Commit to `main`. The `build-apk` workflow runs automatically.
4. Open the run → Artifacts → download `bs-esp-apks`.
5. Unzip, install `app-debug.apk` (or `app-release.apk`).

Artifacts expire after 90 days. Release APK is signed with the debug key, so it
installs fine but is not Play Store publishable as-is.

## First run
1. Open the app.
2. Tap each red row: allow overlay, allow notifications, set battery unrestricted.
3. Rows turn green.
4. Tap Start ESP, accept the system screen-capture prompt.
5. Launch Blood Strike. Drag the panel by its header, tap the header to collapse.

First run downloads a ~4.5 MB model from Google storage. Needs internet once.

## Tuning
- Fast mode on, FPS 12-15, confidence 40-50 is the usable range.
- Confidence above 60 drops distant enemies first.
- Fixed game graphics quality helps; dynamic resolution wrecks a static threshold.
- Overlay briefly shows `backend: NNAPI` or `backend: CPU`. NNAPI is the fast path.

## Troubleshooting
- **Gradle build fails** — check the run log; usually a Kotlin/AGP version typo.
- **App won't start** — overlay or notification permission missing; the Start button
  stays disabled until both are granted.
- **No boxes** — model download failed (check internet), or confidence too high.
- **Boxes lag badly** — lower capture resolution, lower FPS, close background apps.
- **Panel vanishes after minutes** — battery optimisation re-enabled; fix it.
- **Capture stops on its own** — Android kills projection when another app takes it;
  restart ESP.

## Permissions used
- `SYSTEM_ALERT_WINDOW` — the overlay itself.
- `FOREGROUND_SERVICE` / `_MEDIA_PROJECTION` — keep capture alive while gaming.
- `POST_NOTIFICATIONS` — required notification for the foreground service.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — stops Doze throttling the pipeline.
- `INTERNET` — one-time model download only.

No analytics, no accounts, no root.
