"""Trajectory evidence is captured before UI throttling, with paired axes."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from service import CompanionService


class ControllerTrajectoryTests(unittest.TestCase):
    def setUp(self):
        self.service = CompanionService({'backend': 'dry-run'})

    def observe(self, x, y, source='wifi'):
        self.service._observe(source, 'controller', [0, 8, x, y, -x, -y, 0, 0])

    def test_paired_axes_survive_multiple_updates_between_ui_polls(self):
        self.service._handle('trace', True)
        self.observe(100, 200)
        self.observe(300, 400)
        self.service._publish()
        samples = self.service.snapshot()['controller_samples']
        self.assertEqual([p['axes'] for p in samples], [[100, 200, -100, -200], [300, 400, -300, -400]])
        samples[0]['axes'][0] = 999
        self.assertEqual(self.service.snapshot()['controller_samples'][0]['axes'][0], 100)

    def test_record_pause_clear_and_source_boundaries(self):
        self.observe(1, 2)
        self.assertFalse(self.service.controller_samples)
        self.service._handle('trace', True)
        self.observe(1, 2)
        self.observe(1, 2)
        self.observe(1, 2, 'bluetooth')
        self.assertEqual([p['source'] for p in self.service.controller_samples], ['wifi', 'bluetooth'])
        self.service._handle('trace', False)
        self.observe(3, 4)
        self.assertEqual(len(self.service.controller_samples), 2)
        self.assertEqual(self.service.live['controller'][2:4], [3, 4])
        self.service._handle('clear', None)
        self.assertFalse(self.service.controller_samples)
        self.assertFalse(self.service.history)

    def test_bounded_history_and_other_categories_do_not_add_points(self):
        self.service._handle('trace', True)
        for i in range(1000):
            self.observe(i, i + 1)
        self.service._observe('wifi', 'keyboard', [62])
        self.service._observe('wifi', 'mouse', [1, 2, 3, 0])
        self.assertEqual(len(self.service.controller_samples), 256)
        self.assertEqual(self.service.controller_samples[0]['axes'][0], 744)
        self.assertEqual(self.service.controller_samples[-1]['axes'][0], 999)
