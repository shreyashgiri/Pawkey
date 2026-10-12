#!/usr/bin/env python3
"""Generates PawKeys' key sounds into app/src/main/res/raw/.

All sounds are synthesised here (no recordings), so they are original and can be
tweaked and regenerated:  python3 tools/make_sounds.py

- click:  a soft tick
- kitten: a tiny, high "mew"
- cat:    a short "meow" (voice-like harmonics through sliding vowel formants)
- duck:   a nasal, buzzy "quack"
"""
import os
import wave

import numpy as np

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(7)


def envelope(n, attack, release_curve=3.0):
    """Fast attack, smooth exponential-ish fall."""
    t = np.linspace(0, 1, n)
    a = max(1, int(attack * RATE))
    env = np.ones(n)
    env[:a] = np.linspace(0, 1, a)
    env[a:] = (1 - np.linspace(0, 1, n - a)) ** release_curve
    return env


def voice(duration, f0, formants, bandwidth=120.0, harmonics=40, breath=0.0):
    """Additive source-filter voice.

    f0: array of the pitch over time. formants: list of arrays (one per formant)
    of formant centre frequencies over time. Each harmonic's loudness is how close
    it sits to the formants at that moment.
    """
    n = len(f0)
    phase = np.cumsum(2 * np.pi * f0 / RATE)
    out = np.zeros(n)
    for k in range(1, harmonics + 1):
        fk = k * f0
        gain = np.zeros(n)
        for i, fc in enumerate(formants):
            weight = 1.0 / (1 + i * 0.6)
            gain += weight / (1 + ((fk - fc) / bandwidth) ** 2)
        gain *= (fk < RATE / 2 - 500)  # stay below Nyquist
        out += gain * np.sin(k * phase) / k ** 0.3
    if breath:
        out += breath * rng.standard_normal(n) * np.abs(out).max()
    return out


def glide(points, n):
    """Piecewise-linear curve through (fraction, value) points."""
    xs, ys = zip(*points)
    return np.interp(np.linspace(0, 1, n), xs, ys)


def click():
    n = int(0.035 * RATE)
    t = np.arange(n) / RATE
    tone = np.sin(2 * np.pi * 2300 * t) * np.exp(-t * 180)
    noise = rng.standard_normal(n) * np.exp(-t * 600) * 0.5
    return (tone + noise) * envelope(n, 0.001, 1.5)


def kitten():
    n = int(0.16 * RATE)
    f0 = glide([(0, 820), (0.35, 1180), (0.7, 1050), (1, 880)], n)
    f0 *= 1 + 0.015 * np.sin(2 * np.pi * 18 * np.arange(n) / RATE)  # little quaver
    f1 = glide([(0, 1200), (0.4, 1500), (1, 1100)], n)
    f2 = glide([(0, 3400), (0.4, 2600), (1, 2200)], n)
    return voice(0.16, f0, [f1, f2], bandwidth=260, harmonics=12) * envelope(n, 0.012, 2.2)


def cat():
    n = int(0.24 * RATE)
    f0 = glide([(0, 470), (0.3, 690), (0.65, 600), (1, 420)], n)
    f0 *= 1 + 0.01 * np.sin(2 * np.pi * 11 * np.arange(n) / RATE)
    # "mi" -> "a" -> "ow": formants slide from ee to ah to oo.
    f1 = glide([(0, 420), (0.35, 850), (0.75, 700), (1, 480)], n)
    f2 = glide([(0, 2300), (0.35, 1500), (0.75, 1000), (1, 850)], n)
    f3 = glide([(0, 3200), (1, 2600)], n)
    sound = voice(0.24, f0, [f1, f2, f3], bandwidth=160, harmonics=24, breath=0.006)
    # The "m": soft nasal onset.
    onset = np.clip(np.linspace(0, 1, n) / 0.12, 0.25, 1.0)
    return sound * onset * envelope(n, 0.02, 1.8)


def duck():
    n = int(0.17 * RATE)
    f0 = glide([(0, 300), (0.2, 330), (1, 250)], n)
    f1 = glide([(0, 700), (0.5, 800), (1, 600)], n)
    f2 = glide([(0, 1700), (1, 1500)], n)
    nasal = np.full(n, 2600.0)
    sound = voice(0.17, f0, [f1, f2, nasal], bandwidth=220, harmonics=36, breath=0.05)
    # Raspy flutter that makes it a quack rather than a hum.
    t = np.arange(n) / RATE
    sound *= 0.65 + 0.35 * np.sign(np.sin(2 * np.pi * 70 * t))
    return sound * envelope(n, 0.006, 1.4)


def save(name, samples, peak=0.6):
    samples = samples / (np.abs(samples).max() + 1e-9) * peak
    pcm = (samples * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f"key_{name}.wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    print(path, len(pcm), "samples")


if __name__ == "__main__":
    save("click", click(), peak=0.5)
    save("kitten", kitten())
    save("cat", cat())
    save("duck", duck())
