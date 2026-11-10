# BlockParty 0.9.0-alpha

NeoForge mod for Minecraft 1.21.1 / Java 21.

## Changes in 0.7.0-alpha
- `/bp` teleports the player into the first configured BlockParty lobby; `/bp join <id>` remains available.
- Player inventory, armor, offhand, selected slot and original game mode are captured when joining, before lobby items are granted. The minigame uses its own inventory and restores the saved state when the player leaves or the match ends.
- The match uses a simple 10-second countdown. `/bp forcestart <id>` is still the admin solo-test command.
- With 2+ players, the automatic countdown begins when at least half of the joined players have voted for a track (rounded up). Ties are resolved randomly among the tied tracks.
- Existing built-in tracks remain: Neon Beat, Pixel Rush and Night Drive. Music playback uses the registered BlockParty music events and repeats at the configured track duration.
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
- Matches now have 20 rounds. Late rounds retain enough time for players to move.
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

**Music pause limitation:** this release stops and restarts the track from its beginning after the floor-fall phase. Minecraft's ordinary server sound packets do not expose a seek/pause position for streamed OGG playback, so exact continuation at the previous timestamp requires a custom client-side audio implementation and is not claimed here.
