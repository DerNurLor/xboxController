# PhonePad

PhonePad turns an Android phone into a virtual Xbox-layout gamepad. The Redmi Note 12 Pro+ 5G / Android 14 test device was verified over Bluetooth with this project.

## Install the APK

Build a debug APK with the pinned Android command-line tools:

```sh
python3 scripts/setup-tools.py
python3 scripts/build.py
adb install -r build/PhonePad-debug.apk
```

The ready APK is `build/PhonePad-debug.apk`. On Xiaomi devices, enable Developer options → Install via USB and approve the install prompt. The app needs Nearby devices permission for Bluetooth.

## Bluetooth

Pair the phone with the computer in Android Bluetooth settings. Open PhonePad, select **Connect → Bluetooth · choose paired host**, and choose the computer. Keep PhonePad visible: Android HID registration is foreground scoped. Linux will enumerate a standard gamepad. If Linux has a stale pairing record, disconnect and reconnect the phone once in Bluetooth settings; the host must rediscover the HID service.

The controller uses native Android touch handling with stable per-pointer ownership. Settings stays accessible at the upper right. Connection status and details live in the connection menu.

### V4 layouts and controls

**Settings → Game profiles** manages games containing multiple layouts. **Profiles & layout** manages layouts and previews templates before selection. All 23 requested templates are available alongside legacy Default/FPS/Racing. Built-ins remain recoverable; edits save to custom copies. Version-2 layouts and legacy preferences remain supported by the version-3 schema.

Tap **LAYOUT** during gameplay for quick switching. Configure next/previous controls, empty-edge swipes, radial selection, button combinations, a held temporary layout or an LT-held precision layout in **Profiles & layout → Temporary layout**. Switching releases old inputs; temporary selection restores on release or interruption without changing the saved selection.

**Controls / restore hidden** exposes unified LT/RT, dedicated L3/R3, L3+R3, Aim, floating Fire, LS Hold and Modifier. Controls have independent geometry, touch-area dimensions, opacity and haptics. Sticks support fixed/floating origins, per-stick dead zone, gain, response and inversion; RS also supports relative camera mode. Trigger modes can be set independently. Unified LT/RT has a configurable origin, travel, orientation, curve, dead zone and direction; up activates RT by default. Racing derives trigger pressure from physical LS Y even when HID Y is disabled or inverted.

**FPS & aiming** configures aim hold/toggle, precision gain, independent camera X/Y response, filtering, second-finger camera fire and explicit movement hold. LS Hold keeps the current LS output until tapped again or a new LS touch starts. Interruptions cancel all latched input.

Button properties support combined buttons and explicit trigger pressures, alternate modifier mappings, and exclusive hold/tap/double-tap/long-press/swipe/second-finger/toggle gestures. Actions can emit inputs, switch layouts/games, hold a modifier or toggle movement hold. Camera secondary fire takes precedence over the secondary R3 gesture.

**Edit layout** supports drag, pinch, a resize handle, focused property pages, grid/snap, center alignment guides, undo/redo and a read-only preview. Gameplay remains neutral while editing. Duplicate a layout to save another arrangement. **Controller test** shows final controller state, HID bytes, layout, pointers and active modes.

**Gyroscope** optionally adds calibrated angular-rate input to RS, with filtering, inversion, sensitivity and LT-only activation. It is disabled by default. Calibration needs the phone still for one second after closing Settings; no orientation is accumulated. Touch and gyro add, clamp to range, then use aim sensitivity. The sensor adapter follows Android's [gyroscope units and coordinate conventions](https://developer.android.com/reference/android/hardware/SensorEvent).

See [FEATURE_STATUS.md](FEATURE_STATUS.md) for verification levels and limitations, and [TEST_CHECKLIST.md](TEST_CHECKLIST.md) for repeatable scenarios.

This uses Android `BluetoothHidDevice`, available from API 28. The HID descriptor is a standard gamepad layout with 16 buttons, an eight-way hat, signed 16-bit sticks and 8-bit triggers. Bluetooth rumble is represented by a small output report and is forwarded to the phone vibrator where the host sends it.

## Wi-Fi companion

The companion uses authenticated UDP with a 128-bit pairing key, sequence numbers, replay rejection, and a 500 ms neutral-state watchdog:

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r companion/requirements.txt
python3 companion/phonepad.py --backend linux --key-file phonepad.key
```

Enter the computer’s LAN address, port `39570`, and the printed 32-character key in PhonePad’s **Connect → Wi-Fi · connect to companion** dialog. Do not expose the UDP port to the internet; use a trusted LAN or firewall rule. The key file is created with mode 0600.

Linux creates `PhonePad Virtual Xbox Layout` using `/dev/uinput`. Add the user to the group allowed to write `/dev/uinput`, or run the companion with the appropriate local permission. Windows can use `--backend windows` with `vgamepad`; its dependency installs a ViGEmBus-based Xbox 360 virtual device. ViGEmBus is retired upstream, so Windows deployment should be reviewed and tested on the target machine. DirectInput-compatible games can use the Linux HID backend or Windows’ controller mapping tools; XInput-only games require an Xbox-compatible Windows backend.

Use `--backend dry-run` to inspect decoded states without creating an input device. The companion validates packet size, HMAC, session identity, ordering, value ranges, and stale input. It sends neutral state during shutdown and link loss.

## Verification

```sh
python3 scripts/test.py
python3 scripts/check-linux.py
python3 scripts/build-device-tests.py
adb install -r build/device-tests/PhonePad-tests.apk
adb shell am instrument -w dev.phonepad.tests/dev.phonepad.DeviceTests
adb shell am force-stop dev.phonepad
adb shell am instrument -w -e phase persistence dev.phonepad.tests/dev.phonepad.DeviceTests
adb uninstall dev.phonepad.tests
```

`test.py` runs the protocol/core tests and 744 input assertions covering pointer capture, independent trigger ranges/directions, both sticks, camera deltas, stick clicks, integrated racing diagonals, releases and HID encoding. Android instrumentation exercises real MotionEvents, the native editor, profile CRUD/migration and persistence, cancellation, a simulated Bluetooth disconnection callback, and multiple view sizes. The second instrumentation invocation verifies full profile persistence in a different Android process. Instrumentation uses isolated preference files and does not send test gameplay input to a host.

`check-linux.py` creates a temporary virtual device and removes it automatically. `check-bluetooth.py` now uses geometry exported from the actual Android view by instrumentation, checks the current layout and viewport, and validates host-received controls over a real Bluetooth connection. It still requires an attached phone paired with this host; game testing is a separate step.

## Scope limits

The app does not emulate Xbox console authentication, proprietary accessories, headset audio, or physical trigger resistance. Console compatibility is not promised: consoles generally require platform-specific authentication and a supported USB/Bluetooth identity. Phone vibration cannot reproduce independent Xbox motor hardware.
