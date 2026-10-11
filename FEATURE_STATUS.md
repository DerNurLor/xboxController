# PhonePad V5 feature status

## Companion V2 additions — 2026-10-11

- Up to four authenticated Wi-Fi phones receive separate virtual controllers; mouse and keyboard are shared with held-input ownership. Input Monitor selects players, Connections lists them, and rumble targets the selected player. Two simulated authenticated clients were independently observed at distinct Linux controller event nodes; shared release and timeout cleanup passed. Two physical phones have not been tested.
- Mapped controls retain their original controller highlights alongside actual mouse/keyboard output. Presentation-only source metadata uses negotiated PP01 kinds 7/8 and vendor HID report 5; old gamepad/desktop reports are unchanged, and source metadata never injects controller input. Original mapped B/LT/RT verified from Android over LAN and native Bluetooth HID. After re-pairing, reconnecting with PhonePad open refreshed BlueZ's HID services and descriptor. Bluetooth capture passed source highlights without duplicate gamepad injection, X/Y, wheel, five mouse buttons, Ctrl+A, releases and SYN_REPORT. The capture handshake now waits for the instrumented phone connection before grabbing interfaces.
- Android mapper offers a multi-key Combination picker and New custom button using existing bindings, persistence and undo. Key chords hold/release together; desktop modifier presses precede ordinary keys and modifier releases follow them. Signed Android app installed with existing preferences preserved. Real Android UI test passed five assertions for custom-button creation, visual Ctrl+C selection and save/reload.
- Desktop regression suite: 97 tests passed, including QML player selection, source/output separation and receiver lifecycle. Responsive production-QML screenshots refreshed in build/companion-verification. Real phone Wi-Fi capture passed 21 assertions and Linux event observation. Physical QR scanning and vibration are confirmed by the user; this is user-reported evidence, not an additional instrumented physical measurement.

## Companion V2 verification — 2026-10-10

- Production UI uses PySide6/QML for all five sections; Python transport/input services remain shared. Compact navigation, connection rows, technical disclosures, input illustrations and temporary save notifications replace persistent explanations.
- Fixed native evdev loss recovery/keypad decoding, monitor cleanup and rapid settings persistence. Raw HID, kernel input observation, receiver state and feedback capability are reported separately.
- Hardware checks passed: Linux UDP-to-uinput mouse/keyboard/controller delivery and neutral/reconnect; Android Bluetooth mouse/keyboard to evdev; authenticated feedback through USB relay and Bluetooth output reports to Android mixer. Physical vibration is subsequently confirmed by the user; game consumption is not independently verified.
- Real-phone LAN Wi-Fi verified end to end on port 39570 with `scripts/check-desktop-wifi.py --backend linux`: Android touch/mapping and serialized controller state reached independently captured Linux mouse, keyboard and controller event nodes. Verified relative movement, wheel, five buttons, Ctrl+A, B, both sticks/triggers, diagonal D-pad, explicit release, held-input timeout cleanup and reconnect. Captures exclusively grab test-owned devices before transmission; user applications receive no test input. Port 39579 was blocked by the existing firewall.
- Pairing QR has a full-screen view with integer module scaling, live renewal/expiry and a close action. Qt screenshot decoded with the Android ZXing library; physical camera scanning is subsequently confirmed by the user.
- Responsive screenshots: build/companion-verification (800×600, 1180×800, 1600×1000).


## Editing Mode V2 — 2026-10-09

- Mapper assignments now have stable IDs, deep-copied data and independent direction/output tuning. The original opposite-direction bug came from editing control-wide sensitivity/dead-zone/inversion fields. Legacy sources migrate to separate records; existing controller mappings remain supported while their primary tab is hidden.
- Keyboard/mouse workspaces support visual selection, bidirectional drag assignment, contextual/pinned inspectors, per-device selection, zoom/pan and mapping undo/redo. Shared input glyphs appear in mapping targets and macro action previews.
- Layout editing adds multi-selection, group movement, alignment/distribution, matching dimensions, smart edge/center guides and existing grid/history integration. Behavior exposes gesture actions; selected controls have nearby macro/edit/connect actions.
- Named macro definitions and assignments are stored separately. Many-to-many references, six triggers, independent duplication and legacy migration retain existing data. The library replaces button-status lists. Structured workflows support block movement, branching, inline settings, draft recovery and undo/redo.
- Preview shares the runtime interpreter in an isolated simulation, with virtual time, Play/Pause/Step/Restart/Stop, simulated state, branch results, held outputs and an event log. It has no transport access.
- Automated core checks (962 input, 73 automation, 83 Editor V2 assertions, 30 Python tests) and the signed Android build pass. The broader Android suite passes 299 assertions; configuration and haptic preferences survive an actual process restart. Connected-phone workspace instrumentation passes 28 assertions, including actual drag dispatch, save/reload, mapping deletion/history, alignment and preview isolation. Physical PC/game output and a broad screen-density/accessibility matrix still require manual verification. Workflows intentionally expose only implemented instructions; arbitrary graph wiring/cycles and state-changing opcodes are not offered.


## Input mapping, macros and plugin game states — 2026-10-08

- Edit layout's top bar opens the mapper and macros directly. The mapper pairs a keyboard, mouse or controller illustration with the touch layout for drag-and-drop assignment. Macros have their own visual editor with linked triggers and action cards. Add offers custom buttons and mouse trackpads, including a fullscreen background.
- Keyboards, mice and controllers connected to Android can activate mappings; hardware axes scale outputs, mouse directions/wheel can be remapped, and game mode captures relative mouse input. Trackpads support pointer motion, tap, two-finger scrolling and right-click tap.
- Mapping fields use existing layout persistence, copying, undo and JSON exchange. Existing controller HID reports remain unchanged.
- Authenticated PP01 desktop frames add keyboard/mouse output through Linux uinput or Windows SendInput, independent sequence/ACK handling and neutral watchdogs. Keyboard/mouse and game-state feedback require Wi-Fi and the updated companion; Bluetooth controller transport remains supported.
- Companion exposes a local token-authenticated, TTL-limited game-state API. Plugins can drive visibility, conditional actions and event-triggered macros. No game-specific detector is bundled. See [usage and protocol](docs/AUTOMATION.md) and [example publisher](companion/examples/publish_state.py).
- The editor no longer opens button/gesture forms when a layout control is selected. Mapper and Macros are direct edit-toolbar actions. The mapper draws keyboard rows, mouse zones and controller controls beside the touch-layout targets; users can drag or tap to bind. The signed APK was rebuilt, installed and the mapper screen inspected on the connected phone. Physical peripheral mapping still needs hands-on verification.

