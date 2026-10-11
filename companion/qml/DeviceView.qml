import "ControllerEvidence.js" as Evidence
import QtQuick
import QtQuick.Controls
Item {
    id: view
    property string mode: "controller"
    property var live: ({controller:[0,8,0,0,0,0,0,0],mouse:[0,0,0,0],keyboard:[]})
    property var history: []
    property var motion: []
    property var pulses: []
    onHistoryChanged: drawing.requestPaint()
    onMotionChanged: drawing.requestPaint()
    onLiveChanged: { drawing.requestPaint() }
    onPulsesChanged: drawing.requestPaint()
    onModeChanged: drawing.requestPaint()
    implicitHeight: mode === "keyboard" ? 290 : 360
    Canvas {
        id: drawing
        anchors.fill: parent
        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()
        onPaint: {
            var c=getContext("2d");c.reset();c.clearRect(0,0,width,height)
            var logicalWidth=view.mode==="keyboard"?950:660,logicalHeight=view.mode==="keyboard"?345:380;var scale=Math.min(width/logicalWidth,height/logicalHeight);c.save();c.translate((width-logicalWidth*scale)/2,(height-logicalHeight*scale)/2);c.scale(scale,scale)
            var accent="#8ed9c5", dim="#243743", text="#e5e9ec", outline="#526976"
            function circle(x,y,r,fill,label){c.beginPath();c.arc(x,y,r,0,Math.PI*2);c.fillStyle=fill;c.fill();c.strokeStyle=outline;c.lineWidth=2;c.stroke();if(label){c.fillStyle=fill===accent?"#101820":text;c.font="bold 14px sans-serif";c.textAlign="center";c.fillText(label,x,y+5)}}
            function box(x,y,w,h,fill,label){c.fillStyle=fill;c.beginPath();c.roundedRect(x,y,w,h,5,5);c.fill();if(label){c.fillStyle=fill===accent?"#101820":text;c.font="14px sans-serif";c.textAlign="center";c.fillText(label,x+w/2,y+h/2+4,w-6)}}
            function pulsed(kind,code){for(var pulse of view.pulses)if((pulse.kind===kind || (kind==="controller" && pulse.kind==="controller_source"))&&pulse.code===code)return true;return false}
            if(view.mode==="controller") {
                var p=Evidence.combine(view.live.controller,view.live.controller_source),b=p[0]
                function pressed(bit){return (b&(1<<bit))!==0||pulsed("controller",bit)}
                c.beginPath();c.moveTo(168,77);c.bezierCurveTo(98,75,67,230,90,315);c.bezierCurveTo(107,375,165,319,206,266);c.lineTo(454,266);c.bezierCurveTo(495,319,553,375,570,315);c.bezierCurveTo(593,230,562,75,492,77);c.bezierCurveTo(420,63,426,99,330,99);c.bezierCurveTo(234,99,240,63,168,77);c.closePath();c.fillStyle=dim;c.fill();c.strokeStyle=outline;c.lineWidth=2;c.stroke()
                box(151,30,86,22,p[6]>0?accent:dim,"LT  "+Math.round(p[6]/255*100)+"%");box(423,30,86,22,p[7]>0?accent:dim,"RT  "+Math.round(p[7]/255*100)+"%")
                box(151,57,86,19,pressed(6)?accent:dim,"LB");box(423,57,86,19,pressed(7)?accent:dim,"RB")
                circle(178,158,43,"#101820","");circle(178+p[2]/32767*24,158+p[3]/32767*24,27,pressed(13)?accent:"#405665","")
                circle(414,238,43,"#101820","");circle(414+p[4]/32767*24,238+p[5]/32767*24,27,pressed(14)?accent:"#405665","")
                var face=[[494,180,0,"A"],[527,147,1,"B"],[461,147,3,"X"],[494,114,4,"Y"]]
                for(var f of face)circle(f[0],f[1],18,pressed(f[2])?accent:"#101820",f[3])
                var h=p[1];box(234,203,25,27,[7,0,1].indexOf(h)>=0?accent:"#101820","↑");box(234,254,25,27,[3,4,5].indexOf(h)>=0?accent:"#101820","↓");box(207,230,27,25,[5,6,7].indexOf(h)>=0?accent:"#101820","←");box(259,230,27,25,[1,2,3].indexOf(h)>=0?accent:"#101820","→")
                circle(298,160,15,pressed(10)?accent:"#101820","▢");circle(362,160,15,pressed(11)?accent:"#101820","≡");circle(330,126,17,pressed(12)?accent:"#101820","P");circle(330,195,11,pressed(15)?accent:"#101820","•")
            } else if(view.mode==="mouse") {
                var m=view.live.mouse
                c.beginPath();c.moveTo(239,140);c.bezierCurveTo(239,20,421,20,421,140);c.lineTo(421,250);c.bezierCurveTo(421,370,239,370,239,250);c.closePath();c.fillStyle=dim;c.fill();c.strokeStyle=outline;c.stroke()
                box(250,91,72,94,m[0]&1||pulsed("mouse",0)?accent:"#101820","L");box(338,91,72,94,m[0]&2||pulsed("mouse",1)?accent:"#101820","R");box(322,96,16,55,m[0]&4||pulsed("mouse",2)?accent:outline,"")
                box(224,186,12,36,m[0]&8||pulsed("mouse",3)?accent:outline,"");box(224,232,12,36,m[0]&16||pulsed("mouse",4)?accent:outline,"")
                var points=[[0,0]],px=0,py=0,minX=0,maxX=0,minY=0,maxY=0
                for(var sample of view.motion){px+=sample[0];py+=sample[1];points.push([px,py]);minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py)}
                box(12,146,182,144,"#101820","")
                c.fillStyle=text;c.font="12px sans-serif";c.textAlign="center";c.fillText("Recent movement",103,134)
                if(points.length===1){c.fillStyle=outline;c.fillText("Move on your phone",103,218)}
                if(points.length>1){var motionScale=Math.min(162/Math.max(1,maxX-minX),124/Math.max(1,maxY-minY));c.beginPath();for(var pi=0;pi<points.length;pi++){var tx=103+(points[pi][0]-(minX+maxX)/2)*motionScale,ty=218+(points[pi][1]-(minY+maxY)/2)*motionScale;if(pi===0)c.moveTo(tx,ty);else c.lineTo(tx,ty)}c.strokeStyle=accent;c.lineWidth=2;c.stroke()}
                c.fillStyle=accent;c.font="14px sans-serif";c.textAlign="center";c.fillText("X "+m[1]+"   Y "+m[2]+"   Wheel "+m[3],330,360)
            } else {
                var keys=view.live.keyboard
                var rows=[[[41,"Esc"],[58,"F1"],[59,"F2"],[60,"F3"],[61,"F4"],[62,"F5"],[63,"F6"],[64,"F7"],[65,"F8"],[66,"F9"],[67,"F10"],[68,"F11"],[69,"F12"],[76,"Del"]],[[53,"`"],[30,"1"],[31,"2"],[32,"3"],[33,"4"],[34,"5"],[35,"6"],[36,"7"],[37,"8"],[38,"9"],[39,"0"],[45,"−"],[46,"="],[42,"Back"]],[[43,"Tab"],[20,"Q"],[26,"W"],[8,"E"],[21,"R"],[23,"T"],[28,"Y"],[24,"U"],[12,"I"],[18,"O"],[19,"P"],[47,"["],[48,"]"],[49,"\\"]],[[57,"Caps"],[4,"A"],[22,"S"],[7,"D"],[9,"F"],[10,"G"],[11,"H"],[13,"J"],[14,"K"],[15,"L"],[51,";"],[52,"'"],[40,"Enter"]],[[225,"Shift"],[29,"Z"],[27,"X"],[6,"C"],[25,"V"],[5,"B"],[17,"N"],[16,"M"],[54,","],[55,"."],[56,"/"],[229,"Shift"],[82,"↑"]],[[224,"Ctrl"],[227,"Meta"],[226,"Alt"],[44,"Space"],[230,"Alt"],[231,"Meta"],[228,"Ctrl"],[80,"←"],[81,"↓"],[79,"→"]]]
                for(var r=0;r<rows.length;r++){var row=rows[r],x=12;for(var key of row){var w=key[0]===44?186:(r===3&&key[0]===40?76:r===4&&(key[0]===225||key[0]===229)?61:42);box(x,43+r*48,w,40,keys.indexOf(key[0])>=0||pulsed("keyboard",key[0])?accent:dim,key[1]);x+=w+4}}
                var extra=[[[70,"Prt"],[71,"Scr"],[72,"Pause"],[83,"Num"],[84,"/"],[85,"*"],[86,"−"]],[[73,"Ins"],[74,"Home"],[75,"PgUp"],[95,"7"],[96,"8"],[97,"9"],[87,"+"]],[[76,"Del"],[77,"End"],[78,"PgDn"],[92,"4"],[93,"5"],[94,"6"]],[[0,""],[0,""],[0,""],[89,"1"],[90,"2"],[91,"3"],[88,"Enter"]],[[0,""],[0,""],[0,""],[98,"0"],[99,"."]]]
                for(var er=0;er<extra.length;er++)for(var ek=0;ek<extra[er].length;ek++){var k=extra[er][ek];if(k[0])box(662+ek*40,43+er*48,36,40,keys.indexOf(k[0])>=0||pulsed("keyboard",k[0])?accent:dim,k[1])}
            }
            c.restore()
        }
    }
}
