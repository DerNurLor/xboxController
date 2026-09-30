# PhonePad second-pass audit

## Findings in the previous implementation

| Path | Finding | Resolution |
|---|---|---|
| PadState → BluetoothHidDevice.sendReport | Signed 16-bit axes, unsigned 8-bit triggers and eight-way hat encoding were usable; existing core tests exercised them. | Retained report format and transport. |
| Pointer map | Pointer IDs existed, but stick-click timing was global and only final ACTION_UP generated clicks. The click bit could survive without another touch event. | ControllerInput owns each pointer and stick timing; second-touch click/long press lasts only for the owning pointer. |
| Stick coordinates | Fixed centers used model X while hit tests used an aspect-ratio-dependent display offset. Floating LS was hardcoded. | One safe-surface coordinate system; configurable left/right modes. |
| Camera option | Used displacement from touchdown, effectively another fixed joystick. | Frame deltas divided by elapsed time, bounded to RS; idle timeout and release neutralize. |
| Vertical trigger | Formula mixed absolute center coordinates and touchdown Y. Both trigger modes drew the same segmented horizontal meter. | Relative movement/travel mapping; vertical fill, circular edge feedback, digital button appearance. |
| Profile selection | Default selection did nothing; spinner initialization could reapply FPS/Racing; name capitalization disagreed. | Explicit profile manager with immutable generated presets and stable custom IDs. |
| Layout save | Saved X/Y/size but not changed width/height or visibility. | Complete versioned config, normalized geometry, deep copies, typed validation. |
| Reset | Removed only some keys, leaving behavior settings behind. | Reset the entire profile from its recorded base preset. |
| Editor | Grid-snap label actually ran incomplete smart-snapping math. Save hit target did not match drawing. | Actual grid snap, shared toolbar dimensions, precise native property forms and control palette. |
| Controller test | Modal blocked gameplay surface and focus loss neutralized the values it was meant to test. | Nonmodal overlay drawn in the controller view. |
| Haptics | One generic feedback flag; no levels. | Short amplitude/duration choices: Off/Light/Medium/Strong. |
| Disconnect | Transport could lose its host while the UI pointer map remained held. | Explicit disconnect callback releases UI state and cached reports; tick also covers Wi-Fi loss. |
| Gameplay UI | Persistent bottom status and segmented trigger meters contradicted prior completion claims. | Removed from gameplay; details remain in Connection/test. |

The previous final messages overstated completion. Compilation and protocol-only tests had not verified the new UI/input behavior.

## Current architecture and trace

`MotionEvent (pointerId)` → `ControllerInput` → existing `PadState` → existing `PadState.report()` → `MainActivity.Actions.changed()` → `BluetoothPad.send()` → Android `BluetoothHidDevice.sendReport(host, 1, report)`.

Wi-Fi uses the same final report via its existing queue. Editing/focus loss/profile switch release the input engine first and publish neutral. Bluetooth loss invokes the same release path immediately. There is no replacement Bluetooth stack or new dependency.

- ABXY, shoulders and center buttons retain pointer capture until release/cancel.
- D-pad uses a coherent eight-direction hat region.
- LS/RS use circular analog bounds, radial stick dead zone and sensitivity.
- Camera mode derives RS from deltas, not a fixed origin; stationary touch returns to zero.
- Independent triggers use touchdown-relative displacement or digital hold.
- Racing calculates LT/RT from physical vertical LS displacement with its own dead zone and curve. Separate visible trigger values combine by maximum, independent of pointer insertion order.
- Test mode reads the actual final state and the same bytes supplied to the transport callback.

## Verification and limits

- Pure Java tests cover 447 assertions, including all trigger ranges, mirrored/reversed directions, both sticks, buttons, eight hat directions, double tap/long press, camera reversal, five pointers, and racing diagonals through HID report bytes.
- Android instrumentation covers real MotionEvents, edit isolation/pinch, complete JSON configuration reload, profile operations, migration/defaults, a simulated Bluetooth disconnect callback, and multiple viewport sizes.
- Separate instrumentation invocation after process stop verifies profile persistence in a different Android process.
- Native Default/FPS/Racing renders are inspected from instrumentation screenshots.
- Existing protocol and core tests are retained. Compiler warnings are existing deprecated Android APIs, not compilation failures.
- This pass validates the generated report and transport callback path, not reception in a PC game over a live radio connection. The legacy host gesture script still uses old coordinates and must not be presented as current verification.
- Old builds never saved resized dimensions; migration cannot recover values they did not persist. Old X/Y/size and known behavior preferences are preserved in a custom copy.
- Axis assistance, overlay click zones, and rotation are not exposed as unimplemented switches. Trigger dead zone handles small steering deviations; long press/double tap cover stick clicks.

## Completed run

- APK compiled, signature verified, and installed on the connected Redmi Note 12 Pro+ 5G.
- Core tests and all 9 existing protocol tests passed.
- 447 Java input assertions passed.
- 79 Android assertions passed, including native pinch, pointer combinations, schema defaults/migration, custom-layout operations and disconnect callback release.
- Full-profile persistence passed in a second Android process after force-stop.
- Temporary `dev.phonepad.tests` APK removed after verification; PhonePad launched again.
