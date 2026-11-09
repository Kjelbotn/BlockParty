# BlockParty 0.6.1-alpha

NeoForge mod for Minecraft 1.21.1 / Java 21. Classic BlockParty mode is the only mode in this release. Hardcore Mode is deferred.

## Match rules
- Automatic lobby countdown begins when at least 2 players are joined.
- Maximum 10 players per match.
- Admin solo test: `/bp forcestart <id>` while exactly one player is joined (permission level 2).
- Admin manual start: `/bp start <id>` requires at least 2 joined players.
- Join: right-click the Join Game Nether Star in the lobby, or `/bp join <id>`.
- 15 rounds: 10, 9, 8, 7, 6, 5, 4.5, 4, 3.5, 3, 2.5, 2, 1.5, 1 and 0.5 seconds.
- The actionbar shows target colour, live remaining seconds and a segmented countdown bar.
- The arena's purple/magenta glass and concrete trim changes to match each round's target colour.
- A large target-colour name is displayed on the black arena panel. The leaderboard remains in the lobby and is hidden during active rounds.

## Power-ups
- Speed Upgrade grants a stronger movement-speed boost for the current round.
- Super Jump grants a high jump boost for the current round.
- Night Vision grants temporary night vision.
- Leap gives an immediate upward impulse.
- Color Teleporter moves the player to an unoccupied, safe floor tile in the announced target colour.
- Slowness is a negative power-up: collecting its beacon applies a stronger temporary movement penalty.
- Power-ups are intentionally uncommon: 35% chance per eligible round, with at least one complete round between successful waves. Up to five beacons can spawn, spaced farther apart.

## DJ Mode and music
- The lobby has a dedicated DJ screen showing the featured track, artist, BPM and vote counts.
- Players vote using the Music Vote book; votes can be changed until the match starts.
- The winner of the vote is played during a six-second DJ intro before the first round.
- The playlist includes three built-in tracks and eight replaceable custom slots. The custom slots stay hidden until you give them a title or artist in `blockparty-music.properties`. Custom OGG files can be supplied through a resource pack; set track names, artists, BPM and duration in the same file.
- Redstone-lamp fixtures pulse to the track's configured BPM and note/end-rod particles appear around the DJ booth and arena.
- Music continues through the rounds and restarts from the beginning after the configured track duration. Exact pause/resume at the same audio timestamp is not supported in this server-side alpha.

## Arena generation
`/bp generate <id> [size]` builds an open-air stadium, raised spectator walkway and open-air lobby. Floor size is 24-50; default 40. The generator clears the area around the build site, so use an empty area and back up your world before regenerating. Invisible level-15 light sources and denser visible fixtures keep the arena readable. The lobby includes a DJ booth, screen, neon dance floor, lounge seating and brighter lighting. Existing saved arenas receive a one-time, non-destructive visual upgrade when a player first visits the lobby after updating.

## Inventory safety
The mod snapshots the full main inventory, armor, offhand and selected hotbar slot before a match. It clears the inventory for the minigame and restores the snapshot when a player leaves, is eliminated from the match, or the match ends. Normal logout is handled. A sudden server/process crash is not yet covered by a disk-persisted inventory journal, so back up the world before testing.

## Useful commands
- `/bp help`
- `/bp join <id>`
- `/bp forcestart <id>` (admin; solo test with exactly one player)
- `/bp start <id>` (admin; minimum 2 players)
- `/bp stop <id>` (admin)
- `/bp status <id>`
- `/bp leaderboard`
- `/bp rank`
- `/bp music reload` (admin; reload playlist configuration)

## World time
During an active match, the world is set to daytime with the daylight cycle paused. The previous time and daylight-cycle rule are restored when the match ends.

## GitHub update
Upload the contents of the GitHub Update ZIP into the existing repository root (where `build.gradle` and `src/` are visible), replacing the matching files and folders. Commit the changes. GitHub Actions should produce `build/libs/blockparty-0.6.1-alpha.jar`.

## Validation
The source and resource files have been reviewed and statically checked. A full NeoForge Gradle compile was not run in this environment; GitHub Actions is the authoritative compile test. Do not install the JAR unless the GitHub Actions build succeeds.