Updated 2026-10-01. V5 implementation and verification are recorded below. V4/V4.1 evidence is retained as history. Physical game compatibility and human ergonomics are separate gates.


## Current density and editor refinement — 2026-10-01

- Overview exposes Edit layout and Change layout alongside the connection/game-mode action. Editing a built-in automatically creates a custom copy through the existing store.
- Settings use a narrower 20% rail, compact spacing and responsive preview columns. Common FPS, trigger, racing, gyro, haptic and temporary-layout options stay accessible; Advanced groups retain tuning, and dependent options appear only when applicable. The camera sensitivity shortcut updates both existing axes only when changed, preserving asymmetric values when opening the page.
- Editing is a dedicated workspace with a compact toolbar, full canvas, floating Add/layers dialogs and contextual properties opposite the selected control. Save/Back returns to Overview. Preview/Test uses isolated controller input; existing geometry, history, mappings and automatic persistence remain authoritative.
- Game mode reduces outer canvas margins while retaining safe insets and the deliberate hold handle. No schema, HID or transport changes.

Verification: 835 input assertions, 9 Python tests, the 91-field inventory, Linux input/rumble/neutral integration, APK/signature and instrumentation builds pass. Full device regression passes 241 Android assertions; the final panel placement passes 71 focused assertions. Separate-process persistence passes. Screenshot review covers settings and the editor. Evidence: `build/density-*.txt`, `build/density-build.log`, and `build/ux-review/`. The live Bluetooth rerun could not proceed: the paired host disconnected and Overview correctly kept Start game mode unavailable (`build/density-connect.txt`). Human touch ergonomics, TalkBack, physical haptics and PC game compatibility remain manual acceptance gates.

## Current tactile settings refinement — 2026-10-01

The current TASK.md refinement builds on the settings redesign below and supersedes its corner-hold and expandable-preview descriptions.

- Setup now opens an Overview with live readiness and one relevant primary action: Connect computer while disconnected, Start game mode while connected. Landscape uses a proportional navigation rail, grouped surfaces and adaptive controls/preview columns; existing categories and advanced settings remain available.
- Game mode reserves a 24 dp top strip outside controller geometry for a visible, deliberate 1.4-second hold handle and a first-entry hint. Movement, extra contacts and short taps cancel the hold. Its compact overlay offers Resume, Switch layout, Quick settings and Exit game mode. Opening or resuming that non-focusable overlay preserves held input; entering settings or leaving game mode releases it. Back and accessibility recovery remain available.
- Stick, trigger, camera/fire and haptic previews are visible within their settings pages and remain isolated from gameplay output. Trigger diagrams show direction and travel; FPS feedback distinguishes camera movement, first tap, held touch and fire. Human-readable percentages, milliseconds and contextual labels retain exact original-unit entry. Trigger and gesture feel presets report Custom after individual tuning.
- Connection shows actual transport state and paired/history-backed computer rows. Raw Bluetooth addresses stay in diagnostics. Settings continue through the existing profile/global stores: no schema, HID report or transport protocol changes, and no replacement configuration store.

Verification: **835 input assertions**, **9 Python tests**, the **91-field** settings inventory, Linux companion input/rumble/neutral integration, Android APK/signature and instrumentation builds pass. Connected-device full regression passes **224 Android assertions**; final compact-layout adjustments pass **54 focused settings/game-mode/rendering assertions**. Profile and haptic preferences survive a separate Android-process restart. Foreground captures are regenerated in `build/ux-review/`; detailed evidence is in `build/refinement-*.txt` and build logs.

Live Bluetooth to Linux also passes A/B/X/Y/LB/RB press/release and LS/RS range/neutral checks (`build/refinement-bluetooth.txt`). The host check now uses 150 ms bounded button presses instead of instantaneous ADB taps and checks neutral within each axis's kernel-declared fuzz, never its larger flat/deadzone. This host reported RY=99 with fuzz=255; exact-zero controller/HID assertions remain unchanged. Earlier failed instantaneous-tap and strict-host-zero observations are retained in the build evidence.

Remaining manual acceptance: human navigation and touch ergonomics, TalkBack gameplay, physical vibration quality and other vibrator hardware, LAN UDP feedback, host game rumble, and Windows/XInput/GTA V. Automated checks and screenshots do not establish those results.

## Earlier settings / product UX redesign — 2026-10-01

The active TASK.md redesign is implemented on top of V5. Earlier V5 draft-form descriptions below are historical and are superseded by this section.

- One landscape workspace groups Controller, Play experience, Connection, Appearance, Advanced and Diagnostics. Shared rows support toggles, sliders with exact entry, choices, text, actions and status. Narrow rows stack; help, technical tuning and interactive isolated previews expand contextually.
- Settings save through the existing stores: switches immediately, sliders on release, numeric values on Done/focus loss/navigation. Validation preserves saved values on failure. Unchanged forms never create preset copies. Undo restores the page's starting values; contextual reset requires confirmation. Only naming, import and Wi-Fi connection transactions need explicit submission. All **91 persisted fields** remain inventoried; schema-v4/v2/v3 import and the 13-byte report are unchanged.
- Setup shows layout/connection and Start game mode. Game mode has no normal app navigation, status toolbar or one-tap STOP target. A 1.4-second stationary hold in an empty top-left corner opens the neutralized pause menu; movement/second contact cancels it. Android Back and accessibility retain recovery/disconnect. Explicit game mode survives layout switching.
- Connection prioritizes paired devices reporting computer class and devices with an actual successful connection history. Show all preserves unusual receivers. Raw addresses appear in connection diagnostics; pairing is not represented as proof of availability or compatibility.
- Existing layout/game management, control instances, mappings, gestures, editor history, appearance, stick/trigger/racing tuning, camera/gyro features, haptic source separation and both transports remain available. No speculative new themes, rumble protocols or gestures were added.

