import "ControllerEvidence.js" as Evidence
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ColumnLayout {
    id: monitor
    objectName: "inputMonitor"
    property string view: "controller"
    property var snapshot: bridge.status
    // Current input is useful even when detailed event history is paused.
    property int playerSlot: 0
    property int guidedStep: -1
    property int guidedWaitMs: 0
    property bool guidedComplete: false
    property var guidedBaseline: ({movement:0,clicks:0,scrolls:0})
    onViewChanged: { guidedStep=-1; guidedWaitMs=0; guidedComplete=false }
    property var inputState: {
        var state = bridge.live
        var output = state.controller
        var source = state.controller_source
        for (var player of snapshot.players || []) if (player.slot === playerSlot) {
            output = player.controller; source = player.controller_source
        }
        return {controller: Evidence.combine(output,source), output:output, source:source, keyboard:state.keyboard, mouse:state.mouse}
    }
    property var visiblePulses: (snapshot.input_pulses || []).filter(function(pulse) {
        return !playerSlot || pulse.kind === "keyboard" || pulse.kind === "mouse" || pulse.source === (playerSlot===1?"wifi":"wifi:"+playerSlot)
    })
    property var trajectorySamples: {
        var samples = snapshot.controller_samples || []
        if (!playerSlot) return samples
        for (var player of snapshot.players || [])
            if (player.slot === playerSlot)
                return samples.filter(function(point) { return point.source === player.source })
        return []
    }
    spacing: Theme.gap
    function normalize(value) { return Math.max(-1,Math.min(1,value/(value<0?32768:32767))) }
    function outputName(pulse) {
        if (pulse.kind === "mouse")
            return "Mouse " + (["Left", "Right", "Middle", "Back", "Forward"][pulse.code] || "button " + (pulse.code + 1))
        if (pulse.kind === "controller")
            return "Controller " + ({0:"A",1:"B",3:"X",4:"Y",6:"LB",7:"RB",10:"View",11:"Menu",12:"Home",13:"L3",14:"R3",15:"Share"}[pulse.code] || "button " + (pulse.code + 1))
        var key = pulse.code
        if (key >= 4 && key <= 29) return "Keyboard " + String.fromCharCode(65 + key - 4)
        if (key >= 30 && key <= 39) return "Keyboard " + (key === 39 ? "0" : key - 29)
        if (key >= 58 && key <= 69) return "Keyboard F" + (key - 57)
        return "Keyboard " + ({40:"Enter",41:"Esc",42:"Backspace",43:"Tab",44:"Space",57:"Caps",62:"F5",224:"Ctrl",225:"Shift",226:"Alt",227:"Meta"}[key] || "key " + key)
    }
    function outputSummary() {
        var found = []
        for (var pulse of visiblePulses) {
            if (pulse.kind === "controller_source") continue
            var name = outputName(pulse)
            if (found.indexOf(name) < 0) found.push(name)
        }
        return found.length ? "Recent PC output · " + found.join("  /  ") : "Press a control to begin"
    }
    function latestOutputKind() {
        var pulses = visiblePulses.filter(function(pulse) { return pulse.kind !== "controller_source" })
        if (!pulses.length) return ""
        var fallback = pulses[pulses.length - 1].kind
        for (var i = pulses.length - 1; i >= 0; --i)
            if (pulses[i].kind !== view) return pulses[i].kind
        return fallback
    }
    function guidedActions() {
        if (view === "controller") return ["Press A", "Move the left stick", "Press a trigger", "Move the D-pad"]
        if (view === "mouse") return ["Move the pointer", "Click the left button", "Scroll the wheel"]
        return ["Press A", "Hold Ctrl + A together"]
    }
    function beginGuidedTest() {
        guidedComplete=false
        guidedStep=0
        guidedWaitMs=0
        var totals=mouseTotals()
        guidedBaseline={movement:totals.movement_reports,clicks:totals.button_presses,scrolls:totals.wheel_reports}
    }
    function mouseTotals() {
        var total={movement_reports:0,button_presses:0,wheel_reports:0}
        Object.keys(snapshot.mouse_activity).forEach(function(k){var a=snapshot.mouse_activity[k];total.movement_reports+=a.movement_reports;total.button_presses+=a.button_presses;total.wheel_reports+=a.wheel_reports})
        return total
    }
    function guidedActionSeen() {
        var state=inputState.controller
        if (view === "controller") {
            if (guidedStep===0) return (state[0]&1)!==0 || visiblePulses.some(function(p){return p.kind==="controller"&&p.code===0})
            if (guidedStep===1) return Math.abs(normalize(state[2]))>0.12 || Math.abs(normalize(state[3]))>0.12
            if (guidedStep===2) return state[6]>8 || state[7]>8
            return state[1]!==8
        }
        if (view === "mouse") {
            var total=mouseTotals()
            if (guidedStep===0) return total.movement_reports>guidedBaseline.movement
            if (guidedStep===1) return total.button_presses>guidedBaseline.clicks || (inputState.mouse[0]&1)!==0
            return total.wheel_reports>guidedBaseline.scrolls
        }
        if (guidedStep===0) return inputState.keyboard.indexOf(4)>=0
        return inputState.keyboard.indexOf(4)>=0 && inputState.keyboard.indexOf(224)>=0
    }
    Timer {
        interval: 100; repeat: true; running: monitor.guidedStep>=0
        onTriggered: {
            if (monitor.guidedActionSeen()) {
                if (monitor.guidedStep+1>=monitor.guidedActions().length) { monitor.guidedStep=-1; monitor.guidedComplete=true }
                else monitor.guidedStep++
                monitor.guidedWaitMs=0
            } else monitor.guidedWaitMs+=interval
        }
    }
    Flow { Layout.fillWidth: true; spacing: 8
        Repeater { model: ["controller","mouse","keyboard"]
            PadButton { required property string modelData; text: modelData.charAt(0).toUpperCase()+modelData.slice(1); primary: monitor.view===modelData; onClicked: monitor.view=modelData }
        }
        PadButton { objectName: "monitorRecord"; text: snapshot.trace?"Stop test":"Start test"; enabled: snapshot.active || snapshot.native_observing; onClicked: bridge.trace(!snapshot.trace) }
        PadButton { objectName: "guidedInputTest"; text: monitor.guidedStep>=0?"Restart guided test":"Guided test"; primary: true; onClicked: monitor.beginGuidedTest() }
    }
    ComboBox {
        objectName: "monitorPlayer"
        visible: (snapshot.players || []).length > 1
        model: [{slot:0,label:"Latest controller"}].concat((snapshot.players || []).map(function(player) {return {slot:player.slot,label:"Player "+player.slot+" · "+player.address[0]} }))
        textRole: "label"
        onActivated: monitor.playerSlot=model[currentIndex].slot
        Accessible.name: "Controller player"
    }
    Flow { Layout.fillWidth: true; spacing: 6
        StatusChip { text: "Received · "+(snapshot.connection.received?"yes":"waiting"); good: snapshot.connection.received; hint: "Input packet reached the Companion." }
        StatusChip { text: "Decoded · "+(snapshot.connection.decoded?"yes":"waiting"); good: snapshot.connection.decoded; hint: "A controller or desktop state was decoded." }
        StatusChip { text: "Forwarded · "+(snapshot.connection.writes>0?"backend":snapshot.connection.hid_connected?"Bluetooth HID":"waiting"); good: snapshot.connection.writes>0 || snapshot.connection.hid_connected; hint: "Forwarding evidence does not prove a game consumed the input." }
        StatusChip { text: "OS observed · "+snapshot.connection.os_evidence; good: snapshot.connection.os_evidence==="Linux events observed"; hint: "Requires an OS-level event observation." }
    }
    RowLayout { Layout.fillWidth: true; spacing: 10
        Label { objectName: "recentOutputSummary"; text: monitor.outputSummary(); color: (snapshot.input_pulses || []).length ? Theme.accent : Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
        PadButton { objectName: "showRecentOutput"; visible: monitor.latestOutputKind()!==""; text: "Show "+monitor.latestOutputKind(); onClicked: monitor.view=monitor.latestOutputKind() }
    }
    StatusChip { objectName: "guidedInputPrompt"; visible: monitor.guidedStep>=0 || monitor.guidedComplete; Layout.fillWidth: true; text: monitor.guidedComplete?"Guided test complete · all inputs arrived":monitor.view+" · "+(monitor.guidedStep+1)+" / "+monitor.guidedActions().length+" · "+monitor.guidedActions()[monitor.guidedStep]+(monitor.guidedWaitMs>=4000?" · No input yet — check the phone connection and try again":""); good: monitor.guidedComplete }
    GridLayout { columns: width>850 && monitor.view!=="keyboard"?2:1; Layout.fillWidth: true; rowSpacing: 12; columnSpacing: 12
        Card { Layout.fillWidth: true; Layout.preferredWidth: 640; Layout.minimumHeight: monitor.view==="keyboard"?Math.max(220,width*0.36):350
            DeviceView { objectName: "inputDeviceView"; anchors.fill: parent; mode: monitor.view; live: monitor.inputState; motion: snapshot.mouse_trail; pulses: monitor.visiblePulses; Accessible.name: monitor.view+" live input" }
        }
        Card { visible: monitor.view!=="keyboard"; Layout.fillWidth: true; Layout.preferredWidth: 260
            ColumnLayout { anchors.fill: parent; spacing: 12
                RowLayout { visible: monitor.view==="controller"; Layout.fillWidth: true
                    Stick { objectName: "monitorLeftStick"; title: "Left"; axisX: normalize(monitor.inputState.controller[2]); axisY: normalize(monitor.inputState.controller[3]); samples: monitor.trajectorySamples; Layout.fillWidth: true }
                    Stick { objectName: "monitorRightStick"; title: "Right"; axisX: normalize(monitor.inputState.controller[4]); axisY: normalize(monitor.inputState.controller[5]); samples: monitor.trajectorySamples; axisOffset: 2; Layout.fillWidth: true }
                }
                RowLayout { visible: monitor.view==="controller"; Layout.fillWidth: true
                    Label { text: "LT"; color: Theme.muted }
                    ProgressBar { from: 0; to: 255; value: monitor.inputState.controller[6]; Layout.fillWidth: true; Accessible.name: "Left trigger" }
                    Label { text: Math.round(monitor.inputState.controller[6]/255*100)+"%"; color: Theme.accent }
                    Label { text: "RT"; color: Theme.muted }
                    ProgressBar { from: 0; to: 255; value: monitor.inputState.controller[7]; Layout.fillWidth: true; Accessible.name: "Right trigger" }
                    Label { text: Math.round(monitor.inputState.controller[7]/255*100)+"%"; color: Theme.accent }
                }
                MouseEvidence { visible: monitor.view==="mouse"; Layout.fillWidth: true; activity: snapshot.mouse_activity; backend: snapshot.config.backend }
                PadButton { objectName: "rumbleTest"; visible: monitor.view==="controller"; text: "Test vibration"; enabled: snapshot.feedback.available; hint: snapshot.feedback.reason; onClicked: bridge.rumble(monitor.playerSlot) }
                Label { visible: monitor.view==="controller" && !snapshot.feedback.available; text: "No feedback output path"; color: Theme.muted; font.pixelSize: 11 }
            }
        }
    }
    Disclosure { objectName: "inputInspector"; title: "Event history and raw values"; Layout.fillWidth: true
        RowLayout { Layout.fillWidth: true
            Label { text: snapshot.event_count+" updates · "+snapshot.event_rate+"/s"; color: Theme.muted; Layout.fillWidth: true }
            PadButton { text: "Clear history"; onClicked: bridge.command("clear") }
        }
        Label { text: (monitor.view === "controller" ? "PC output: " + JSON.stringify(monitor.inputState.output) + "\nPhone controls: " + JSON.stringify(monitor.inputState.source || []) : JSON.stringify(monitor.inputState[monitor.view])); color: Theme.accent; font.family: "monospace"; Layout.fillWidth: true; wrapMode: Text.Wrap }
        Label { text: snapshot.history.slice(-8).map(function(e){return e.source+"  "+e.kind+"  "+JSON.stringify(e.state)}).join("\n") || "Start a test to record events"; color: Theme.muted; font.family: "monospace"; Layout.fillWidth: true; wrapMode: Text.Wrap }
    }
}
