# BlockParty 1.4.3 — Preserve custom arena decorations

- Prevented `/bp generate <id>` and `/bp xmas generate` from rebuilding an already registered arena, protecting custom lobby and arena decorations from accidental overwrites.
- Clarified that ordinary JAR updates do not require arena regeneration; `/bp demolish <id>` remains the explicit destructive operation and removes custom builds in that footprint.

# BlockParty 1.4.2 — Festive Xmas floor patterns

- Added six dedicated Xmas pixel-art floor patterns: Christmas tree, candy-cane stripes, five-point star, snowflake, presents, and wreath.
- Festive motifs use concrete colours and preserve the exact 40×40 playable floor; regular geometric patterns remain in rotation between festive rounds.

# BlockParty 1.4.1 — Xmas right-edge playability and stable lobby items

- Corrected the X/Z coordinate order when detecting the red/white border around the Xmas floor, preventing the 40×40 playfield from being shifted away from the final playable row beside the right wall.
- Removed recurring lobby-item name normalization from the once-per-second lobby check to avoid unnecessary inventory updates during countdown.

# BlockParty 1.4.0 — Exact Xmas floor boundary alignment

- Anchored the Xmas 40×40 playable area to the red/white candy-cane border outside the floor, preventing the left and bottom dead border from being included while preserving the final playable row beside the right wall.

# BlockParty 1.3.9 — Xmas spectator-edge tiles and stable lobby inventory

- Kept the full 40×40 Xmas floor playable, including the edge tiles beside the spectator houses; removed the schematic-generation trimming that could exclude those blocks.
- Prevented unnecessary full inventory synchronization during the once-per-second lobby item check, which caused lobby items to blink.

# BlockParty 1.3.8 — Music voting layout and exact Xmas floor bounds

- Reordered both lobby music voting boards so tracks 01–05 render in the left column and 06–10 in the right column. Moved the most-voted track panel to the far-left side, clear of the song list.
- Removed the extra striped edge row from the Xmas dance-floor schematic placement and stopped expanding the configured floor bounds. The playable dance floor is exactly 40×40 blocks (1,600 tiles); every tile in those bounds is captured and participates in normal round recolouring and safe-tile checks.

# BlockParty 1.3.7 — Xmas floor edge and music board fixes

- The extra concrete row on the +Z edge of the Xmas dance floor is now included in the live floor bounds even when corners come from legacy/manual configuration. It is captured with the rest of the floor and participates in normal colour patterns, target checks and safe-tile selection.
- Corrected the horizontal placement of the Xmas side-facing lobby music-voting layout so the playlist, song columns and most-voted panel read in the intended left-to-right order. Classic layout remains unchanged.

# BlockParty 1.3.6 — Correct Xmas lobby music playlist order

- Fixed the Xmas side-facing lobby screen so song 01 appears at the upper left and the playlist continues down the left column before continuing in the right column. Classic keeps its own corrected screen orientation.

# BlockParty 1.3.5 — Correct lobby music playlist order

- Fixed the main-lobby music voting display so song 01 starts at the upper-left position, with the list continuing down the left column and then down the right column. Applied to both arena maps.

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

