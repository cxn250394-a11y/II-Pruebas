#!/usr/bin/env python3
"""Check debug-only live state samples; a silent or stalled renderer is never a pass."""
import math
import re
import sys

PATTERN = re.compile(r'SANDRIFT_STATE menu=(true|false) paused=(true|false) finished=(true|false) elapsed=([0-9.eE+-]+)')


def check(log, expected):
    samples = []
    for match in PATTERN.finditer(log):
        menu, paused, finished, elapsed = match.groups()
        elapsed = float(elapsed)
        if not math.isfinite(elapsed):
            return False
        samples.append((menu, paused, finished, elapsed))
    if len(samples) < 2:
        return False
    want_paused = 'true' if expected == 'paused' else 'false'
    if any(menu != 'false' or paused != want_paused or finished != 'false'
           for menu, paused, finished, _ in samples):
        return False
    times = [sample[3] for sample in samples]
    if expected == 'paused':
        return max(times) - min(times) < 0.01
    return times[-1] - times[0] > 0.05


if __name__ == '__main__':
    if len(sys.argv) != 3 or sys.argv[2] not in ('paused', 'running'):
        sys.exit('usage: assert-state.py LOG paused|running')
    with open(sys.argv[1], encoding='utf-8') as f:
        ok = check(f.read(), sys.argv[2])
    print(f"{'PASS' if ok else 'FAIL'} live-state-{sys.argv[2]}")
    sys.exit(0 if ok else 1)
