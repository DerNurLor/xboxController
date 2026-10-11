# UX, UI, Layouts, and Customization

Design for real landscape smartphone use.

Consider:
- natural thumb reach;
- two-handed grip;
- limited available fingers;
- large invisible touch targets;
- accidental activation;
- display cutouts/safe areas;
- different aspect ratios;
- visibility during gameplay;
- minimal need to look at the controller.

Do not blindly imitate a physical controller.

Avoid oversized settings dialogs and dashboard-like gameplay interfaces.

Prefer:
- contextual controls;
- compact navigation;
- visual editing;
- meaningful feedback.

## Configuration

When introducing a new control or interaction mode, consider whether it should be configurable per layout.

Prefer reusable control types over one-off implementations.

Custom layouts must remain compatible with future updates.

Preserve existing user configuration through schema migration when necessary.

Never silently overwrite user-created profiles.

## Administrative UI vs gameplay

Gameplay pointers must not accidentally activate administrative chrome.

Entering a modal/editor must neutralize controller state safely where required.

Do not silently disable essential recovery/emergency controls when locking the toolbar.
