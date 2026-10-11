# PhonePad

**Turn your Android phone into a customizable PC gamepad.**

PhonePad is an open-source Android controller that turns a touchscreen phone into a configurable Xbox-style gamepad for PC gaming. It supports native Bluetooth HID input as well as a companion-based Wi-Fi connection, advanced touch layouts, motion aiming, and game rumble forwarded back to the phone.

> PhonePad is currently under active development. Linux is the most extensively tested host platform.

## Highlights

- **Android phone as a gamepad** — sticks, D-pad, buttons, bumpers, triggers, stick clicks, and custom controls.
- **Bluetooth HID** — connect directly to a computer as a standard gamepad without a companion for normal controller input.
- **Wi-Fi companion** — low-latency authenticated input with a virtual controller on the host.
- **Host game rumble on Linux** — force-feedback events from supported games can be sent back to the phone over the companion connection.
- **Custom layouts** — built-in presets plus editable layouts with drag, resize, pinch, snapping, alignment guides, undo/redo, and per-control properties.
- **FPS / two-thumb controls** — use large touch regions for movement and camera control, with configurable aim, fire, movement hold, and gesture behavior.
- **Racing controls** — analog trigger pressure and layouts intended for driving games.
- **Gyroscope aiming** — calibrated motion input can be mixed with the right stick and optionally enabled only while aiming.
- **Game Mode** — hides the top bar while playing. Hold the small top handle or use Android Back to exit and restore the bar. The quick-switch panel remains available when enabled.
- **Haptics** — configurable local touch feedback plus host-driven rumble where the selected transport/backend supports it.
- **Layout switching** — quick switching, button combinations, edge gestures, temporary layouts, and LT-held precision layouts.
- **Diagnostics and controller testing** — inspect controller state, HID data, pointers, active modes, motion data, and feedback events.

## How it works

PhonePad provides two main ways to connect to a PC.

### Bluetooth

Android's `BluetoothHidDevice` API exposes the phone as a composite HID device with gamepad, keyboard and mouse reports. Pair the phone and computer, then connect from PhonePad. The host should enumerate all three input devices. If Linux cached an older gamepad-only descriptor, remove the phone from the host's Bluetooth devices, pair it again and reconnect so Linux fetches the current descriptor.

Native Bluetooth currently uses a generic HID identity. On the tested Linux setup it does **not** expose `EV_FF` / `FF_RUMBLE`, so ordinary games cannot automatically send standard Linux force feedback back through this Bluetooth device.

### Wi-Fi companion

For the full Linux experience, including verified game rumble, PhonePad can communicate with the PC companion over the local network.

The companion creates virtual controller, keyboard, and mouse devices through `/dev/uinput`, receives authenticated controller state from the phone, and can forward Linux force-feedback events back to Android. Some Linux desktops do not grant the active session access to newly created virtual input devices. If Companion reports this at startup, install the included udev rule with `sudo companion/install-linux-input-rule.sh`, then restart the companion and reconnect PhonePad.

The protocol uses a 128-bit pairing key, sequence numbers, replay rejection, validation, and a neutral-state watchdog.

## Controller experience

PhonePad is designed around layouts rather than one fixed virtual controller.

Layouts can contain independent sticks, buttons, D-pad controls, bumpers, triggers, unified LT/RT controls, Aim, Fire, LS Hold, Modifier, L3/R3, and combined controls. Each control can have its own position, touch area, opacity, haptic behavior, and input mapping.

Sticks support fixed or floating origins, dead zones, sensitivity/gain, response tuning, inversion, and relative camera behavior. Controls can also use gestures such as tap, hold, double-tap, long-press, swipe, second-finger actions, and toggles.

The layout editor supports direct manipulation, resizing, pinch gestures, grid/snap, alignment guides, focused property editing, undo/redo, and previewing without sending gameplay input.

## FPS and motion aiming

