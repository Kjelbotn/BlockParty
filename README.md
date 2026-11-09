# BlockParty 0.5.0-alpha

NeoForge mod for Minecraft 1.21.1 / Java 21. Classic BlockParty mode is the only mode in this release. Hardcore Mode is deferred.

## Match rules
- Automatic lobby countdown begins when at least 2 players are joined.
- Maximum 10 players per match.
- Admin solo test: `/bp forcestart <id>` while exactly one player is joined (permission level 2).
- Admin manual start: `/bp start <id>` requires at least 2 joined players.
- Join: right-click the Join Game Nether Star in the lobby, or `/bp join <id>`.
- 15 rounds: 10, 9, 8, 7, 6, 5, 4.5, 4, 3.5, 3, 2.5, 2, 1.5, 1 and 0.5 seconds.
- The actionbar shows target colour, live remaining seconds and a segmented countdown bar.
- Lobby music voting remains available through the Music Vote book.

## Power-ups
- Speed, Night Vision and Jump Boost grant temporary effects for the current round.
- Leap gives an immediate upward boost.
- Color Teleporter moves the player to an unoccupied, safe floor tile in the announced target colour.
- Power-ups are intentionally uncommon: 35% chance per eligible round, with at least one complete round between successful waves. Up to five beacons can spawn, spaced farther apart.

## Arena generation
`/bp generate <id> [size]` builds an open-air stadium, raised spectator walkway and open-air lobby. Floor size is 24-50; default 40. The generator clears the area around the build site, so use an empty area and back up your world before regenerating. Invisible level-15 light sources and visible fixtures keep the arena readable.

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
Upload the contents of the GitHub Update ZIP into the existing repository root (where `build.gradle` and `src/` are visible), replacing the matching files and folders. Commit the changes. GitHub Actions should produce `build/libs/blockparty-0.5.0-alpha.jar`.

## Validation
The source and resource files have been reviewed and statically checked. A full NeoForge Gradle compile was not run in this environment; GitHub Actions is the authoritative compile test. Do not install the JAR unless the GitHub Actions build succeeds.
