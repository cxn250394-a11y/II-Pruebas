#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('state', Path(__file__).with_name('assert-state.py'))
state = importlib.util.module_from_spec(spec)
spec.loader.exec_module(state)

def sample(ms, paused='true', elapsed='10.0', menu='false', finished='false'):
    return f'SANDRIFT_STATE boot_ms={ms} menu={menu} paused={paused} finished={finished} elapsed={elapsed}\n'

def sequence(paused='true', menu='false', finished='false', times=('10', '10', '10')):
    return ''.join(sample(ms, paused, t, menu, finished) for ms, t in zip((1000, 3000, 5000), times))

class StateEvidenceTest(unittest.TestCase):
    def test_paused_and_running(self):
        self.assertTrue(state.check(sequence(), 'paused', 0, 7000))
        self.assertTrue(state.check(sequence('false', times=('10','11','12')), 'running', 0, 7000))
    def test_silence_and_short_samples_fail(self):
        for text in ('', sample(1000), sample(1000)+sample(3000)):
            self.assertFalse(state.check(text, 'paused', 0, 7000))
    def test_late_freeze_and_short_coverage_fail(self):
        self.assertFalse(state.check(sequence(), 'paused', 0, 9000))
        text = sample(4000)+sample(5000)+sample(6000)
        self.assertFalse(state.check(text, 'paused', 0, 7000))
        self.assertFalse(state.check(sequence(), 'paused', 0, 4000))
    def test_window_start_and_internal_gap_fail(self):
        self.assertFalse(state.check(sample(4000)+sample(6000)+sample(8000), 'paused', 0, 9000))
        self.assertFalse(state.check(sample(1000)+sample(3000)+sample(7000), 'paused', 0, 8000))
        # Samples outside the measured window cannot rescue incomplete evidence.
        self.assertFalse(state.check(sample(1000)+sample(3000)+sample(5000), 'paused', 2500, 9500))
    def test_wrong_screen_or_changed_time_fail(self):
        self.assertFalse(state.check(sequence(menu='true'), 'paused', 0, 7000))
        self.assertFalse(state.check(sequence(finished='true'), 'paused', 0, 7000))
        self.assertFalse(state.check(sequence(times=('10','10','11')), 'paused', 0, 7000))
        self.assertFalse(state.check(sequence('false'), 'running', 0, 7000))
        self.assertFalse(state.check(sample(1000)+sample(3000)+sample(5000,'false'), 'paused', 0, 7000))
    def test_nonmonotonic_timestamp_fails(self):
        self.assertFalse(state.check(sample(1000)+sample(5000)+sample(3000), 'paused', 0, 7000))

if __name__ == '__main__':
    unittest.main()
