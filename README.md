# AutoMirror: phone screen mirroring on Android Auto

Shows your phone's screen on your car's Android Auto display, and lets you tap and scroll it from the car screen.

> ⚠️ Use it only while the car is parked.

## How it works
- The app registers with Android Auto as a **navigation** app, so Android Auto gives it the whole car screen to draw on (Car App Library, `NavigationTemplate` + `SurfaceCallback`).
- On the phone, **MediaProjection** captures the screen and draws it onto that surface.
- Touches on the car screen are passed to the phone through an **accessibility service**.

## Download
Get `AutoMirror.apk` from the [latest release](../../releases/latest).

## Install so it shows up in Android Auto
Android Auto hides Car App Library apps that weren't installed "from Google Play".
Neither `adb install` nor a file manager is enough. It works with **UnknownAuto**:

1. Android Auto ▸ tap **Version** 10 times ▸ ⋮ ▸ Developer settings ▸ **Unknown sources**: ON.
2. Phone: Developer options ▸ **USB debugging**: ON. Connect it by USB to a computer that has adb.
3. Install **Shizuku** (Play Store) and **UnknownAuto** (github.com/GitMag/UnknownAuto).
4. Shizuku ▸ "Start via computer" ▸ run the `adb shell .../libshizuku.so` command it shows.
5. UnknownAuto ▸ Load APK ▸ `AutoMirror.apk` ▸ **Install** ▸ "Allow all the time" in Shizuku ▸ Update. It should show ✔.
6. Accessibility ▸ AutoMirror: ON. On Samsung you may first need: Apps ▸ AutoMirror ▸ ⋮ ▸ "Allow restricted settings".
7. Restart the phone and connect to the car (wireless Android Auto works too).

Check: `adb shell dumpsys package gr.automirror | findstr /i "initiating installerPackageName"`
should show `com.android.vending`, not `com.android.shell`. The app also shows ✅ next to "Install accepted by Android Auto".

## Use
1. Connect the phone to the car and open **AutoMirror** from Android Auto's app list.
2. On the phone, open AutoMirror ▸ **Start mirroring** ▸ choose **Entire screen**.
3. Tap on the car screen = tap on the phone. To scroll, tap the pan button (cross arrows) on the car screen and drag.
4. Keep the phone **unlocked**: with the screen locked or off, the picture goes black.

## Limitations
- It can't be published on the Play Store (Google doesn't allow mirroring), so it's sideload only.
- A wide car screen with an upright phone means black bars. Turn the phone sideways (e.g. in YouTube or maps) to fill the screen.
- The app doesn't carry sound; audio plays as usual through the Android Auto / Bluetooth connection.
- Some Android Auto versions limit touch while the car is moving.

## Building
Actions ▸ Build APK ▸ **Run workflow**, or push a `v*` tag (e.g. `v1.2`). Each build becomes its own release.
Each build is signed with a new key, so before updating: uninstall the old version and repeat step 5.

To build locally: open the project in **Android Studio** (Ladybug or newer), let it sync Gradle, then Build ▸ Build APK(s).
