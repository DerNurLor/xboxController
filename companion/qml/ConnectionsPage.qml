import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window
ColumnLayout {
    id: page
    property var snapshot: bridge.status
    property bool pairingReady: !!bridge.qrUrl && snapshot.pairing_state==="Ready"
    spacing: Theme.gap
    PairingWindow {
        id: pairingWindow
        transientParent: page.Window.window
        qrSource: bridge.qrUrl
        pairingState: snapshot.pairing_state
        secondsRemaining: snapshot.pairing_seconds
        onReopen: bridge.command("pair")
    }
    Card { Layout.fillWidth: true
        ColumnLayout { anchors.fill: parent; spacing: 12
            RowLayout { Layout.fillWidth: true
                Label { text: "⇄  Wi-Fi"; color: Theme.text; font.pixelSize: 18; Layout.fillWidth: true }
                StatusChip { text: snapshot.receivers.wifi; good: snapshot.receivers.wifi==="Connected" }
                PadSwitch { objectName: "wifiEnabled"; text: "Enabled"; checked: snapshot.config.wifi; onClicked: bridge.enable("wifi",checked) }
                PadButton { objectName: "wifiReceiver"; text: snapshot.requested.wifi && snapshot.receivers.wifi!=="Failed" ? "Stop" : "Start"; primary: !snapshot.requested.wifi; enabled: snapshot.config.wifi; onClicked: bridge.receiver("wifi",!snapshot.requested.wifi || snapshot.receivers.wifi==="Failed") }
            }
            Label { text: snapshot.wifi_error; visible: text.length>0; color: Theme.error; Layout.fillWidth: true; wrapMode: Text.Wrap }
            RowLayout { Layout.fillWidth: true; spacing: 18
                Rectangle {
                    visible: page.pairingReady
                    Layout.preferredWidth: 196; Layout.preferredHeight: 196
                    radius: 10; color: "white"
                    PairingQr { anchors.fill: parent; source: bridge.qrUrl; visible: page.pairingReady }
                }
                ColumnLayout { Layout.fillWidth: true; spacing: 10
                    Label { text: (snapshot.players || []).length ? snapshot.players.length+" phone"+(snapshot.players.length>1?"s":"")+" connected" : page.pairingReady ? "Scan with PhonePad" : snapshot.pairing_state==="Stopped" ? "Wi-Fi is off" : snapshot.pairing_state==="Disabled" ? "Pairing disabled" : snapshot.pairing_state==="Expired" ? "Pairing code expired" : "Pairing "+snapshot.pairing_state.toLowerCase(); color: Theme.text; font.pixelSize: 18; Layout.fillWidth: true; wrapMode: Text.Wrap }
                    Label { visible: page.pairingReady; text: snapshot.qr ? "Code "+JSON.parse(snapshot.qr.substring(snapshot.qr.indexOf(":")+1)).code+" · "+snapshot.pairing_seconds+"s" : ""; color: Theme.accent }
                    Flow { Layout.fillWidth: true; spacing: 8
                        PadButton { objectName: "pairingFullscreen"; visible: page.pairingReady; text: "Full screen"; onClicked: pairingWindow.showFullScreen() }
                        PadButton { objectName: "pairingReopen"; visible: page.pairingReady || snapshot.pairing_state==="Expired" || snapshot.pairing_state==="Used"; text: page.pairingReady ? "New code" : "Create pairing code"; enabled: snapshot.receivers.wifi==="Running" || snapshot.receivers.wifi==="Connected"; onClicked: bridge.command("pair") }
                        PadButton { objectName: "pairingCopy"; visible: page.pairingReady; text: "Copy"; hint: "Copy one-time pairing information"; onClicked: bridge.copyConnection() }
                    }
                }
            }
            Repeater { model: snapshot.players || []
                RowLayout { required property var modelData; Layout.fillWidth: true
                    Label { text: "Phone "+modelData.slot; color: Theme.text; font.weight: Font.DemiBold }
                    StatusChip { text: "Connected · Wi-Fi"; good: true; Layout.fillWidth: true }
                    Label { text: "Controller "+modelData.slot; color: Theme.muted }
                }
            }
            Label { text: snapshot.pairing_message; visible: snapshot.pairing_message.indexOf("Pair ")===0 || snapshot.pairing_state==="Unavailable"; color: Theme.muted; wrapMode: Text.Wrap; Layout.fillWidth: true }
            RowLayout { visible: snapshot.pairing_message.indexOf("Pair ")===0
                PadButton { text: "Approve"; primary: true; onClicked: bridge.approve(true) }
                PadButton { text: "Decline"; onClicked: bridge.approve(false) }
            }
            Disclosure { title: "Connection details"; Layout.fillWidth: true
                Label { text: snapshot.config.name+" · "+(snapshot.local_address || snapshot.config.bind)+":"+snapshot.config.port; color: Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
                Label { text: snapshot.peer.length ? "Authenticated · "+snapshot.peer[0] : "No authenticated session"; color: Theme.muted }
                Label { text: "Pairing: "+snapshot.pairing_state; color: Theme.muted }
                Label { text: "PhonePad → Connection → Wi-Fi → Scan QR"; color: Theme.muted }
            }
        }
    }
    Card { Layout.fillWidth: true
        ColumnLayout { anchors.fill: parent; spacing: 12
            RowLayout { Layout.fillWidth: true
                Label { text: "ᛒ  Bluetooth"; color: Theme.text; font.pixelSize: 18; Layout.fillWidth: true }
                StatusChip { text: snapshot.connection.hid_connected?"HID connected":"Adapter "+snapshot.connection.adapter.toLowerCase(); good: snapshot.connection.hid_connected }
                PadButton { objectName: "bluetoothScan"; text: snapshot.scanning?"Scanning…":"Scan"; enabled: !snapshot.scanning; onClicked: bridge.command("scan") }
            }
            RowLayout { visible: snapshot.bluetooth_error.length>0 || snapshot.bluetooth.error.length>0; Layout.fillWidth: true
                Label { text: snapshot.bluetooth_error || snapshot.bluetooth.error; color: Theme.error; wrapMode: Text.Wrap; Layout.fillWidth: true }
                PadButton { visible: !!snapshot.bluetooth_retry_address; text: snapshot.scanning?(snapshot.bluetooth_retry_action==="pair"?"Pairing…":"Connecting…"):"Retry"; enabled: !snapshot.scanning; onClicked: snapshot.bluetooth_retry_action==="pair"?bridge.pairBluetooth(snapshot.bluetooth_retry_address):bridge.reconnect(snapshot.bluetooth_retry_address) }
            }
            Label { visible: !snapshot.bluetooth.devices.length; text: snapshot.connection.hid_connected?snapshot.connection.phone:snapshot.connection.adapter==="Off"?"Enable Bluetooth in system settings":"No devices found"; color: Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
            RowLayout { visible: snapshot.bluetooth.devices.length>0; Layout.fillWidth: true; spacing: 8
                ComboBox { id: deviceType; objectName: "bluetoothDeviceFilter"; model: ["HID devices","All devices"]; currentIndex: 0; Accessible.name: "Bluetooth device filter" }
                Field { id: filter; visible: snapshot.bluetooth.devices.length>3; Layout.fillWidth: true; placeholderText: "Find device"; Accessible.name: "Find Bluetooth device" }
            }
            Repeater { model: snapshot.bluetooth.devices
                ColumnLayout { id: deviceRow; required property var modelData; Layout.fillWidth: true; spacing: 6; visible: (deviceType.currentIndex===1 || modelData.hid_capable || modelData.input_created || modelData.connected) && (modelData.name+modelData.address).toLowerCase().indexOf(filter.text.toLowerCase())>=0
                    RowLayout { Layout.fillWidth: true
                        Label { text: "▯"; font.pixelSize: 26; color: Theme.muted }
                        Label { text: modelData.name; color: Theme.text; Layout.fillWidth: true; elide: Text.ElideRight }
                        StatusChip { text: modelData.connected ? (modelData.input_created?"HID ready":"Linked") : modelData.paired?"Paired":modelData.hid_capable?"HID available":"Available"; good: modelData.connected && modelData.hid_capable }
                        PadButton { text: snapshot.pairing_address===modelData.address?"Pairing…":snapshot.reconnecting_address===modelData.address?"Connecting…":modelData.paired?"Reconnect":"Pair"; visible: modelData.hid_capable && !modelData.connected; enabled: !snapshot.scanning && snapshot.connection.adapter==="On"; onClicked: modelData.paired?bridge.reconnect(modelData.address):bridge.pairBluetooth(modelData.address) }
                    }
                    Disclosure { title: "Device details"; Layout.fillWidth: true
                        Label { text: modelData.address+" · "+(modelData.hid_capable?"HID advertised":"No HID advertised"); color: Theme.muted }
                        Label { text: modelData.services_resolved?"Services resolved":"Services unresolved"; color: Theme.muted }
                    }
                }
            }
            Label { visible: snapshot.bluetooth.devices.length>0 && deviceType.currentIndex===0 && !snapshot.bluetooth.devices.some(function(d){return d.hid_capable || d.input_created || d.connected}); text: "No HID devices found · choose All devices to browse nearby devices"; color: Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
            Disclosure { title: "Monitoring"; Layout.fillWidth: true
                StatusChip { text: snapshot.connection.observation+" · "+snapshot.observation_source; good: snapshot.native_observing; Layout.fillWidth: true }
                RowLayout {
                    PadSwitch { objectName: "bluetoothEnabled"; text: "Start with Companion"; checked: snapshot.config.bluetooth; onClicked: bridge.enable("bluetooth",checked) }
                    PadButton { objectName: "bluetoothReceiver"; text: snapshot.requested.bluetooth && snapshot.receivers.bluetooth!=="Failed"?"Stop receiver":"Start receiver"; enabled: snapshot.config.bluetooth; onClicked: bridge.receiver("bluetooth",!snapshot.requested.bluetooth || snapshot.receivers.bluetooth==="Failed") }
                    PadButton { text: "Refresh"; enabled: !snapshot.scanning; onClicked: bridge.command("refresh") }
                }
                Label { text: "Linux HID stays connected when receivers stop."; color: Theme.muted; Layout.fillWidth: true; wrapMode: Text.Wrap }
            }
        }
    }
}
