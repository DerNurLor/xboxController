# Token Efficiency and Tool Usage

These rules are always applicable during PhonePad development.

## Context recovery

### Session startup

Recover project context once.

For a fresh development session:
1. Treat the root `AGENTS.md` as already active when Codex injected it into the session. Do not shell-read it again merely to confirm its contents.
2. Read `TASK.md` directly exactly once. `TASK.md` is the authoritative active specification.
3. Read `FEATURE_STATUS.md` once when current implementation status is relevant.
4. Load only the task-scoped `AGENTS/*` files required by the active task.
5. Read additional project docs only when they answer a concrete unresolved question.

Do not substitute a chat summary for `TASK.md`.
Do not use SigMap, Headroom retrieval, `head`, `tail`, `base64`, `cat`, or alternate readers to reread `TASK.md` after a successful direct read.
Do not reread the global RTK instructions if they are already present/injected in the current session context.
Do not run `pwd`, directory listings, `git status`, or environment probes merely as a startup ritual when the working directory/state is already known.

If a required read actually failed or was truncated, recover only the missing range with one targeted read.

### Ongoing context

- Reuse unchanged information already present in context.
- Do not reread unchanged source, documentation, tool instructions, or already-resolved search results.
- `TASK.md` describes what to do; repository code and tests describe what currently exists.
- Do not reimplement completed work merely because older requirements remain in historical documentation.

For very long tasks, preserve only:
- active objective;
- current decisions;
- changed files;
- relevant test results;
- unresolved issues;
- constraints;
- exact next actions.

Discard superseded plans, resolved failures, duplicated logs, repetitive narration, and already-consumed raw outputs during compaction/handoff.

After a major independent phase, prefer a concise handoff/fresh logical session when carrying the existing history would cost more than reconstructing the small amount of still-relevant state.

## SigMap MCP

Use SigMap as the first-pass discovery tool for unfamiliar code.

Prefer it for:
- project structure;
- symbols/functions/classes;
- dependencies/callers;
- implementations and event handlers;
- entry points;
- impact analysis;
- compact relevant context.

Workflow:
1. Narrow the subsystem with SigMap.
2. Identify the minimum relevant files/symbols.
3. Read the actual source that will be changed.
4. Edit only after source inspection.

SigMap summaries never replace reading the exact code before modification.

Skip SigMap when the exact file/location is already known and a narrow direct read is cheaper.

If SigMap is unavailable, stale, incomplete, or wrong, fall back to scoped `rg --files`, targeted `rg`, and narrow source ranges.

Refresh/notify SigMap after file creation, deletion, rename, or major restructuring when the exposed MCP tools support it.

## RTK

Use the shared RTK instructions when they are already available in session context. Do not reread `/home/archlinux/.codex/RTK.md` on startup or after compaction merely to reconfirm them.

Prefer supported RTK commands when compressed output preserves the needed evidence, for example:

```text
rtk git status
rtk git diff
rtk rg 'pattern' path
```

Prefer a dedicated RTK subcommand over `rtk proxy` when both can provide the required evidence.

Use raw commands or `rtk proxy <command>` only when:
- full/unfiltered output is required;
- exact build/test logs are needed;
- diagnostics may have been omitted;
- compressed output looks suspicious/incomplete;
- RTK does not support the command;
- the wrapper fails.

Do not wrap short/near-empty commands in `rtk proxy` when compression is unlikely to save meaningful output.
Do not retry the same command through multiple wrappers solely to improve compression.

Do not stack redundant CLI compression layers. RTK is the default CLI-output filter unless a deliberate A/B test is being performed.

Always inspect command exit semantics. Empty output, `no_match`, or exit code 1 is not automatically success or failure. Preserve original exit status through pipelines (`pipefail` or equivalent).

## Headroom

Headroom may be used as an external context/compression layer when Codex is launched through it.

Treat it as transport optimization, not as a source of truth.

Do not rely on compressed content when exact raw output is needed for:
- debugging;
- protocol verification;
- security-sensitive review;
- failure diagnosis.

Preserve prompt-cache stability. Do not repeatedly rewrite stable instructions or inject large changing summaries merely to increase compression percentage.

Do not stack another input-compression proxy on top of Headroom unless running a controlled comparison.

## Model-turn budget

Minimize model ↔ tool round trips, not only the size of tool output.

A model turn with a large cached conversation still consumes quota. Prefer fewer, more purposeful reasoning turns.

Before ending an evidence-gathering tool phase:
- identify the next 2–5 predictable, independent inspections that are already known to be necessary;
- batch them in one tool call when practical;
- read directly related source ranges together when doing so keeps the result understandable;
- do not return to the model after each successful lookup merely to decide the next obvious lookup;
- do not create a new reasoning turn for routine sequencing such as `read A → read B → status → read C`;
- stop the batch early only when a result changes the investigation path or a failure needs interpretation.

Preferred loop:
`one reasoning decision → batched targeted evidence → one reasoning decision → coherent edit batch → targeted verification`.

Avoid:
`reason → one read → reason → one read → reason → status → reason → equivalent search`.

Token savings must never justify blind bulk operations. Batch only actions whose need is already established.

### Startup turn budget

At startup, do not spend several model turns rediscovering instructions.

When files are available:
- read the active task/state/instructions in one bounded batch where the tool permits;
- never reread root `AGENTS.md` if it was already injected by Codex;
- read `TASK.md` once;
- do not route `TASK.md` through SigMap or Headroom retrieval;
- do not try `cat`, then `head`, then base64, then another reader for the same successfully read file;
- begin repository audit immediately after the minimum required context is loaded.

### Tool-phase budget

