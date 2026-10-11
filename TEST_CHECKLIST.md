# Repeatable verification

## Automated

- `python3 scripts/test.py`: Java state/HID/input regressions and Python authenticated protocol tests.
- `python3 scripts/build.py`: compile/package/sign/verify Android APK.
- `python3 scripts/build-device-tests.py`: compile Android instrumentation.
- With authorized hardware: install both APKs; run `adb shell am instrument -w dev.phonepad.tests/dev.phonepad.DeviceTests`; force-stop app and run again with `-e phase persistence`.

## Input acceptance

- Every button, eight hat directions, signed sticks and unsigned triggers reach expected HID values.
- Simultaneously hold LS + RS + RT + A + RB; lift each independently and cancel remaining pointers.
- Check pointer down/up events that also move an existing finger, pointer-ID reuse and fresh DOWN after interruption.
- Floating origin starts at touchdown; fixed origin stays at configured center.
- Double tap and stationary long press own L3/R3 until release. Drag after long press without dropping click.
- Camera reverses without crossing origin, handles fast events, becomes neutral while stationary and on release.
- Racing: diagonal steering/throttle/brake, independent trigger dead zone, LS Y suppression and maximum aggregation with separate triggers.
- Optional LT / RT drag steering: either trigger maps finger movement in all four directions to LS; each is disabled by default, configurable under Behavior in the layout editor and in Stick behavior settings, survives profile save/reload, and yields to a directly touched LS; releasing the trigger returns the mapped axes to neutral.
- Focus loss, screen interruption, disconnect, resize, edit entry, profile/layout changes produce neutral and invalidate old owners.

## Configuration and UI

- Create, rename, duplicate, reset, import/export and restart; all geometry and behavior persist.
- Reject empty names/malformed JSON without changing active selection or losing custom data.
- Edit mode never emits gameplay; test drag/pinch/numeric dimensions, hidden-control restoration and grid snapping.
- Inspect landscape safe areas and touch targets across aspect ratios.

## Hardware gates

V4 historically passed instrumentation and live Bluetooth reception on Redmi Note 12 Pro+ 5G. V4.1 current results are recorded separately in FEATURE_STATUS.md. Human multi-finger ergonomics, physical gyro aiming and actual PC-game compatibility remain separate verification gates. A compiled APK or passing logical-state test does not satisfy those gates.

## V4 regression scenarios

- Unified trigger: both orientations, inversion, fixed/floating origin, dead zone, curve, origin return, release/cancel; LS + RS + unified at once. Check its visual track does not cover face buttons.
- Same-button dual ownership; dedicated L3 overlapping combined L3+R3; releasing one source must preserve the other.
- Long press then drag; double tap across distinct Android DOWN/UP streams; second-finger click releases with its primary stick.
- Camera + aim + secondary fire; fire release leaves camera captured, primary release cancels dependent fire. Compare equal camera speed at 2/8/16/32 ms event intervals.
- Aim toggle and movement hold survive the next ordinary touch stream, but not focus loss/cancel/disconnect/layout changes.
- Mapped tap pulses expire; mapped toggle/long press/swipe/second finger behave independently. Gesture modifier works regardless of pointer insertion order.
- Layout chord switches once; temp hold returns on UP/CANCEL/focus loss; LT-driven precision retains LT pressure and returns on release or analog neutral.
- Two-layout game copy/export/import retargets hold/aim/layout actions within the copied group; reset retains game membership. Rename updates game-action destinations.
- Opening ordinary settings does not copy a preset; explicitly editing a built-in creates and selects a custom copy without modifying the original. Drag/pinch/resize handle stay neutral. Undo/redo restores full configuration; changing layouts clears history.
- Version-2 migration preserves existing controls, with all new optional controls hidden; unsupported future layouts remain in storage after another layout is saved.
- Gyro: still calibration removes small bias, moving calibration rejects samples, dead zone removes rest noise, stale data expires, aim-only gating works, touch+gyro clamp and cancellation are predictable.

## Live host verification

