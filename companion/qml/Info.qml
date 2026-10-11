import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ColumnLayout {
    property string label: ""
    property string value: ""
    property color ink: Theme.text
    spacing: 7
    Label { text: label; color: Theme.muted; font.pixelSize: 12; Layout.fillWidth: true; wrapMode: Text.Wrap }
    Label { text: value; color: ink; font.pixelSize: 16; font.weight: Font.DemiBold; Layout.fillWidth: true; wrapMode: Text.Wrap }
}
