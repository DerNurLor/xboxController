# V5 Execution Rules

Read `TASK.md` for current V5 scope.

Previous V4 requirements are historical and must not be reimplemented merely because they remain in documentation.

Read `FEATURE_STATUS.md` first to determine what already exists.

Audit the live repository before coding; documentation may be stale.

## Compatibility constraints

- Preserve schema-v4 layout imports.
- Preserve the existing 13-byte input HID report unless a separately versioned, verified protocol extension is essential.
- Do not change host descriptors merely to fake rumble.

## Per-phase workflow

For each V5 phase:
1. Discover relevant symbols once.
2. Batch the already-known focused inspections when practical.
3. Inspect only involved code and callers.
4. Make a minimal coherent patch.
5. Add/run the narrowest useful tests.
6. Move to the next phase without a routine status turn.

Reuse existing:
- settings;
- mapping;
- input ownership;
- profile;
- haptics;
- transport paths.

Avoid:
- broad rewrites;
- redundant searches;
- full-file dumps;
- speculative features;
- repetitive narration.

Preserve complete raw failure output for debugging.

Document measured results separately from unverified claims.

Keep a compact continuation state only when a handoff/compaction is actually needed:
- decisions;
- changed files;
- relevant test results;
- remaining work.

Do not generate a handoff/status artifact after every phase.
Do not carry obsolete logs and resolved investigation into the next phase.

## Safety invariants

- No gameplay pointer may activate administrative chrome.
- Entering any modal/editor must neutralize safely.
- Virtual buttons/axes/triggers and host rumble must stop on the appropriate interruption.
- Independent haptic sources must not leak stale effects.
- Local haptic cues must never masquerade as host game feedback.
- A physical two-motor phone cannot be assumed.
- Do not silently disable essential recovery/emergency controls when locking the toolbar.
