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
- Focus loss, screen interruption, disconnect, resize, edit entry, profile/layout changes produce neutral and invalidate old owners.

## Configuration and UI

- Create, rename, duplicate, reset, import/export and restart; all geometry and behavior persist.
- Reject empty names/malformed JSON without changing active selection or losing custom data.
- Edit mode never emits gameplay; test drag/pinch/numeric dimensions, hidden-control restoration and grid snapping.
- Inspect landscape safe areas and touch targets across aspect ratios.

## Hardware gates

The Redmi Note 12 Pro+ 5G is connected for this session. Instrumentation and live Bluetooth reception have passed (see FEATURE_STATUS.md). Human multi-finger ergonomics, physical gyro aiming and actual PC-game compatibility remain separate verification gates. A compiled APK or passing logical-state test does not satisfy those gates.

## V4 regression scenarios

- Unified trigger: both orientations, inversion, fixed/floating origin, dead zone, curve, origin return, release/cancel; LS + RS + unified at once. Check its visual track does not cover face buttons.
- Same-button dual ownership; dedicated L3 overlapping combined L3+R3; releasing one source must preserve the other.
- Long press then drag; double tap across distinct Android DOWN/UP streams; second-finger click releases with its primary stick.
- Camera + aim + secondary fire; fire release leaves camera captured, primary release cancels dependent fire. Compare equal camera speed at 2/8/16/32 ms event intervals.
- Aim toggle and movement hold survive the next ordinary touch stream, but not focus loss/cancel/disconnect/layout changes.
- Mapped tap pulses expire; mapped toggle/long press/swipe/second finger behave independently. Gesture modifier works regardless of pointer insertion order.
- Layout chord switches once; temp hold returns on UP/CANCEL/focus loss; LT-driven precision retains LT pressure and returns on release or analog neutral.
- Two-layout game copy/export/import retargets hold/aim/layout actions within the copied group; reset retains game membership. Rename updates game-action destinations.
- Opening the editor alone does not copy a preset. Drag/pinch/resize handle stay neutral. Undo/redo restores full configuration; changing layouts clears history.
- Version-2 migration preserves existing controls, with all new optional controls hidden; unsupported future layouts remain in storage after another layout is saved.
- Gyro: still calibration removes small bias, moving calibration rejects samples, dead zone removes rest noise, stale data expires, aim-only gating works, touch+gyro clamp and cancellation are predictable.

## Live host verification

The revised `scripts/check-bluetooth.py` consumes `cache/live-layout.json` exported by Android instrumentation from the restored actual view. It checks current layout identity/configuration and viewport size before issuing gestures, and reads only the PhonePad gamepad device. Run instrumentation first, return to PhonePad, connect Bluetooth to this host, then run `python3 scripts/check-bluetooth.py`. Do not reuse metadata after changing layout, viewport or orientation.

This verifies host-received ordinary buttons, stick/trigger ranges and neutral releases. Native multitouch and advanced modes have separate instrumentation/pure-engine coverage; a PC-game session and human ergonomics review remain separate gates.