For routine repository work, prefer a small number of evidence phases:
1. discovery;
2. focused source inspection;
3. coherent edit;
4. focused verification;
5. final/broad verification when warranted.

Do not artificially force a numeric call limit when correctness requires more investigation, but every extra model/tool round trip should answer a new question.

## Tool-call minimization

Before every tool call, ask whether the required evidence is already present in the current context or can be obtained from an existing result.

Default behavior:
- do not call a tool merely to reconfirm unchanged state;
- batch independent lookups when the tool supports batching;
- combine closely related shell inspections when this does not make failures ambiguous;
- prefer one targeted call over several exploratory calls;
- reuse paths, symbols, URLs, test results, device state, and prior command output;
- do not run `git status`, `git diff`, directory listings, or environment probes after every edit;
- inspect repository status/diff at meaningful checkpoints or final review, not continuously;
- normally run repository-wide `git status`/`git diff` no more than once during final review unless a concrete debugging need arises;
- do not perform a separate tool call just to produce a progress update;
- do not inspect the same state using several equivalent mechanisms unless the first result was insufficient;
- do not use SigMap or Headroom retrieval for plain project instruction files that can be read directly;
- avoid repeated no-op/near-zero-savings proxy calls.

For investigation loops:
1. Form a concrete hypothesis.
2. Run the cheapest call that can falsify it.
3. Reuse the result.
4. Escalate only if the result is insufficient.

Avoid tool ping-pong such as:
`search → read → search equivalent term → reread same file → status → summary`.

Prefer:
`targeted discovery → focused reads → coherent edit batch → targeted verification`.

When several tiny edits belong to one coherent fix, make them together before rebuilding, provided doing so does not make debugging materially harder.

Do not create temporary scripts/files merely to avoid a straightforward narrow command unless the script will be reused or improves verification.

## Progress/status suppression

During autonomous work:
- do not emit routine “I found…”, “I’m now…”, “next I will…” updates;
- do not summarize after every successful command or micro-phase;
- do not repeat already-known test counts or state unless they changed;
- do not generate a status report just because several tools were called.

Send an intermediate message only when:
- blocked by missing information or access;
- a destructive/fundamental decision requires user approval;
- the user explicitly requested progress updates;
- a materially important unexpected result changes the task.

Otherwise continue working and provide one concise final report.

## Reading/search discipline

- Reuse known file paths, symbols, callers, and previous search results.
- Avoid equivalent searches.
- Read the smallest useful source range: relevant functions plus necessary surrounding state/callers.
- Avoid whole-file dumps unless genuinely needed.
- Scope recursive searches by directory, file type, or symbol.
- Exclude generated output unless relevant.
- Explain the need before an unfiltered repository-wide search.
- Prefer a single multi-pattern/scoped search over multiple near-duplicate searches.
- Do not read generated artifacts, logs, or large data files unless they are directly relevant.
- Use SigMap for unfamiliar source-code structure, not for `TASK.md`, `AGENTS*.md`, changelogs, or small known documentation files.
- If a file was just successfully read, treat its contents as available; do not immediately retrieve it again through another tool.

## Test/build discipline

- Prefer the narrowest relevant test first.
- Batch coherent edits before rebuilding when safe.
- Do not rebuild/reinstall after every micro-edit.
- Do not reinstall the APK/device-test APK unless the installed artifact actually changed and device verification is needed.
- Do not restart the app/device test runner when the prior installed build is still valid for the question being tested.
- Run broader regression only when shared behavior changed, targeted testing indicates wider impact, the task is nearing completion, or the specification requires it.
- Do not repeat a successful build/test unless relevant code, test code, build inputs, installed artifact, or environment state changed.
- If one targeted test fails, diagnose that failure before launching unrelated full suites.
- Prefer test filters/phases/individual test targets over full suites during iteration.
- Preserve full raw failure output for debugging, but do not repeatedly resend unchanged failures to the model.
- When build → install → run is necessarily one verification path, combine steps into one bounded command/tool phase when failure attribution remains clear.
- Cache/reuse unchanged build artifacts and installed test APKs when technically valid.

## Output discipline

- Default to silent execution between the initial task and final result.
- Do not announce post-compaction recovery, the next obvious test, or the next obvious build step.
- Avoid narrating obvious intermediate actions.
- Do not repeat long tool output in prose.
- Do not restate unchanged context or conclusions.
- Final reports should contain only changed files/behavior, verification results, important limitations, and required next action if any.
- Do not produce a standalone status/summary artifact unless the user asked for one or persistent project documentation requires an update.
- Never abbreviate executable code, exact errors, protocol data, or required commands in a way that harms correctness.

## After context compaction

After automatic or manual context compaction:

- Treat the compacted handoff as authoritative continuation state unless there is concrete evidence that a required fact is missing.
- Continue with the recorded next action directly when possible.
- Do not perform a generic project re-orientation sequence.
- Do not automatically run `pwd`, `git status`, `git diff`, directory listings, environment probes, or broad searches.
- Do not reread root `AGENTS.md`; Codex already receives root instructions separately.
- Do not reread `/home/archlinux/.codex/RTK.md`, `TASK.md`, `FEATURE_STATUS.md`, project docs, or unchanged source files solely because compaction occurred.
- Do not ask SigMap/Headroom to reconstruct information already preserved in the handoff.
- Recover only a specific missing fact, with one targeted call whenever possible.
- If the handoff contains working directory, active objective, changed files, test state, constraints, and next action, no recovery call is needed.
- Do not emit “I’ll continue…”, “I’m now…”, or any other progress/status message after compaction.
- Do not repeat a previously successful verification unless relevant state changed after that verification.

If the compacted handoff is genuinely incomplete, reconstruct the minimum missing state, not the whole project context.
