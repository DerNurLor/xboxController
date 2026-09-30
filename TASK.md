# TASK.md — PhonePad Adaptive Controller V4

## Project objective

Upgrade the existing PhonePad application into a highly customizable, ergonomic touchscreen game controller for Android.

The application must retain its Bluetooth HID functionality while introducing advanced touchscreen interaction, configurable controls, game-specific profiles, rapid layout switching, improved FPS controls, and a significantly redesigned interface.

This task concerns the existing project. Preserve working functionality and migrate existing user settings.

---

# PHASE 1 — EXISTING IMPLEMENTATION AUDIT

## 1.1 Inspect the current project

Examine:

- Android application structure.
- Controller UI.
- Existing layout editor.
- Profile persistence.
- Controller-state management.
- Bluetooth HID implementation.
- Analog axis handling.
- Trigger implementation.
- Multitouch handling.
- Existing gesture recognition.
- Settings navigation.
- Existing tests.

Determine which previous requirements are actually implemented and functional.

## 1.2 Identify inconsistencies

Pay particular attention to:

- Trigger modes that do not match their visual representation.
- Floating sticks behaving like fixed sticks.
- Settings that do not affect actual HID output.
- Controls that cannot be independently resized.
- Incorrect hitboxes.
- Unnecessary settings dialogs.
- Profile persistence problems.
- Incorrect simultaneous-input handling.
- Stuck controls.
- Layout switching problems.

Fix confirmed defects before expanding the application.

---

# PHASE 2 — CONTROL ENGINE

## 2.1 Centralized controller state

Maintain one authoritative logical controller state.

It must represent:

- LS X/Y.
- RS X/Y.
- LT/RT.
- D-pad.
- Face buttons.
- Shoulder buttons.
- Stick clicks.
- System buttons.

Separate this state from UI components and HID serialization.

## 2.2 Independent pointer ownership

Track each active pointer independently.

Every pointer must have a defined owner or gesture context.

Controls must support simultaneous interaction without stealing unrelated pointers.

## 2.3 Analog normalization

Ensure analog controls use consistent normalized ranges internally.

Convert these values into the appropriate HID report representation only at the serialization boundary.

Avoid duplicated or contradictory axis transformations.

---

# PHASE 3 — ADVANCED TRIGGER SYSTEM

## 3.1 Unified Analog Trigger

Create a new control type that combines LT and RT into one gesture area.

Default behavior:

- Initial touch establishes the neutral position.
- Drag upward activates RT.
- Drag downward activates LT.
- Increasing displacement increases analog pressure.
- Returning to the origin resets both values.
- Releasing the touch resets both values.

For a single pointer, LT and RT are mutually exclusive.

## 3.2 Configuration

Expose:

- Control position.
- Control dimensions.
- Hitbox dimensions.
- Vertical/horizontal orientation.
- Direction inversion.
- Dead zone.
- Activation distance.
- Analog sensitivity.
- Response curve.
- Floating/fixed origin.
- Visibility.
- Haptic feedback.

## 3.3 Independent triggers

Preserve the ability to use LT and RT separately.

Support:

- Digital mode.
- Edge-slide analog mode.
- Vertical analog slider.

The selected mode must have an appropriate visual representation.

Do not use horizontal progress bars for vertical interaction.

## 3.4 LS-integrated racing mode

Preserve and improve the optional mode:

LS X = steering.

Physical LS upward displacement = RT.

Physical LS downward displacement = LT.

Allow the user to disable LS Y HID output while retaining physical Y movement for trigger calculation.

Diagonal steering and acceleration/braking must work simultaneously.

Use independent dead-zone settings to reduce accidental throttle/brake activation while steering.

---

# PHASE 4 — STICK INTERACTION

## 4.1 Left and right stick modes

Support:

- Fixed joystick.
- Floating joystick.
- Right-side touchpad mode.

Provide independently configurable:

- Size.
- Position.
- Activation region.
- Dead zone.
- Sensitivity.
- Response curve.
- Axis inversion.

## 4.2 L3/R3 gestures

Support:

- Double tap.
- Second-finger tap.
- Long press.
- Dedicated button.
- Configurable gesture shortcut.

## 4.3 Combined L3+R3

Introduce a configurable combined control.

Pressing it must activate L3 and R3 simultaneously.

Releasing it must release both, respecting any other active input owner where applicable.

The control can be added, hidden, resized, and repositioned through the layout editor.

---

# PHASE 5 — FPS CONTROL SYSTEM

## 5.1 Right-side camera touchpad

Implement an optional touchpad that translates relative finger movement into RS output.

