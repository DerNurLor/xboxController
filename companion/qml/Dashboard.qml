import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ColumnLayout {
    id: dashboard
    property var snapshot: bridge.status
    signal navigate(int page)
    function inputActivityText() {
        if (!snapshot.connection.connected) return "Waiting for phone"
        var age = snapshot.last_input_age
        if (age === null || age === undefined) return "Waiting for input"
        return age < 2 ? "Input active" : "Last input " + age + "s ago"
    }
    spacing: Theme.gap
    Card { Layout.fillWidth: true
        ColumnLayout { anchors.fill: parent; spacing: 12
            RowLayout { Layout.fillWidth: true; spacing: 18
                DeviceView { Layout.fillWidth: true; Layout.preferredWidth: 620; Layout.preferredHeight: 250; live: bridge.live; pulses: snapshot.input_pulses; Accessible.name: "Live controller overview" }
                ColumnLayout { Layout.preferredWidth: 250; Layout.fillWidth: true; spacing: 10
                    Label { text: "CONNECTED PHONE"; color: Theme.muted; font.pixelSize: 11 }
                    Label { text: snapshot.connection.phone; color: Theme.text; font.pixelSize: 18; font.weight: Font.DemiBold; Layout.fillWidth: true; elide: Text.ElideRight }
                    StatusChip { objectName: "dashboardDeviceStatus"; text: snapshot.connection.transport; good: snapshot.connection.connected; Layout.fillWidth: true }
                    Label { text: !snapshot.connection.connected ? "No phone connected" : snapshot.last_input_age===null || snapshot.last_input_age===undefined ? "No input received" : snapshot.last_input_age<2 ? "Input arriving" : "Input paused · "+snapshot.last_input_age+"s ago"; color: snapshot.connection.connected && snapshot.last_input_age!==null && snapshot.last_input_age!==undefined && snapshot.last_input_age<2?Theme.accent:Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
                    Flow { Layout.fillWidth: true; spacing: 6
                        Repeater { model: ["controller","mouse","keyboard"]
                            StatusChip { required property string modelData; text: modelData; good: snapshot.virtual_states[modelData]==="Created" || snapshot.virtual_states[modelData]==="Kernel HID"; hint: snapshot.virtual_states[modelData] }
                        }
                    }
                }
            }
            RowLayout { Layout.fillWidth: true
                StatusChip { objectName: "dashboardInputStatus"; text: dashboard.inputActivityText(); good: snapshot.connection.connected && snapshot.last_input_age !== null && snapshot.last_input_age !== undefined && snapshot.last_input_age < 2; Layout.fillWidth: true }
                Item { Layout.fillWidth: true }
                PadButton { objectName: "dashboardPair"; text: snapshot.connection.connected?"Connections":"Pair phone"; onClicked: navigate(1) }
                PadButton { objectName: "dashboardTest"; text: "Test input"; primary: true; onClicked: navigate(2) }
                PadButton { objectName: "dashboardDiagnostics"; text: "Troubleshoot"; onClicked: navigate(4) }
            }
        }
    }
    GridLayout { columns: width>650?3:2; columnSpacing: 12; rowSpacing: 12; Layout.fillWidth: true
        Repeater { model: ["controller","mouse","keyboard"]
            Card { required property string modelData; Layout.fillWidth: true; Layout.preferredWidth: 180
                contentItem: Info { label: modelData.charAt(0).toUpperCase()+modelData.slice(1); value: snapshot.virtual_states[modelData] }
            }
        }
    }
    Card { Layout.fillWidth: true; visible: snapshot.errors.length>0
        ColumnLayout { anchors.fill: parent
            Label { text: snapshot.errors.slice(-2).join("\n"); color: Theme.error; Layout.fillWidth: true; wrapMode: Text.Wrap }
            PadButton { text: "Diagnostics"; onClicked: navigate(4) }
        }
    }
}
