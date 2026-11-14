# 8 Ball Assist

An Android 8+ overlay for pool. Tested on **8 Ball Pool**.

## Release

Install `8 Ball Assist.apk` from the release package. Local builds are saved to `dist/8 Ball Assist.apk`.

1. Open the app and enable **Display over other apps**.
2. Tap **Start overlay** and share the **entire screen**.
3. Open your pool game in landscape.

Floating controls are hidden by default. Enable **Show floating controls** on the main screen or in advanced settings. With controls hidden, the paths still appear. Open the app to stop tracking, or use **Stop** in its notification. Allow notifications to keep that shortcut available.

## Controls and presets

- **Move toolbar:** drag its status text.
- **More / Less** expands or collapses the tools. The toolbar has a translucent background.
- **Hide** hides the controls while tracking continues. Restore them in the app or with **Controls** in the notification.
- **Pause:** hide predictions and pause tracking.
- **Rails:** change the number of predicted bounces.
- **R− / R+:** adjust ball radius.
- **Flip:** reverse the selected direction.
- **×:** stop screen capture and close the overlay.
- **Table:** tap the top-left and bottom-right inner cushion corners to set the table boundary.
- **Auto:** return to automatic table detection.
- **Guide:** choose Auto, Yellow, or White for the game's aiming lines.
- **Pockets:** pause tracking and show the table boundary, six pockets, and detected balls. Drag a pocket marker to correct its position.
- **Presets → Save reviewed table:** name and save the reviewed table, pockets, guide style, and ball radius.
- **Presets → Load:** use a saved layout until you tap Auto or rotate the screen.
- **Presets & advanced settings:** rename or delete presets, choose a default, and toggle cue paths, collision previews, or brief tracking history.
- **Automatic preset fallback:** off by default. Enable it in advanced settings to use your default preset when table detection fails. Screen proportions must match within 2.5%.

Rotation clears active calibration. Saved presets survive app updates.

## Build

Requires a JDK, Android SDK platform 35, and build-tools 36.0.0. Set `ANDROID_HOME` if needed, then run `./build.ps1` in PowerShell.

The build signs and verifies `dist/8 Ball Assist.apk`. Previous packages are kept as ZIP archives. Keep `build/local.keystore` to install future builds as updates.

## Privacy

Screen analysis runs in memory on your device. The app has no network permission and uses no accessibility service, game-memory access, or root. Presets store table geometry, not screenshots.

## Additional Information

- **Solid white:** cue-ball path.
- **Colored dashes:** object-ball path, matching its detected color.
- **Thin, short dashes:** paths after a collision, ending at the next rail or collision.
- **Estimated:** direction inferred from the contact marker or cue stick when the aiming guide is too short or missing.

Tracking confirms direction across frames and smooths small angle changes. An inferred direction needs three consistent observations and previews at most one rail bounce. Old paths disappear if fresh results stop arriving. Short-guide direction changes and moving cue balls reset confirmation.

Predictions do not account for spin, shot power, friction, or stopping distance. Missed balls and incorrect table boundaries affect the result. Check detected balls with **Pockets** and disable collision previews if those detections are wrong. Other table themes and cue skins need further testing.
