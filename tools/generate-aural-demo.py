#!/usr/bin/env python3
"""Generate an original quiet PCM test tone. No downloaded music or dependencies."""
import math
import struct
import wave
from pathlib import Path
root = Path(__file__).resolve().parents[1]
p = root / 'apps/aural/app/src/main/assets/aural-demo.wav'
p.parent.mkdir(parents=True, exist_ok=True)
rate, seconds = 44100, 8
with wave.open(str(p), 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(rate)
    for i in range(rate * seconds):
        t = i / rate
        envelope = min(t / .25, (seconds - t) / .25, 1.0)
        value = int(32767 * .035 * envelope * (math.sin(2 * math.pi * 440 * t) + .5 * math.sin(2 * math.pi * 660 * t)))
        w.writeframesraw(struct.pack('<hh', value, value))
print('Generated original 8-second stereo PCM demo:', p.relative_to(root))