Verification: core/protocol checks, **835 input assertions**, **9 Python tests**, settings inventory and Linux companion input/rumble/neutral integration pass. Android build/signature verification passes. Connected Redmi: **214 full Android assertions**, including native pause cancellation/completion, automatic saving, invalid numeric recovery, Undo, preset preservation and large-font rendering; separate Android-process profile/haptic persistence passes. Live Bluetooth input to Linux also passes A/B/X/Y/LB/RB press/release and LS/RS range/neutral (`build/settings-bluetooth-regression.txt`). Thirteen foreground captures and native instrumented renders are in `build/ux-review/`, with a review gallery at `build/ux-review/index.html`; `scripts/capture-settings.py` reproduces the foreground screens without modifying saved tuning.

Remaining acceptance: human navigation/ergonomics and TalkBack gameplay, physical vibration quality, amplitude-less/no-vibrator hardware, LAN UDP feedback, Bluetooth host game rumble, and Windows/XInput/GTA V. Those gates are not implied by automated checks or screenshots.

## V5 delivery and evidence — 2026-10-01

P0–P4 implementation is present; P5 automated and available-device checks are recorded below. Human gameplay/haptic acceptance and unavailable host paths remain open, not passed.

| Area / audited path | Reuse and implemented change |
| --- | --- |
| `PadView.onTouchEvent`, `ControllerInput` ownership | Existing captured pointers preserved; hidden admin hitboxes removed during lock, fresh-touch toolbar arbitration, 1.4 s cancellable unlock, Back/accessibility recovery and emergency disconnect. Settings/editor entry neutralizes input. |
| `ControllerInput`, `CameraFireGesture`, `ControllerConfig` | Existing relative RS, AIM toggle, LS gestures and movement hold reused. New opt-in two-thumb preset; short tap then nearby second press fires while that same pointer moves the camera. Ordinary drag cannot fire. Leaving the visible camera rectangle stops fire without losing camera capture; re-entry cannot resume fire. R3/secondary fire priorities are explicit. |
| `BluetoothPad`, `WifiPad`, existing companion FF | Report 2 bytes remain heavy/light/duration; authenticated PP01 feedback is reused without a wire change. Internal stops are distinct from received host reports; stale queued sessions cannot actuate. Input report remains 13 bytes. |
| `RumbleState`, `PhoneHaptics` | Separate heavy/light values retained until weighted RMS single-actuator mixing. Local feedback has its own lease. Bounded effects, source-specific stop, expiration, focus/pause/disconnect handling, capability checks, amplitude-less duty fallback and rate-limited actuator updates. Synthetic tests are labeled. |
| `ControllerSettings`, `SettingsScreen`, `SettingsPreview`, `SettingHelp` | Dedicated full-screen category workspace reuses existing configuration/save paths. Draft Apply/Cancel/Reset, sliders with precise entry, explanations, interactive isolated behavior previews, builtin preview/duplicate and global haptics controls. Gameplay and gyro gameplay output are neutral while settings are open. |
| `ProfileStore`, `settings-inventory.json` | Additive optional fields; schema v4 and v2/v3/v4 import compatibility retained. Inventory check covers 91 persisted scalar fields and their destination, scope, defaults and ranges. No parallel settings store. |

New production files: `CameraFireGesture.java`, `GameplayLock.java`, `RumbleState.java`, `PhoneHaptics.java`, `SettingsScreen.java`, `SettingsPreview.java`, `SettingHelp.java` in `app/src/main/java/dev/phonepad/`. Modified production paths: `ControllerConfig`, `ControllerInput`, `ControllerSettings`, `PadView`, `MainActivity`, `BluetoothPad`, `WifiPad`, `GyroInput`, `ProfileStore`. Regression changes: `tests/InputTest.java`, `tests/android/DeviceTests.java`, `scripts/test.py`, `scripts/phone-ui.py`; new settings/phone-feedback/Bluetooth-feedback check scripts and settings inventory. Existing V4.1 changes and user instruction files are not new V5 work.

Verification:

- `scripts/test.py`: core/protocol checks, **835 input assertions**, **9 Python tests**, and settings inventory coverage pass.
- APK compilation, packaging and v3 signature verification pass; artifact: `build/PhonePad-debug.apk`.
- Connected Redmi Note 12 Pro+ 5G: **201 Android assertions** pass, including real MotionEvents for two-thumb input, protected chrome, settings drafts/binding/persistence, multiple sizes and large font footer bounds. Separate Android process restart preserves profile and global haptic preferences.
- Native settings screenshots reviewed in `build/v5-review/`; corrected diagram label spacing and preset LS HOLD overlap. This is rendering/instrumentation evidence, not human gameplay approval.
- `scripts/check-linux.py`: real Linux virtual input and FF upload/play/stop/erase/neutral checks pass.
- Current-layout Bluetooth input: A/B/X/Y/LB/RB press/release and LS/RS range/neutral pass on Linux (`build/bluetooth-check.json`).
- Actual Linux FF → existing authenticated PP01 → Android mixer: heavy-only, light-only, both and stop pass with **547 authenticated callbacks**, plus local/host coexistence and lifecycle checks (`build/phone-feedback.txt`). This used the test-only USB UDP/TCP relay, not LAN UDP; production packet contents were unchanged.
- LAN ping succeeds, but the LAN UDP feedback test did not establish a session. Bluetooth rumble test found the matching PhonePad HID descriptor but could not write `/dev/hidraw3` (permission denied); zero output writes. Neither path is claimed verified.

Remaining acceptance: physical vibration quality; human two-thumb ergonomics; actual amplitude-less/no-vibrator devices; LAN UDP delivery; Bluetooth game/driver output with appropriate host access; Windows/XInput and GTA V. The phone's one actuator does not provide two spatial motors. Generic HID games need compatible mapping/feedback support. Settings search is not implemented (optional). Small confirmations/host selection remain native dialogs; major settings use the dedicated workspace.


## Implemented

