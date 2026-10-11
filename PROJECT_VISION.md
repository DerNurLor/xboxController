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

The existing engine and report format were retained. V4 introduced layout schema v3 with behavior and game membership without replacing legacy preferences. Named game groups hold stable layout identities; temporary switching does not change the durable selection. Multiple input sources combine by button union and maximum trigger pressure. Modifier mappings replace normal mappings while active; gyroscope angular rate adds to touch RS before final clamping/precision gain.

Settings expose advanced behavior incrementally through small property pages. Hidden optional controls and disabled gyro preserve existing gameplay until configured. Presets are starting points, with preview and recoverable built-ins; they are not claims of game-specific automatic integration.

## V4.1 maintenance direction

Native controls are instances with stable IDs, separate from their output mapping and label. Schema v4 serializes the complete ordered instance list, including deletions, while older schemas retain their fixed-slot defaults during migration. Existing preference keys remain available for rollback. Game metadata exists independently of layouts, so empty groups are valid.

Continuous transfer is optional in both layouts and limited to compatible primary sticks. Keep pointer identity and normalized axes while releasing discrete/trigger/gesture state; interruptions always neutralize. Do not infer compatibility from visual resemblance alone.

Measure before optimizing: retain immediate touch handling and bounded gesture timers, but avoid idle redraws and unnecessary GPU layers. Native accessibility and compact configuration navigation remain part of the existing Android interface.

## V5 direction — two-thumb play, safe chrome and honest haptics

PhonePad must make common movement, camera, aiming and firing actions practical with two thumbs; a third/fourth finger is optional rather than mandatory. Reuse V4.1 gesture, modifier, aim, movement-hold and layout infrastructure. New actions require deterministic pointer ownership and explicit gesture arbitration; never silently infer in-game GTA/walking/driving/combat context from generic HID.

Gameplay chrome is a separate, protected interaction layer: configurable auto-hide and a deliberate unlock gesture prevent accidental LAYOUT/SETTINGS/CONNECT activation. Emergency release/disconnect remains reachable. Never hide gameplay buttons or block screen edges without accounting for cutouts and user controls.

Haptics has two distinct sources: host-originated game rumble (when actually supported and delivered), and optional local touch/gesture cues. Preserve left/right motor intensities as independent logical channels until a documented, device-capability-aware mixer produces one phone vibration waveform/amplitude. A single actuator cannot physically emulate two spatially independent motors. Handle Android amplitude-control limitations, stale effects, effect stop/replacement, lifecycle and rate limiting. Do not fabricate game rumble from local taps or claim universal Bluetooth/XInput feedback support.

V5 priority: audit existing paths and establish tests; safe gameplay chrome; two-thumb input prototype and manual ergonomics; host-to-phone haptic pipeline and mixer; settings/editor/presets; regression, device and host validation. Detailed executable specification: TASK.md. FEATURE_STATUS.md is the source of truth for implementation status.

## V5 architecture implemented (2026-10-01)

Gameplay lock is per layout. `GameplayLock` owns only a deliberately started unlock contact; hidden administrative controls have no hitboxes. Existing gameplay capture remains in `ControllerInput`. Returning to play from settings, and selecting another layout, applies that layout's lock preference. Explicit editor Save/Back returns to Overview. Android Back and native accessibility keep emergency release/disconnect reachable even when custom controls occupy a corner.

`CameraFireGesture` implements a short stationary tap followed by a nearby held contact. It does not delay relative camera motion. A normal drag never arms RT; leaving the visible camera rectangle stops RT while retaining camera capture, and re-entry cannot re-fire. Camera R3 is excluded in this mode; independent LS click/sprint gestures remain available. The opt-in Two-Thumb preset uses sticky AIM and keeps Movement Hold explicit. It cannot detect an in-game vehicle or choose game mappings automatically.

`PhoneHaptics` owns Android vibration. Actual host channels remain separate in `RumbleState` until weighted RMS mixing; local cue leases are independent. Finite hardware effects, amplitude capability checks, bounded updates and explicit lifecycle stopping prevent stale effects. Transport safety stops are distinct from received host STOP reports, so diagnostics do not invent host activity. Existing Bluetooth report 2 and authenticated PP01 feedback remain unchanged.

Settings use one native landscape workspace (`SettingsScreen`) organized around Controller, Play experience, Connection, Appearance, Advanced and Diagnostics. Shared rows put labels beside controls and stack on narrow displays. Detailed tuning, help and isolated previews expand contextually. Ordinary settings validate and persist through the existing profile/device stores; sliders save on release, numeric entry on Done/focus loss/navigation, and switches immediately. Opening a page never copies a built-in. Page Undo and contextual reset support experimentation. Naming, imports and Wi-Fi credentials retain explicit submission. No additional configuration schema or heavyweight UI framework was introduced.

## Product UX direction — 2026-10-01

Setup opens on Overview, with a connection-gated launch action. Game mode keeps a visible, low-contrast handle in a 24 dp inset-safe strip outside the controller surface. A stationary 1.4-second hold opens a compact overlay; taps, movement and a second contact cannot open it. A one-time visual hint teaches the handle. Resume preserves controller state; Switch layout, Quick settings and Exit game mode neutralize before navigation. Android Back and accessibility remain recovery alternatives. Existing layout lock preferences and explicit game-mode layout transitions are retained.

Physical settings pair controls with visible, isolated previews in adaptive columns. A 20% navigation rail uses labels, small glyphs and an accent selection marker. Trigger and gesture presets report Custom after manual tuning; percentages and units replace raw three-decimal primary values, with exact original units available by tapping the value. Schema-v4 fields remain the source of truth; FPS movement tolerance remains a percentage of the shorter touch dimension. Haptics stays in its existing device-wide store.

Connection presents paired computers first, using Bluetooth's reported device class and successful connection history. Show all preserves access to receivers with missing or unusual class metadata. Pairing does not prove availability or compatibility. Raw identifiers remain diagnostic information. All existing schema-v4 configuration fields, input reports, transport protocols and controller mappings remain the source of truth.

The layout editor is a dedicated canvas workspace reached directly from Overview. Built-ins become custom copies when editing starts. A compact toolbar, floating control palette/layers and contextual properties preserve canvas space. Preview/Test runs isolated controller input. Common settings stay visible while Advanced and dependent groups retain access to every tuning capability. Existing stores and automatic persistence remain authoritative.


## Feedback and sensor compatibility boundary

The generic Bluetooth input descriptor remains stable. Its vendor output report is manually writable but does not create Linux FF_RUMBLE capability: this host measured neither EV_FF nor FF_RUMBLE on the native device. The companion virtual controller is the implemented force-feedback route; actual-game and LAN acceptance are still open. Xbox identity/protocol emulation or a HID PID replacement would be a separate compatibility project, not a product-name change.

Gyro bias and smoothing belong in physical device coordinates before display remapping. Rates, not integrated orientation, contribute additively to RS under the existing aim gate. Independent axis gains inherit legacy sensitivity, and timestamps bound sample lifetime. Diagnostics and a real stationary sensor test establish event delivery/rest behavior; directional ergonomics in both landscapes require physical acceptance.

## Visual input workspaces

Layout editing, device mapping and macros share compact dark surfaces with teal selection feedback. The mapper uses proportional device diagrams and a collapsible properties panel; the macro editor uses an ordered, connected timeline. New UI must preserve the source-chord language, independent input ownership, profile compatibility and existing transport boundaries. Simultaneous output groups are supported without adding new parallel-branch execution semantics.
