# BlockParty 0.9.3-alpha

NeoForge mod for Minecraft 1.21.1 / Java 21.

## Changes in 0.7.0-alpha
- `/bp` teleports the player into the first configured BlockParty lobby; `/bp join <id>` remains available.
- Player inventory, armor, offhand, selected slot and original game mode are captured when joining, before lobby items are granted. The minigame uses its own inventory and restores the saved state when the player leaves or the match ends.
- The match uses a simple 10-second countdown. `/bp forcestart <id>` is still the admin solo-test command.
- With 2+ players, the automatic countdown begins when at least half of the joined players have voted for a track (rounded up). Ties are resolved randomly among the tied tracks.
- The official music vote contains exactly two Bangerfabrikken tracks: **BlockParty** and **Odin er en alkis - Moon Edition**. Previous development/test tracks have been removed.
- Floor patterns now use varied 4x4 mosaics, with all available palette colours represented in each tile group when the palette has 16 colours.
- Sound effects can be replaced with a resource pack; see `BlockParty-Sound-Pack-Template-1.21.1.zip`.

## Commands
- `/bp` — join the first configured lobby.
- `/bp join <id>` — join a named arena.
- `/bp leave` — leave the arena.
- `/bp forcestart <id>` — admin solo test, exactly one joined player.
- `/bp start <id>` — admin start, at least two joined players.
- `/bp generate <id> [size]` — generate an arena (24–50; default 40).
- `/bp help` — command help.

## Replace sound effects with a resource pack
Install a resource pack containing these paths under `assets/blockparty/sounds/effects/`:
`round_start.ogg`, `round_end.ogg`, `color_reveal.ogg`, `elimination.ogg`, `beacon_spawn.ogg`, `powerup_pickup.ogg`, `victory.ogg`.
Keep the filenames exactly as shown. The mod already registers the corresponding sound events, so a resource pack does not need to replace `sounds.json`. The provided template contains the current sounds as examples; replace those OGG files with your own.

## Music
The three built-in tracks are retained. Players should have the Music sound category enabled in Minecraft's Audio settings. The tracks are streamed sound events. The server sends playback to each joined player and loops using the configured duration in `blockparty-music.properties`.

## Build and testing
Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21. Build with the repository's GitHub Actions workflow. This package has been statically checked, but a full Gradle build was not run in this environment.


## 0.7.1-alpha notes
- The pit under the dance floor is intentionally left open; there is no polished-blackstone foundation directly below it.
- Arena redstone lamps are generated lit.
- Music event IDs for the three built-in songs are mapped to their registered sound events. If music is still inaudible, check the client's **Music & Sounds → Music** slider and the server log for `Starting BlockParty track`.


## BlockParty 0.8.0-alpha

- `/bp` joins the configured lobby and displays the early-access disclaimer.
- `/bp leave` returns you to the exact location and orientation from before joining.
- `/bp demolish <id>` (operator permission required) removes the generated stadium footprint and its tagged displays. This clears the footprint to air; overwritten terrain is not backed up.
- Matches now have up to 25 rounds. Round 1 starts at 10 seconds, with the timer gradually decreasing to 3.5 seconds in round 25.
- The match sidebar appears when players enter the dance floor, not while waiting in the lobby.

If music is still silent, first test with the BlockParty mod installed on both server and client, and temporarily disable other resource packs to rule out an override of `assets/blockparty/sounds.json` or the music OGG files. The server-side `Starting BlockParty track` log confirms only that the server sent the sound request, not that the client successfully loaded the sound.


## 0.8.1-alpha troubleshooting and changes
- Floor patterns are now designed as recognizable geometric motifs rather than a repeating 4x4 tile formula.
- `/bp leave` checks both arena membership and the stored return-location session.
- Music event IDs for the three bundled tracks now match the registered sound event names exactly.
- Music uses Minecraft's **Records/Jukebox** volume category. In Options → Music & Sounds, raise **Jukebox/Note Blocks** (label may vary by client language). The server sends the registered sound event; the client must have BlockParty installed as a mod, not only as a resource pack.


## 0.9.0-alpha
- Prevented dropping the BlockParty Join star and Music Vote book.
- Music now starts when the first round begins, not during the pre-match countdown.
- Stops the selected track while the floor is removed, then starts it again when the next round's floor is ready.
- Added **BlockParty — Bangerfabrikken** as the first music-voting option, bundled as `sounds/music/bangerfabrikken.ogg` (3:36).

**Music pause limitation in 0.9.0:** this release stops and restarts the track from its beginning after the floor-fall phase. Version 0.9.1 replaces that behavior with client-controlled playback and per-channel pause/resume.


## 0.9.2-alpha — client-side music playback
- Added a client music controller. The server now sends explicit PLAY, PAUSE, RESUME and STOP commands instead of asking each client to create an unmanaged vanilla sound from a sound packet.
- The selected streamed OGG is created as a local Minecraft `SoundInstance` in the Records/Jukebox category.
- The client retains the active sound instance and pauses/unpauses its own OpenAL channel. This preserves the playback cursor rather than stopping and replaying the song.
- The server pauses the music when the floor is removed and resumes it when the next round's floor is ready. The server-side replay timer is frozen during the pause.
- Leaving an arena sends a stop command only to the departing player; the other players continue listening.
- The music still starts only when the first round begins, not during the lobby or countdown.

**Client requirement:** BlockParty must be installed on both the server and every participating client. The client must have the Records/Jukebox sound category enabled.

**Implementation note:** the client controller accesses Minecraft 1.21.1's active sound-channel map through NeoForge's obfuscation-aware reflection helper and pauses only the BlockParty music channel. If a future Minecraft update changes the sound engine internals, this small adapter may need updating.


### 0.9.2-alpha CI follow-up
The follow-up source archive fixes the initial GitHub Actions compile errors:
- No longer imports the unavailable `net.neoforged.fml.DistExecutor`.
- Uses the 1.21.1 `ItemTossEvent` API correctly: `getEntity()` for the dropped item entity and `getPlayer()` for the player inventory.
- Cancels the protected toss, restores the stack to the tossing player's inventory, and discards the item entity to prevent duplication.
- Removes the deprecated explicit event-bus selector while retaining client-only event subscription.


## 0.9.3-alpha — bankable LEAP BOOST and 25-round pacing

- LEAP BOOST feathers now persist across round transitions until the player right-clicks to use them. Unused feathers are cleared only when the match ends.
- A player carrying a LEAP BOOST feather can still scroll the hotbar; otherwise the target block remains locked in the centre slot.
- Increased the match limit from 20 to 25 rounds. Round 1 starts at 10 seconds, and the time decreases gradually to 3.5 seconds by round 25.
- The feather remains protected from dropping, including when saved for a later round.


## Admin: reset player statistics
Use `/bp statsreset <player>` to reset a player's persistent BlockParty profile (points, streaks, personal best, wins and MVP awards). Requires permission level 2. The target may be online or have a previously saved profile.


### Multiple arena styles (0.12.0-alpha)
- Classic: `/bp generate classic 40`
- Festival: `/bp generatefestival festival 40`
- List arenas: `/bp arenas`
- Join a chosen arena lobby: `/bp join classic` or `/bp join festival`
Generate each arena in a separate, open area. The Festival style has a separate lobby and play area with no physical bridge between them.
