import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
ColumnLayout {
    id: stick
    property real axisX: 0
    property real axisY: 0
    property string title: "Stick"
    property var samples: []
    property int axisOffset: 0
    function normalize(value) { return Math.max(-1, Math.min(1, value / (value < 0 ? 32768 : 32767))) }
    onSamplesChanged: plot.requestPaint()
    onAxisXChanged: plot.requestPaint()
    onAxisYChanged: plot.requestPaint()
    Label { text: stick.title; color: Theme.muted }
    Canvas {
        id: plot; Layout.fillWidth: true; Layout.preferredHeight: 125
        onWidthChanged: requestPaint()
        onPaint: {
            var c=getContext("2d");c.reset();c.clearRect(0,0,width,height);var r=50,x=width/2,y=height/2
            c.strokeStyle="#405665";c.beginPath();c.arc(x,y,r,0,Math.PI*2);c.moveTo(x-r,y);c.lineTo(x+r,y);c.moveTo(x,y-r);c.lineTo(x,y+r);c.stroke()
            c.strokeStyle="#568e81";c.beginPath();for(var i=0;i<stick.samples.length;i++){var p=stick.samples[i];var px=x+stick.normalize(p.axes[stick.axisOffset])*r,py=y+stick.normalize(p.axes[stick.axisOffset+1])*r;if(i===0 || p.source!==stick.samples[i-1].source)c.moveTo(px,py);else c.lineTo(px,py)}c.stroke()
            c.fillStyle="#8ed9c5";c.beginPath();c.arc(x+stick.axisX*r,y+stick.axisY*r,5,0,Math.PI*2);c.fill()
        }
    }
    Label { text: "X  "+stick.axisX.toFixed(3)+"    Y  "+stick.axisY.toFixed(3); color: Theme.text; font.pixelSize: 12; Layout.alignment: Qt.AlignHCenter }
}