Features:

- Independent X/Y sensitivity.
- Adjustable response curve.
- Optional precision mode.
- Configurable smoothing.
- Immediate neutral output after release.
- Independent multitouch handling.

Preserve Bluetooth gamepad compatibility.

## 5.2 Aim mode

Allow LT to be activated through:

- Dedicated button.
- Hold gesture.
- Toggle gesture.
- Temporary layout modifier.

While aiming, optionally use a separate RS sensitivity value.

## 5.3 Fire mode

Allow RT to be activated through:

- Dedicated button.
- Floating fire button.
- Second-finger touch inside a configurable camera region.

Camera movement must continue while RT is active.

## 5.4 Movement Hold

Implement an optional explicit LS Hold function.

When activated, temporarily preserve the current LS output.

Provide visible status and immediate cancellation.

Reset the state safely during interruptions.

## 5.5 Gyroscopic aiming

Investigate Android sensor support.

When suitable hardware is available, implement optional gyro-assisted RS input.

Required settings:

- Enable/disable.
- Sensitivity.
- Axis inversion.
- Recenter/calibration.
- Optional activation only while LT is held.
- Filtering.
- Drift mitigation.

Gyro and touchpad input must have a clearly defined combination policy.

Avoid unpredictable simultaneous overwriting of RS values.

---

# PHASE 6 — COMBINED CONTROLS AND MODIFIERS

## 6.1 Combined virtual buttons

Allow one virtual control to generate multiple logical HID button presses.

Examples:

- L3 + R3.
- LB + RB.
- LT + RT.
- A + B.

For analog triggers in a combination, define explicit output values.

Avoid contradictory mappings.

## 6.2 Modifier controls

Implement optional modifier behavior similar to a keyboard Shift key.

Example:

Normal state:

A = Jump.

Modifier held:

A = Crouch.

Another example:

Normal RS = Camera.

Aim modifier held:

RS = Precision Camera.

Modifiers must be configurable per layout.

## 6.3 Gesture mapping

Allow selected gestures to trigger configurable actions.

Supported gesture categories should include:

- Tap.
- Double tap.
- Long press.
- Swipe.
- Hold.
- Second-finger touch.

Possible actions:

- HID button.
- Combined buttons.
- Toggle input.
- Temporary modifier.
- Layout switching.
- Profile switching.
- Movement Hold.

Prevent ambiguous mappings from silently activating multiple unintended actions.

---

# PHASE 7 — GAME PROFILES AND MULTIPLE LAYOUTS

## 7.1 Profile hierarchy

Implement the following structure:

GAME PROFILE
    ├── Layout A
    ├── Layout B
    └── Layout C

A profile represents a game or general gameplay category.

Each layout represents a particular control arrangement or gameplay situation.

## 7.2 Profile management

Support:

- Create.
- Duplicate.
- Rename.
- Delete.
- Import.
- Export.
- Reset built-in profile.

## 7.3 Layout management

Support:

- Create.
- Duplicate.
- Rename.
- Delete.
- Edit.
- Preview.
- Reset.
- Import.
- Export.

Built-in templates must remain recoverable.

## 7.4 Persistence

Store:

- Control positions.
- Dimensions.
- Hitboxes.
- Visibility.
- Control mappings.
- Gesture configuration.
- Analog settings.
- Modifier configuration.
- Trigger modes.
- Stick modes.
- Layout-switching rules.
- Other relevant profile-specific settings.

Use versioned configuration schemas.

Migrate existing user profiles where necessary.

---

# PHASE 8 — RAPID LAYOUT SWITCHING

## 8.1 Quick switching

The user must be able to change layouts without opening Settings.

Implement configurable methods:

- Small floating layout-switch button.
- Edge swipe.
- Next/Previous Layout controls.
- Radial selection menu.
- Configurable button combination.

## 8.2 Temporary layouts

Support:

TOGGLE — remains active until changed.

HOLD — temporarily replaces the current layout while the configured input remains active.

Example:

Exploration → Hold Aim → Precision Aim → Release → Exploration.

## 8.3 Contextual rules

Allow explicit user-defined switching rules.

Examples:

- LT held → Precision Aim layout.
- LT released → Previous layout.
- Driving shortcut → Driving layout.
- Edge swipe → Next layout.

Do not claim automatic detection of game context without actual external integration.

## 8.4 Safe transitions

Switching layouts must not leave stale input active.

Release controls whose mapping is removed or changed.

Preserve compatible continuous input only when explicitly supported.

Initialize newly active controls safely.

---

# PHASE 9 — PRESET LIBRARY