PhonePad includes a two-thumb FPS workflow intended to make touchscreen control closer to a physical controller while keeping both thumbs available for movement and camera input.

It supports:

- aim hold or toggle;
- configurable aim sensitivity;
- independent camera X/Y tuning;
- response curves and smoothing;
- same-thumb camera/fire interaction;
- optional second-finger fire;
- movement hold;
- floating controls;
- gyroscope-assisted right-stick input.

Gyroscope input is calibrated in device coordinates and remapped for display rotation. Touch and motion input can be combined, and motion aiming can be configured to operate continuously or only while LT/aim is active.

## Rumble and haptics

PhonePad distinguishes between **local touch haptics** and **feedback originating from the game**.

With the Linux Wi-Fi companion, standard Linux force-feedback events can be captured from the virtual controller and translated into heavy/light logical rumble channels. Android then mixes those channels for the phone's vibration hardware.

This path has been tested with **GTA V Legacy under Proton**: the game generated Linux force-feedback events, the companion forwarded non-zero authenticated feedback to Android, and physical phone vibration was confirmed during gameplay.

A phone generally has different vibration hardware from an Xbox controller, so PhonePad does not claim to reproduce two independent physical Xbox rumble motors exactly.

## Build and install

Requirements include Python 3, ADB, and an Android device supported by the project's Android build setup.

```sh
python3 scripts/setup-tools.py
python3 scripts/build.py
adb install -r build/PhonePad-debug.apk
```

The resulting APK is:

```text
build/PhonePad-debug.apk
```

On Xiaomi devices you may need to enable **Developer options → Install via USB** and approve the installation prompt.

Bluetooth operation requires Android's Nearby devices permission.

## Bluetooth quick start

1. Pair the phone and computer in Android Bluetooth settings.
2. Open PhonePad.
3. Open the connection screen and choose the computer.
4. Start Game Mode when you are ready to play.

Android HID registration is foreground-scoped, so keep PhonePad active while establishing the connection.

If a Linux host has cached an older HID service, disconnecting and reconnecting the phone in Bluetooth settings can force service rediscovery.

## Linux companion quick start

