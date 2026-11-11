# BlockParty 1.0.0 — First stable release

- Declared BlockParty 1.0.0 as the first stable, fully playable release.
- Added a playlist JSON manifest and eight pre-registered custom song slots. A new song now requires only an OGG file and one JSON entry—no per-song Java, sound-definition, duration-file or workflow edits.
- GitHub Actions now reads the playlist automatically, normalizes each enabled OGG, measures its duration, validates sound-event mappings and verifies all active tracks in the final JAR.
- Expanded the voting menu to support up to 27 songs, enough for the 17 current tracks plus all eight custom slots.
- Fixed the podium exit control so podium finishers only receive Redstone Dust after their five-second freeze ends; manual early exit now also clears the freeze state.

# BlockParty 0.19.17-alpha — Seven New Music Tracks

- Added StianNorway — Damer, Objekter.
- Added StianNorway — Vi må ikke si et ord.
- Added Luxus Leverpostei — Plastisk Kirurgi.
- Added Kaptein Sabeltann — Pinkys Sang.
- Added Luxus Leverpostei — §195.
- Added Luxus Leverpostei — Sur Mus.
- Added Oingo Boingo — Little Girls [PedoBear].
- Added all seven built-in sound events and measured track durations to the playlist manifest.
- Added every new OGG to CI audio normalization, full-decode/audio-signal validation, sound-event mapping checks and final JAR asset verification.
- Expanded the music voting menu capacity to 21 songs so the full playlist remains selectable.

# BlockParty 0.19.16-alpha — Beta 1.0 Candidate

- Music now loops automatically when a track ends before the 25-round match is complete.
- Rebuilt the scoreboard to the requested hierarchy, colors and mixed-color values: Dancers Left, Game Info, live Round/Round Speed and Your Stats.
- Added a bold dark-red Waiting... action-bar message between rounds, then the target display/countdown, followed by ✖ STOP! ✖ as the floor falls.
- Added three descending bass-drum cues at 3, 2 and 1 seconds.
- Added the custom arena-screen sequence support for the BareTruls track ID: INNI (1s), blank (1s), HODET (1s), blank (1s), DITT! (1s), blank (2s). The BareTruls OGG asset itself is not yet in the repository and must be bundled before that track can be enabled.

