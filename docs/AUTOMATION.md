# Input mapper, macros and game states

In **Edit layout**, use the top-bar **Mapper** or **Macros** button. The mapper shows a full keyboard or mouse beside contextual layout controls. Changes belong to the layout and travel with duplication, undo, save and JSON export. Add custom buttons or a mouse trackpad from the layout editor.

## Mapping

The full-screen workspace has Keyboard, Mouse and Macros tabs. External devices appear automatically. Select an input then a layout control, or select the control first; dragging works in either direction. The right panel can collapse or stay pinned. Selected and mapped inputs are highlighted.

Select a connected mapping to configure it, use its remove action to unbind it, or select another device region and control to add an independent source. Conflicting assignments require an explicit **Map to both** choice. Existing `+` source chords retain their meaning; `|` separates independent alternatives. Stick, D-pad and trackpad directions have individual source mappings. Output chips open the visual output picker, with pressure values available for analog outputs. Sensitivity, dead zone and inversion appear only for applicable mappings.

Joystick/trigger sources scale analog outputs. Mouse direction sources are short pulses proportional to movement (full strength at 20 pixels); they release after 60 ms without movement.

Add a trackpad for one-finger pointer movement, tap to click, two-finger scrolling and two-finger tap for right click. Its fullscreen-background option leaves other controls above it. Custom buttons mapped to mouse buttons can hold them for dragging. With a trackpad present, an attached mouse passes movement, buttons and scrolling through unless explicitly mapped. Game mode captures that mouse for unbounded relative motion; exiting game mode, opening settings or losing capture releases its inputs.

Controller outputs retain the existing Bluetooth/Wi-Fi transport. Keyboard and mouse actions can use composite Bluetooth HID reports directly. The Wi-Fi companion also supports desktop input injection and is useful for its virtual-controller and rumble features. The desktop backend supports Linux uinput and Windows SendInput; Windows injection cannot control applications at a higher integrity level.

## Macros

On **Macros**, create or open a named macro in the library. Definitions are reusable: assign several macros to one control, or the same macro to several controls. Removing an assignment retains its definition; duplicating a macro creates an independent definition. Add Press, Release, Delay, Repeat or Condition blocks, drag them into order, or use each block's edit, duplicate, move and remove actions. Repeat and condition groups move together. Start/end markers and connections show execution order. Select outputs on the illustrated devices and configure durations with timing controls. Select multiple trigger controls to share a macro.

Save validates the program before applying it; closing an edited draft asks before discarding it. Once, While held and Toggle retain the execution semantics below. Simultaneous output groups and independent macros are supported; this editor does not introduce a separate parallel-branch scheduler.

```text
press KEY_W+KEY_SHIFT
wait 300
release KEY_W+KEY_SHIFT
repeat 3
press A
wait 80
release A
wait 120
end
```

- `press outputs` / `release outputs`: hold or release a group.
- `wait milliseconds`: yield while preserving held outputs (1–60000 ms). Include a wait between press and release so the host can observe the press.
- `repeat N` … `end`: 1–1000 iterations; 0 repeats until cancelled.
- `if vehicle=true` … `else` … `end`: branch on a state; else is optional.
- `# comment`: ignored.

**Once** runs on a press; **While held** cancels when released; **Toggle** starts/stops on successive presses. All held outputs release at completion/cancellation. Multiple controls retain independent ownership of shared keys/buttons.

**Enable when** gates a control and cancels its macro when false. **Show when** also hides it outside the editor. Conditions use `name=value` or `name!=value`, optionally joined by `&&`. Missing or expired state never satisfies either comparison. Local conditions include `input.LT=true`, `input.RT=true` and controller buttons such as `input.A=true`. Local predicates use the previous input frame to avoid order-dependent evaluation.

Enable **Run macro when condition becomes true** to start on a condition's rising edge without a touch. Layouts do not infer game state themselves.

Limits: 8 KB / 256 steps / 8 nested blocks per sequence, 64 interpreted steps per input frame, five-minute runtime. Layout transitions, editing/settings, focus loss and disconnect release inputs. Companion desktop state expires independently after 500 ms without a fresh frame. Retransmitted motion is applied once.

## Game/plugin API

The companion starts a local HTTP endpoint and prints **Game plugin API: /path/game-events-PORT.json**. The private file contains `url` and `token`; it is removed on a clean shutdown. Each companion instance has its own endpoint/token.

A game integration reads that file and sends:

```http
POST /state
Authorization: Bearer <token>
Content-Type: application/json

{"state":{"vehicle":false,"weapon":"rifle"},"ttl_ms":2000}
```

Response: 204 on success; 400 for invalid data, 401 for an invalid token. The server listens only on loopback. No plugin code is executed by PhonePad.

Publish updates periodically before their TTL expires. A null value deletes a state. TTL is 100–30000 ms (default 2000); at most 16 live values / 900 bytes per snapshot. Names use letters, digits, underscore, dot or hyphen (1–48 characters); scalar values serialize to the same character set (1–64 characters). The `input.` prefix is reserved. Booleans become `true` / `false`.

The companion sends snapshots over the authenticated phone session. The phone additionally discards them after one second without updates and clears them on disconnect.

Example publisher:

```sh
python3 companion/examples/publish_state.py --connection /path/game-events-PORT.json vehicle=false weapon=rifle
```

This publishes once for two seconds; a real game plugin must publish actual changes/refreshes. There is no bundled PUBG/GTA state detector, memory reader, or screen recognition.

## Protocol extension

PP01 gamepad frames (type 3) and rumble ACKs (type 4) are unchanged. Type 5 carries 39 bytes: 256 keyboard-usage bits, five mouse-button bits in one byte, then big-endian signed 16-bit relative X/Y/wheel. Type 6 acknowledges its sequence and carries the UTF-8 JSON state snapshot. Both use the existing session nonce/HMAC, with independent sequence counters and duplicate suppression. Clients continue sending gamepad heartbeat frames.

Older companions ignore this extension; controller operation continues, but desktop output and game states require an updated companion.

## Editing Mode V2

Mappings have independent IDs and per-assignment tuning. Select a device region, then a PhonePad control (or drag either direction). Select a connected mapping to change its output, sensitivity, dead zone or inversion without changing opposite directions. The primary mapper hides controller inputs; existing controller mappings and output selection remain supported. Keyboard/mouse diagrams support pinch zoom and two-finger pan.

In the layout editor, **Select** enables multiple selection. Tap controls, then use the contextual alignment, distribution or matching-size tools. Leave selection mode to drag the group. Grid and snapping toggles are independent; smart edge/center guides appear during movement. Transformations participate in layout undo/redo. Behavior retains gesture actions and touch-area settings.

Macro assignments offer Once, While held, Toggle, Tap, Double tap and Long press. A tap lasts less than 250 ms, a double tap finishes within 350 ms of the previous tap, and long press fires at 450 ms. Simultaneous macros own their outputs independently: button/key outputs are combined, analog outputs use the strongest value, and releasing one run does not release another run's output. Legacy macro buttons retain their original replacement behavior while assigned; new assignments run alongside the control's ordinary action.

The structured workflow supports Press, Release, Delay, Repeat and TRUE/FALSE conditions, with nesting and block reorder. Arbitrary graph cycles and unsupported state-changing instructions are not offered. Node edits have undo/redo; unsaved programs are retained locally against their saved version.

**Test** opens an isolated simulator using the runtime instruction interpreter. Play/Pause, Step, Restart and Stop expose the current action, virtual delay, held inputs, branch result and event history. Configure simulated state to test conditions. Preview never invokes a transport or sends input to the PC.
