import QtQuick
import QtQuick.Controls
Button {
    id: button
    property bool primary: false
    property string hint: ""
    implicitWidth: Math.ceil(contentItem.implicitWidth)+leftPadding+rightPadding
    implicitHeight: 38
    leftPadding: 14; rightPadding: 14
    hoverEnabled: true
    background: Rectangle {
        radius: 8
        color: button.down ? Qt.darker(button.primary ? Theme.accent : Theme.control,1.15) : button.primary ? (button.hovered ? Qt.lighter(Theme.accent,1.08) : Theme.accent) : button.hovered ? "#304653" : Theme.control
        opacity: button.enabled ? 1 : 0.4
        border.width: button.activeFocus ? 2 : 0
        border.color: Theme.accent
    }
    contentItem: Text { opacity: button.enabled ? 1 : 0.4; text: button.text; color: button.primary ? Theme.background : Theme.text; font.pixelSize: 13; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
    Accessible.name: text
    ToolTip.visible: hovered && hint.length>0
    ToolTip.text: hint
}
