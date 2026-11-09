# BlockParty 0.4.2-alpha

NeoForge mod for Minecraft 1.21.1 / Java 21. Built for multiplayer matches (2-10 players) and singleplayer admin testing.

## Match rules
- Automatic lobby countdown begins when at least 2 players are joined.
- Maximum 10 players per match.
- Admin solo test: `/bp forcestart <id>` while exactly one player is joined (permission level 2).
- Admin manual start: `/bp start <id>` requires at least 2 joined players.
- Join: right-click the Join Game Nether Star in the lobby, or `/bp join <id>`.
- 15 rounds: 10, 9, 8, 7, 6, 5, 4.5, 4, 3.5, 3, 2.5, 2, 1.5, 1 and 0.5 seconds.
- The actionbar shows target colour, live remaining seconds and a segmented countdown bar.

## Arena generation
`/bp generate <id> [size]` builds an open-air stadium, raised spectator walkway and open-air lobby. Floor size is 40-96; default 80. The generator clears the area around the build site, so use an empty area and back up your world before regenerating. Invisible level-15 light sources and visible fixtures keep the arena readable.

## Useful commands
- `/bp help`
- `/bp join <id>`
- `/bp forcestart <id>` (admin; solo test with exactly one player)
- `/bp start <id>` (admin; minimum 2 players)
- `/bp stop <id>` (admin)
- `/bp status <id>`
- `/bp leaderboard`
- `/bp rank`

## World time
During an active match, the world is set to daytime with the daylight cycle paused. The previous time and daylight-cycle rule are restored when the match ends.

## GitHub update
Upload the contents of the GitHub Update ZIP into the existing repository root (where `build.gradle` and `src/` are visible). Replace `src/` and `gradle.properties`; keep your existing working `.github/workflows/build.yml`. Commit the changes. Expected JAR: `build/libs/blockparty-0.4.2-alpha.jar`.

## Validation
The Java source was statically checked for balanced braces and expected feature hooks; resource JSON was parsed and sound references checked. A full NeoForge Gradle compile was not run in this environment. GitHub Actions is the authoritative compile test.
