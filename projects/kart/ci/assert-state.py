#!/usr/bin/env python3
"""Check debug-only live state samples; a silent or stalled renderer is never a pass."""
import math
import re
import sys

PATTERN = re.compile(r'SANDRIFT_STATE boot_ms=([0-9]+) menu=(true|false) paused=(true|false) finished=(true|false) elapsed=([0-9.eE+-]+)')


def check(log, expected, start_ms, end_ms):
    if end_ms - start_ms < 6500:
        return False
    samples = []
    for match in PATTERN.finditer(log):
        boot_ms, menu, paused, finished, elapsed = match.groups()
        elapsed = float(elapsed)
        if not math.isfinite(elapsed):
            return False
        boot_ms = int(boot_ms)
        if start_ms <= boot_ms <= end_ms:
            samples.append((boot_ms, menu, paused, finished, elapsed))
    if len(samples) < 3:
        return False
    stamps = [sample[0] for sample in samples]
    if any(b <= a for a, b in zip(stamps, stamps[1:])):
        return False
    if (stamps[-1] - stamps[0] < 3500 or stamps[0] - start_ms > 3500
            or end_ms - stamps[-1] > 3500
            or any(b - a > 3500 for a, b in zip(stamps, stamps[1:]))):
        return False
    want_paused = 'true' if expected == 'paused' else 'false'
    if any(menu != 'false' or paused != want_paused or finished != 'false'
           for _, menu, paused, finished, _ in samples):
        return False
    times = [sample[4] for sample in samples]
    if expected == 'paused':
        return max(times) - min(times) < 0.01
    return times[-1] - times[0] > 0.05


if __name__ == '__main__':
    if len(sys.argv) != 5 or sys.argv[2] not in ('paused', 'running'):
        sys.exit('usage: assert-state.py LOG paused|running DEVICE_START_MS DEVICE_END_MS')
    with open(sys.argv[1], encoding='utf-8') as f:
        ok = check(f.read(), sys.argv[2], int(sys.argv[3]), int(sys.argv[4]))
    print(f"{'PASS' if ok else 'FAIL'} live-state-{sys.argv[2]}")
    sys.exit(0 if ok else 1)