| Area | Current behavior |
| --- | --- |
| State and transports | One normalized PadState; unchanged 13-byte HID report; Bluetooth HID and authenticated Wi-Fi. Wi-Fi safety resets and layout transitions discard queued edges. |
| Pointer lifecycle | Stable pointer IDs, fresh coordinates on down/up transitions, independent source aggregation, dependent second-finger release, focus/disconnect/layout/edit resets. Normal new touch streams preserve double-tap history and explicit toggles. |
| Analog controls | Fixed/floating sticks; per-stick dead zone, sensitivity, response exponent, inversion and separate activation dimensions. Legacy shared values remain defaults. |
| Triggers | Per-control digital/edge/vertical modes, direction reversal, unified LT/RT with orientation/inversion/dead zone/travel/sensitivity/curve/floating origin; integrated racing uses physical Y independently of HID Y/inversion. |
| Clicks and combinations | Double tap, captured long press, second finger, dedicated L3/R3 and combined L3+R3. Button mappings support multiple bits and explicit LT/RT pressures with maximum trigger aggregation. |
| FPS | Relative RS, independent X/Y gain, response, smoothing, aim sensitivity, aim hold/toggle, floating fire control and camera second-finger fire. Explicit movement hold with visible cancellation. |
| Gestures/modifiers | Per-button hold/tap/double tap/long press/swipe/second finger/toggle; configurable buttons, layout/game switching, movement hold and temporary modifier. Modifier replaces button mappings and applies precision sensitivity. Tap emits a bounded 60 ms press; toggles reset on interruptions. |
| Game profiles | Independently persisted named game groups, including empty groups; create/duplicate/rename/delete/import/export. Whole-game copies/imports remap internal layout references. Reset preserves game ownership. General contains recoverable built-ins. |
| Persistence | Layout JSON v4; imports v2/v3/v4 and retains legacy preferences unchanged. New writes use profiles.v4/active.v4 and games.v1. New controls default hidden in old layouts. Unsupported individual stored layouts remain retained but unavailable, rather than being deleted. |
| Switching | Compact layout menu, next/previous controls, optional empty-edge swipe, paged radial selector, button chord, mapped gestures, temporary hold control and LT-held aim layout. Optional compatible primary-stick transfer requires both layouts to opt in; all other old contacts are released. |
| Editor | Add compatible instances (128 controls per layout), duplicate/delete any control, stable IDs and editable labels; drag, pinch, working resize handle, separate geometry/touch-area/appearance/behavior/mapping pages, visibility, opacity, per-control haptics, grid/snap, center alignment guides, 40-step undo/redo, preview and duplication as a new layout. Gameplay is neutral while editing. |
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

## Historical V4 verification evidence

- V4 completion local suite: **744 Java input assertions**, core state/HID tests and **9 Python protocol tests** pass.
- V4 completion Android suite: **133 assertions** pass on Redmi Note 12 Pro+ 5G. Covers real MotionEvents, five simultaneous pointers, adapter double taps/toggles, editor undo/redo, game management, internal-reference remapping, future-version retention, temporary layouts, LT rules, chords and schema reload.
- Separate Android process: full saved configuration survives force-stop/restart.
- Linux companion integration passes buttons, axes, diagonal hat, force-feedback upload/play/stop/erase and neutral release.
- APK compiles, packages and passes signature verification. Android instrumentation compiles.
- Screenshot review performed for FPS Precision and Racing Unified Trigger; the unified preset needed the correction noted above.
- Live Bluetooth to this Linux PC passed **10 controls**: A/B/X/Y, LB/RB, LS/RS and LT/RT, including range/press and neutral release. Evidence: `build/bluetooth-check.json`. Corrected unified-trigger screenshot rechecked; the trigger no longer covers the face buttons.

## Practical limits and remaining validation

- No actual PC game or Windows/XInput game has been tested with the V4.1 build. Standard HID is not a promise of Xbox console or universal XInput compatibility.
- Hardware gyroscope presence is confirmed (`lsm6dsoq_gyro`). Pure filtering/calibration/composition tests pass; physical aiming feel, calibration under real motion and end-to-end latency remain unmeasured.
- Temporary LT rules track held independent LT, hold-AIM and unified-trigger sources. Toggle aim and racing-LS throttle/brake do not initiate temporary layouts. Other than explicitly compatible primary sticks, old contacts are released. Transfer requires matching stable ID, axis, mode, effective dead zone/gain, curve and inversion; RS also requires matching camera/aim settings and no outgoing aim/modifier or gyro. Racing-integrated LS and movement hold never transfer. Geometry may change: the captured origin is adjusted to preserve normalized output until lift. Click history and dependent fire never transfer. Focus loss, cancel and disconnect always release everything.
- Second-finger fire takes precedence over second-finger R3 in camera mode. Gesture categories are exclusive per mapped control; no automatic game-context detection exists.
- Empty groups persist independently through rename/copy/export/import/restart. The native game list lets an empty group create its first layout. Mapped switching to an empty group is a safe no-op.
- Imported external layout references are disabled when their targets are absent. Keep related layouts together with whole-game export/import.
- Undo history is an editing-session convenience, not durable storage. It resets on explicit layout selection. Settings remain native menus/forms, now split into focused control-property pages.
- Additional instances use the existing compatible control kinds, with one primary owner per stick axis and one D-pad owner. Buttons aggregate by union and triggers by maximum pressure. Per-instance geometry, hitbox, visibility and supported behavior/mapping overrides persist; unified-trigger, aim and some special-control behavior remains shared per layout. Shared standard-control settings can restore a deleted standard slot, initially hidden, on Apply.
- Alignment guides show the selected center; magnetic alignment snapping is not implemented. Native accessibility actions expose settings, layouts, connection, editor and release-all; individual canvas gameplay controls are not screen-reader virtual nodes.

## Historical V4 installed artifact

At V4 completion, `build/PhonePad-debug.apk` was installed on the connected phone. That Android run: 133 assertions passed, followed by a successful separate-process persistence run. Temporary instrumentation APK was removed; PhonePad was reopened and its Bluetooth HID profile reconnected to this PC. The latest Bluetooth check passed again after the final install.

## V4.1 changes and current verification

