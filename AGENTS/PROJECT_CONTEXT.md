# Project Context and Development Philosophy

## Project identity

PhonePad is an Android application that transforms a smartphone into a virtual game controller for a PC using Bluetooth HID.

It is not merely a visual replica of an Xbox or PlayStation controller. It should exploit smartphone capabilities such as:

- multitouch;
- customizable touch regions;
- gesture recognition;
- dynamic controller layouts;
- combined controls;
- motion sensors;
- haptic feedback;
- context-dependent interaction;
- configurable analog input.

The goal is a practical, customizable touchscreen gaming controller.

Physical-controller conventions should be preserved where they improve compatibility and familiarity, but not at the expense of touchscreen ergonomics.

## Development philosophy

Prioritize:
1. Correct input behavior and HID compatibility.
2. Reliable multitouch and controller-state management.
3. Low input latency.
4. Touchscreen ergonomics.
5. Configurability and accessibility.
6. Stability and backward compatibility.
7. Visual consistency.
8. Maintainability and extensibility.

A beautiful UI with unreliable input is unacceptable.

A technically correct feature that is unnecessarily difficult to use should also be improved.

## Understand the real problem

For feature requests:
1. Identify the underlying usability/problem statement.
2. Inspect the relevant existing implementation.
3. Consider interaction with other controls.
4. Evaluate touchscreen suitability.
5. Consider simpler/more ergonomic alternatives.
6. Implement the intended behavior.
7. Verify it appropriately.

Suggestions are encouraged, but do not silently replace explicit requirements with unrelated ideas.

Ask before destructive changes or fundamental architecture changes.

Do not repeatedly ask for clarification when minor details can be resolved through sound engineering judgment.

## Existing architecture

PhonePad is an existing application.

Do not rewrite it from scratch unless explicitly authorized.

Before modifying a subsystem:
- understand its current architecture;
- identify dependencies;
- understand state representation;
- inspect relevant data models;
- check persistence;
- examine HID report structure;
- review related tests.

Preserve working functionality.

Avoid unnecessary libraries and architectural complexity.

Prefer incremental, maintainable improvements.

## Separation of responsibilities

Maintain clear separation among:
1. Touch/sensor input.
2. Gesture interpretation.
3. Logical controller state.
4. Control mappings.
5. Layout/profile configuration.
6. HID report serialization.
7. Bluetooth communication.
8. Visual presentation.

UI components should not directly manipulate arbitrary HID report bytes.

Logical controller state should be independently testable.
