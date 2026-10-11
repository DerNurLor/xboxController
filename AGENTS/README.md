# PhonePad AGENTS structure

This directory splits the former monolithic `AGENTS.md` into task-scoped instruction files so Codex can load only what is relevant.

## Root file

`../AGENTS.md` is the mandatory entry point and is normally injected by Codex automatically. Do not shell-read it again merely to confirm it. It contains the core priorities and routes the agent to task-scoped instructions.

## Files

- `TOKEN_EFFICIENCY.md` — startup/context rules, model-turn budget, SigMap, RTK, Headroom, compaction, tool-call/read/test discipline.
- `WEB_RESEARCH.md` — low-token internet research rules; load only when browsing is necessary.
- `PROJECT_CONTEXT.md` — project identity, architecture, development philosophy.
- `INPUT_AND_HID.md` — multitouch, controller state, HID, Bluetooth, latency.
- `UX_UI.md` — touchscreen UX, layouts, settings, customization.
- `QA_DEBUGGING.md` — verification, debugging, completion criteria.
- `MAINTENANCE.md` — documentation, reporting, long-term maintenance.
- `V5_EXECUTION.md` — current V5-specific constraints and safety invariants.

The goal is not to weaken project rules. It is to avoid injecting unrelated instructions into every task.
