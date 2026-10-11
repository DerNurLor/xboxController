# Maintenance, Documentation, and Reporting

Treat PhonePad as a continuously evolving software product.

New functionality must integrate with existing functionality rather than becoming an isolated feature.

Maintain architectural consistency.

Avoid accumulating:
- redundant settings;
- duplicated input handlers;
- incompatible configuration formats.

Refactor when appropriate to support new requirements, but do not rewrite unrelated subsystems unnecessarily.

## Persistent project documentation

At the end of each substantial development task, review and update:

### PROJECT_VISION.md
Project philosophy, long-term goals, architectural direction, UX principles, intended capabilities.

### FEATURE_STATUS.md
Implemented, partially implemented, planned, broken, and unverified functionality, including known issues/limitations.

### TEST_CHECKLIST.md
Repeatable verification scenarios for controls, gestures, multitouch, analog values, profile switching, persistence, Bluetooth HID, and regression testing.

If these files do not exist, create them only after inspecting the real project.

Do not populate them with invented implementation details.

Keep them synchronized with the repository.

## Communication

During autonomous implementation, remain silent unless:
- blocked by missing information/access;
- user input is required for a destructive or fundamental decision;
- the user explicitly requested progress updates;
- a materially important unexpected result changes the task.

Do not send a routine pre-implementation status report. Keep discoveries and the implementation map internal unless they require user action.

After implementation, provide one concise final report covering only:
- material changes;
- tests/verification actually performed;
- results;
- remaining limitations;
- manual/hardware verification still required.

Avoid repetitive progress narration and do not repeat unchanged context, commands, logs, test counts, or conclusions.

Do not conceal errors or unfinished work.
