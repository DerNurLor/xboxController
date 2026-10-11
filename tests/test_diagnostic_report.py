import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from diagnostic_report import checks, export_json


def snapshot():
    return dict(connection=dict(connected=False, transport='Not connected', received=False,
                                decoded=False, writes=0, hid_connected=False),
                config=dict(backend='linux'), receivers=dict(wifi='Stopped', bluetooth='Stopped'),
                devices=[], active=False,
                permissions=dict(supported=True, nodes=[dict(name='uinput', path='/dev/uinput',
                    required=True, exists=False, writable=False)]),
                native_counters=dict(kernel_input_reports=0), feedback=dict(available=False,
                    reason='No feedback path available'),
                peer=['192.168.1.40'], history=[dict(state=[1, 2, 3])])


class DiagnosticReportTests(unittest.TestCase):
    def test_unobserved_stages_are_not_failed_and_missing_permission_is_actionable(self):
        rows = {row['name']: row for row in checks(snapshot())}
        self.assertEqual(rows['Phone connection']['status'], 'Not Tested')
        self.assertEqual(rows['Input received']['status'], 'Not Tested')
        self.assertEqual(rows['Linux permissions']['status'], 'Failed')
        self.assertIn('permission repair', rows['Linux permissions']['recovery'])
        self.assertEqual(rows['Phone vibration feedback']['status'], 'Warning')

    def test_export_excludes_addresses_paths_and_raw_event_data(self):
        result = export_json(snapshot())
        self.assertNotIn('192.168.1.40', result)
        self.assertNotIn('/dev/uinput', result)
        self.assertNotIn('"history"', result)
        self.assertIn('Input forwarded to backend', result)

    def test_dry_run_is_explicitly_warning_not_pass(self):
        state = snapshot()
        state['config']['backend'] = 'dry-run'
        rows = {row['name']: row for row in checks(state)}
        self.assertEqual(rows['Virtual input backend']['status'], 'Warning')


if __name__ == '__main__':
    unittest.main()