- P0: SigMap MCP navigation and RTK 0.50.0 verified; Git is available on main. The old environment report is corrected without removing project instructions.
- P1/P2: Native arbitrary compatible instances, deletion/duplication, full-snapshot undo/redo, schema v4, independent empty game registry and opt-in stick transfer are implemented. V4 preference keys remain an untouched rollback snapshot: installing V4 again sees the old snapshot, not edits made only in V4.1. No downgrade conversion is performed.
- P3: A native grid exposes all main settings sections in landscape. Layout/editor shortcuts are grouped separately from less frequent layout options. Native menus/forms have bounded dimensions; selected layouts are marked, duplicate controls are numbered in the palette, and forms/previews show labels rather than generated IDs. Toolbar targets extend vertically into empty space without intercepting controls. Added native accessibility actions.
- P4: Opaque controls no longer allocate opacity layers. Redraws occur on input, visual-state or status changes. Idle/editor heartbeat is 100 ms; active contacts, pulse expiry and enabled gyro retain 16 ms updates. Touch sends remain synchronous with event interpretation. Persistence field metadata is cached and excludes static constants; game lists read validated metadata instead of decoding every layout. Dense transition/save logic was separated into focused methods; no broad engine rewrite.
- Current local suite: **795 Java input assertions**, core state/HID/Wi-Fi queue tests and **9 Python protocol tests** pass. APK compilation, packaging and signature verification pass; instrumentation compiles.
- Current Android suite: **169 assertions** passed on Redmi Note 12 Pro+ 5G, including new instance CRUD/undo/redo, deleted/empty layout serialization, migration identity/rollback, failed-commit rollback, empty game CRUD, unified trigger plus both sticks, simultaneous FPS movement/camera/aim/fire, and temporary stick transfer with cancellation. Subsequent force-stop plus a separate instrumentation process passed full configuration and empty-game persistence.
- Current Linux integration passes buttons, sticks, triggers, diagonal hat, force-feedback upload/play/stop/erase and neutral release.
- Current live Bluetooth reception passes **10 controls** (A/B/X/Y, LB/RB, LS/RS, LT/RT) on the existing custom layout, including final neutral buttons, axes, triggers and hat. Evidence: `build/bluetooth-check.json`. Host verification now discovers visible instances by output mapping and rejects stale layout/viewport/orientation metadata.
- Native editor, gameplay and settings screenshots reviewed, including an added/resized button after deleting its original standard slot. Final APK is installed on the phone; no Git commit was made.

## Measured performance (same Android device)

Before rendering changes, idle gameplay rendered 6,740 frames over about 126.5 seconds (about 53 frames/s), with a 27 ms median frame and 95% reported jank. After changes, a visible idle activity rendered **0 new frames over 12.13 seconds**. Repeat with `python3 scripts/measure-performance.py`; JSON and raw gfxinfo are written under build/.

Warmed Android loops before/after metadata caching: encoding 833.4 → 651.0 µs/layout; decoding 895.5 → 744.0 µs/layout; five-pointer input update 5.74 → 5.75 µs (effectively unchanged). These are local processing/render measurements, **not end-to-end touch-to-host latency** or physical aiming measurements. The 16 ms gesture timer and Wi-Fi pacing remain; no latency improvement beyond the measured rendering/serialization work is claimed.

## Historical V5 request handoff — before implementation

This section is a request for the next coding-agent run, not evidence that the source repository contains the features. Audit live source before marking anything delivered.

| Request | Starting evidence / work required |
| --- | --- |
| Two-thumb gameplay | V4.1 has aim hold/toggle, movement hold, camera second-finger fire, gesture mappings, modifiers and quick layouts. New deterministic same-thumb camera/fire interaction, ergonomic preset and conflicts/gesture arbitration require audit, implementation and actual gameplay review. |
| Protected gameplay chrome | V4.1 has compact toolbar and enlarged targets. Add an explicit play-lock/auto-hide mechanism that cannot steal gameplay pointers; maintain safe unlock/emergency access. |
| Advanced haptics | README records a small Bluetooth output report forwarded to Android vibration; Linux integration reports FF upload/play/stop/erase. Audit exact report descriptor, Bluetooth callback, companion feedback protocol and Android vibrator implementation. Add independently represented logical motor channels, mixing, stop/watchdog, device capability handling and clear local-vs-host distinction. |
| Wi-Fi rumble | Existing Linux force-feedback integration is verified, but end-to-end host-to-phone haptics and parity with Bluetooth must be tested, not assumed. |
| Windows / real games | No actual GTA V/Windows/XInput game verification is recorded in V4.1. Do not claim it without a live test. |

Required status vocabulary: planned, implemented, automated-tested, Android-device-tested, host-feedback-tested, actual-game-tested. Record exact commands, hardware and limitations after work.


## Rumble / physical gyro correction — 2026-10-01

Implementation is automated-tested and Android-device-tested; **TASK.md acceptance remains blocked**, not actual-game-tested. Stable 13-byte HID input and report-2 output format are unchanged. Linux effect updates now affect playing effects; repeat/delay/stop/erase and bounded renewable feedback survive stale/session transitions safely. Gyro uses XYZ device-space calibration before rotation, timestamp-aware filtering, independent inherited axis gains and delayed-sample rejection. Low-speed movement above the dead zone remains detectable with high smoothing.

Verification on Redmi Note 12 Pro+ 5G (22101316UG), lsm6dsoq gyro, and this Linux host:

| Gate | Result / evidence |
| --- | --- |
| Local rumble | Synthetic source/mixer and lifecycle automated checks PASS; physical comfort not assessed. |
| Manual Bluetooth output | Prior user-observed physical vibration PASS; this run blocked by EACCES on /dev/hidraw1, zero writes. |
| Native Bluetooth EV_FF / FF_RUMBLE | Both **absent**, measured on connected native evdev device; build/native-bluetooth-ff.json. |
| Companion EV_FF / FF_RUMBLE | PASS actual uinput capability bits and upload/play/update/stop/erase; build/rumble-gyro-linux.txt. |
| Steam / Proton / GTA V | NOT TESTED; no real game-origin rumble proof. |
| Wi-Fi companion LAN | BLOCKED: no authenticated session/packets; phone and host ping succeeded. Cause unresolved; firewall inspection/capture privileges unavailable. |
| Companion through USB diagnostic relay | PASS real Linux FF producer, 547 authenticated callbacks, mixer/expiry/local coexistence; build/rumble-gyro-usb.txt. Does not establish LAN or game support. |
| Physical gyro sensor/events/rest | PASS final APK: 108 samples, about 53 Hz, zero drops, still calibration completed, filtered XYZ and mapped XY zero at rest, rotation 90. build/rumble-gyro-device-final.txt. |
| Left/right/up/down and both landscapes | Synthetic mapping PASS all four rotations/signs; physical direction and aiming feel NOT TESTED. |
| Touch + gyro, aim gating, stale release | Automated PASS; physical simultaneous aiming NOT TESTED. |