Create the Python environment and start the companion:

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r companion/requirements.txt
python3 companion/phonepad.py --backend linux --key-file phonepad.key
```

### Desktop app

The companion also has a standalone desktop interface for Linux and Windows. It bridges either Wi-Fi or the phone Bluetooth HID connection. Install the same companion requirements above, then launch:

```sh
python3 companion/phonepad_gui.py
```

The desktop interface uses PySide6 / Qt Quick. Use the environment where the companion requirements were installed. Dashboard, Connections, Input Monitor, Settings, and Diagnostics share the same receiver service. Settings persist in `~/.config/phonepad/settings.json`; saving restarts only affected receivers. Connections can start or stop Wi-Fi and Bluetooth monitoring independently; changing Bluetooth monitoring preserves an authenticated Wi-Fi session. Wi-Fi pairing is available after startup; one-time QR codes expire after five minutes and can be regenerated in Connections. Bluetooth uses native Linux HID by default: pairing, a connected audio profile, and a usable HID device are shown separately. Companion observes native HID without injecting duplicate input. The terminal's explicit `--bluetooth-path` bridge remains available for virtual-controller compatibility. Input Monitor observes real events; Start test enables bounded event history. No game-consumption or end-to-end latency claim is inferred from backend writes.

Wi-Fi discovery uses UDP `39570`; QR scanning bypasses discovery but still needs the companion port reachable. Existing saved addresses are also checked. The default companion key and identity persist in `~/.config/phonepad/`; `--key-file` retains compatibility with existing keys. First pairing uses P-256 ECDH, HKDF-SHA256, and AES-GCM. Older companions can use **Manual connection**. The terminal version remains available. `--no-discovery` disables discovery and first-time wireless pairing. Linux virtual input needs `/dev/uinput` access; its virtual event devices also need active-session access. Install the included udev rule with `sudo companion/install-linux-input-rule.sh`, then reconnect or restart the desktop session if needed. The authenticated Wi-Fi controller/rumble protocol is unchanged.

In PhonePad, open **Connection → Wi-Fi companion**. Scanning the desktop QR code connects and remembers the computer automatically. You can also use **Find my computer** and approve the encrypted first pairing with the matching eight-character check.

Approval-based pairing is available for two minutes after companion startup. Type `pair` in its terminal to reopen that window. QR pairing has a separate one-time five-minute code. Discovery lists computers running the updated companion on the local network; it does not identify every computer on the network. It uses UDP `39570`, broadcast and a bounded local IPv4 sweep (up to the phone’s local /24 segment). Guest Wi-Fi isolation or host firewalls can prevent discovery; QR scanning bypasses discovery but still needs the companion port reachable. Existing saved addresses are also checked.

The default companion key and identity persist in `~/.config/phonepad/`; `--key-file` retains compatibility with an existing key. Keep those files private. First pairing uses P-256 ECDH, HKDF-SHA256 and AES-GCM; matching the check before PC approval protects the first exchange. Discovery names themselves are not authenticated. The authenticated PP01 controller/rumble protocol remains unchanged.

For older companions or blocked discovery, use **Manual connection**. Start the companion with `--show-key` to display the key for this fallback. Hold a remembered computer in the nearby list to forget its saved key. `--no-discovery` disables discovery and first-time wireless pairing.

The Linux backend creates:

```text
PhonePad Virtual Xbox Layout
```

through `/dev/uinput`.

Your user must have permission to write to `/dev/uinput`. The companion also needs the desktop session to be able to read its virtual event devices; the included udev rule grants access to the active local session for PhonePad's virtual controller, keyboard, and mouse only.

Do **not** expose UDP port `39570` directly to the internet. Use PhonePad on a trusted local network and configure the firewall appropriately.

For force-feedback debugging:

```sh
python3 companion/phonepad.py --backend linux --key-file phonepad.key --trace-feedback
```

## Windows

The companion contains a Windows backend using `vgamepad`, which can create an Xbox 360-style virtual device through ViGEmBus.

ViGEmBus is retired upstream, so Windows support should currently be considered less mature than the tested Linux path and should be validated on the target system.

## Testing

Run the core and host-side checks with:

```sh
python3 scripts/test.py
python3 scripts/check-linux.py
```

Android device tests can be built and executed with:

```sh
python3 scripts/build-device-tests.py
adb install -r build/device-tests/PhonePad-tests.apk
adb shell am instrument -w dev.phonepad.tests/dev.phonepad.DeviceTests
adb shell am force-stop dev.phonepad
adb shell am instrument -w -e phase persistence dev.phonepad.tests/dev.phonepad.DeviceTests
adb uninstall dev.phonepad.tests
```

The test suite covers controller state and HID encoding, multitouch/pointer ownership, sticks and triggers, camera behavior, releases, layout/profile persistence and migration, editor behavior, cancellation, multiple view sizes, and simulated connection lifecycle behavior.

For detailed verification status and remaining acceptance tests, see [`FEATURE_STATUS.md`](FEATURE_STATUS.md). Repeatable manual scenarios are documented in [`TEST_CHECKLIST.md`](TEST_CHECKLIST.md).

## Current compatibility

| Feature | Status |
| --- | --- |
| Android touch controller | Supported |
| Native Bluetooth gamepad, keyboard and mouse HID | Supported; Linux enumerates all three input devices |
| Custom layouts and editor | Supported |
| Game Mode | Supported |
| Wi-Fi companion | Supported |
| Linux virtual controller | Supported |
| Linux game rumble → phone over Wi-Fi | Verified |
| Gyroscope aiming | Implemented; physical direction/feel still requires broader device testing |
| Native Bluetooth game rumble on Linux | Not available through the current generic HID device |
| Windows virtual Xbox-style backend | Implemented; needs broader testing |
| Xbox console authentication | Not supported |
| Headset audio / proprietary Xbox accessories | Not supported |
| Physical adaptive/force-resistant triggers | Not supported |

## Tested setup

Development and attended testing have included:

- Android 14 on a Redmi Note 12 Pro+ 5G;
- Linux host;
- native Android Bluetooth HID input;
- Linux `/dev/uinput` companion controller;
- authenticated LAN transport;
- GTA V Legacy under Proton;
- game-generated force feedback forwarded to the phone.

In the tested GTA V Legacy / Proton setup, **Steam Input had to be disabled for GTA V** before the game accepted the companion's controller. This is a setup-specific workaround rather than a universal requirement.

## Project status

PhonePad is an experimental but functional project. The core controller, layout system, editor, Bluetooth input, Wi-Fi companion, Linux virtual controller, haptics pipeline, and motion aiming are implemented, but hardware/OS/game combinations still require broader real-world testing.

The project deliberately does **not** emulate Xbox console authentication or claim native Xbox hardware identity. Console compatibility is therefore outside the current scope.

## Documentation

- [`FEATURE_STATUS.md`](FEATURE_STATUS.md) — detailed implementation and verification status.
- [`TEST_CHECKLIST.md`](TEST_CHECKLIST.md) — repeatable validation scenarios.
- [`settings-inventory.json`](settings-inventory.json) — persisted settings inventory.

## License

No license is stated in the current project documentation. Add a `LICENSE` file before describing the project as freely redistributable or accepting reuse under a specific open-source license.

### Appearance

**More → Appearance** offers Dark, Light or System themes and Mint, Blue, Violet or Amber accents. These preferences are device-wide and do not change exported layouts or input mappings. Xbox/PlayStation label selection remains per layout and now uses vector home, menu/view and PlayStation face-button symbols. Explicit custom labels take precedence. Settings rows have additional vertical spacing.

### Settings and quick controls

Use **×** to close settings immediately. Back returns through the current sidebar section; selecting another section starts a fresh branch, and Back at its root closes settings.

**Play → Motion aiming → Calibrate gyro** works directly in settings, including while motion aiming is off. Keep the phone still for one second. Calibration does not enable aiming or send controller input.

Add **Unified LT / RT** through the layout editor’s **+ Add** menu. **Steering + gas / brake (left stick)** in that menu enables the existing combined left-stick behavior; the editor’s left-stick switch restores normal steering. The ordinary trigger settings retain tuning without adding/removing controls.

An enabled quick-switch panel remains available during game mode. Its centered shortcuts show numbers by default; tap the grip and enable **Show full layout names** to display names. The grip also opens a live size slider (75–150%), Auto/Horizontal/Vertical direction and edge attachment; drag it to move the panel. Each layout independently remembers visibility, shortcuts, name display, size, direction and position. Existing panel settings supply the initial defaults. **+** adds/removes shortcuts outside game mode.

The top bar includes a pencil button to edit the current layout. Left-stick gas/brake selects gas with an upward push or brake with a downward push; pedal strength follows distance from center while steering sideways. Return to center or lift your finger to coast.

Editing a built-in layout asks whether to **Edit this layout** or **Create a copy**. In-place edits retain its identity and shortcuts; a copy inherits the panel configuration and replaces references to the original in quick-switch panels. Reset layout restores factory controls. Settings saves preserve unrelated controls and changes made since the page opened.

To delete a saved custom layout, open **Choose layout**, select it, then tap **Delete layout** and confirm. Its quick-switch shortcuts are removed automatically. Built-in templates are protected; edited templates offer **Restore built-in layout** instead.

Enable **Settings → Play → Camera & aiming → Fullscreen camera background** to aim by swiping empty screen space behind other controls. The same option is available in the right stick’s editor behavior settings. It is saved per control and layout; turning it off restores the original camera area.
