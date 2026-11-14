# BlockParty 1.3.4 — Xmas podium removal and faster Memory Mode

- Removed the winner podium presentation from the Xmas variant only. Classic keeps its existing podium, winner teleport, freeze and fireworks.
- Memory Mode's gray-floor find phase starts at 8 seconds and decreases by 0.5 seconds every two rounds, down to the existing 2-second minimum.
- The Memory Mode memorization/reveal duration is unchanged.

# BlockParty 1.1.0 — Memory Mode, Daily Challenges and polish

- Added Memory Mode as an alternative to Classic. The floor colours are shown for five seconds, then hidden as gray concrete; players must remember where the announced target colour was. The server retains the original floor pattern for fair validation.
- Added a lobby Game Mode menu. Registered players' preferences are counted and the majority selects the next match; ties default to Classic.
- Added three daily challenges selected from a rotating pool. Individual progress and completion checkmarks are saved per player, and completed challenges grant bonus points.
- Added colourful firework-like bursts around podium finishers for the five-second podium presentation.
- Restored BareTruls — Inni Hodet Ditt to the music vote list and registered its existing OGG asset.
- Fixed the vote menu rendering of the literal song title §95 so the section sign is not interpreted as a Minecraft colour code.

# BlockParty 1.0.3 — Countdown pitch adjustment

- Shifted the between-round 3–2–1 Note Block countdown down one pitch step: 3 seconds now uses the former 2-second tone, 2 seconds uses the former 1-second tone, and 1 second adds a new darker tone.

# BlockParty 1.0.2 — Chat and Color Scanner fixes

- Removed the [MUSIKK] prefix and bold styling from music vote announcements; messages now use normal-weight text.
- Fixed Color Scanner using one shared arena timer and broadcast particles. Each scanner now has its own player timer, and target-color particles are sent only to the player who collected that power-up.

# BlockParty 1.0.1 — Match flow and audio polish

- Reduced the between-round floor restoration pause from two seconds to one second; music and the next target now return sooner.
- Replaced the low bass-drum countdown cue with three clearer, descending Note Block Pling notes at 3, 2 and 1 seconds before the floor falls.
- Announced new music votes and changed votes in the arena chat, showing the player's name and selected song.

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

