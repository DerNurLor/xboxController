import QtQuick

Rectangle {
    property alias source: code.source
    color: "white"
    Image {
        id: code
        objectName: "pairingQrImage"
        anchors.centerIn: parent
        // The provider supplies one pixel per module, including the quiet zone.
        // Use whole modules rather than stretching a dense code to fractional pixels.
        width: sourceSize.width > 0 ? sourceSize.width * Math.max(1, Math.floor(Math.min(parent.width, parent.height) / sourceSize.width)) : 0
        height: width
        cache: false
        smooth: false
        mipmap: false
        Accessible.name: "PhonePad pairing QR code"
    }
}
