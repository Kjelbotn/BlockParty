# BlockParty — NeoForge 1.21.1

**Version 0.4.0-alpha** · Java 21 · Singleplayer-first · NeoForge 21.1.x

BlockParty is a singleplayer-friendly Minecraft minigame inspired by classic Block Party servers. Version 0.4.0 adds the separate indoor lobby, music voting book, elevated spectator promenade, persistent player progression, and match-end presentation.

## What is included

- A generated indoor stadium with a large concrete dance floor (80×80 by default; 40–96 supported).
- A continuous spectator promenade four blocks above the dance floor. It has inner and outer glass rails and no spectator stands/tribunes.
- Eliminated players are moved to the promenade in Adventure Mode. They remain visible and can walk around the arena, but cannot enter the live floor or collect power-ups.
- A separate indoor lobby with a large BlockParty title screen, leaderboard, and music-voting instructions.
- Text Display entities for in-world text, rather than pixel/block lettering. The arena target is only the colour name, with no `FIND` prefix.
- 15 rounds with the specified 10.0s → 0.5s timing progression.
- A three-second transition: the remaining target-colour concrete stays visible while players finish falling; the next floor pattern replaces it in place. There is no black/empty intermission floor.
- Five spaced beacon power-ups are spawned at the start of each round. They float on the block directly above five different concrete floor tiles. Effects last only until that round ends.
- A night-time match with daylight cycle temporarily paused, then restored when the match ends.
- A colourful sidebar scoreboard, elimination lightning, winner title, MVP broadcast, win streak, personal best, permanent points and ranks.
- Music voting through a Book item in the inventory. Right-click the book to open clickable chat choices.

## Build and install

1. Build with Java 21 and Gradle 8.10.2:

   ```bash
   gradle build --no-daemon
   ```

2. The JAR is written to `build/libs/blockparty-0.4.0-alpha.jar`.
3. Install the JAR in the `mods` folder of the NeoForge 1.21.1 profile. For singleplayer, that is the Modrinth instance's `mods` folder.
4. Restart Minecraft after replacing older BlockParty JARs. Do not keep two BlockParty versions installed together.

The GitHub Actions workflow in `.github/workflows/build.yml` runs the same build and uploads the JAR as an artifact.

## Generate and play

1. Back up your world before generating a stadium.
2. Go to a large, empty area. The generator clears and rebuilds the stadium/lobby footprint around the position where you stand. Do not run it next to buildings you want to keep.
3. Enable cheats in singleplayer if needed: Esc → Open to LAN → Allow Cheats: ON → Start LAN World.
4. Stand at the centre of the intended dance-floor area and run:

   ```mcfunction
   /bp generate main
   ```

   This creates an 80×80 concrete floor. Choose a different size from 40 to 96 if you prefer:

   ```mcfunction
   /bp generate main 96
   ```

5. Join the lobby:

   ```mcfunction
   /bp join main
   ```

   The 60-second lobby countdown starts automatically. A Music Voting Book is placed in your inventory. Right-click it to vote. You can change your vote until the match starts.

6. To start immediately instead of waiting:

   ```mcfunction
   /bp start main
   ```

## Commands

| Command | Description |
|---|---|
| `/bp help` | Show command help |
| `/bp generate <id> [size]` | Generate a stadium and arena, size 40–96 (default 80) |
| `/bp join <id>` | Join the lobby |
| `/bp leave` | Leave the arena |
| `/bp start <id>` | Start immediately (admin/cheats) |
| `/bp stop <id>` | Stop the match (admin/cheats) |
| `/bp status <id>` | Show arena status |
| `/bp list` | List saved arenas |
| `/bp vote <songId>` | Cast/change a music vote; normally done by clicking a song in chat |
| `/bp leaderboard` | Show the top 10 players |
| `/bp rank` or `/bp profile` | Show your rank, points, streaks and personal best |
| `/bp music reload` | Reload the world's music playlist (admin/cheats) |

## Music voting and custom songs

The mod creates a world-local file named `blockparty-music.properties` in the active world's save folder the first time the world starts with BlockParty. In a normal Modrinth instance, this is under:

`<instance>/saves/<world-name>/blockparty-music.properties`

The file contains a comma-separated `tracks` list and display names. Example:

```properties
tracks=neon_beat,pixel_rush,night_drive,custom_1,custom_2,custom_3,custom_4,custom_5,custom_6,custom_7,custom_8
name.custom_1=My Song One
name.custom_2=My Song Two
```

To add your own audio:

1. Use the included `BlockParty-Custom-Music-Pack` template as a Minecraft 1.21.1 resource pack.
2. Put your own OGG files in `assets/blockparty/sounds/music/` with these fixed names: `custom_1.ogg` through `custom_8.ogg`.
3. Enable the resource pack in Options → Resource Packs. In singleplayer it only needs to be enabled on your local client; in multiplayer every client needs the same pack.
4. Edit `blockparty-music.properties` in the world save folder to set song names and choose which IDs appear in voting.
5. Run `/bp music reload` to reload the playlist. If you changed OGG files, reload resource packs (F3+T) or restart Minecraft so the client reloads the audio assets.

The music stops during the three-second transition and the selected winner track is played again when the next round begins. **This alpha restarts the selected track from its beginning for each round; it does not seek back to the exact audio timestamp.** Exact pause/resume at the same sample position requires a dedicated client-side audio player and is left for a later release.

## Points, streaks and ranks

- Players earn one permanent point for each round they survive.
- Points are saved per player UUID in `blockparty-player-stats.properties` in the world save folder.
- Win streak counts consecutive rounds survived; it resets when a player is eliminated.
- Personal Best is the highest round reached in a single match (maximum 15).
- A match-end MVP is selected from round points, with collected power-ups as a tiebreaker.

| Points | Rank |
|---:|---|
| 0 | Rookie |
| 100 | Bronze Dancer |
| 250 | Silver Dancer |
| 500 | Gold Dancer |
| 1,000 | Platinum Dancer |
| 2,500 | Diamond Dancer |
| 5,000 | Superstar |
| 10,000 | BlockParty Legend |

## Notes and limitations

- The source package is intended for Minecraft 1.21.1 and NeoForge 21.1.x, using Java 21.
- The music voting book is a normal Book with a custom name; if the player's inventory is full, free a slot and rejoin the arena.
- This release is a source build candidate. GitHub Actions must complete successfully before its JAR should be treated as build-verified. Then test arena generation and the match in a backup world.
