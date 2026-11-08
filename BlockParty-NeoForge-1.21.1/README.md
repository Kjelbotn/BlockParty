# BlockParty — NeoForge 1.21.1

A first playable alpha of a classic Block Party-style Minecraft minigame.

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.265** for the development build (the mod metadata accepts the 21.1.x line)
- Java **21** JDK

The same mod JAR is intended to be installed on the dedicated server and on every joining client. Round state and eliminations are controlled by the server; player messages use the normal Minecraft HUD/chat.

## Build

### Automated build (GitHub Actions)

The repository includes `.github/workflows/build.yml`. After the project is pushed to a GitHub repository, open the repository's **Actions** tab, select **Build BlockParty**, and run **Run workflow** (or let it run after a push). When the job succeeds, open the run and download the `blockparty-neoforge-1.21.1` artifact. It contains the compiled mod JAR.

### Build locally

On Windows:

```bat
gradlew.bat build
```

On Linux/macOS:

```sh
./gradlew build
```

The build script uses an installed Gradle if available. Otherwise, the bootstrap script downloads Gradle 8.10.2 into the local Gradle user directory. The first actual mod build also downloads the NeoForge/Minecraft development dependencies and therefore needs internet access.

The built mod will be at:

```text
build/libs/blockparty-0.1.0-alpha.jar
```

Copy that JAR into the `mods` folder on both the NeoForge server and each client. Restart the server after installing it.

## Create an arena

Run these commands as an operator (permission level 2). Stand on the arena floor when selecting the two corners; the command selects the block directly underneath your feet.

1. Stand at the intended lobby spawn and run:
   ```text
   /bp create main
   ```
2. Stand on one corner block of the colored floor and run:
   ```text
   /bp pos1 main
   ```
3. Stand on the opposite corner block, at the **same Y level**, and run:
   ```text
   /bp pos2 main
   ```
4. To move the lobby spawn later, stand where players should wait and run:
   ```text
   /bp setlobby main
   ```

The selected rectangle must be completely filled with colored wool, concrete, or terracotta blocks, and it must contain at least two different dye colors. The current maximum is 4,096 floor blocks (for example, 64×64).

Arena definitions are saved in `blockparty-arenas.properties` in the world save folder and loaded automatically when the server starts.

## Play

- Players use `/bp join main` to join.
- The arena automatically starts a 10-second countdown when at least two players have joined.
- An operator can force-start with `/bp start main` (useful for testing with one player).
- Players get a short window to stand on the announced color. Other colored floor blocks disappear, players on the wrong color are eliminated into spectator mode, and the original floor is restored before the next round.
- The time to find the color decreases as the rounds progress.
- The final remaining player wins.
- `/bp leave` leaves the arena. `/bp stop main` ends a running game and restores the floor.

## Commands

| Command | Permission | Purpose |
|---|---:|---|
| `/bp create <id>` | OP | Create an arena; current position becomes lobby spawn |
| `/bp pos1 <id>` | OP | Set first floor corner |
| `/bp pos2 <id>` | OP | Set opposite floor corner |
| `/bp setlobby <id>` | OP | Update lobby spawn |
| `/bp join <id>` | Everyone | Join an arena |
| `/bp leave` | Everyone | Leave the current arena |
| `/bp start <id>` | OP | Force-start a game |
| `/bp stop <id>` | OP | Stop a game and restore the floor |
| `/bp list` | OP | List configured arenas |
| `/bp status <id>` | Everyone | Show arena status |
| `/bp remove <id>` | OP | Remove an empty arena |
| `/bp help` | Everyone | Show quick help |

The long command root `/blockparty` is also available.

## Current alpha scope / known limitations

- One active match per arena; multiple arenas can exist at once.
- Floor blocks must be placed as a flat rectangle at one Y level.
- The arena must be built from the 16 dye colors of wool, concrete, or terracotta. Other blocks are rejected during start validation.
- No custom client keybind, custom GUI, cosmetics, economy, party system, or persistent player statistics yet. These are sensible next milestones after the first live server test.
- This is an initial source release. Compile and test it against the exact NeoForge build used by the server before using it on a public production server.
