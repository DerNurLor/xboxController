import QtQuick
import QtQuick.Controls
Switch {
    id: control
    implicitHeight: 32
    spacing: 9
    padding: 0
    hoverEnabled: true
    indicator: Rectangle {
        implicitWidth: 44; implicitHeight: 24
        x: control.leftPadding
        y: (control.height-height)/2
        radius: height/2
        color: control.checked ? Theme.accent : Theme.control
        opacity: control.enabled ? 1 : 0.4
        border.width: control.activeFocus ? 2 : 1
        border.color: control.activeFocus ? Theme.accent : Theme.border
        Rectangle {
            width: 16; height: 16; radius: 8
            x: control.checked ? 24 : 4; y: 4
            color: control.checked ? Theme.background : Theme.muted
            Behavior on x { NumberAnimation { duration: 100 } }
        }
    }
    contentItem: Text {
        text: control.text
        leftPadding: control.indicator.width+control.spacing
        verticalAlignment: Text.AlignVCenter
        color: control.enabled ? Theme.text : Theme.muted
        font: control.font
    }
    Accessible.name: text
}
