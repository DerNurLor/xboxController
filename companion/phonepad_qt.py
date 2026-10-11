#!/usr/bin/env python3
"""PhonePad Qt Quick desktop entry point."""
import argparse
import os
import sys
from pathlib import Path
from PySide6.QtCore import QTimer, QUrl
from PySide6.QtGui import QGuiApplication
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle
from qt_controller import Controller, QrProvider


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--smoke-test',action='store_true')
    parser.add_argument('--screenshot',type=Path)
    parser.add_argument('--config-dir',type=Path)
    parser.add_argument('--page',type=int,default=0,choices=range(5))
    parser.add_argument('--width',type=int,default=1180)
    parser.add_argument('--height',type=int,default=800)
    args=parser.parse_args()
    QQuickStyle.setStyle('Basic')
    app=QGuiApplication(sys.argv);app.setApplicationName('PhonePad Companion');app.setOrganizationName('PhonePad')
    engine=QQmlApplicationEngine();provider=QrProvider();controller=Controller(provider,args.config_dir)
    engine.addImageProvider('pairing',provider)
    engine.rootContext().setContextProperty('bridge',controller)
    engine.load(QUrl.fromLocalFile(str(Path(__file__).parent/'qml/Main.qml')))
    if not engine.rootObjects():controller.close();return 1
    window=engine.rootObjects()[0];window.setProperty('page',args.page);window.setWidth(args.width);window.setHeight(args.height)
    app.aboutToQuit.connect(controller.close)
    def finish():
        if args.screenshot:
            args.screenshot.parent.mkdir(parents=True,exist_ok=True)
            if not window.grabWindow().save(str(args.screenshot)):app.exit(2);return
        app.quit()
    if args.smoke_test or args.screenshot:QTimer.singleShot(900,finish)
    result=app.exec()
    del engine
    return result

if __name__=='__main__':sys.exit(main())