The revised `scripts/check-bluetooth.py` consumes `cache/live-layout.json` exported by Android instrumentation from the restored actual view. It checks current layout identity/configuration, settled viewport size and display rotation before issuing gestures, and reads only the PhonePad gamepad device. It discovers visible controls by logical mapping rather than fixed slot IDs. Run instrumentation first, return to PhonePad, connect Bluetooth to this host, then run `python3 scripts/check-bluetooth.py`. Do not reuse metadata after changing layout, viewport or orientation.

This verifies host-received ordinary buttons, stick/trigger ranges and neutral releases. Native multitouch and advanced modes have separate instrumentation/pure-engine coverage; a PC-game session and human ergonomics review remain separate gates.

## V4.1 acceptance and regression

- Native editor: add each compatible kind; duplicate buttons, sticks and triggers; rename labels; position/resize independently; edit visibility/hitbox; delete standard and added instances; undo/redo each operation. Verify stable IDs and complete settings through save, import/export and process restart. Deleting every control must stay empty on reload.
- Try two visual LS instances: the first primary owner keeps the axis until release; a fresh touch can then own the other. Same-button instances retain independent owners. Trigger sources still aggregate by maximum pressure.
- Schema: load v2/v3 profiles; verify unchanged legacy preference keys and active identity when v4 keys are created. Reject duplicate IDs, invalid kinds and incompatible reserved IDs. Retain unsupported future-version entries. Inject commit failure: registry/layout creation and editor history must remain recoverable.
- Empty games: create, rename, duplicate, export/import, delete; delete/move the last layout and restart. Empty game selection must offer creation rather than index an empty list. Mapped switching to an empty game must not crash.
- Transfer: enable in both layouts; duplicate a layout to preserve IDs, move/resize its sticks, and switch with NEXT/chord/temporary hold. Verify identical initial axes, subsequent movement and independent lift; no buttons, clicks, trigger pressure or secondary fire transfer. Turn either option off or change mode/gain/inversion/curve/hide/delete target: affected stick must neutralize. Integrated racing LS, latched movement, aimed/modifier RS and gyro RS must not transfer.
- Temporary transitions: LS plus hold-layout and LS plus LT-driven layout; release/analog-neutral restores the original. CANCEL, focus loss and disconnect release all; old non-stick MOVE/UP must never recapture controls. Nested temporary controls must not replace the original return owner.
- Timer/rendering: idle uses 100 ms heartbeat; active contacts and tap pulses use 16 ms. From idle, press a button and verify immediate HID output, then bounded pulse release. Camera stationary timeout, long press and stale gyro expiry still work. Opaque and translucent controls, diagnostics and calibration indicators redraw when needed.
- UI: bounded native menus/forms remain usable with keyboard and large fonts; layout markers and labels are legible. Empty-space toolbar target expansion must not intercept RB/LB in full-height layouts. Accessibility actions can open settings/layouts/connection/editor and release inputs. Human TalkBack gameplay ergonomics remain a manual gate.
- Performance: `python3 scripts/measure-performance.py` captures a 12-second visible idle sample; `adb shell am instrument -w -e phase performance dev.phonepad.tests/dev.phonepad.DeviceTests` measures warmed encoding/decoding and pure input updates on Android. These do not measure end-to-end latency.
- Hardware sequence: install app/tests, run normal instrumentation (bring PhonePad foreground if Android blocks the launch), force-stop app, run `-e phase persistence`. Reopen gameplay and use current exported geometry for host checks. UI helpers reject changed viewport/orientation; instrumentation exports only after restored layout/insets settle.
- `python3 scripts/check-linux.py`: companion state, rumble and neutral. `python3 scripts/phone-ui.py bluetooth` then `python3 scripts/check-bluetooth.py`: paired-host selection and live HID controls/neutral. Neither substitutes for an actual PC-game test.

## V5 acceptance — run only after implementation; record evidence

### Safe gameplay chrome
- Tap, drag, long-press, swipe and multi-touch near LAYOUT/SETTINGS/CONNECT while play-lock is enabled: no administrative action and no stolen gameplay pointer. Confirm safe-area/cutout handling.
- Verify intentional unlock gesture, visual feedback, accidental-gesture rejection, accessibility alternative and emergency release/disconnect. Lock persists or resets according to its documented preference, not by accident.
- Opening any menu deliberately neutralizes inputs before interaction; dismissing does not resurrect old pointer IDs or held triggers. Test rapid taps, background, rotation and connection loss.