Final checks: `python3 scripts/test.py` PASS (882 Java input assertions, core suite, 13 Python tests, 93 settings fields); `python3 scripts/check-linux.py` PASS; APK build/signature/install PASS; full Android instrumentation PASS 241 assertions; separate-process persistence PASS; gyro phase PASS five assertions plus real sensor telemetry. Logs use build/rumble-gyro-* names. Phone Bluetooth was temporarily enabled for native capability measurement and restored to its original OFF state. User layouts/settings were retained. No commit created.

Remaining completion gates: resolve LAN packet delivery; permit access to the exact descriptor-matched hidraw node for a fresh BT feedback test; run a real game's rumble through the selected virtual controller; physically verify calibrated rest, horizontal/vertical directions, both landscapes, LT gating and additive thumb aim. Research and practical companion commands are in README. No kernel/identity spoof or incompatible descriptor replacement was attempted.


### Bluetooth feedback permission follow-up — 2026-10-01

User restored the PhonePad hidraw character node (the path had been a regular file) and granted this account access. Fresh bounded test **PASS**: real Linux FF producer → Bluetooth vendor output report 2 → production Android haptics, heavy-only/light-only/both/explicit stop; 614 output writes and producer exit 0. Evidence: build/rumble-gyro-bluetooth-permitted-final.txt. The device handle disappeared at instrumentation shutdown; the checker now retains the Android result instead of masking it with cleanup errors and rejects regular files as output targets. This supersedes the earlier permission-blocked fresh Bluetooth feedback gate. Physical vibration quality and actual-game rumble remain unverified; native EV_FF/FF_RUMBLE are still absent.


Physical-feedback clarification: user could not confirm vibration during the latest automated run. Android vibrator-service history records PhonePad USAGE_GAME waveform requests starting and ending, but this is not physical perception evidence. Saved Quiet mode and texture were enabled at 70% strength, yielding about 50/255 amplitude for a single motor and 71/255 for both during approximately 400 ms pulses. These settings remain unchanged. The host checker now accepts bounded --pulse-ms (100–1500) and prints the pulse start time for an attended test.


Attended attempt at 22:59:37 host time: three 1200 ms FF pulses, heavy/light/both with stops, passed transport/mixer checks (631 writes, no discovery errors). Evidence: build/rumble-bluetooth-attended.txt. Phone radio was initially OFF, temporarily enabled, and restored OFF afterward. User confirmed feeling all three pulses. Physical host-generated Bluetooth vibration is verified for this attended test; actual-game rumble and subjective quality remain separate gates.


## LAN and gyro preview follow-up — 2026-10-01

LAN failure isolated to host firewall configuration: UFW active, default incoming DROP, no PhonePad rule; Android and PC route lookups both selected wlan0 for the peer LAN address. User added a rule limited to UDP 39570, wlan0, phone 192.168.1.68 and PC 192.168.1.8. The unchanged LAN transport then passed real Linux FF heavy/light/both/stop and local-cue coexistence with 508 authenticated callbacks (build/lan-feedback-firewall-fixed.txt). VPN/network binding changes were unnecessary. Normal app also authenticated to the companion over LAN.

Gyro preview bug fixed: it depended on removed live-sensor/preview-aim switches, so sensor registration was impossible and the LT gate zeroed the display. Preview now follows Motion aiming, consumes timestamp-validated physical XYZ samples with the gameplay rotation mapping, honors independent gains/inversions, releases stale output, and unregisters/zeros on disable. The isolated preview explicitly bypasses LT and sends no gameplay input; the gameplay LT gate remains unchanged. Calibration toast no longer tells already-enabled users to enable gyro.

Built, signed and installed updated APK. Verification: 882 input assertions, 13 Python tests, 93-field inventory/core PASS; Linux integration PASS; Android full 241 assertions and separate persistence PASS. Focused gyro phase nine assertions PASS, including real preview registration/events, disable cleanup and gameplay neutrality. Phone moved during this run so still calibration remained pending; no new calibrated-rest claim. Physical directions and both landscapes await user testing. Companion --trace-feedback logs actual Linux FF upload/play/stop/erase for the GTA V Legacy build 24129523 session; no synthetic producer runs in that session.

GTA input investigation: user reports no character movement even after focusing the game. Paired evdev capture measured 1,324 PhonePad events and 1,320 Steam virtual Xbox events with matching axes; Proton has the Steam device open. A read-only XInputGetState probe in the same Proton prefix reports slot 0 connected and follows an injected phone right-stick gesture (RX 11,700 → 26,474 → 0). Slots 1–3 are disconnected. Evidence: build/gta-steam-input-observation.txt and build/gta-xinput-motion-probe.txt. This proves delivery to the game-facing API, not GTA consumption. No game-originated FF events observed yet; actual-game rumble remains unverified.

### GTA follow-up — 2026-10-06

User confirmed gameplay input works with the GTA V per-game Steam Input override disabled. The earlier game session subsequently recorded nonzero Linux FF uploads and play events in build/gta-feedback-session.txt (no synthetic FF producer in that session); Android delivery/physical game vibration still needs correlated confirmation. Y failure was resolved by moving the floating video window that covered the control; user confirmed Y now works, with no button mapping change. Host DHCP address changed from 192.168.1.8 to 192.168.1.119; companion restarted at the new address, and matching narrow UFW allowance requested. Physical gyro direction/landscape acceptance remains pending.

New-address firewall rule added by user; normal PhonePad app authenticated at 192.168.1.119 and entered game mode. Correlated capture started in build/gta-feedback-oct06.txt and build/gta-android-rumble-oct06.txt. At the observation checkpoint Android was receiving authenticated zero-rumble ACKs, while the new companion had gain events but no FF uploads; awaiting a known game vibration event and user physical confirmation.

### Actual GTA rumble verified — 2026-10-06

