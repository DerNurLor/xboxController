import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ColumnLayout {
    id: evidence
    objectName: "mouseEvidence"
    property var activity: ({})
    property string backend: ""
    spacing: 10
    Repeater { model: Object.keys(evidence.activity)
        ColumnLayout { required property string modelData; property var values: evidence.activity[modelData]; Layout.fillWidth: true; spacing: 8
            Label { text: modelData==="wifi"?"Wi-Fi mouse":"Bluetooth mouse"; color: Theme.muted }
            StatusChip { text: values.movement_reports+" movements"; good: values.movement_reports>0; Layout.fillWidth: true }
            StatusChip { text: values.button_presses+" clicks"; good: values.button_presses>0; Layout.fillWidth: true }
            StatusChip { text: values.wheel_reports+" scrolls"; good: values.wheel_reports>0; Layout.fillWidth: true }
            Label { visible: values.button_presses>0 && values.movement_reports===0; text: "Map Pointer to a stick or trackpad to move."; color: Theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true }
        }
    }
    Label { visible: !Object.keys(evidence.activity).length; text: "Move a pointer control on your phone"; color: Theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true }
}