### Two-thumb gameplay
- With only two thumbs, walk + move camera; aim toggle + walk + camera; fire + move camera + walk; sprint + turn + fire; release fire while camera remains owned; release camera without stuck RT.
- Explicitly test tap/double tap/hold/swipe discrimination, movement thresholds, timing, second-finger fire vs R3 priority, aim toggle, LS HOLD, modifiers, gyro and layout switch during a gesture. No unintended jump, shots or layout changes.
- Check game actions on actual host with controller tester and, separately, GTA V if available; record whether the specific control mappings match the game's settings. Perform human thumb-reach review on target device.

### Host-originated rumble and local haptics
- Inspect HID descriptor/output report and Android Bluetooth callback; verify actual receipt, report IDs/length/ranges, channel interpretation, malformed report rejection and no feedback loop.
- Linux companion: force-feedback upload/play/update/stop/erase, both channels independently, simultaneous channels, effect expiry, disconnect/reconnect and phone delivery. Test Wi-Fi authentication/session/sequence handling of any added feedback messages.
- Android mixer: (0,0) stops; left-only, right-only, both, varying strengths, short impulses, sustained/rapid updates, amplitude-supported and unsupported devices, vibrator absent, haptics disabled, DND/system policy where relevant, background/focus loss and disconnect. No endless vibration or excessive retriggering.
- Local button/gesture haptics work independently of host rumble, obey per-control and global preferences, and cannot erase a sustained game effect without mixer policy. Verify output and perceived effect on actual phone; mark game-rumble verification separately.
- Bluetooth and Wi-Fi feedback behavior should be equivalent where both transports support it; explicitly document unavailable host/game/OS feedback paths. Never infer two physical phone motors.

### Existing regressions
- Re-run V4.1 Java/Python/Android suites, separate-process persistence, Linux companion, current-layout Bluetooth check, neutral-state tests and APK build/signature. Verify old schema v2/v3/v4 imports and user layouts. Record actual counts, not inherited historical counts.


## V5 delivery verification and remaining manual checks — 2026-10-01

Recorded automated results are in FEATURE_STATUS.md. Reproduce with:

```sh
python3 scripts/test.py
python3 scripts/build.py
python3 scripts/build-device-tests.py
adb install -r build/PhonePad-debug.apk
adb install -r build/device-tests/PhonePad-tests.apk
adb shell am instrument -w dev.phonepad.tests/dev.phonepad.DeviceTests
adb shell am force-stop dev.phonepad
adb shell am instrument -w -e phase persistence dev.phonepad.tests/dev.phonepad.DeviceTests
python3 scripts/check-linux.py
python3 scripts/check-phone-feedback.py --host 127.0.0.1 --usb
```

Bluetooth input requires a paired connected host and geometry from the current viewport: run instrumentation with `-e phase geometry`, connect Bluetooth, then `python3 scripts/check-bluetooth.py`. Bluetooth feedback: `python3 scripts/check-bluetooth-feedback.py --host-name cachyos-x8664` (substitute your paired host name); the host needs write access to the descriptor-matched PhonePad hidraw device. The development run was permission-blocked. For actual network delivery use `python3 scripts/check-phone-feedback.py --host <PC-LAN-IP>`; the USB result does not prove this path.

Perform these remaining phone/PC acceptance tests:

