"""Qt adapter. All transport work stays in CompanionService's worker."""
import json
from pathlib import Path
from PySide6.QtCore import QObject, Property, Signal, Slot, QTimer, QSize
from PySide6.QtGui import QGuiApplication, QImage, QColor
from PySide6.QtQuick import QQuickImageProvider
from configuration import load, save
from service import CompanionService
from diagnostic_report import checks, export_json


class QrProvider(QQuickImageProvider):
    def __init__(self):
        super().__init__(QQuickImageProvider.ImageType.Image)
        self.image=QImage()

    def update(self, payload):
        if not payload:self.image=QImage();return
        import qrcode
        qr=qrcode.QRCode(border=4,error_correction=qrcode.constants.ERROR_CORRECT_M)
        qr.add_data(payload);qr.make(fit=True);matrix=qr.get_matrix()
        image=QImage(len(matrix),len(matrix),QImage.Format.Format_RGB32);image.fill(QColor('white'))
        for y,row in enumerate(matrix):
            for x,dark in enumerate(row):
                if dark:image.setPixelColor(x,y,QColor('#101820'))
        self.image=image

    def requestImage(self, image_id, size, requested_size):
        size.setWidth(self.image.width());size.setHeight(self.image.height())
        return self.image


class Controller(QObject):
    statusChanged=Signal()
    liveChanged=Signal()
    messageChanged=Signal()
    qrChanged=Signal()
    def __init__(self, provider, directory=None):
        super().__init__()
        self.path=Path(directory or Path.home()/'.config/phonepad')/'settings.json'
        self._message=''
        self._message_error=False
        self.message_timer=QTimer(self);self.message_timer.setSingleShot(True)
        self.message_timer.timeout.connect(self.clearMessage)
        try:config=load(self.path)
        except (OSError,ValueError,TypeError) as error:
            from configuration import DEFAULTS
            config=dict(DEFAULTS);self._message='Settings could not be loaded: '+str(error);self._message_error=True
        self._desired_config=dict(config)
        self.service=CompanionService(config,self.path.parent)
        self.provider=provider;self._qr='';self._qr_url='';self.version=0
        self._status=self.service.snapshot();self._live=self._status.pop('live');self._history=[]
        self._status['history']=[]
        self.timer=QTimer(self);self.timer.setInterval(33);self.timer.timeout.connect(self.poll);self.timer.start()
        self.service.command('refresh')

    @Property('QVariantMap',notify=statusChanged)
    def status(self):return self._status
    @Property('QVariantList',notify=statusChanged)
    def diagnosticChecks(self):return checks(self._status)
    @Property('QVariantMap',notify=liveChanged)
    def live(self):return self._live
    @Property(str,notify=messageChanged)
    def message(self):return self._message
    @Property(bool,notify=messageChanged)
    def messageError(self):return self._message_error
    @Property(str,notify=qrChanged)
    def qrUrl(self):return self._qr_url

    def report(self,text,transient=False):
        self.message_timer.stop()
        self._message=str(text);self._message_error=not transient;self.messageChanged.emit()
        if transient:self.message_timer.start(3000)

    @Slot()
    def clearMessage(self):
        self._message='';self._message_error=False;self.messageChanged.emit()

    @Slot()
    def poll(self):
        state=self.service.snapshot();live=state.pop('live')
        if state['errors'] and state['errors'] != self._status.get('errors', []):
            self.report(state['errors'][-1])
        if live!=self._live:self._live=live;self.liveChanged.emit()
        if state['qr']!=self._qr:
            self._qr=state['qr']
            try:
                self.provider.update(self._qr);self.version+=1
                self._qr_url='image://pairing/'+str(self.version) if self._qr else ''
            except Exception as error:
                self._qr_url='';self.report('QR rendering failed: '+str(error))
            self.qrChanged.emit()
        if state!=self._status:self._status=state;self.statusChanged.emit()

    @Slot(str)
    def command(self,name):self.service.command(name)
    @Slot(int)
    def rumble(self,slot):self.service.command('rumble',slot)
    @Slot(bool)
    def trace(self,enabled):self.service.command('trace',enabled)
    @Slot(bool)
    def approve(self,approved):self.service.command('approve',approved)
    @Slot(str)
    def reconnect(self,address):self.service.command('reconnect',address)
    @Slot(str)
    def pairBluetooth(self,address):self.service.command('pair_bluetooth',address)
    @Slot(str,bool)
    def receiver(self,transport,enabled):self.service.command('receiver',(transport,enabled))
    @Slot(str,bool)
    def enable(self,transport,enabled):
        config={**self._desired_config,transport:enabled}
        self._save(config)
    @Slot(str,str,str,str,bool)
    def settings(self,name,bind,port,backend,feedback):
        self._save({**self._desired_config,'name':name,'bind':bind,'port':port,'backend':backend,'trace_feedback':feedback})
    def _save(self,config):
        try:
            config=save(self.path,config);self._desired_config=dict(config);self.service.command('configure',config)
            self.report('Settings saved',transient=True)
        except (OSError,ValueError,TypeError) as error:self.report(error)
    @Slot()
    def copyConnection(self):
        if self._qr:QGuiApplication.clipboard().setText(self._qr);self.report('Pairing information copied',transient=True)
        else:self.report('Start Wi-Fi and generate a pairing code first.')
    @Slot()
    def copyRepairCommand(self):
        command=self._status['permissions']['recovery_command']
        QGuiApplication.clipboard().setText(command)
        self.report('Repair command copied — review it before running',transient=True)
    @Slot()
    def runDiagnostics(self):
        self.service.command('refresh')
        self.service.command('inspect_backend')
        self.report('Diagnostics refreshed',transient=True)
    @Slot()
    def copyDiagnosticReport(self):
        QGuiApplication.clipboard().setText(export_json(self.service.snapshot()))
        self.report('Sanitized diagnostic report copied',transient=True)
    @Slot(result=str)
    def diagnostics(self):return json.dumps(self._status,indent=2,default=str)
    def close(self):self.timer.stop();self.service.close()
