"""Precise UI labels derived from independent service and input evidence."""


def summarize(state):
    bt = state['bluetooth']
    native = bool(bt.get('hid') or bt.get('input_devices'))
    wifi = bool(state['peer'])
    transports = (['Wi-Fi'] if wifi else []) + (['Bluetooth HID'] if native else [])
    devices = bt.get('hid') or bt.get('input_devices') or []
    name = (devices[0].get('product') or devices[0].get('name') or 'PhonePad') if native else 'PhonePad' if wifi else 'No phone'
    kernel = state['native_counters']['kernel_input_reports']
    raw = state['native_counters']['received_reports']
    decoded = state['native_counters']['decoded_reports']
    counters = state['counters']
    observed = state['native_observing']
    return dict(phone=name, transport=' + '.join(transports) or 'Not connected',
                connected=wifi or native, hid_connected=native,
                service='Receivers ' + state['state'].lower(),
                adapter='On' if bt.get('powered') is True else 'Off' if bt.get('powered') is False else 'Unknown',
                observation='Observing' if observed or wifi else 'Waiting',
                received=bool(raw or kernel or counters.get('received_packets', 0)),
                decoded=bool(decoded or kernel or counters.get('backend_writes', 0)),
                os_evidence='Linux events observed' if kernel else 'HID devices present' if bt.get('input_devices') else 'Not observed',
                backend='Kernel HID' if native else state['virtual'],
                writes=counters.get('backend_writes', 0))
