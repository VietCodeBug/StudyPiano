# Third-Party Notices & Audio Engine License Documentation

## 1. Acoustic Piano Multi-Sample Synthesis Engine
* **Component**: `SoundPoolPianoEngine`
* **License**: Project Original / Apache License 2.0 compatible
* **Architecture**: Physical hammer strike impulse & multi-harmonic acoustic decay model with per-harmonic exponential damping, dynamically rendered to 16-bit PCM WAV caches and managed by Android `SoundPool`.

## 2. FreePats Acoustic Piano Sound Samples (Reference & Ingestion)
* **Component**: FreePats Acoustic Grand Piano Samples
* **Source**: `https://freepats.zenvoid.org/Piano/acoustic-grand-piano.html`
* **License**: Creative Commons Attribution 3.0 / CC0 Public Domain Dedication
* **Attribution**: FreePats community project.
* **Usage**: Used for offline fallback sample anchors (A0 through C8) when physical soundfonts or external `.ogg` sample bundles are imported.

## 3. OpenSheetMusicDisplay (OSMD)
* **Component**: OpenSheetMusicDisplay JavaScript Library
* **Source**: `https://github.com/opensheetmusicdisplay/opensheetmusicdisplay`
* **License**: BSD 3-Clause License
* **Copyright**: (c) 2016-2024 PhonicScore UG & OSMD Contributors
* **Usage**: Offline MusicXML sheet music rendering inside Android local WebView sandbox (zero external internet network connections permitted).
