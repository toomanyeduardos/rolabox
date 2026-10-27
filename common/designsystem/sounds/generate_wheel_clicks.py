"""Generates the wheel's click sounds, made for Rolabox (39.08).

The sounds are synthesized here rather than recorded or copied, so they belong to the project. Run it
from the repository root after changing a sound, and commit the WAVs it writes:

    python3 common/designsystem/sounds/generate_wheel_clicks.py

A step of a turn gets a short, bright tick. A press gets a lower, slightly longer tock. Both start at
full strength on the first samples, so the click lands with the haptic and the step it belongs to.
"""

import math
import random
import struct
import wave
from pathlib import Path

RATE = 44_100
OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "res" / "raw"


def click(duration_ms, tones, tone_decay_ms, noise_decay_ms, noise_level, peak, seed):
    """A decaying noise burst for the attack, plus decaying sines for the click's pitch."""
    rng = random.Random(seed)
    count = int(RATE * duration_ms / 1000)
    samples = []
    previous_noise = 0.0
    for i in range(count):
        t = i / RATE
        noise = rng.uniform(-1.0, 1.0)
        # A first-order high-pass keeps the noise crisp instead of thumping.
        bright = noise - previous_noise
        previous_noise = noise
        value = noise_level * bright * math.exp(-t * 1000 / noise_decay_ms)
        for frequency, level in tones:
            value += level * math.sin(2 * math.pi * frequency * t) * math.exp(-t * 1000 / tone_decay_ms)
        samples.append(value)
    # A fade over the last millisecond, so the sound ends at silence without a pop.
    fade = int(RATE / 1000)
    for i in range(fade):
        samples[count - fade + i] *= 1 - (i + 1) / fade
    scale = peak / max(abs(s) for s in samples)
    return [s * scale for s in samples]


def write(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / name), "wb") as file:
        file.setnchannels(1)
        file.setsampwidth(2)
        file.setframerate(RATE)
        file.writeframes(b"".join(struct.pack("<h", round(s * 32_767)) for s in samples))


def main():
    write(
        "ds_wheel_tick.wav",
        click(duration_ms=7, tones=[(4_200, 1.0), (6_100, 0.4)], tone_decay_ms=1.0,
              noise_decay_ms=0.5, noise_level=0.6, peak=0.6, seed=8),
    )
    write(
        "ds_wheel_press.wav",
        click(duration_ms=16, tones=[(1_600, 1.0), (2_900, 0.5)], tone_decay_ms=3.0,
              noise_decay_ms=0.6, noise_level=0.5, peak=0.7, seed=9),
    )


if __name__ == "__main__":
    main()
