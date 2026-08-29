# Sprint 3.3 Player Baseline

Recorded before Sprint 3.3 remediation. These are observed runtime failures from the supplied Pixel 7 / Android 13 Appetize run; no UI or audio item is marked PASS from unit-test evidence.

| Area | Observed baseline | Verification state |
|---|---|---|
| Section state | Selecting Section 2, then changing hand or Wait/Rhythm, reloads the whole song | NEEDS_EMULATOR |
| Preparation landscape | Content is clipped; track list and primary action cannot be reached | NEEDS_EMULATOR |
| Preparation portrait | Primary action renders as a green bar without readable text | NEEDS_EMULATOR |
| Toolbar | Remains visible after more than 10 seconds in Rhythm mode | NEEDS_EMULATOR |
| Keyboard range | Defaults to approximately C3-C5 instead of all 88 keys | NEEDS_EMULATOR |
| Falling-note scale | Notes are oversized and obscure the playfield | NEEDS_EMULATOR |
| Long notes | Sustain duration is drawn as an opaque, unreasonable column | NEEDS_EMULATOR |
| Hand colors | Left/right hands are not preserved; almost all notes are cyan | NEEDS_EMULATOR |
| Demo overlay | Large listening banner obscures the middle of the playfield | NEEDS_EMULATOR |
| Section clock | Section 2 shows absolute `00:06 / 00:12`, not relative `00:00 / 00:06` | NEEDS_EMULATOR |
| Preparation layout | Section 4 chip wraps to an undesirable second line | NEEDS_EMULATOR |
| Piano audio | Runtime harmonic/sine synthesis sounds like a whistle, not a piano | NEEDS_REAL_DEVICE |
| Acoustic piano quality | No evidence that the current sound is an acoustic/high-quality digital piano | NEEDS_REAL_PIANO |

Screenshot and golden checks remain `NOT_RUN` until their images are rendered and inspected. Appetize checks remain `NEEDS_EMULATOR`; latency, polyphony, sustain, and perceived timbre remain `NEEDS_REAL_DEVICE` / `NEEDS_REAL_PIANO` as applicable.
