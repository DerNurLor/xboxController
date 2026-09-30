# PhonePad

PhonePad is a landscape Android touchscreen controller using standard Bluetooth HID, with an optional authenticated Wi-Fi companion. Correct output, independent touch ownership and immediate neutralization take precedence over new gestures or appearance. Preserve existing profiles and transport compatibility.

## Architecture

`PadView` adapts Android events and renders controls. `ControllerInput` interprets pointer IDs into one normalized `PadState`; serialization happens in `PadState.report()`. `BluetoothPad` and `WifiPad` transport the same report. `ControllerConfig` describes a layout, `ProfileStore` persists it, and `ControllerSettings` edits it. Keep interpretation testable without Android. Add sources through explicit aggregation, never UI byte manipulation.

## Development plan (2026-09-30)

1. P0: audit lifecycle, pointer transitions, timing, source aggregation, persistence failures; add reproductions and fix confirmed defects.
2. P1: configurable unified triggers, stick click ownership, independent analog tuning and camera aim/fire interaction.
3. P2: game profiles containing layouts, safe quick and temporary switching, versioned migration.
4. P3: editor history/handles/preview and compact settings navigation.
5. P4: meaningful preset library, combined controls and gesture/modifier mappings.
6. P5: optional sensor input with calibration, filtering and explicit combination policy.

At each phase run relevant regressions and compile. Device and host validation are separate acceptance gates. See FEATURE_STATUS.md and TEST_CHECKLIST.md for actual delivery status; this plan is not a completion claim.

## V4 implementation direction

The existing engine and report format were retained. Layout schema v3 adds behavior and game membership without replacing legacy preferences. Named game groups hold stable layout identities; temporary switching does not change the durable selection. Multiple input sources combine by button union and maximum trigger pressure. Modifier mappings replace normal mappings while active; gyroscope angular rate adds to touch RS before final clamping/precision gain.

Settings expose advanced behavior incrementally through small property pages. Hidden optional controls and disabled gyro preserve existing gameplay until configured. Presets are starting points, with preview and recoverable built-ins; they are not claims of game-specific automatic integration.
