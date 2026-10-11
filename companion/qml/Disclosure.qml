import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ColumnLayout {
    id: disclosure
    property string title: "Details"
    property bool expanded: false
    default property alias contents: body.data
    spacing: 10
    data: [Button {
        objectName: "disclosureToggle"
        text: (disclosure.expanded ? "▾  " : "▸  ")+disclosure.title
        Layout.fillWidth: true
        horizontalPadding: 0
        contentItem: Text { text: parent.text; color: Theme.muted; font.pixelSize: 13; verticalAlignment: Text.AlignVCenter }
        background: Rectangle { color: "transparent"; border.width: parent.activeFocus ? 1 : 0; border.color: Theme.accent; radius: 6 }
        onClicked: disclosure.expanded=!disclosure.expanded
        Accessible.name: disclosure.title
        Accessible.description: disclosure.expanded ? "Expanded" : "Collapsed"
    },
    ColumnLayout { id: body; Layout.fillWidth: true; spacing: 12; visible: disclosure.expanded }]
}
