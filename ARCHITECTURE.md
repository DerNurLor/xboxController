> Historical initial assessment. For the current architecture and V4 implementation, read [PROJECT_VISION.md](PROJECT_VISION.md) and [FEATURE_STATUS.md](FEATURE_STATUS.md).

# Phone gamepad: initial engineering assessment

Status: requirements discovery; no application or hardware compatibility claim yet.

## Local findings

On 2026-09-29, Linux input enumeration reported an Xbox Wireless Controller
connected over Bluetooth, with gamepad, consumer-control, keyboard and mouse
interfaces. Its gamepad advertises analog axes and force feedback. A separate
Microsoft X-Box 360 pad virtual device also exists; its owner was not verified.
Neither device has yet been sampled or calibrated.

The workspace was empty. Java 21 and Android Studio are present; an Android SDK
and Gradle installation have not yet been located. ADB reports no connected phone.
The sandbox exposes input metadata but not the input device nodes.

## Architecture choices

| Transport | Host implementation | Main constraint |
| --- | --- | --- |
| Local Wi-Fi | Android app sends authenticated controller state to a companion | Requires companion installation and a shared reachable network |
| USB network or development ADB tunnel | Reuse companion and controller protocol | Phone networking support must be tested; ADB is a development option |
| Direct Bluetooth HID | Android BluetoothHidDevice service and gamepad descriptor | Requires phone HID-device support; generic HID does not ensure Windows XInput compatibility |

Recommended starting point, subject to user requirements: native Android touch
controller plus a local-network companion, Linux first and Windows next.
Keep transport separate from touch input and host device creation so a Bluetooth
backend can be added without replacing the controls.

Linux can create a gamepad using uinput. Windows needs a separately selected
virtual-controller backend for Xbox-compatible input. ViGEmBus provides an
existing approach but is retired and must not be represented as maintained.
Generic HID and Xbox/XInput compatibility must be tracked separately.

## Proposed functional scope

- Independent multitouch left/right analog sticks and stick clicks.
- Analog left/right triggers; bumpers; A/B/X/Y; eight-way D-pad;
  Menu, View and Guide. Share requires a compatible host mapping.
- Configurable dead zones, sensitivity, control size, placement and saved layouts.
- Pairing, connection status, reconnect and a host watchdog that releases every
  control after loss of input. Release on Android pause, cancellation and disconnect.
- Host-to-phone rumble where supported. A phone vibrator cannot physically
  reproduce independent controller motors or trigger motors.
- Diagnostics for control values, packet timing and connection health.

Headset audio, Xbox console authentication, proprietary accessories and physical
trigger resistance are separate capabilities, not implied by gamepad emulation.
Other target platforms must be named before compatibility can be promised.

## Protocol and reliability requirements

Use versioned, bounded messages containing complete state, sequence numbers and
session identity. Authenticate pairing and input; do not allow unauthenticated
network packets to operate a virtual controller. Reject malformed, stale and
replayed messages. Specify reconnect semantics, session expiration and a neutral
state timeout. Rate-limit output and vibration. Do not advertise force feedback
unless the host backend actually processes effects and forwards them.

## Verification plan

1. Identify Android version, HID-device support if requested, and touch capacity.
2. Read only the connected controller's gamepad events to record axis ranges,
   buttons and neutral values; do not capture unrelated keyboard events.
3. Test simultaneous controls, pointer cancellation, trigger ranges and dead zones.
4. Test protocol parsing, authentication, ordering, timeout and reconnect behavior.
5. Verify a Linux virtual device in a controller tester and actual target games.
6. Verify the Windows backend on Windows with XInput applications and rumble.
7. Measure end-to-end latency on the actual phone and network; report measurements
   rather than claim an unmeasured latency target.

## Information needed

- Phone model, Android version and whether it is rooted (root is not assumed).
- Required connection modes and acceptance of a desktop companion/driver.
- Target OS versions, games and meaning of “other” platforms.
- A phone for APK testing; USB debugging and authorization permit local deployment.
- Access to Windows hardware for Windows integration verification.

## Primary references

- Android Bluetooth HID device API: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice
- Linux uinput: https://kernel.org/doc/html/latest/input/uinput.html
- Windows input compatibility: https://learn.microsoft.com/en-us/windows/win32/xinput/xinput-and-directinput
- Windows virtual HID framework: https://learn.microsoft.com/en-us/windows-hardware/drivers/hid/virtual-hid-framework--vhf-
- ViGEmBus lifecycle: https://github.com/nefarius/ViGEmBus
