## 0.10.9-alpha
- Prevented natural and other mob spawns inside the configured BlockParty arena, spectator walkway, pit and lobby; existing mobs in those footprints are periodically removed.
- Disabled hunger and exhaustion for joined BlockParty players while preserving and restoring their original food level, saturation and exhaustion when they leave.
- Locked the world time to 13500 throughout countdown and match phases, reasserting the fixed time each tick, then restored the original time and daylight-cycle rule when the match ends.
- Hardened out-of-bounds elimination: active players crossing three blocks below the arena floor are eliminated and moved to the spectator walkway; fall/void death is intercepted at that threshold.
- Corrected the completed-match announcement to 25 rounds.

## 0.10.8-alpha
- Fixed out-of-bounds elimination, late joining, Personal Best display and lobby leaderboard refresh.

## 0.10.7-alpha
- Players who leave the playable arena bounds during an active match are now immediately eliminated and moved to the spectator walkway instead of falling into the world void and dying.
- The boundary check applies only to active players; existing spectators are unaffected.

## 0.10.6-alpha
- Corrected Win Streak and Best Win Streak to count consecutive completed match victories only; surviving individual rounds no longer affects either value.
- Added a one-time stats migration that clears legacy streak values from older versions, because those values may have counted rounds rather than match wins. Total wins, points, MVPs and personal bests are preserved.
- Redesigned the lobby music-voting display: removed BPM, made the current leader prominent, highlighted the leading track, and displayed vote counts more clearly.
- Kept the official playlist limited to **BlockParty** and **Odin er en alkis - Moon Edition**, both by Bangerfabrikken.

## 0.10.5-alpha
- Removed all development/test songs from the music voting playlist and packaged resources.
- Added **Odin er en alkis - Moon Edition** by Bangerfabrikken as the second official voting option.
- The music vote now contains only **BlockParty** and **Odin er en alkis - Moon Edition**.

# Changelog

## 0.10.4-alpha
- After a match ends, all participants (winner and eliminated spectators) are teleported back to the BlockParty lobby instead of the spectator walkway. Players remain in the BlockParty session until they use `/bp leave` or the redstone-dust leave item.

## 0.10.3-alpha
- Replaced round streak display with consecutive match wins (Win Streak) and best consecutive match wins (Best Win Streak).

## 0.10.2-alpha
- Fixed NeoForge/Minecraft 1.21.1 API compatibility for weather parameters and scoreboard entry cleanup.


## 0.10.1-alpha
- Match-sidebar now shows session points earned in the current game, current streak, best streak and power-ups. Removed rank and next-rank progress from the in-game sidebar; persistent lifetime progression remains available in the lobby/profile/leaderboard.
- Added admin command `/bp statsreset <player>` (permission level 2), which resets points, current/best streak, personal best, wins and MVP awards for an online or previously recorded player.


- Fixed item tossing rules so the Nether Star, music book, and redstone leave item are protected only while inside an active BlockParty lobby.
- Outside a BlockParty lobby or active match, these items behave like normal survival inventory items and can be dropped normally.
- Kept target-color blocks and LEAP BOOST protected during active matches.

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


## 0.9.5-alpha
- Added a redstone-dust Leave Lobby control fixed to hotbar slot 9. Right-clicking it performs the same leave flow as `/bp leave`.
- The leave control is protected against dropping and is removed when leaving the arena.
- Leaving now returns only to the saved pre-join survival location; missing return data produces a warning instead of teleporting to the lobby.

## 0.11.0-alpha
- Opened the lobby-to-arena entrance by removing the central inner glass rail section.
- Added solid matching floor blocks beside the entrance ramp to close side gaps.
- Added server-side command restrictions for players in a BlockParty session/lobby; only /bp commands remain available, so external teleport commands and EasyHomes shortcuts cannot bypass /bp leave.

## 0.11.1-alpha
- Music voting book is no longer given automatically to lobby visitors.
- Players must explicitly click the Nether Star to unlock and receive the voting book.
- Music voting menu and vote commands require an unlocked BlockParty session and physical presence inside the BlockParty area.
- Nether Star, music voting book and leave Redstone Dust are removed when a session player leaves the BlockParty area.
- Re-entering the lobby allows the lobby controls to be refreshed; the player must click the Nether Star again to unlock music voting.

## 0.11.3-alpha
- Disconnecting during a BlockParty session restores the original Survival inventory, food state, Survival mode and saved pre-join location before player data is saved.
- Disabled direct player-versus-player damage when either player is physically inside a BlockParty lobby, arena or spectator area.

## 0.11.4-alpha
- Improved the BlockParty exit cleanup: stop any active item use, restore Survival mode and saved hunger/inventory, explicitly refresh player abilities, inventory menus and selected hotbar slot.
- This targets the issue where food and other right-click interactions could remain unavailable immediately after leaving BlockParty.

## 0.11.5-alpha
- Music-vote threshold now requires at least two-thirds of joined players (rounded up): 2 players require 2 votes; 3 players require 2 votes.
- Equal vote counts continue to select a random song from the enabled playlist.
- Corrected the PvP damage event type to the NeoForge incoming-damage event after the previous LivingAttackEvent import failed compilation.

## 0.11.6-alpha
- /bp opens a visitor-only lobby session, gives Nether Star and redstone; Nether Star explicitly registers for the next match and unlocks music voting.
- Visitors are excluded from participant counts and vote thresholds.
- Replaced unwalkable full-block entrance ramp with a real stairway and protected landing.
- Closed both side gaps between lobby and arena.
- Removed outer tinted-glass rail while preserving the inner spectator safety glass.
- Existing arenas receive an in-place geometry repair on first lobby visit; no arena regeneration required.
- Music assets are unchanged and excluded from the update package.

## 0.11.7-alpha
- Restored the tinted-glass perimeter around the entire spectator walkway; only the centered entrance is opened.
- Corrected the spectator stairs to face toward the arena and aligned the landing with the actual outer edge of the spectator ring.
- Extended the bridge side walls and foundations to close the exposed gaps along both sides of the lobby-to-arena passage.

## 0.11.8-alpha
- Removed the visible support pillars beneath the spectator ring.
- Added a landing grace during the floor-fall phase: players initially marked for elimination are saved if they land on the announced target colour before falling into the pit.
- Added a final target-contact sample at round timeout to reduce tick-order edge cases.
