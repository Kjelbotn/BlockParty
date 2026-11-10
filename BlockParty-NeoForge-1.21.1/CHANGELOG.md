# Changelog\n\n## 0.9.0-alpha\n- Protected the Join star and Music Vote book from being dropped.\n- Delayed music playback until the first round starts.\n- Stops music during the falling-floor phase and restarts it when the next round begins (restart from track beginning; exact seek/resume is not supported by vanilla streamed sound playback).\n- Added Bangerfabrikken's 3:36 track as the first playlist entry.\n\n## 0.8.2-alpha
- Fixed two Java compilation errors in the spiral and radial floor-pattern calculations reported by GitHub Actions.

# Changelog

## 0.8.1-alpha
- Reworked floor generation into 12 recognizable geometric patterns: concentric squares, spiral, checkerboard, diagonal bands, woven lanes, diamonds, radial wheel, nested blocks, cross, waves, pinwheel and maze.
- `/bp leave` now locates an active return session as well as normal arena membership, preventing false "not in arena" responses.
- Corrected the built-in music event IDs (`music_neon`, `music_arcade`, `music_rush`).
- Music is now sent through the Records/Jukebox sound category at full event volume. Check the Records/Jukebox slider in Music & Sounds.
- Kept 20 rounds and the adjusted late-round timings from 0.8.0-alpha.

# Changelog

## 0.8.0-alpha
- Added exact return-location restoration for `/bp leave` and after match completion.
- Added administrator command `/bp demolish <id>` to remove a generated arena footprint and its BlockParty displays.
- Added an early-development disclaimer when players use `/bp`.
- Sidebar is shown only once the match begins on the dance floor.
- Extended matches to 20 rounds and adjusted the late-round timers so players have time to move.
- Expanded floor pattern generation with additional irregular and mixed layouts.
- Kept stadium redstone lamps permanently lit.
- Changed music playback to send the selected registered sound directly to each participating player.

**Demolish warning:** `/bp demolish <id>` clears the generated footprint to air. Terrain that was overwritten when the arena was generated cannot be restored automatically.

# Changelog

## 0.7.0-alpha
- Simplified match start to a 10-second countdown and removed the DJ intro phase.
- Added majority-vote threshold and random tie resolution.
- Added `/bp` quick lobby join and inventory snapshot at lobby entry.
- Expanded floor mosaic layouts.
- Documented resource-pack replacement for all seven sound effects.

## 0.7.1-alpha
- Removed the polished-blackstone foundation beneath the arena dance floor so the pit remains open.
- Turned arena redstone lamps on by default and kept music-light positions illuminated.
- Added a short target-tile contact grace window to prevent tick-order eliminations when the correct tile disappears at the round boundary.
- Corrected the built-in music event IDs used when stopping/restarting tracks (`neon_beat`, `pixel_rush`, `night_drive`).
- Increased built-in music event gain and added server-side track playback diagnostics.
