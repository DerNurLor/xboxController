import QtQuick
import QtQuick.Controls
TextField {
    color: Theme.text; selectionColor: Theme.accent; selectedTextColor: Theme.background
    placeholderTextColor: Theme.muted
    padding: 10
    background: Rectangle { color: Theme.background; radius: 7; border.color: parent.activeFocus ? Theme.accent : Theme.border }
}
