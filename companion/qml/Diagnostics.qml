import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ColumnLayout {
    id: page
    property var snapshot: bridge.status
    property var inputState: bridge.live
    spacing: Theme.gap
    function countStatus(status) {
        return bridge.diagnosticChecks.filter(function(item) { return item.status === status }).length
    }
    Card { Layout.fillWidth: true
        ColumnLayout { anchors.fill: parent; spacing: 12
            RowLayout { Layout.fillWidth: true
                ColumnLayout { Layout.fillWidth: true; spacing: 3
                    Label { text: "Run Diagnostics"; color: Theme.text; font.pixelSize: 18; font.weight: Font.DemiBold }
                    Label { text: "Check the path from phone connection to host input."; color: Theme.muted; font.pixelSize: 12 }
                }
                PadButton { objectName: "runDiagnostics"; text: "Run checks"; primary: true; onClicked: bridge.runDiagnostics() }
                PadButton { objectName: "exportDiagnostics"; text: "Copy report"; onClicked: bridge.copyDiagnosticReport() }
            }
            Flow { Layout.fillWidth: true; spacing: 8
                Repeater { model: ["Pass", "Warning", "Failed", "Not Tested"]
                    StatusChip { required property string modelData; text: countStatus(modelData)+" "+modelData.toLowerCase(); good: modelData==="Pass" && countStatus(modelData)>0; error: modelData==="Failed" && countStatus(modelData)>0 }
                }
            }
            GridLayout { columns: width>760?2:1; Layout.fillWidth: true; columnSpacing: 10; rowSpacing: 8
                Repeater { model: bridge.diagnosticChecks
                    Rectangle { required property var modelData; Layout.fillWidth: true; Layout.minimumHeight: 74; radius: 9; color: Theme.background
                        RowLayout { anchors.fill: parent; anchors.margins: 11; spacing: 10
                            ColumnLayout { Layout.fillWidth: true; spacing: 3
                                RowLayout { Layout.fillWidth: true
                                    Label { text: modelData.name; color: Theme.text; font.weight: Font.DemiBold; Layout.fillWidth: true }
                                    StatusChip { text: modelData.status; good: modelData.status==="Pass"; error: modelData.status==="Failed" }
                                }
                                Label { text: modelData.detail; color: Theme.muted; font.pixelSize: 12; wrapMode: Text.Wrap; Layout.fillWidth: true }
                                Label { visible: !!modelData.recovery && modelData.status!=="Pass"; text: modelData.recovery; color: Theme.accent; font.pixelSize: 11; wrapMode: Text.Wrap; Layout.fillWidth: true }
                            }
                        }
                    }
                }
            }
        }
    }
    Disclosure { objectName: "diagnosticAdvanced"; title: "Advanced details"; Layout.fillWidth: true
        Disclosure { objectName: "trajectoryDetails"; title: "Stick path and center reference"; expanded: true; Layout.fillWidth: true
            RowLayout { Layout.fillWidth: true
                Label { text: "Move each stick through the center and outer edge."; color: Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
                PadButton { objectName: "trajectoryRecord"; text: snapshot.trace?"Stop recording":"Record path"; enabled: snapshot.active || snapshot.native_observing; onClicked: bridge.trace(!snapshot.trace) }
                PadButton { objectName: "trajectoryClear"; text: "Clear"; onClicked: bridge.command("clear") }
            }
            RowLayout { Layout.fillWidth: true
                Stick { objectName: "diagnosticLeftStick"; title: "Left stick"; samples: snapshot.controller_samples; axisX: normalize(inputState.controller[2]); axisY: normalize(inputState.controller[3]); Layout.fillWidth: true }
                Stick { objectName: "diagnosticRightStick"; title: "Right stick"; samples: snapshot.controller_samples; axisOffset: 2; axisX: normalize(inputState.controller[4]); axisY: normalize(inputState.controller[5]); Layout.fillWidth: true }
            }
        }
        Disclosure { objectName: "advancedCounters"; title: "Delivery counters"; Layout.fillWidth: true
            GridLayout { columns: width>700?3:2; Layout.fillWidth: true; rowSpacing: 12
                Repeater { model: Object.keys(snapshot.native_counters)
                    Info { required property string modelData; label: "OS · "+modelData.replace(/_/g," "); value: String(snapshot.native_counters[modelData]); Layout.fillWidth: true }
                }
                Repeater { model: Object.keys(snapshot.counters)
                    Info { required property string modelData; label: "Wi-Fi · "+modelData.replace(/_/g," "); value: String(snapshot.counters[modelData]); Layout.fillWidth: true }
                }
                Info { label: "Bluetooth source reports"; value: String(snapshot.source_reports || 0); Layout.fillWidth: true }
            }
            Label { text: "Received, decoded, forwarded, and OS-observed are separate stages. Backend writes do not prove game consumption."; color: Theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true }
            Label { text: "Last packet: "+(snapshot.last_packet.channel || "none")+" · "+(snapshot.last_packet.sequence || "—")+" · "+(snapshot.last_packet.stage || "—"); color: Theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true }
        }
        Disclosure { objectName: "deviceDetails"; title: "Devices and permissions"; Layout.fillWidth: true
            Repeater { model: snapshot.devices
                ColumnLayout { required property var modelData; Layout.fillWidth: true
                    StatusChip { text: modelData.name+" · "+modelData.status; good: modelData.ready; error: modelData.status==="Permission required"; Layout.fillWidth: true }
                    Label { text: modelData.error || modelData.missing.join(", "); visible: text.length>0; color: Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
                }
            }
            Repeater { model: snapshot.permissions.nodes
                Label { required property var modelData; text: modelData.name+" · "+(!modelData.exists?"Missing":modelData.readable&&modelData.writable?"Read/write":"Restricted")+(modelData.required?" · required":" · optional"); color: modelData.required&&!modelData.writable?Theme.error:Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
            }
            RowLayout {
                PadButton { objectName: "inspectBackend"; text: "Recheck devices"; onClicked: bridge.command("inspect_backend") }
                PadButton { objectName: "copyRepairCommand"; text: "Copy repair command"; onClicked: bridge.copyRepairCommand() }
            }
        }
        Disclosure { objectName: "rawDetails"; title: "Raw reports and event log"; Layout.fillWidth: true
            TextArea { readOnly: true; Layout.fillWidth: true; wrapMode: TextEdit.Wrap; color: Theme.muted; font.family: "monospace"; text: parent.visible?JSON.stringify({receivers:snapshot.receivers,bluetooth:snapshot.bluetooth,counters:snapshot.counters,nativeCounters:snapshot.native_counters,lastPacket:snapshot.last_packet,devices:snapshot.devices,history:snapshot.history},null,2):""; background: Rectangle { color: Theme.background; radius: 8 } }
        }
    }
}
