import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
RowLayout {
    property string text: ""
    property bool good: false
    property bool error: false
    property string hint: ""
    spacing: 7
    Rectangle { width: 7; height: 7; radius: 4; color: parent.error ? Theme.error : parent.good ? Theme.accent : Theme.muted }
    Label { text: parent.text; color: parent.error ? Theme.error : Theme.text; elide: Text.ElideRight; Layout.fillWidth: true }
    ToolTip.visible: hover.hovered && hint.length>0
    ToolTip.text: hint
    HoverHandler { id: hover }
    Accessible.name: text
}
