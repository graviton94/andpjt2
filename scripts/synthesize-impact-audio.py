"""Build the game's original, short PCM impact samples."""
from math import exp, pi, sin, tanh
from pathlib import Path
from random import Random
from struct import pack
from wave import open as wave_open


RATE = 24_000
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
# Length, low end (Hz), transient (Hz), noise, ring (Hz).
SOUNDS = {
    "release": (.11, 110, 1600, .58, 550),
    "merge": (.15, 125, 1900, .24, 660),
    "burst": (.26, 75, 1100, .46, 392),
    "overflow": (.29, 70, 1250, .38, 523),
    "collapse": (.57, 53, 2100, .44, 262),
    "tap": (.08, 180, 2400, .14, 880),
}


def synthesize(name, length, bass, transient, grit, note):
    rng = Random(name)
    noise_tail = 0.0
    data = bytearray()
    for i in range(round(length * RATE)):
        t = i / RATE
        noise_tail = .76 * noise_tail + .24 * rng.uniform(-1, 1)
        sub = sin(2 * pi * (bass * t + 5.1 * (1 - exp(-32 * t)))) * exp(-t * 19)
        click = sin(2 * pi * (transient * t / (1 + t * 43))) * exp(-t * 88)
        debris = (rng.uniform(-.5, .5) + noise_tail) * exp(-t * 21)
        chime = (sin(2 * pi * note * t) + .44 * sin(2 * pi * note * 1.504 * t)) * exp(-t * 10)
        second = max(0, t - .076)
        rebound = sin(2 * pi * note * 2 * second) * exp(-second * 11) * min(1, second * 190) if name == "collapse" else 0
        envelope = min(1, t * 900, (length - t) * 44)
        sample = tanh(.66 * sub + .36 * click + grit * debris + .27 * chime + .37 * rebound) * envelope
        data.extend(pack("<h", round(29_000 * sample)))
    with wave_open(str(OUTPUT / f"impact_{name}.wav"), "wb") as audio:
        audio.setparams((1, 2, RATE, 0, "NONE", "PCM"))
        audio.writeframes(data)


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for event, parameters in SOUNDS.items():
        synthesize(event, *parameters)
