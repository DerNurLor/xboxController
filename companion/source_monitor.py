"""Read only vendor-defined source reports; never inject them into Linux input."""
import os
from protocol import decode, NEUTRAL


class SourceMonitor:
    def __init__(self, observe):
        self.observe=observe;self.devices={};self.errors={};self.reports=0

    def update(self, devices):
        paths={d['path'] for d in devices if d.get('source_input')}
        for path in list(self.devices):
            if path not in paths:self._close(path)
        self.errors={path:error for path,error in self.errors.items() if path in paths}
        for path in paths-self.devices.keys():
            try:
                self.devices[path]=dict(fd=os.open(path,os.O_RDONLY|os.O_NONBLOCK),last=0,active=False)
                self.errors.pop(path,None)
            except OSError as error:self.errors[path]=str(error)

    def accept(self, path, report, now):
        if len(report)!=14 or report[0]!=5:return False
        try:decode(report[1:])
        except ValueError:return False
        self.observe('bluetooth-source:'+path,report[1:])
        entry=self.devices[path];entry['last']=now;entry['active']=report[1:]!=NEUTRAL
        self.reports+=1
        return True

    def poll(self, now):
        for path,entry in list(self.devices.items()):
            try:
                for _ in range(64):
                    try:report=os.read(entry['fd'],128)
                    except BlockingIOError:break
                    if not report:raise OSError('HID device closed')
                    self.accept(path,report,now)
                if entry['active'] and now-entry['last']>.5:
                    self.observe('bluetooth-source:'+path,NEUTRAL);entry['active']=False
            except OSError as error:
                self.errors[path]=str(error);self._close(path)

    def _close(self, path):
        entry=self.devices.pop(path)
        try:self.observe('bluetooth-source:'+path,NEUTRAL)
        finally:os.close(entry['fd'])

    def close(self):
        for path in list(self.devices):self._close(path)
