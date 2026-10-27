#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('state', Path(__file__).with_name('assert-state.py'))
state = importlib.util.module_from_spec(spec)
spec.loader.exec_module(state)

def sample(paused='true', elapsed='10.0', menu='false', finished='false'):
    return f'SANDRIFT_STATE menu={menu} paused={paused} finished={finished} elapsed={elapsed}\n'

class StateEvidenceTest(unittest.TestCase):
    def test_paused_and_running(self):
        self.assertTrue(state.check(sample()+sample(), 'paused'))
        self.assertTrue(state.check(sample('false','10')+sample('false','12'), 'running'))
    def test_silence_and_single_sample_fail(self):
        for text in ('', sample()):
            self.assertFalse(state.check(text, 'paused'))
    def test_wrong_screen_or_changed_time_fail(self):
        self.assertFalse(state.check(sample(menu='true')*2, 'paused'))
        self.assertFalse(state.check(sample(finished='true')*2, 'paused'))
        self.assertFalse(state.check(sample()+sample(elapsed='11'), 'paused'))
        self.assertFalse(state.check(sample('false')*2, 'running'))
        self.assertFalse(state.check(sample()+sample('false'), 'paused'))

if __name__ == '__main__':
    unittest.main()