Create the following functional templates:

1. Classic Xbox.
2. Compact Xbox.
3. Minimal Controller.
4. FPS Standard.
5. FPS Touchpad.
6. FPS Precision.
7. Third-Person Action.
8. GTA Walking.
9. GTA Driving.
10. GTA Combat.
11. Racing Standard.
12. Racing One-Stick.
13. Racing Unified Trigger.
14. Platformer.
15. Fighting Game.
16. Retro Controller.
17. Twin-Stick Shooter.
18. Flight Controller.
19. Tank / Vehicle.
20. One-Handed Left.
21. One-Handed Right.
22. Accessibility / Large Buttons.
23. Custom Blank Layout.

Presets must contain meaningful differences in control placement and behavior.

Provide a preview before applying a preset.

---

# PHASE 10 — UI/UX REDESIGN

## 10.1 Gameplay screen

Create a clean, immersive landscape controller interface.

Avoid:

- Oversized panels.
- Permanent diagnostic text.
- Excessive labels.
- Large connection controls.
- Unnecessary empty space.

Use:

- Subtle visual feedback.
- Large forgiving hitboxes.
- Compact secondary controls.
- Minimal connection indicator.
- Appropriate safe-area handling.

## 10.2 Settings

Replace oversized scrolling dialogs with organized navigation.

Suggested structure:

- Game Profiles.
- Layout Manager.
- Layout Editor.
- Controls & Gestures.
- Sticks.
- Triggers.
- FPS & Aiming.
- Gyroscope.
- Feedback.
- Connection.
- Controller Test.

## 10.3 Visual layout editor

Implement:

- Drag to move.
- Resize handles.
- Optional pinch resizing.
- Position and size controls.
- Hitbox editing.
- Control palette.
- Visibility toggle.
- Transparency.
- Grid.
- Snap to grid.
- Alignment guides.
- Undo/redo.
- Preview.
- Save as new layout.

Gameplay HID events must be disabled while editing.

## 10.4 Quick-access interface

Provide a compact quick menu for:

- Switching layouts.
- Activating temporary modes.
- Opening Settings.
- Opening Controller Test.
- Checking connection state.

The menu must not permanently obstruct gameplay.

---

# PHASE 11 — CONTROLLER TEST

Create a dedicated diagnostic screen displaying the final logical controller state and, where practical, the serialized HID report.

Include:

- LS X/Y.
- RS X/Y.
- LT/RT.
- D-pad.
- A/B/X/Y.
- LB/RB.
- L3/R3.
- Home/View/Menu.
- Active layout.
- Active modifiers.
- Active input pointers.
- Bluetooth status.

Ensure diagnostic values correspond to actual controller output rather than merely displaying raw UI coordinates.

---

# PHASE 12 — VERIFICATION

## Required tests

### Basic controls

Verify every individual button, axis and trigger.

### Multitouch

Verify:

- LS + RS.
- LS + RT.
- LS + RS + RT.
- LS + RS + RT + A + RB.
- Unified Trigger + LS + RS.
- Camera movement + Aim + Fire.
- Combined L3+R3.

### Analog controls

Verify:

- Correct axis ranges.
- Correct trigger ranges.
- Dead-zone behavior.
- Diagonal input.
- Neutral reset.
- Response curves.

### Layout switching

Verify:

- Toggle switching.
- Hold switching.
- Modifier behavior.
- Safe release of removed controls.
- Restoration of previous layouts.
- No ghost input.

### Persistence

Create a custom profile with several layouts.

Modify positions, mappings, gestures and analog settings.

Restart the application.

Verify that the complete configuration is restored.

### Safety

Verify reset behavior after:

- Pointer cancellation.
- Focus loss.
- Screen interruption.
- Bluetooth disconnect.
- Profile change.
- Layout change.

### Build and regression

Run existing tests.

Add tests for newly introduced input transformations.

Build the Android application.

Fix compilation errors.

Clearly identify tests that require physical Android hardware or a connected PC.

---

# IMPLEMENTATION PRIORITIES

Complete work in the following order:

P0 — Existing defects, input reliability and multitouch.

P1 — Unified triggers, L3/R3 improvements, FPS interaction.

P2 — Profile hierarchy and rapid layout switching.

P3 — Visual layout editor and settings redesign.

P4 — Preset library and combined controls.

P5 — Gyroscopic aiming and advanced interaction improvements.

Do not sacrifice P0 reliability to implement lower-priority visual features.

The task is complete only when the implemented features are integrated into the existing application, tested to the extent supported by the environment, and accurately documented.

