#!/usr/bin/env python3
"""Record and explicitly wait out a Pixel Launcher ANR in the test emulator."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'mx.charly.calendario.terra'
OUT = Path('calendario-evidence')


def adb(*args):
    return subprocess.run(['adb', *args], stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=True, timeout=45).stdout


def guard(path):
    path = Path(path)
    for attempt in range(3):
        root = ET.parse(path).getroot()
        nodes = list(root.iter())
        anrs = [n for n in nodes if n.get('package') == 'android'
                and "isn't responding" in n.get('text', '').casefold()]
        if anrs:
            labels = [n.get('text', '') for n in anrs]
            if labels != ["Pixel Launcher isn't responding"]:
                raise AssertionError(f'Unexpected system ANR: {labels}')
            # Fail after two explicit Wait attempts. Preserve evidence first.
            count = len(list(OUT.glob('launcher-anr-*.xml'))) + 1
            prefix = OUT / f'launcher-anr-{count:02d}'
            prefix.with_suffix('.xml').write_bytes(path.read_bytes())
            prefix.with_suffix('.png').write_bytes(adb('exec-out', 'screencap', '-p'))
            with (OUT / 'launcher-anr-events.txt').open('a') as f:
                f.write(f'Observed Pixel Launcher ANR, snapshot {count}; '
                        + ('pressing Wait.\n' if attempt < 2 else 'unresolved; failing.\n'))
            if attempt >= 2:
                raise AssertionError('Pixel Launcher ANR remains after two Wait attempts')
            buttons = [n for n in nodes if n.get('package') == 'android'
                       and n.get('class') == 'android.widget.Button'
                       and n.get('clickable') == 'true'
                       and n.get('text', '').casefold() == 'wait']
            if len(buttons) != 1:
                raise AssertionError(f'Expected exactly one system Wait button, got {len(buttons)}')
            coords = list(map(int, re.findall(r'\d+', buttons[0].get('bounds', ''))))
            if len(coords) != 4 or coords[2] <= coords[0] or coords[3] <= coords[1]:
                raise AssertionError(f'Invalid Wait button bounds: {coords}')
            adb('shell', 'input', 'tap', str((coords[0] + coords[2]) // 2),
                str((coords[1] + coords[3]) // 2))
            time.sleep(3)
            adb('shell', 'uiautomator', 'dump', '/sdcard/calendar-guard.xml')
            path.write_bytes(adb('exec-out', 'cat', '/sdcard/calendar-guard.xml'))
            continue
        # Never pass through another system dialog or an unrelated foreground app.
        other_dialog = [n for n in nodes if n.get('package') == 'android'
                        and n.get('class') == 'android.widget.Button']
        if other_dialog:
            raise AssertionError('Unexpected system dialog is still visible')
        if not any(n.get('package') == PACKAGE for n in nodes):
            raise AssertionError('Calendar is not visible in the current UI hierarchy')
        return
    raise AssertionError('Launcher ANR guard exhausted')


if __name__ == '__main__':
    guard(sys.argv[1])
