# Changelog

## 0.9.1-alpha — CI compile fixes
- Removed the unavailable DistExecutor reference from the clientbound music payload handler.
- Updated protected-item toss handling to use ItemTossEvent#getEntity() and getPlayer(); restore the protected stack to the player's inventory and discard the dropped entity.
- Removed the deprecated explicit EventBusSubscriber bus argument; client tick/logout handlers remain on the game event bus.


## 0.9.1-alpha
- Added a client-side BlockParty music controller with explicit PLAY, PAUSE, RESUME and STOP network commands.
- Pauses the active OpenAL channel for the selected music sound, preserving its playback cursor through the floor-fall phase.
- Resumes the same client sound instance when the next round begins; no restart from the beginning.
- Keeps the Records/Jukebox category and server-authoritative start/stop timing.
- Stops music for a player who leaves without interrupting the other arena participants.

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


## 0.9.2-alpha – round pacing, music pause and hotbar controls
- Hardened client music pause/resume channel lookup so it no longer depends on one obfuscated SoundEngine field name.
- Target-color concrete is protected from dropping and is placed in the middle hotbar slot.
- Hotbar selection is locked to the target block during a match; a LEAP BOOST feather temporarily unlocks scrolling and can be right-clicked for a forward/upward boost.
- LEAP feathers expire at the end of the round.
- Slightly increased reaction time in later rounds while keeping the early-game pace unchanged.
- Unified target-contact tick tracking to use the same server tick counter throughout the round.


## 0.9.3-alpha — bankable LEAP BOOST and 25-round pacing
- LEAP BOOST feathers now persist across rounds until used, instead of being removed at the end of each round.
- Unused feathers are cleared only when the match ends; the feather remains protected from dropping.
- Increased the match limit to 25 rounds. The round timer starts at 10 seconds and gradually decreases to 3.5 seconds by round 25.
