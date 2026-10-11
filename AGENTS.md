# AGENTS.md — PhonePad Development Instructions

PhonePad is an existing Android application that turns a smartphone into a customizable PC game controller.

## Mandatory priorities

1. Correct input behavior and HID compatibility.
2. Reliable multitouch/controller state.
3. Low input latency.
4. Touchscreen ergonomics.
5. Configurability/accessibility.
6. Stability/backward compatibility.
7. Visual consistency.
8. Maintainability/extensibility.

Never trade correctness, source understanding, debugging quality, or verification for token savings.

## Active task

`TASK.md` is the authoritative active task specification.

At the start of a fresh development session, read `TASK.md` directly once before implementation. Do not substitute a chat summary for it.

If this root `AGENTS.md` was injected by Codex into session context, do not shell-read it again merely to confirm it.

Read `FEATURE_STATUS.md` once when current implementation status is needed. Treat older task/V5 material as historical project state unless the active `TASK.md` says otherwise.

## Task-scoped instructions

Load only what is relevant:

- `AGENTS/TOKEN_EFFICIENCY.md` — always relevant for Codex/tool usage.
- `AGENTS/WEB_RESEARCH.md` — only when internet research is actually needed.
- `AGENTS/PROJECT_CONTEXT.md` — architecture/product context.
- `AGENTS/INPUT_AND_HID.md` — input, HID, Bluetooth, latency.
- `AGENTS/UX_UI.md` — UI, settings, layouts, touch ergonomics.
- `AGENTS/QA_DEBUGGING.md` — testing/debugging/completion.
- `AGENTS/MAINTENANCE.md` — docs/reporting/long-term maintenance.
- `AGENTS/V5_EXECUTION.md` — legacy/current V5 compatibility/safety rules when relevant.

Do not load every instruction file automatically.

## Core behavior

- Audit the live implementation before modifying unfamiliar subsystems.
- Do not rewrite the project from scratch unless explicitly authorized.
- Preserve working functionality and user configuration.
- Prefer incremental, maintainable changes.
- Do actual implementation work when authorized; do not stop at plans/mockups.
- Ask before destructive/fundamental architecture changes.
- Never claim hardware/device/game verification that was not performed.
- During autonomous work, remain silent unless blocked or user input is genuinely required.
- Minimize model↔tool round trips: batch predictable independent evidence gathering and coherent edits when safe.
- After context compaction, trust the handoff and recover only specifically missing facts; do not reread all instructions/docs.

## Final response

Provide one concise final report:
- material changes;
- verification actually performed and results;
- remaining limitations/manual verification.

Do not repeat full logs or routine progress history.
