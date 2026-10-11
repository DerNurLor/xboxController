# Input, Multitouch, HID, Bluetooth, and Latency

PhonePad is a real-time input application.

## Input reliability

Every touch must be independently tracked.

Never assume only one finger is active.

Use appropriate pointer identifiers and lifecycle handling.

Correctly process:
- touch start;
- touch movement;
- touch release;
- touch cancellation;
- application focus loss;
- screen interruption;
- Bluetooth disconnection;
- layout switching;
- profile switching.

Prevent stuck buttons, triggers, and analog axes.

When input ownership becomes invalid, release affected controls safely.

## HID and controller state

Preserve the separation between logical controller state and HID serialization.

Do not let UI components directly manipulate arbitrary HID report bytes.

Changes to HID descriptors, report structure, or host protocol require explicit reasoning, compatibility consideration, and verification.

Do not change host descriptors merely to simulate unsupported feedback behavior.

## Bluetooth

Treat Bluetooth connection state and disconnection as controller-state lifecycle events.

Disconnection must not leave stale virtual input active.

Report separately what was:
- implemented;
- compiled;
- automated-tested;
- verified on Android hardware;
- verified with a real PC/game.

Never claim physical Bluetooth/game verification that was not performed.

## Latency

Prioritize responsiveness over decorative animation.

Avoid:
- unnecessary debounce;
- excessive UI rerenders;
- expensive processing during pointer movement;
- artificial input delays;
- unnecessary communication batching.

Where filtering/smoothing is necessary, behavior should be predictable and configurable.