1. In Settings → Controller → Current layout choose/duplicate **Two-Thumb FPS / GTA On Foot**. Toggle AIM; steer with left thumb and use its configured L3 sprint gesture. With the right thumb, tap briefly, lift, then press nearby within the configured gap and drag: RT must stay held while RS moves and LS continues. Ordinary right-thumb swipes must never shoot. Hold still: RS neutral, RT held. Leave the visible camera rectangle: RT stops, camera continues; re-enter: no renewed RT until a fresh gesture. Release/cancel/background/disconnect: no stuck output on the PC input monitor.
2. While locked, tap former LAYOUT/CONNECT/SETTINGS positions, cross the top with captured fingers and add a second finger: no administrative action. Hold the visible top-edge handle 1.4 seconds to open the Game Mode overlay; movement or another contact cancels. Check controls near the top still work. Verify Android Back/accessibility unlock and emergency disconnect, then automatic re-lock after returning to play from settings. Editor Save/Back returns to Overview.
3. Change stick, trigger/racing, FPS and gyro values with their visible isolated previews. Confirm PC input stays neutral in settings. Switches save immediately; sliders save on release; numeric Done/navigation saves valid values and rejects invalid entries. Page Undo restores the opening values; contextual Reset asks first. Opening and leaving a built-in form without changes must not create a copy. Check custom layouts/imports remain intact, narrow landscape, cutouts and large system text remain usable.
4. Settings → Haptics: run explicitly synthetic heavy/light/both tests, vary strength/mix/quiet/texture, and assess comfort on the actual phone. During host FF, trigger local cues: host rumble must continue. Send stop, wait for expiry, disconnect, background and disable haptics: no stuck vibration. Actual unsupported-amplitude/no-vibrator hardware remains untested.
5. On the intended Windows/game setup, verify mapping, sprint/aim/fire together and actual host-originated rumble independently. GTA V and XInput compatibility are not established by Linux HID/FF tests. Record game/driver versions and observed results before marking these gates passed.


## Settings product UX regression — 2026-10-01

- Focused device run: `adb shell am instrument -w -e phase settings dev.phonepad.tests/dev.phonepad.DeviceTests`. Full regression also runs these assertions. Run persistence after the full suite in a separate process.
- Review `build/ux-review/`: Setup, Settings, Controller, Connection, device selection, Sticks, Triggers/Racing, Haptics, Game mode settings, Diagnostics/Advanced and full-screen Game mode. Native instrumented renders use actual widgets; separately captured `live-*.png` files show the foreground phone UI.
- At 1280×720 with 1.5× fonts, row labels stack over controls, both columns scroll and no fixed form footer covers content. At normal landscape widths, labels and controls share rows. Open the keyboard and reach the final field/action.
- Test invalid numeric input, recovery, page Undo, navigation-save and same-profile persistence after the first built-in copy. Verify all mapping/gesture, game CRUD, import/export and editor operations remain reachable.
- Game mode: former STOP/Connect/Settings positions cannot perform admin actions. A second contact cancels the pause hold; a completed deliberate hold invokes the pause callback with neutral output and no exposed toolbar. Back/accessibility must recover when controls occupy the corner.
- Device picker: computers first; previously connected only after actual success; Show all retains unknown-class receivers. No fabricated online/compatibility indicators. Pair via Android settings, return, connect, disconnect, and inspect raw identifiers in diagnostics.
- Human acceptance still required: unfamiliar-user navigation, thumb reach, TalkBack gameplay, physical haptic feel, unsupported vibrator devices, LAN feedback and actual Windows/game compatibility.


## Tactile interface refinement acceptance

- Launch opens Overview. Disconnected: NOT CONNECTED and Connect computer. Connected: READY TO PLAY and Start game mode. Check the state changes while Overview is open.
- Enter Game Mode: small edge handle remains visible, clear of display cutouts and all custom controls. First entry shows the visual hold hint once; subsequent entries/restarts do not repeat it.
- Tap/release, move, and add a second contact during the hold: no overlay. Complete the hold: Resume, Switch layout, Quick settings and Exit game mode appear. Existing latched input survives overlay/Resume; settings, exit, focus loss and disconnect neutralize. Verify Android Back and accessibility recovery.
- Quick settings shows layout, connection and device haptic intensity. Change intensity, return, reopen and restart to verify persistence. Open full settings and return to the controller; exit to Overview and select another section. No navigation dead ends.
- Trigger direction/reversal, travel and sensitivity update the visible preview. Drag each side; pressure returns to zero on release. Balanced/Precise/Responsive preserve unrelated settings; manual tuning reports Custom. Tap a percentage to edit the documented exact units; invalid values must not save.
- FPS: first tap, nearby held second touch, camera movement, fire and leaving the drawn camera region are visible in the preview. Shorter first-tap timing corresponds to the Quick end. Tolerance uses the actual stored percentage, not dp.
- Sticks show neutral region and response; haptics shows independent channels and mixed amplitude. Previews send no host input. Narrow layouts stack controls and previews; large-font pages remain scrollable.
- Connection shows only observed status and paired/history metadata. No fabricated latency, quality, battery or available-device claims. Raw Bluetooth identifiers remain in diagnostics.
- Foreground captures: launch in Overview, then `python3 scripts/capture-settings.py`. Instrumentation includes native renders and overlay ownership/readiness regressions. Human thumb-reach, TalkBack, physical vibration and actual-game acceptance remain separate gates.

