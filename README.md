# Macro Maker for Android

A free auto-tapper for Android phones. Place numbered circles on the screen and it taps them, in
order, as often as you set. No ads, no account, no internet: the app has no internet permission,
so nothing it does can leave your phone.

It's the phone companion to [Macro Maker for Mac](https://github.com/godman4242/MacroMaker).

## What it does

- Taps one spot, or several spots in order (up to 20), from a small floating panel over any app.
- **Tap every** N milliseconds (20 or more), with optional random ± variation.
- Stops after a number of taps, after a number of seconds, or when you press ■.
- Stops by itself if you switch its permission off mid-run.

**What it can't do**, because Android doesn't allow it for apps:
- Press keyboard keys or record your real touches. You place tap points instead.
- Tap an app that isn't on screen. Taps land on whatever is showing at that spot, so split-screen
  is the closest you get to "in the background".

Tap points are screen positions: rotating the phone moves what's underneath them.

## Install

1. Download the APK from the [Releases page](../../releases) on your phone. *(No release is
   published yet — until then, build it yourself: see "Build from source".)*
2. Open it. If Android asks, allow installs from your browser or file app.
3. Open **Macro Maker**, read the notice, then press **I agree — turn it on**.
4. In Android's Accessibility settings, tap **Macro Maker taps**, switch it on and press **Allow**.
   Android shows the same "full control" warning for every app using this permission.
5. If Android says **"Restricted setting"** (Android 13 and newer does this for downloaded apps):
   open **Settings → Apps → Macro Maker → ⋮ (top right) → Allow restricted settings**, then repeat step 4.

## Use it

1. Press **+** on the floating panel. A numbered circle appears.
2. Drag the circle onto the spot you want tapped. Add more circles to tap several spots in order.
3. Press **▶** to start and **■** to stop. The ■ button shows how many taps have landed.
4. **✕** stops and hides the panel. It stays hidden, even after a restart, until you press
   **Show the floating controls** in the app. Drag **✥** to move the panel.

⚠️ Auto-tapping can break a game's rules. Use it where it's allowed.

## Privacy

- No `INTERNET` permission (see `app/src/main/AndroidManifest.xml`).
- The Accessibility service is set to `canRetrieveWindowContent="false"` and subscribes to no
  accessibility events (`app/src/main/res/xml/tap_service.xml`): it can tap, but cannot read the screen.
- Settings stay on the phone and are excluded from backups.

## iPhone?

There is no iPhone version, and there can't be one. iOS gives apps no way to tap inside other apps,
and the App Store rejects apps that try. Two free options instead:

- **Built into iOS:** Settings → Accessibility → Switch Control → Recipes lets you record a custom
  gesture and repeat it. Triple-click the side button to stop.
- **From a Mac:** iPhone Mirroring shows your iPhone in a Mac window, and Macro Maker for Mac can
  click inside it with its "It's a game" mode (untested so far).

## Build from source

Needs JDK 17+ and the Android SDK (`compileSdk` 37).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # timing, limits and save-file rules (plain JVM)
./gradlew lintDebug            # Android lint, expected: 0 issues
./gradlew connectedDebugAndroidTest   # real taps on a running emulator or phone
```

`git config core.hooksPath .githooks` turns on the commit gate (unit tests + lint + build).

The on-device tests tap a counting button (`app/src/debug/.../CounterActivity.kt`, debug builds
only), so "50 taps" means the button really received 50 clicks. They switch the service off and
on before each test, because the test runner force-stops the app and Android only re-attaches a
force-stopped app's service when the setting changes.

### Measured on an Android 17 emulator (2026-09-27)

- Into another app (the dialer): 20 taps at the 20 ms setting finished in under 1.2 s, so at least
  16 taps per second. Real phones will differ.
- A normal-length press on ■ stopped a top-speed run 12 times out of 12.
- **Testing trap:** `adb shell input` touches are *software-injected* and Android's gesture
  injector cancels them differently from real touches. They made ■ look unreliable (4–5 out of 12)
  when hardware-level touches (`adb emu event mouse X Y 0 1`, then `... 0 0`) stopped it 12 out
  of 12. Measure touch behaviour with `adb emu event`, not `adb shell input`.
- `uiautomator dump` briefly pauses accessibility services while it runs, which reconnects this
  one. Don't use it to observe the panel mid-test.

## License

MIT — see [LICENSE](LICENSE).
