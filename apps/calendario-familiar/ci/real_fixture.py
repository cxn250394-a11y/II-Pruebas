#!/usr/bin/env python3
"""Drive synthetic calendar data through the real Android UI and verify restart."""
import datetime
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = 'mx.charly.calendario.terra'
OUT = Path('calendario-evidence')
TITLE = 'CitaSinteticaQA'
NOTE = 'NotaSinteticaQA'
EXPECTED_COLOR = 0xFFD6EBFA - (1 << 32)  # Palette Cielo, Java signed int
counter = 0


def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=45)


def dump(name):
    global counter
    counter += 1
    adb('shell', 'uiautomator', 'dump', '/sdcard/cal-fixture.xml')
    data = adb('exec-out', 'cat', '/sdcard/cal-fixture.xml').stdout
    (OUT / f'fixture-{counter:02d}-{name}.xml').write_bytes(data)
    return ET.fromstring(data)


def bounds(node):
    xy = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
    assert len(xy) == 4 and xy[2] > xy[0] and xy[3] > xy[1], node.attrib
    return ((xy[0] + xy[2]) // 2, (xy[1] + xy[3]) // 2)


def tap_node(node):
    x, y = bounds(node)
    adb('shell', 'input', 'tap', str(x), str(y))
    time.sleep(1)


def find(root, predicate):
    return [node for node in root.iter() if predicate(node)]


def find_button(root, text):
    # Android's themed dialog buttons may render all caps even when the
    # application supplies mixed case. Keep both the label and type exact.
    return find(root, lambda n: n.get('class') == 'android.widget.Button'
                and n.get('clickable') == 'true'
                and n.get('text', '').casefold() == text.casefold())


def tap_text(text, *, exact=False, scroll=False):
    for attempt in range(5 if scroll else 1):
        root = dump('find')
        def matches(node):
            values = (node.get('text', ''), node.get('content-desc', ''))
            return any(v == text if exact else text in v for v in values)
        nodes = find(root, matches)
        if nodes:
            tap_node(nodes[0])
            return
        if attempt < 4 and scroll:
            adb('shell', 'input', 'swipe', '850', '2000', '850', '1050', '500')
            time.sleep(1)
    raise AssertionError(f'No visible UI element: {text}')


def reveal_text(text):
    for attempt in range(5):
        root = dump('reveal')
        if find(root, lambda n: text in n.get('text', '') or text in n.get('content-desc', '')):
            return root
        if attempt < 4:
            adb('shell', 'input', 'swipe', '850', '2000', '850', '1050', '500')
            time.sleep(1)
    raise AssertionError(f'No visible UI content after scrolling: {text}')


def capture(name):
    (OUT / name).write_bytes(adb('exec-out', 'screencap', '-p').stdout)


def field(hint, value):
    root = dump('form')
    nodes = find(root, lambda n: n.get('class') == 'android.widget.EditText'
                 and hint in n.get('text', ''))
    assert len(nodes) == 1, f'Expected one input {hint}: {[n.attrib for n in nodes]}'
    tap_node(nodes[0])
    adb('shell', 'input', 'text', value)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')  # close the keyboard
    time.sleep(1)


def start_launcher():
    result = adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a',
                 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', PKG)
    launcher = result.stdout.decode().strip().splitlines()[-1].strip()
    assert re.fullmatch(r'mx\.charly\.calendario\.terra/mx\.charly\.calendario\.Day\d\d', launcher), launcher
    (OUT / 'fixture-restart-launcher.txt').write_text(launcher + '\n')
    adb('shell', 'am', 'start', '-W', '-n', launcher)
    time.sleep(5)


def check_database(date):
    # App has been force-stopped: copy debug DB and WAL (if present) to an isolated
    # temp directory, then inspect the real app-written database read-only.
    listing = adb('shell', 'run-as', PKG, 'ls', 'databases').stdout.decode().split()
    with tempfile.TemporaryDirectory(prefix='calendar-fixture-') as temp:
        directory = Path(temp)
        name = 'calendario-familiar.db'
        assert name in listing, listing
        for suffix in ('', '-wal', '-shm'):
            filename = name + suffix
            if filename in listing:
                data = adb('exec-out', 'run-as', PKG, 'cat', 'databases/' + filename).stdout
                (directory / filename).write_bytes(data)
        with sqlite3.connect(f'file:{directory / name}?mode=ro', uri=True) as db:
            rows = db.execute('SELECT e.day,e.title,e.event_time,e.note,d.color '
                'FROM events e LEFT JOIN day_colors d ON d.profile_id=e.profile_id AND d.day=e.day '
                'WHERE e.profile_id=1 AND e.title=?', (TITLE,)).fetchall()
            assert rows == [(date, TITLE, '09:30', NOTE, EXPECTED_COLOR)], rows
            assert db.execute('SELECT COUNT(*) FROM events WHERE profile_id=2 AND title=?', (TITLE,)).fetchone()[0] == 0
        (OUT / 'fixture-db-check.txt').write_text(
            f'PASS SQLite: profile=Yo, date={date}, title={TITLE}, time=09:30, note={NOTE}, '
            f'day_color=Cielo(0xFFD6EBFA); profile Mamá has no fixture.\n')


def find_saved_week_card():
    # A test that straddles midnight on Sunday can restart in a different
    # week. Search current, previous and next week, bounded to one step.
    for attempt, navigation in enumerate((None, 'Semana anterior', 'Semana siguiente', 'Semana siguiente')):
        if navigation:
            tap_text(navigation)
        # On the third move we have reached the week after the original one;
        # skip the intermediate return to the original week.
        if attempt == 2:
            continue
        root = dump('week-after-restart')
        cards = find(root, lambda n: n.get('clickable') == 'true' and TITLE in n.get('content-desc', ''))
        assert len(cards) <= 1, f'Duplicate fixture cards: {len(cards)}'
        if cards:
            return cards[0]
    raise AssertionError('Persisted fixture not visible in current or adjacent weeks')


def main():
    # emulator.sh already navigated to week using the real app. Choose its first
    # empty day, which remains visible even if the current week spans months.
    root = dump('week-before')
    cards = find(root, lambda n: n.get('clickable') == 'true'
                 and 'Sin eventos. Abrir día.' in n.get('content-desc', ''))
    assert cards, 'Expected an empty visible day card in the synthetic emulator'
    tap_node(cards[0])
    root = dump('day-before')
    dates = [n.get('text', '') for n in root.iter() if re.fullmatch(r'\d{4}-\d{2}-\d{2}', n.get('text', ''))]
    assert len(dates) == 1, dates
    date = dates[0]
    datetime.date.fromisoformat(date)
    tap_text('Pintar día', exact=True)
    tap_text('Cielo', exact=True)
    tap_text('Añadir evento o cita')
    field('Ej. Consulta', TITLE)
    field('Hora opcional', '09:30')
    field('Detalles importantes', NOTE)
    root = dump('save-button')
    buttons = find_button(root, 'Guardar')
    assert len(buttons) == 1, f'Expected exactly one Save dialog button: {len(buttons)}'
    tap_node(buttons[0])
    reveal_text(TITLE)
    capture('fixture-day-saved.png')
    # Restart the process, not just the screen, before checking persistence.
    adb('shell', 'am', 'force-stop', PKG)
    check_database(date)
    start_launcher()
    tap_text('Ver semana')
    card = find_saved_week_card()
    description = card.get('content-desc', '')
    assert '09:30' in description and NOTE in description, description
    capture('fixture-week-after-restart.png')
    tap_node(card)
    root = dump('day-after-restart')
    visible = ' '.join((n.get('text', '') + ' ' + n.get('content-desc', '')) for n in root.iter())
    assert date in visible, visible
    reveal_text(TITLE)
    reveal_text(NOTE)
    capture('fixture-day-after-restart.png')
    (OUT / 'fixture-summary.txt').write_text(
        'PASS UI: created synthetic appointment and painted day through native controls; '
        'force-stop/restart retained them; weekly card and daily detail verified. '
        'SQLite values verified independently; physical device and TalkBack NOT_RUN.\n')


if __name__ == '__main__':
    main()