### Focused density and editor acceptance

- Check wide/narrow landscape, cutouts and large fonts: compact rail, useful two-column settings and readable slider targets.
- FPS common controls remain visible; Advanced retains axis/curve/smoothing/timing. Opening preserves unequal X/Y; changing common sensitivity updates both. Disabled features hide dependent options without clearing saved values.
- Overview Edit/Change actions reach the current layout directly. Built-in editing creates a copy; undo/redo and persistence remain intact.
- With no selection, editor canvas fills available space. Selection opens relevant properties on the opposite side; Add and layers open floating dialogs. Verify hidden controls remain selectable.
- Preview/Test exercises sandbox controls without host output. Closing properties returns to the editor; Save/Back returns to Overview.


## Rumble / gyro completion gates (current TASK)

- Automated: `python3 scripts/test.py` and `python3 scripts/check-linux.py`; verify independent motors, active upload update, delayed/repeated/infinite effects, stop/erase, finite transport leases and neutral-session stale clearing.
- Device: build/install both APKs; run `adb shell am instrument -w dev.phonepad.tests/dev.phonepad.DeviceTests`, force-stop, then `-e phase persistence`. Run `-e phase gyro` while phone is stationary; inspect real sensor count, bias, rate, rotation and zero filtered output. Latest results: 241 full assertions, persistence PASS, five gyro assertions/108 samples PASS.
- Bluetooth: `python3 scripts/check-bluetooth-feedback.py --host-name cachyos-x8664`. Inspect native capability JSON separately from vendor-output actuation. Current native EV_FF/FF_RUMBLE absent; hidraw permission blocks fresh output test. Restore prior radio state after temporary testing.
- Host feedback: run LAN check from README. USB relay is a diagnostic substitute only. Verify heavy-only/light-only/both/stop, expiry after transport loss, local cues during host rumble, disable/background/disconnect cancellation. Current USB test PASS; LAN blocked.
- Physical gyro: calibrate at rest, return to gameplay, verify no RS drift, move left/right/up/down in landscape 90 and 270; verify invert and unequal axis gains, touch RS plus gyro, LT on/off gating, touch release and lifecycle/sensor-gap neutralization. These physical direction/feel gates remain open.
- Actual game: record game, Steam/Proton/driver versions, selected virtual device and a reproducible rumble event. Confirm real vibration and stop without local cues. Native gamepad recognition or successful manual report is insufficient. Current game-origin gate untested.


Attended Bluetooth feedback verification: user confirmed feeling all three 1200 ms heavy-only/light-only/both pulses sent at 22:59:37. Linux FF producer → vendor report 2 → Android mixer → perceived phone vibration PASS; explicit-stop transport assertion PASS. Evidence: build/rumble-bluetooth-attended.txt. This supersedes the earlier permission-blocked Bluetooth feedback result, without establishing native FF_RUMBLE or actual-game support.

## Redesigned mapper and macro workspace

- Automated on phone: map by tapping in either order; drag physical → virtual and virtual → physical; pin/collapse/reopen panel; preserve chords, profile round-trip and shared macro triggers; execute saved macro and release outputs.
- Core coverage: independent source alternatives, directional mappings, opposing inputs, dead zone/inversion, device disconnect and macro output ownership.
- Manual follow-up: inspect landscape cutouts and narrow screens; test actual external keyboard/mouse/controller disconnects, replacement/removal and conflict confirmation; edit/duplicate/reorder nested macro blocks; verify all output transports with the companion/game.
