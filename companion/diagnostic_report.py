"""Small, sanitized diagnostic summaries for the Qt Companion."""
import json
from datetime import datetime, timezone


def _check(name, status, detail, recovery=""):
    return dict(name=name, status=status, detail=detail, recovery=recovery)


def checks(snapshot):
    connection = snapshot.get('connection', {})
    config = snapshot.get('config', {})
    backend = config.get('backend', 'unknown')
    result = [
        _check('Phone connection', 'Pass' if connection.get('connected') else 'Not Tested',
               connection.get('transport', 'No phone connected'),
               'Start a receiver, then pair the phone from Connections.'),
        _check('Input received', 'Pass' if connection.get('received') else 'Not Tested',
               'Input has reached the Companion.' if connection.get('received') else 'No controller input received yet.',
               'Open Input Monitor and press a phone control.'),
        _check('Input decoded', 'Pass' if connection.get('decoded') else 'Not Tested',
               'A controller or desktop report was decoded.' if connection.get('decoded') else 'No decoded input is available yet.',
               'Check the selected transport and pair the phone again if input stays missing.'),
    ]
    if connection.get('hid_connected'):
        forwarded = snapshot.get('native_counters', {}).get('kernel_input_reports', 0) > 0
        forward_detail = 'Linux observed input events.' if forwarded else 'Bluetooth HID is present; no Linux input event observed yet.'
        result.append(_check('Input forwarded to OS', 'Pass' if forwarded else 'Not Tested', forward_detail,
                             'Press a control and check OS observation in Input Monitor.'))
    else:
        writes = connection.get('writes', 0) > 0
        result.append(_check('Input forwarded to backend', 'Pass' if writes else 'Not Tested',
                             'The selected backend accepted input.' if writes else 'No backend input write recorded.',
                             'Start a test and confirm the virtual devices are ready.'))

    devices = snapshot.get('devices', [])
    ready = bool(devices) and all(device.get('ready') for device in devices)
    if backend == 'dry-run':
        result.append(_check('Virtual input backend', 'Warning', 'Preview mode does not create OS input devices.',
                             'Choose the Linux or Windows backend to send input to the operating system.'))
    elif ready:
        result.append(_check('Virtual input backend', 'Pass', 'All virtual input devices are ready.'))
    elif snapshot.get('active'):
        result.append(_check('Virtual input backend', 'Failed', 'One or more virtual input devices are unavailable.',
                             'Open device details and resolve missing permissions or device capabilities.'))
    else:
        result.append(_check('Virtual input backend', 'Not Tested', 'Start the Companion to inspect virtual devices.',
                             'Start the Companion, then run diagnostics again.'))

    permissions = snapshot.get('permissions', {})
    required = [node for node in permissions.get('nodes', []) if node.get('required')]
    if not permissions.get('supported'):
        result.append(_check('Linux permissions', 'Not Tested', 'Linux device permissions do not apply on this system.'))
    elif not required:
        result.append(_check('Linux permissions', 'Not Tested', 'Permission details are not available yet.',
                             'Refresh diagnostics to inspect /dev/uinput.'))
    else:
        writable = all(node.get('exists') and node.get('writable') for node in required)
        result.append(_check('Linux permissions', 'Pass' if writable else 'Failed',
                             'Required input device is writable.' if writable else 'Required input device is missing or not writable.',
                             '' if writable else 'Review the suggested Linux permission repair under Devices and permissions.'))

    feedback = snapshot.get('feedback', {})
    result.append(_check('Phone vibration feedback', 'Pass' if feedback.get('available') else 'Warning',
                         feedback.get('reason', 'No feedback path detected.'),
                         '' if feedback.get('available') else 'Feedback needs a supported, connected host output path.'))
    return result


def report(snapshot):
    rows = checks(snapshot)
    return dict(generated_at=datetime.now(timezone.utc).isoformat(timespec='seconds'),
                system={'platform': 'Linux' if snapshot.get('permissions', {}).get('supported') else 'Other',
                        'backend': snapshot.get('config', {}).get('backend', 'unknown'),
                        'wifi_receiver': snapshot.get('receivers', {}).get('wifi', 'Unknown'),
                        'bluetooth_receiver': snapshot.get('receivers', {}).get('bluetooth', 'Unknown')},
                checks=rows)


def export_json(snapshot):
    """Export only summarized status. Never include addresses, paths, keys, or raw reports."""
    return json.dumps(report(snapshot), indent=2, sort_keys=True)
