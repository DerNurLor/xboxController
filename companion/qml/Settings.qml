import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ColumnLayout {
    signal navigate(int page)
    property var config: bridge.status.config
    property bool validAddress: /^([0-9]{1,3}\.){3}[0-9]{1,3}$/.test(bind.text) && bind.text.split(".").every(function(part) { return Number(part) <= 255 })
    spacing: Theme.gap
    Card { Layout.fillWidth: true
        ColumnLayout { anchors.fill: parent; spacing: 14
            Label { text: "General"; color: Theme.text; font.pixelSize: 17 }
            RowLayout { Layout.fillWidth: true
                Label { text: "Computer name"; color: Theme.muted; Layout.preferredWidth: 140 }
                Field { id: name; objectName: "computerName"; text: config.name; Layout.fillWidth: true; Accessible.name: "Computer name" }
            }
            Disclosure { title: "Connections"; Layout.fillWidth: true
                RowLayout { Layout.fillWidth: true
                    StatusChip { text: "Wi-Fi "+(config.wifi?"enabled":"off"); good: config.wifi; Layout.fillWidth: true }
                    StatusChip { text: "Bluetooth "+(config.bluetooth?"enabled":"off"); good: config.bluetooth; Layout.fillWidth: true }
                    PadButton { objectName: "manageConnections"; text: "Manage"; onClicked: navigate(1) }
                }
            }
            Disclosure { title: "Input devices"; expanded: true; Layout.fillWidth: true
                RowLayout { Layout.fillWidth: true
                    Label { text: "Backend"; color: Theme.muted; Layout.preferredWidth: 140 }
                    ComboBox { id: backend; objectName: "backendChoice"; model: ["linux","windows","dry-run"]; currentIndex: model.indexOf(config.backend); Layout.fillWidth: true; Accessible.name: "Input backend" }
                }
                Label { text: backend.currentText==="linux"?"Requires /dev/uinput access":backend.currentText==="dry-run"?"Preview only · no OS input":"Requires vgamepad"; color: Theme.muted }
            }
            Disclosure { title: "Feedback"; Layout.fillWidth: true
                PadButton { text: "Test vibration"; enabled: bridge.status.feedback.available; onClicked: bridge.command("rumble"); hint: bridge.status.feedback.reason }
                Label { text: bridge.status.feedback.available?"Output path ready":"No feedback output path"; color: Theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true }
            }
            Disclosure { title: "Advanced"; Layout.fillWidth: true
                RowLayout { Layout.fillWidth: true
                    Label { text: "Listen address"; color: Theme.muted; Layout.preferredWidth: 140 }
                    Field { id: bind; text: config.bind; Accessible.name: "Bind IPv4 address"; Layout.fillWidth: true }
                    Field { id: port; text: String(config.port); placeholderText: "Port"; Accessible.name: "UDP port"; Layout.preferredWidth: 90; validator: IntValidator { bottom: 1; top: 65535 } }
                }
                CheckBox { id: feedback; text: "Log force feedback"; checked: config.trace_feedback }
            }
            Label { visible: !name.text.trim() || !port.acceptableInput || !validAddress; text: !name.text.trim()?"Enter a computer name":!validAddress?"Enter a valid IPv4 address":"Port must be 1–65535"; color: Theme.error }
            PadButton { objectName: "saveSettings"; text: "Save changes"; enabled: !!name.text.trim() && port.acceptableInput && validAddress; primary: true; onClicked: bridge.settings(name.text,bind.text,port.text,backend.currentText,feedback.checked) }
        }
    }
}
