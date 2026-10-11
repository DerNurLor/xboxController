import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window

Window {
    id: root
    objectName: "pairingFullscreenWindow"
    title: "PhonePad pairing"
    color: Theme.background
    property url qrSource
    property string pairingState
    property int secondsRemaining
    signal reopen()
    Shortcut { sequence: "Escape"; enabled: root.visible; onActivated: root.close() }
    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16
        RowLayout {
            Layout.fillWidth: true
            Label { text: "Scan with PhonePad"; color: Theme.text; font.pixelSize: 24; Layout.fillWidth: true }
            PadButton { objectName: "closePairingFullscreen"; text: "Close"; hint: "Escape"; onClicked: root.close() }
        }
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true
            PairingQr {
                objectName: "fullscreenPairingQr"
                anchors.centerIn: parent
                width: Math.min(parent.width, parent.height)
                height: width
                source: root.qrSource
                visible: root.qrSource.toString().length > 0
            }
            Label {
                anchors.centerIn: parent
                visible: !root.qrSource.toString().length
                text: root.pairingState === "Used" ? "Phone paired" : "Pairing code unavailable"
                color: Theme.text
                font.pixelSize: 24
            }
        }
        RowLayout {
            Layout.alignment: Qt.AlignHCenter
            Label { text: "Expires in " + root.secondsRemaining + "s"; visible: !!root.qrSource.toString().length; color: Theme.muted }
            PadButton { text: "New pairing code"; visible: !root.qrSource.toString().length; enabled: root.pairingState !== "Stopped" && root.pairingState !== "Disabled"; onClicked: root.reopen() }
        }
    }
}
