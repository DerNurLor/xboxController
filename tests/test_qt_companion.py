"""Offscreen production QML workflow checks, with the real UDP service in dry-run."""
import os
os.environ.setdefault('QT_QPA_PLATFORM','offscreen')
import sys
import socket
import struct
import time
import tempfile
import shutil
import subprocess
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
try:
    from PySide6.QtCore import QUrl,QObject,QMetaObject,QPointF,qInstallMessageHandler
    from PySide6.QtGui import QGuiApplication
    from PySide6.QtQml import QQmlApplicationEngine
    from PySide6.QtQuickControls2 import QQuickStyle
    from PySide6.QtTest import QTest
    import qrcode
    QT=True
except ImportError:QT=False

@unittest.skipUnless(QT,'Install Companion requirements for Qt/QR integration checks')
class QtCompanionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        QQuickStyle.setStyle('Basic')
        cls.app=QGuiApplication.instance() or QGuiApplication([])

    def wait_ui(self, milliseconds):
        until=time.monotonic()+milliseconds/1000
        while time.monotonic()<until:
            self.app.processEvents();time.sleep(.01)

    def test_rapid_settings_persist_and_notifications_expire(self):
        from qt_controller import Controller, QrProvider
        from configuration import load
        with tempfile.TemporaryDirectory() as root:
            controller=Controller(QrProvider(),root)
            try:
                controller.enable('wifi',False)
                controller.enable('bluetooth',False)
                controller.settings('Desk','127.0.0.1','39581','dry-run',False)
                saved=load(Path(root)/'settings.json')
                self.assertFalse(saved['wifi']);self.assertFalse(saved['bluetooth'])
                self.assertEqual(saved['name'],'Desk')
                self.assertEqual(controller.message,'Settings saved')
                self.assertFalse(controller.messageError)
                controller.message_timer.start(20);self.wait_ui(60)
                self.assertEqual(controller.message,'')
                controller.settings('Desk','invalid','39581','dry-run',False)
                self.assertTrue(controller.messageError)
                self.assertFalse(controller.message_timer.isActive())
                self.assertEqual(load(Path(root)/'settings.json'),saved)
                controller.clearMessage();self.assertEqual(controller.message,'')
            finally:controller.close()

    def test_player_selector_uses_independent_real_service_states(self):
        from qt_controller import Controller,QrProvider
        from configuration import save
        from protocol import packet,unpack,sign,STATE
        with tempfile.TemporaryDirectory() as root:
            with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as free:
                free.bind(('127.0.0.1',0));port=free.getsockname()[1]
            save(Path(root)/'settings.json',dict(backend='dry-run',bind='127.0.0.1',port=port,bluetooth=False))
            provider=QrProvider();controller=Controller(provider,root)
            engine=QQmlApplicationEngine();engine.addImageProvider('pairing',provider)
            engine.rootContext().setContextProperty('bridge',controller)
            engine.load(QUrl.fromLocalFile(str(Path(__file__).resolve().parents[1]/'companion/qml/Main.qml')))
            clients=[]
            try:
                controller.command('start')
                for _ in range(100):
                    self.wait_ui(10)
                    if controller.status['receivers']['wifi']=='Running':break
                key=bytes.fromhex((Path(root)/'pairing.key').read_text().strip())
                for slot in (1,2):
                    phone=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);phone.settimeout(2);clients.append(phone)
                    phone.connect(('127.0.0.1',port));identity=bytes([slot])*16
                    phone.send(packet(1,identity,0,b'',key));nonce=unpack(phone.recv(4096),key,2,16)[2]
                    session=sign(key,identity+nonce)
                    phone.send(packet(3,nonce,1,STATE.pack(slot,8,slot*1000,0,0,0,0,0),session));phone.recv(4096)
                window=engine.rootObjects()[0];window.setProperty('page',2)
                for _ in range(20):
                    self.wait_ui(10)
                    if len(controller.status.get('players',[]))==2:break
                monitor=window.findChild(QObject,'pageLoader').property('item')
                selector=monitor.findChild(QObject,'monitorPlayer')
                self.assertTrue(selector.isVisible());self.assertEqual(selector.property('count'),3)
                for slot in (1,2):
                    monitor.setProperty('playerSlot',slot);self.app.processEvents()
                    output=monitor.property('inputState').toVariant()['output']
                    self.assertEqual(output[0],slot);self.assertEqual(output[2],slot*1000)
            finally:
                for phone in clients:phone.close()
                controller.close()
                for window in engine.rootObjects():window.close()
                engine.deleteLater();self.wait_ui(30)

    def test_all_screens_sizes_start_pairing_monitor_and_stop(self):
        from qt_controller import Controller,QrProvider
        from configuration import save
        from protocol import packet,unpack,sign
        from desktop import NEUTRAL_DESKTOP
        errors=[]
        previous=qInstallMessageHandler(lambda kind,context,text:errors.append(text))
        try:
            with tempfile.TemporaryDirectory() as root:
                with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as free:
                    free.bind(('127.0.0.1',0));port=free.getsockname()[1]
                save(Path(root)/'settings.json',dict(backend='dry-run',bind='127.0.0.1',port=port))
                provider=QrProvider();controller=Controller(provider,root)
                engine=QQmlApplicationEngine();engine.addImageProvider('pairing',provider)
                engine.rootContext().setContextProperty('bridge',controller)
                engine.load(QUrl.fromLocalFile(str(Path(__file__).resolve().parents[1]/'companion/qml/Main.qml')))
                self.assertTrue(engine.rootObjects(),str(errors))
                window=engine.rootObjects()[0]
                capture_dir=os.environ.get('PHONEPAD_SCREENSHOTS')
                if capture_dir:Path(capture_dir).mkdir(parents=True,exist_ok=True)
                for width,height in ((800,600),(1180,800),(1600,1000)):
                    window.setWidth(width);window.setHeight(height)
                    self.wait_ui(50)
                    rail=window.findChild(QObject,'navigationRail')
                    def visual_items(item):
                        yield item
                        for child in item.childItems():
                            yield from visual_items(child)
                    navigation_buttons=[item for item in visual_items(rail)
                                        if item.objectName().startswith('navigationButton')
                                        or item.objectName()=='navigationCollapse']
                    self.assertEqual(len(navigation_buttons),6)
                    for navigation in navigation_buttons:
                        position=navigation.mapToItem(rail,QPointF(0,0))
                        self.assertGreaterEqual(position.x(),0)
                        self.assertLessEqual(position.x()+navigation.width(),rail.width()+1)
                    for page in range(5):
                        window.setProperty('page',page);self.wait_ui(80)
                        self.assertFalse(window.grabWindow().isNull())
                        if page==0:
                            dashboard=window.findChild(QObject,'pageLoader').property('item')
                            activity=dashboard.findChild(QObject,'dashboardInputStatus')
                            self.assertIsNotNone(activity)
                            if width==800:
                                self.assertEqual(activity.property('text'),'Waiting for phone')
                                for name,target in (('dashboardPair',1),('dashboardTest',2),('dashboardDiagnostics',4)):
                                    if target != 1:
                                        dashboard=window.findChild(QObject,'pageLoader').property('item')
                                    action=dashboard.findChild(QObject,name)
                                    self.assertIsNotNone(action)
                                    self.assertTrue(QMetaObject.invokeMethod(action,'clicked'))
                                    self.assertEqual(window.property('page'),target)
                                    window.setProperty('page',0);self.wait_ui(20)
                        if capture_dir:window.grabWindow().save(str(Path(capture_dir)/f'page-{page}-{width}x{height}.png'))
                        if page==2:
                            monitor=window.findChild(QObject,'pageLoader').property('item')
                            self.assertIsNotNone(monitor)
                            if width==800:
                                guide=monitor.findChild(QObject,'guidedInputTest')
                                prompt=monitor.findChild(QObject,'guidedInputPrompt')
                                self.assertIsNotNone(guide);self.assertIsNotNone(prompt)
                                self.assertTrue(QMetaObject.invokeMethod(guide,'clicked'))
                                self.assertIn('Press A',prompt.property('text'))
                                monitor.setProperty('guidedWaitMs',4000)
                                self.assertIn('No input yet',prompt.property('text'))
                                monitor.setProperty('guidedWaitMs',0)
                                controller.service._observe('wifi','controller',[1,8,0,0,0,0,0,0])
                                controller.service._publish();controller.poll();self.wait_ui(140)
                                self.assertIn('Move the left stick',prompt.property('text'))
                                controller.service._observe('wifi','controller',[0,8,7000,0,0,0,0,0])
                                controller.service._publish();controller.poll();self.wait_ui(140)
                                self.assertIn('Press a trigger',prompt.property('text'))
                                monitor.setProperty('view','mouse');self.wait_ui(30)
                                self.assertTrue(QMetaObject.invokeMethod(guide,'clicked'))
                                self.assertIn('Move the pointer',prompt.property('text'))
                                for state,expected in (([0,12,0,0],'Click the left button'),([1,0,0,0],'Scroll the wheel'),([1,0,0,1],None)):
                                    controller.service._observe('wifi','mouse',state)
                                    controller.service._publish();controller.poll();self.wait_ui(140)
                                    if expected:self.assertIn(expected,prompt.property('text'))
                                self.assertIn('Guided test complete',prompt.property('text'))
                                # Keep the later end-to-end packet assertions independent.
                                controller.service.mouse_activity.clear();controller.service.mouse_trail.clear()
                                controller.service.source_live.get('wifi',{}).pop('mouse',None)
                                controller.service.live['mouse']=[0,0,0,0]
                                controller.service._publish();controller.poll();self.wait_ui(30)
                            for mode in ('controller','mouse','keyboard'):
                                monitor.setProperty('view',mode);self.wait_ui(40)
                                self.assertFalse(window.grabWindow().isNull())
                                if capture_dir:window.grabWindow().save(str(Path(capture_dir)/f'monitor-{mode}-{width}x{height}.png'))
                window.setProperty('page',4);self.wait_ui(60)
                diagnostics=window.findChild(QObject,'pageLoader').property('item')
                repair=diagnostics.findChild(QObject,'copyRepairCommand')
                inspect=diagnostics.findChild(QObject,'inspectBackend')
                run=diagnostics.findChild(QObject,'runDiagnostics')
                export=diagnostics.findChild(QObject,'exportDiagnostics')
                self.assertIsNotNone(repair);self.assertIsNotNone(inspect)
                self.assertIsNotNone(run);self.assertIsNotNone(export)
                self.assertTrue(QMetaObject.invokeMethod(run,'clicked'))
                self.assertTrue(QMetaObject.invokeMethod(export,'clicked'))
                report=self.app.clipboard().text()
                self.assertIn('Phone connection',report)
                self.assertNotIn('recovery_command',report)
                self.assertNotIn('pairing.key',report)
                self.assertTrue(QMetaObject.invokeMethod(repair,'clicked'))
                self.assertEqual(self.app.clipboard().text(),controller.status['permissions']['recovery_command'])
                self.assertTrue(QMetaObject.invokeMethod(inspect,'clicked'));self.wait_ui(80)
                self.assertEqual(controller.status['state'],'Stopped')
                button=window.findChild(QObject,'serviceToggle')
                self.assertIsNotNone(button);self.assertTrue(QMetaObject.invokeMethod(button,'clicked'))
                for _ in range(250):
                    self.wait_ui(20)
                    if controller.status['state']=='Running':break
                self.assertEqual(controller.status['state'],'Running',controller.status)
                self.assertTrue(controller.qrUrl);self.assertFalse(provider.image.isNull())
                # Bluetooth monitoring and preference changes must preserve Wi-Fi's socket.
                receiver_socket=controller.service.sock
                window.setProperty('page',1)
                controller.enable('bluetooth',True);self.wait_ui(100)
                connections_item=window.findChild(QObject,'pageLoader').property('item')
                fullscreen=connections_item.findChild(QObject,'pairingFullscreen')
                self.assertTrue(fullscreen.property('enabled'))
                QMetaObject.invokeMethod(fullscreen,'clicked');self.wait_ui(100)
                qr_window=connections_item.findChild(QObject,'pairingFullscreenWindow')
                self.assertTrue(qr_window.isVisible())
                self.assertEqual(qr_window.property('qrSource').toString(),controller.qrUrl)
                qr_image=qr_window.findChild(QObject,'pairingQrImage')
                self.assertEqual(int(qr_image.width()) % provider.image.width(),0)
                self.assertGreaterEqual(qr_image.width()/provider.image.width(),3)
                # The camera reader uses ZXing. Verify its decoding of the actual
                # full-screen rendering, not just the underlying matrix.
                if shutil.which('java'):
                    screenshot=Path(root)/'qr.png';payload=Path(root)/'qr.txt'
                    self.assertTrue(qr_window.grabWindow().save(str(screenshot)))
                    payload.write_text(controller.status['qr'])
                    repo=Path(__file__).resolve().parents[1]
                    decoded=subprocess.run(['java','-Djava.awt.headless=true','-cp',
                        str(repo/'app/libs/zxing-core-3.5.3.jar'),str(repo/'tests/QrDecodeTest.java'),
                        str(screenshot),str(payload)],capture_output=True,text=True,timeout=30)
                    self.assertEqual(decoded.returncode,0,decoded.stderr)
                if capture_dir:
                    qr_window.grabWindow().save(str(Path(capture_dir)/'pairing-fullscreen.png'))
                    (Path(capture_dir)/'pairing-test-payload.txt').write_text(controller.status['qr'])
                old_qr=controller.qrUrl
                controller.command('pair')
                for _ in range(50):
                    self.wait_ui(20)
                    if controller.qrUrl!=old_qr:break
                self.assertNotEqual(controller.qrUrl,old_qr)
                self.assertEqual(qr_window.property('qrSource').toString(),controller.qrUrl)
                with controller.service.pairing.lock:
                    controller.service.pairing.qr_until=time.monotonic()-1
                for _ in range(50):
                    self.wait_ui(20)
                    if not controller.qrUrl:break
                self.assertFalse(controller.qrUrl)
                self.assertFalse(qr_window.findChild(QObject,'fullscreenPairingQr').isVisible())
                controller.command('pair');self.wait_ui(100)
                close_qr=qr_window.findChild(QObject,'closePairingFullscreen')
                QMetaObject.invokeMethod(close_qr,'clicked');self.wait_ui(30)
                self.assertFalse(qr_window.isVisible())
                bluetooth_button=connections_item.findChild(QObject,'bluetoothReceiver')
                self.assertIsNotNone(bluetooth_button)
                QMetaObject.invokeMethod(bluetooth_button,'clicked');self.wait_ui(100)
                self.assertFalse(controller.status['requested']['bluetooth'])
                self.assertIs(controller.service.sock,receiver_socket)
                self.assertEqual(controller.status['receivers']['wifi'],'Running')
                controller.trace(True)
                with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as phone:
                    phone.settimeout(2);host=('127.0.0.1',port)
                    key=bytes.fromhex((Path(root)/'pairing.key').read_text().strip())
                    phone.sendto(packet(1,bytes(16),0,b'',key),host)
                    nonce=unpack(phone.recv(1537),key,2,16)[2];session=sign(key,bytes(16)+nonce)
                    frame=bytearray(NEUTRAL_DESKTOP);frame[0]=16;frame[7]|=1<<6;frame[32]=1
                    frame[33:39]=struct.pack('!hhh',12,-5,0)
                    phone.sendto(packet(5,nonce,1,frame,session),host);phone.recv(1537)
                    self.wait_ui(90)
                    window.setProperty('page',2);self.wait_ui(50)
                    monitor=window.findChild(QObject,'pageLoader').property('item')
                    monitor.setProperty('view','mouse');self.wait_ui(50)
                    self.assertFalse(window.grabWindow().isNull())
                    evidence=monitor.findChild(QObject,'mouseEvidence')
                    self.assertIsNotNone(evidence)
                    self.assertEqual(controller.status['mouse_activity']['wifi']['button_presses'],1)
                    self.assertEqual(controller.status['mouse_activity']['wifi']['movement_reports'],1)
                    self.assertEqual(controller.status['mouse_trail'][-1][:2],(12,-5))
                    self.assertEqual(controller.live['keyboard'],[4,62]);self.assertEqual(controller.live['mouse'][0],1)
                    device_view=monitor.findChild(QObject,'inputDeviceView')
                    self.assertIsNotNone(device_view)
                    self.assertEqual(list(device_view.property('live').toVariant()['keyboard']),[4,62])
                    self.assertTrue(any(pulse['kind']=='mouse' and pulse['code']==0 for pulse in device_view.property('pulses').toVariant()))
                    summary=monitor.findChild(QObject,'recentOutputSummary')
                    self.assertIsNotNone(summary)
                    self.assertIn('Keyboard F5',summary.property('text'))
                    show_output=monitor.findChild(QObject,'showRecentOutput')
                    self.assertIsNotNone(show_output)
                    self.assertTrue(show_output.isVisible())
                    self.assertTrue(QMetaObject.invokeMethod(show_output,'clicked'))
                    self.assertEqual(monitor.property('view'),'keyboard')
                    self.assertEqual(list(device_view.property('motion')[-1])[:2],[12,-5])
                    controller.trace(False);self.wait_ui(80)
                    self.assertEqual(list(monitor.property('inputState').toVariant()['keyboard']),[4,62])
                    phone.sendto(packet(3,nonce,1,struct.pack('<HBhhhhBB',0,8,0,0,0,0,0,0),session),host);phone.recv(1537)
                    for _ in range(20):
                        self.wait_ui(10)
                        if controller.status['counters']['backend_writes'] >= 2:break
                    writes=controller.status['counters']['backend_writes']
                    source_state=struct.pack('<HBhhhhBB',1<<1,8,0,0,0,0,255,255)
                    phone.sendto(packet(7,nonce,1,source_state,session),host);phone.recv(1537)
                    self.wait_ui(90)
                    visual=monitor.property('inputState').toVariant()['controller']
                    self.assertEqual(visual[0],1<<1);self.assertEqual(list(visual[6:]),[255,255])
                    self.assertEqual(controller.live['controller'][0],0)
                    self.assertEqual(controller.status['counters']['backend_writes'],writes)
                    phone.sendto(packet(7,nonce,2,struct.pack('<HBhhhhBB',0,8,0,0,0,0,0,0),session),host);phone.recv(1537)
                    self.wait_ui(60)
                    # A remapped controller output must reach the same live
                    # controller state and button pulse used by the drawing.
                    pad_state=struct.pack('<HBhhhhBB',1<<1,8,0,0,0,0,0,0)
                    phone.sendto(packet(3,nonce,2,pad_state,session),host);phone.recv(1537)
                    self.wait_ui(80);monitor.setProperty('view','controller');self.wait_ui(40)
                    self.assertEqual(controller.live['controller'][0],1<<1)
                    self.assertTrue(any(pulse['kind']=='controller' and pulse['code']==1
                                        for pulse in device_view.property('pulses').toVariant()))
                    self.assertEqual(list(device_view.property('live').toVariant()['controller'])[0],1<<1)
                    window.setProperty('page',4);self.wait_ui(50)
                    diagnostics=window.findChild(QObject,'pageLoader').property('item')
                    diagnostics.findChild(QObject,'trajectoryDetails').setProperty('expanded',True)
                    self.wait_ui(80)
                    record=diagnostics.findChild(QObject,'trajectoryRecord')
                    clear=diagnostics.findChild(QObject,'trajectoryClear')
                    self.assertTrue(QMetaObject.invokeMethod(record,'clicked'));self.wait_ui(50)
                    for _ in range(30):
                        if controller.status['trace']:break
                        self.wait_ui(20)
                    self.assertTrue(controller.status['trace'],str(errors))
                    pad_state=struct.pack('<HBhhhhBB',1<<1,8,1234,-2345,3456,-4567,100,200)
                    phone.sendto(packet(3,nonce,3,pad_state,session),host);phone.recv(1537)
                    self.wait_ui(60)
                    left=diagnostics.findChild(QObject,'diagnosticLeftStick')
                    right=diagnostics.findChild(QObject,'diagnosticRightStick')
                    for _ in range(30):
                        if any(list(point['axes'])==[1234,-2345,3456,-4567]
                               for point in left.property('samples')):break
                        self.wait_ui(20)
                    self.assertTrue(any(list(point['axes'])==[1234,-2345,3456,-4567]
                                        for point in left.property('samples')))
                    self.assertEqual(right.property('axisOffset'),2)
                    self.assertTrue(QMetaObject.invokeMethod(record,'clicked'))
                    for _ in range(30):
                        self.wait_ui(20)
                        if not controller.status['trace']:break
                    self.assertFalse(controller.status['trace'])
                    frozen=list(left.property('samples'))
                    self.wait_ui(50)
                    self.assertEqual(list(left.property('samples')),frozen)
                    self.assertTrue(QMetaObject.invokeMethod(clear,'clicked'))
                    for _ in range(30):
                        self.wait_ui(20)
                        if not list(left.property('samples')):break
                    self.assertFalse(list(left.property('samples')))
                QMetaObject.invokeMethod(button,'clicked');self.wait_ui(800)
                self.assertEqual(controller.status['state'],'Stopped');self.assertEqual(controller.qrUrl,'')
                window.setProperty('page',1);self.wait_ui(60)
                connections_item=window.findChild(QObject,'pageLoader').property('item')
                wifi_button=connections_item.findChild(QObject,'wifiReceiver')
                QMetaObject.invokeMethod(wifi_button,'clicked');self.wait_ui(180)
                self.assertEqual(controller.status['receivers']['wifi'],'Running')
                self.assertTrue(controller.qrUrl)
                QMetaObject.invokeMethod(wifi_button,'clicked')
                for _ in range(150):
                    self.wait_ui(20)
                    if controller.status['receivers']['wifi']=='Stopped':break
                self.assertEqual(controller.status['receivers']['wifi'],'Stopped')
                self.assertEqual(controller.qrUrl,'')
                controller.close();window.close();del window;del button;del engine
                self.assertFalse(errors,'\n'.join(errors))
        finally:qInstallMessageHandler(previous)