User triggered a known GTA vibration event and confirmed physical phone vibration. Same attended session captured 158 Linux FF uploads and 39 nonzero authenticated Wi-Fi haptic starts at the observation checkpoint, including heavy-only and light-only values. Evidence: build/gta-feedback-oct06.txt and build/gta-android-rumble-oct06.txt. No synthetic FF producer ran in this session. PASS: GTA V Legacy / Proton → companion EV_FF + FF_RUMBLE → authenticated LAN feedback → Android haptics → physical vibration, with per-game Steam Input disabled. This supersedes the earlier actual-game-unverified status for this route; native Bluetooth EV_FF remains absent. Gyro physical direction/both-landscape checks remain pending.

### X/Y and calibration follow-up — 2026-10-06

User clarified that Y/X were swapped in GTA after the video obstruction was removed. Same-prefix XInput probe reproduced phone Y → 0x4000 (X), phone X → 0x8000 (Y). Linux companion now uses legacy Xbox BTN_X/BTN_Y codes for its Xbox identity instead of geographic BTN_WEST/BTN_NORTH. Stable phone HID bits and Windows mapping unchanged. Actual Linux integration now checks X and Y independently and passed alongside rumble upload/play/update/stop/erase.

Calibration was already completing: attended sensor logs contain learned bias and zero filtered/camera output at rest in rotation 270. Added on-device messages when collection starts on return and when bias calibration completes, plus an explicit completion log. Built/signed/installed APK; 882 input assertions, core/settings inventory and Python suite PASS; focused Android gyro phase PASS nine assertions, 108 samples at 53 Hz, completed bias calibration and zero rest output. User postponed the physical LT-held gyro test; LT gating remains unchanged and physical acceptance is pending.

Updated companion is running and updated phone app reauthenticated. Post-fix Linux button regression passed, but the phone returned to another app during the attempted end-to-end XInput retest, so that post-fix physical/UI test is deferred. No post-fix GTA X/Y confirmation claimed. User will retry LT-held gyro later.

### Settings simplification — 2026-10-06

- Five main destinations: Home, Controls, Play, Connection, More. Removed duplicate home navigation and page-wide help; individual setting rows now have accessible info buttons with short explanations and hover/long-press tooltips.
- Kept advanced tuning and diagnostics accessible; gyro calibration and both sensitivity axes are in the main motion settings. Brief page/section reveal animations respect Android animation disabling. Large-text rows stack and numeric values have larger targets. Saved configuration schema is unchanged.
- Verification: 882 input assertions, 93-field inventory, 13 Python tests; installed APK and passed 249 Android regression assertions. Reviewed native settings and large-font screenshots. Hover support is implemented but was not physically tested with a mouse.

### Game menu and controller spacing — 2026-10-06

- Removed duplicate Edit layout / Change layout entries from Controls; Home retains layout access. Game menu has a resume icon, current layout, and a compact two-column grid for layout switching, quick settings, gyro calibration, and exit.
- Removed the reserved 24 dp top strip. Hold-to-open handle now overlays a small top-center area; cutout/navigation safety and editor spacing remain. No saved layout coordinates changed.
- Installed debug APK; 882 input assertions, 93-field inventory, 13 Python tests and 250 full Android assertions pass, including handle multitouch cancellation, overlay state preservation, full-height geometry, and navigation. Reviewed menu rendering. Actual GTA gameplay was not retested for this UI change.

### Navigation polish and quick layout panel — 2026-10-06

- Updated settings/menu surfaces, borders and hold-progress styling; toolbar Settings/Layout now use icons. Settings navigation keeps labels with icons and a small content gap.
- Header and Android Back return through actual settings history, including layout previews and nested forms. Leaving Setup keeps the controller outside game mode.
- Controls → Quick switch layout enables an optional panel. Outside game mode, + selects layouts, dragging the grip moves the panel, and tapping the grip attaches it to a screen edge. Selected layout IDs, position and anchor persist as device-local preferences, separate from exported layouts. Game mode permits switching but hides editing controls; switching releases existing input. Overlay touches cancel an in-progress menu hold.
- Built and installed final APK. PASS: 18 focused panel assertions, 253 full Android assertions, and profile/haptic persistence across process restart. Core suite: 882 input assertions, 93-field settings inventory and 13 Python tests. Reviewed settings and free/docked panel screenshots. Actual GTA gameplay was not retested for this UI change.

### Nearby pairing, connection UX and themes — 2026-10-07

- Connection now presents Bluetooth and Wi-Fi companion directly, suggests the companion after Bluetooth connects, and moves pairing/system/diagnostic actions under More options. The nearby list shows companion names and addresses. Discovery uses UDP broadcast plus a bounded local IPv4 sweep (up to the current /24 segment), with a saved-address fallback; it only identifies computers running a compatible companion.
- Added approved keyless first pairing: compare the eight-character check on phone/PC and approve in the companion terminal. P-256 ECDH, HKDF-SHA256 and AES-GCM provision the existing 128-bit PP01 key encrypted. New pairing has a two-minute window and cannot displace an active controller; keys are remembered by computer identity. Companion identity/key persist, and manual connection remains available for older installations. The companion now requires cryptography for discovery/pairing. No HID or input/feedback packet changes.
- More → Appearance adds Dark/Light/System and Mint/Blue/Violet/Amber device-wide choices. Controller, settings, game menu, previews and quick panel use the selected palette. Added vector Xbox-style home/view/menu and PlayStation-style home/create/face symbols; explicit custom labels and input mappings remain intact. Added gaps between right-panel settings rows.
- Verification: 882 input assertions, 93-field settings inventory, 18 Python tests (including malformed pairing, expiry, denial, encryption/tamper, stable identity and protocol compatibility), 260 Android regression assertions, 18 quick-panel assertions and profile/haptic restart persistence pass. Real Redmi ↔ Linux companion testing passed seven LAN discovery/encrypted pairing/saved-key reconnect checks, including discovery with no supplied host address; PC and Android checks matched before approval. Reviewed light theme, connection and platform-symbol screenshots. No new GTA gameplay or Windows verification claimed.

Final installed build passed all 260 Android regression assertions after preview contrast corrections. Also exercised the normal app UI (not only instrumentation): Connection → Find my computer discovered cachyos-x8664, phone/PC showed the same check, PC approval returned the app to an authenticated Wi-Fi connection and saved the computer key. The updated Linux companion remains running; existing custom layout was preserved. GTA was not launched for this check.

