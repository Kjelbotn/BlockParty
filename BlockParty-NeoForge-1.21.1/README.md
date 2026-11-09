# BlockParty 0.7.0-alpha

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
