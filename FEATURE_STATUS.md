# PhonePad V4 feature status

Updated 2026-09-30. This records implementation and verification separately.

## Implemented

| Area | Current behavior |
| --- | --- |
| State and transports | One normalized PadState; unchanged 13-byte HID report; Bluetooth HID and authenticated Wi-Fi. Wi-Fi safety resets discard queued edges. |
| Pointer lifecycle | Stable pointer IDs, fresh coordinates on down/up transitions, independent source aggregation, dependent second-finger release, focus/disconnect/layout/edit resets. Normal new touch streams preserve double-tap history and explicit toggles. |
| Analog controls | Fixed/floating sticks; per-stick dead zone, sensitivity, response exponent, inversion and separate activation dimensions. Legacy shared values remain defaults. |
| Triggers | Per-control digital/edge/vertical modes, direction reversal, unified LT/RT with orientation/inversion/dead zone/travel/sensitivity/curve/floating origin; integrated racing uses physical Y independently of HID Y/inversion. |
| Clicks and combinations | Double tap, captured long press, second finger, dedicated L3/R3 and combined L3+R3. Button mappings support multiple bits and explicit LT/RT pressures with maximum trigger aggregation. |
| FPS | Relative RS, independent X/Y gain, response, smoothing, aim sensitivity, aim hold/toggle, floating fire control and camera second-finger fire. Explicit movement hold with visible cancellation. |
| Gestures/modifiers | Per-button hold/tap/double tap/long press/swipe/second finger/toggle; configurable buttons, layout/game switching, movement hold and temporary modifier. Modifier replaces button mappings and applies precision sensitivity. Tap emits a bounded 60 ms press; toggles reset on interruptions. |
| Game profiles | Named game groups containing layouts; create/duplicate/rename/delete/import/export. Whole-game copies/imports remap internal layout references. Reset preserves game ownership. General contains recoverable built-ins. |
| Persistence | Layout JSON v3; imports v2/v3 and retains legacy preferences. New controls default hidden in old layouts. Unsupported individual stored layouts remain retained but unavailable, rather than being deleted. |
| Switching | Compact layout menu, next/previous controls, optional empty-edge swipe, paged radial selector, button chord, mapped gestures, temporary hold control and LT-held aim layout. All incompatible old input is released. |
| Editor | Drag, pinch, working resize handle, separate geometry/touch-area/appearance/behavior/mapping pages, visibility, opacity, per-control haptics, grid/snap, center alignment guides, 40-step undo/redo, preview and duplication as a new layout. Gameplay is neutral while editing. |
| Templates | All 23 requested templates plus the three legacy Default/FPS/Racing presets; preview before selecting from the layout manager. Shapes and behavior differ by gameplay category. |
| Diagnostics | Final axes, triggers, hat, buttons, HID bytes, game/layout, active pointer count, aim/modifier/hold state, connection state and gyro calibration status. |
| Gyroscope | Optional angular-rate assistance, display rotation mapping, sensitivity/inversion, LT-only gating, filtering, still calibration and rest dead zone. Add to touch RS, clamp, then apply aim sensitivity; stale samples expire after 100 ms. Disabled by default. |

## Audit defects fixed

- Surviving fingers retained stale coordinates when another pointer started or ended.
- Long-press stick clicks disappeared after the thumb moved.
- Camera gain depended on an artificial minimum event interval.
- Invalid layout names changed active identity before validation.
- Wi-Fi could replay queued input after a safety reset.
- New-stream reset could destroy double-tap history and latched aim/movement state (caught during V4 adapter review).
- Custom layout reset lost game ownership; grouped copies kept references to old layouts.
- Unsupported stored layout versions were silently removed on later saves.
- Unified racing preset visually covered face controls; corrected after device screenshot review.

## Verification evidence

- Latest local suite: **744 Java input assertions**, core state/HID tests and **9 Python protocol tests** pass.
- Android suite most recently executed: **133 assertions** pass on Redmi Note 12 Pro+ 5G. Covers real MotionEvents, five simultaneous pointers, adapter double taps/toggles, editor undo/redo, game management, internal-reference remapping, future-version retention, temporary layouts, LT rules, chords and schema reload.
- Separate Android process: full saved configuration survives force-stop/restart.
- Linux companion integration passes buttons, axes, diagonal hat, force-feedback upload/play/stop/erase and neutral release.
- APK compiles, packages and passes signature verification. Android instrumentation compiles.
- Screenshot review performed for FPS Precision and Racing Unified Trigger; the unified preset needed the correction noted above.
- Live Bluetooth to this Linux PC passed **10 controls**: A/B/X/Y, LB/RB, LS/RS and LT/RT, including range/press and neutral release. Evidence: `build/bluetooth-check.json`. Corrected unified-trigger screenshot rechecked; the trigger no longer covers the face buttons.

## Practical limits and remaining validation

- No actual PC game or Windows/XInput game has been tested with this V4 build. Standard HID is not a promise of Xbox console or universal XInput compatibility.
- Hardware gyroscope presence is confirmed (`lsm6dsoq_gyro`). Pure filtering/calibration/composition tests pass; physical aiming feel, calibration under real motion and end-to-end latency remain unmeasured.
- Temporary LT rules track held independent LT, hold-AIM and unified-trigger sources. Toggle aim and racing-LS throttle/brake do not initiate temporary layouts. Old gameplay contacts are deliberately released at transitions; continuous LS/RS transfer is not implemented.
- Second-finger fire takes precedence over second-finger R3 in camera mode. Gesture categories are exclusive per mapped control; no automatic game-context detection exists.
- Empty game groups are not persisted independently: creating a game creates Layout A; deleting/moving its last layout removes that group from the list.
- Imported external layout references are disabled when their targets are absent. Keep related layouts together with whole-game export/import.
- Undo history is an editing-session convenience, not durable storage. It resets on explicit layout selection. Settings remain native menus/forms, now split into focused control-property pages.
- The palette exposes reusable existing control slots rather than unlimited arbitrary new instances. Alignment guides show the selected center; magnetic alignment snapping is not implemented.

## Final installed artifact

`build/PhonePad-debug.apk` is installed on the connected phone. Final Android run: 133 assertions passed, followed by a successful separate-process persistence run. Temporary instrumentation APK was removed; PhonePad was reopened and its Bluetooth HID profile reconnected to this PC. The latest Bluetooth check passed again after the final install.
