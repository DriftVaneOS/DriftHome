# DriftHome

The DriftVaneOS launcher. Pages of apps, a floating glass dock, edit mode, search and an App Shelf, styled to match AlexLMP.

## Features (v0.1)

- **Home pages:** superellipse ("squircle") icons, not circles, with bold labels.
- **Glance card:** a big glass card on the first page with the time, date, a greeting and battery level.
- **Dock:** a floating glass dock with up to 5 apps.
- **Edit mode:** long-press an app, then **Edit home screen**. Icons wiggle, you can drag them to rearrange, and the other icons spring out of the way. **–** removes an app from home.
- **Drift Search:** swipe down on a home page to find apps or search the web.
- **App Shelf:** the last page. Every app, grouped into glass cards by category.
- **Animations:** spring animations everywhere. Apps open from their icon, icons squish when pressed, and home settles in when you return.
- **Settings** (⚙ in the App Shelf): accent color (AlexLMP palette), icon size, 4 or 5 columns, labels on or off, and set as default home.

## Build

Push to GitHub, and the **Actions** tab builds `DriftHome.apk`; download it from the run's artifacts.
To build locally, use Android Studio, or `gradle assembleRelease` with Gradle 8.14 and JDK 17.

## Test

    adb install DriftHome.apk

Press home and pick DriftHome, or open the App Shelf → ⚙ → **Make DriftHome the default home**.