### Section Back, game toolbar and compact panel — 2026-10-07

- Added an accessible × close button to settings. Sidebar selection starts a fresh navigation branch; Back returns to that section’s parent, then closes at its root. Explicit close preserves the originating game/editor mode.
- Removed the Game menu popup and its calibration shortcut. Holding the top handle, tapping Exit or Android Back exits game mode directly and releases input. The game toolbar exposes Connect first, mode control, previous/current/next layout, a chooser and Settings last, including accessibility actions. Layout switching releases active input and preserves the current mode.
- Motion aiming contains visible, usable calibration even when aiming is disabled. It samples immediately in settings, emits no controller axes there, unregisters when done if aiming is off, and cancels on app pause. Temporary calibration has a 15-second timeout.
- Removed Show unified LT/RT and Steering + gas/brake activation toggles from trigger settings. Editor + Add exposes Unified LT/RT and the existing combined left-stick behavior; the left-stick editor can disable that behavior. Existing layout values and tuning remain intact.
- Enabled quick panels stay visible in game mode, with a configuration grip and an empty-panel chooser fallback. Default shortcut width is reduced; live 75–150% size and Auto/Horizontal/Vertical direction persist separately from edge position. Adding shortcuts remains outside game mode.
- Built and installed final APK. PASS: 269 full Android assertions, 22 quick-panel assertions (live sizing, direction, reload and in-game visibility), 15 real-device gyro assertions (including disabled-aim calibration in settings and pause cancellation), 882 core input assertions, 93-field inventory and 18 Python tests. Profile/haptic persistence across process restart passed. Reviewed game toolbar and panel configuration rendering. No new GTA or physical gyro direction/feel verification claimed.

### Per-layout quick-switch panel
- Centered panel contents use compact numbered shortcuts by default. The grip's configuration dialog includes Show full layout names, with names retained in accessibility labels and tooltips either way.
- Visibility, shortcut selection, name display, size, direction, edge attachment and free position persist independently under stable layout IDs, including temporary layouts. Renaming preserves settings. Legacy global preferences seed each layout once; settings remain device-local.
- Built and installed the APK. PASS: 33 focused quick-panel Android assertions, 269 full Android regression assertions, 882 core input assertions, 93-field inventory and 18 Python tests. Reviewed numbered panel and full-name configuration screenshots. No new game verification performed.

### Hidden game-mode toolbar
- Game mode hides the top toolbar and disables its touch targets, preserving controller input along the top edge. The small hold-to-exit handle and Android Back restore the toolbar; the enabled quick-switch panel remains visible.
- Built and installed the APK; all 269 Android regression assertions passed, including top-edge input and hold/Back exit behavior.

### Toolbar editor and steering pedal continuity
- Added a pencil button to the unlocked top toolbar, opening the existing layout editor and preserving built-in presets through editable copies.
- Integrated left-stick pedals now use radial distance for strength. Up selects gas, down selects brake; sideways steering retains that selection until center, opposite vertical movement, or release. Horizontal movement from center alone coasts. Existing curves, sensitivity, dead zone and separate trigger aggregation remain in use.
- Built and installed APK. PASS: 910 core input assertions, 93-field inventory, 18 Python tests and 269 Android assertions, including toolbar touch-to-editor and emitted trigger reports. Actual GTA driving feel remains unverified.

### Configuration persistence and built-in edit choices
- Device audit found saved unified triggers in GTA Driving custom copies while quick-switch panels still referenced the factory preset. Editing a built-in now asks to edit it in place or create a copy, including settings changes and control-management actions. In-place edits retain the stable ID; factory configuration remains available through Reset layout. New copies inherit panel settings and atomically replace quick-switch references. Existing saved copies are retained.
- Settings commits apply the page’s changed fields instead of replacing the whole layout with an older snapshot. Newer control additions, geometry and unrelated settings survive; stale pages cannot save into another layout. Deferred built-in choices preserve pending edits when navigating.
- Validation: 291 Android regression assertions passed, including 21 configuration checks covering unified controls, all-field JSON/copy roundtrips, export/import, layout switching, both built-in choices, shortcut replacement, and stale-page protection. Core gate passed: 910 input assertions, 93-field inventory and 18 Python tests. Actual process-restart checks passed for complete saved configuration, added unified trigger controls and non-default response settings, in-place template customization, game groups and haptic preferences. Final APK installed; no new game testing performed.

- Layout deletion: custom layout previews offer a confirmed Delete action; deleting an inactive layout preserves the active one, active deletion falls back within the same game or to Default, and stored quick-switch references and panel preferences are cleaned up. Edited factory templates can be restored without removing their shortcuts.

- Camera controls support an optional fullscreen background touch area. Foreground sticks/buttons retain priority, the editor retains the original geometry, and the option survives layout copies, export/import and restart.

### Optional LT / RT drag steering — 2026-10-08

- LT and RT can each map the held trigger finger's horizontal and vertical drag to the left stick. Both options default off, are available in Stick behavior settings and the selected trigger's Behavior editor page, and persist with layout configuration. A directly touched left stick keeps priority; releasing a trigger clears its contribution. Removed the old editor Input button for this option.
- Verification: core and Python/settings checks pass; debug APK built and installed. Device instrumentation was attempted but timed out without reporting a result; physical trigger gestures and GTA feel still need attended verification.

## Input workspace redesign — October 2026

Implemented full-screen keyboard, mouse and controller mapping diagrams with bidirectional touch/drag assignment, independent alternatives, preserved source chords, directional bindings, conflict confirmation and a collapsible/pinned contextual panel. Added a visual macro timeline with grouped editing/reordering and shared triggers while retaining the existing executor. Layout editing now uses a compact toolbar, optional layers panel, add palette and teal selection handles. Existing profile JSON remains compatible; new directional source fields are persisted and copied.

Validation: core suite passes (962 input assertions, 73 automation checks, 30 protocol tests and settings inventory); connected-phone workspace instrumentation passes 14 assertions including both real Android drag directions, persistence and saved macro execution. Desktop-game behavior with real external peripherals was not retested for this UI revision.
