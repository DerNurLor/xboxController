# QA, Verification, Debugging, and Completion

Every significant change requires verification.

Choose the narrowest verification capable of falsifying the current change first, then broaden when needed.

Do not run the full suite after every edit when a focused test can validate the current step. Full regression is still required when scope/completion criteria require it.

Do not rebuild, reinstall, relaunch, and rerun the same broad device suite as four separate reflexive steps after every small edit. Reuse a valid installed artifact when possible, and batch build/install/run work at verification checkpoints.

## Verification process

1. Identify expected behavior.
2. Identify affected subsystems.
3. Implement the change.
4. Test individual input behavior.
5. Test simultaneous input when relevant.
6. Test cancellation/reset behavior.
7. Verify controller-state output.
8. Verify HID serialization when relevant.
9. Run relevant regression tests.
10. Build the application when appropriate.

Do not confuse:
- implemented;
- compiles successfully;
- automated tests passed;
- verified on Android hardware;
- verified with an actual PC game.

Report these separately.

## Debugging

When investigating a problem:
- reproduce it when possible;
- identify responsible subsystem;
- trace relevant state transitions;
- determine root cause;
- fix the cause rather than hiding the symptom;
- add regression coverage when practical.

Do not repeatedly apply superficial UI changes to solve underlying input-state problems.

## Feature completion

A feature is complete only when:
- implementation exists;
- configuration is functional where applicable;
- behavior matches specification;
- interactions with existing features are considered;
- relevant tests have passed;
- persistence works when applicable;
- known limitations are documented.

Do not present incomplete functionality as finished.
