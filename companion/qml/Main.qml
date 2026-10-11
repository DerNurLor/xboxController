import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ApplicationWindow {
    id: window
    objectName: "phonepadWindow"
    width: 1180; height: 800; minimumWidth: 800; minimumHeight: 600
    visible: true; title: "PhonePad Companion"; color: Theme.background
    property int page: 0
    property bool expanded: width>960
    property var titles: ["Dashboard","Connections","Input Monitor","Settings","Diagnostics"]
    palette.window: Theme.background; palette.windowText: Theme.text; palette.base: Theme.control
    palette.text: Theme.text; palette.button: Theme.control; palette.buttonText: Theme.text
    palette.highlight: Theme.accent; palette.highlightedText: Theme.background
    font.family: "Sans Serif"; font.pixelSize: 13
    RowLayout {
        anchors.fill: parent; spacing: 0
        Rectangle {
            objectName: "navigationRail"
            Layout.preferredWidth: window.expanded?164:62; Layout.fillHeight: true; color: "#131e27"
            ColumnLayout { anchors.fill: parent; anchors.margins: 10; spacing: 8
                Label { text: window.expanded?"PHONEPAD":"PP"; color: Theme.accent; font.pixelSize: 16; font.weight: Font.Bold; Layout.alignment: Qt.AlignHCenter; Layout.topMargin: 18; Layout.bottomMargin: 26 }
                Repeater { model: window.titles
                    PadButton { objectName: "navigationButton"+index; Layout.minimumWidth: 0; required property int index; required property string modelData; Layout.fillWidth: true; text: window.expanded?modelData:["⌂","⇄","✚","⚙","≡"][index]; primary: window.page===index; onClicked: window.page=index; hint: window.expanded?"":modelData; Accessible.name: modelData }
                }
                Item { Layout.fillHeight: true }
                PadButton { objectName: "navigationCollapse"; Layout.minimumWidth: 0; Layout.fillWidth: true; text: window.expanded?"‹  Collapse":"»"; Accessible.name: "Toggle sidebar"; onClicked: window.expanded=!window.expanded }
            }
        }
        ColumnLayout { Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: 18; spacing: 14
            RowLayout { Layout.fillWidth: true; spacing: 16
                ColumnLayout { Layout.fillWidth: true
                    Label { text: window.titles[window.page]; color: Theme.text; font.pixelSize: 23; font.weight: Font.DemiBold }
                    StatusChip { objectName: "globalConnectionStatus"; text: bridge.status.connection.phone+" · "+bridge.status.connection.transport; good: bridge.status.connection.connected; Layout.fillWidth: true }
                }
                ColumnLayout {
                    PadButton { objectName: "serviceToggle"; text: bridge.status.active?"Stop Companion":"Start Companion"; primary: !bridge.status.active; onClicked: bridge.command(bridge.status.active?"stop":"start") }
                    Label { text: bridge.status.connection.service; color: bridge.status.state==="Failed"?Theme.error:Theme.muted; font.pixelSize: 11; Layout.alignment: Qt.AlignRight }
                }
            }
            RowLayout { visible: bridge.message.length>0; Layout.fillWidth: true
                Label { text: bridge.message; color: bridge.messageError?Theme.error:Theme.accent; Layout.fillWidth: true; wrapMode: Text.Wrap }
                PadButton { text: "×"; Accessible.name: "Dismiss message"; onClicked: bridge.clearMessage() }
            }
            ScrollView {
                id: scroll; Layout.fillWidth: true; Layout.fillHeight: true; clip: true
                contentWidth: availableWidth; ScrollBar.horizontal.policy: ScrollBar.AlwaysOff
                Loader { objectName: "pageLoader"; width: scroll.availableWidth; sourceComponent: [dashboard,connections,monitor,settings,diagnostics][window.page] }
            }
        }
    }
    Component { id: dashboard; Dashboard { onNavigate: function(index){window.page=index} } }
    Component { id: connections; ConnectionsPage {} }
    Component { id: monitor; Monitor {} }
    Component { id: settings; Settings { onNavigate: function(index){window.page=index} } }
    Component { id: diagnostics; Diagnostics {} }
}
