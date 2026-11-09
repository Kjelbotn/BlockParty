# BlockParty — NeoForge 1.21.1

**Version 0.2.0-alpha** — a singleplayer-friendly Block Party minigame inspired by classic Minecraft minigame servers.

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.x** (the development build is configured for 21.1.265)
- Java **21**

Install the same JAR in the `mods` folder of the NeoForge profile. In singleplayer, NeoForge runs the integrated server automatically; no separate server is needed.

## Build

On Windows:

```bat
gradlew.bat build
```

On Linux/macOS:

```sh
./gradlew build
```

The build uses Gradle 8.10.2 and downloads Minecraft/NeoForge development dependencies on first run, so the machine building the mod needs internet access.

The JAR is created at:

```text
build/libs/blockparty-0.2.0-alpha.jar
```

## Quick setup — automatic stadium generator

Enable cheats in the singleplayer world (Open to LAN → Allow Cheats: ON) and stand in a large, open area. The generator replaces blocks in the arena footprint, so do not run it on top of a base or builds you want to keep.

If you already joined the old test arena, leave it first:

```text
/bp leave
```

Generate a full arena, dance floor and attached lobby in one command:

```text
/bp generate main 20
```

The size is the dance floor's width and depth. Allowed sizes are 12–32; if omitted, it defaults to 20. The generator builds:

- a colorful wool dance floor using all 16 Minecraft colors;
- a floating play floor with a clear fall pit underneath;
- a dark stadium ring with purple lighting bands, pillars and an entrance arch;
- an attached waiting lobby with a feature wall and a BP mark;
- a saved arena definition and lobby spawn.

The command moves you to the generated lobby. You do not need to place wool manually or set `pos1`/`pos2`.

## Play

Join the arena:

```text
/bp join main
```

A 10-second countdown starts automatically, including when only one player is in the arena. To start immediately:

```text
/bp start main
```

The target wool block is placed in the player's currently selected hotbar slot and its item name displays the target color. At the end of the round, every non-target floor tile disappears. Players not standing on the correct color are eliminated into spectator mode.

### Round timer

| Round | Time | Round | Time | Round | Time |
|---:|---:|---:|---:|---:|---:|
| 1 | 10.0 s | 6 | 5.0 s | 11 | 2.5 s |
| 2 | 9.0 s | 7 | 4.5 s | 12 | 2.0 s |
| 3 | 8.0 s | 8 | 4.0 s | 13 | 1.5 s |
| 4 | 7.0 s | 9 | 3.5 s | 14 | 1.0 s |
| 5 | 6.0 s | 10 | 3.0 s | 15 | 0.5 s |

At the start of every round the floor receives a new shuffled color arrangement and one of several patterns (mosaic, vertical bands, horizontal bands, patches or diagonal mosaic). The target is selected from the colors present in that round.

In solo play, survive all 15 rounds to win. In multiplayer, the last surviving player wins; if several players remain after round 15, all remaining players win.

After the match, the mod restores the generated floor's original layout, returns players to the lobby, restores their previous game modes and puts the item they originally held back in the same hotbar slot.

## Commands

| Command | Permission | Purpose |
|---|---:|---|
| `/bp generate <id> [size]` | OP | Generate a complete stadium, wool floor and lobby (12–32, default 20) |
| `/bp join <id>` | Everyone | Join the arena |
| `/bp leave` | Everyone | Leave the arena |
| `/bp start <id>` | OP | Start immediately (useful for solo testing) |
| `/bp stop <id>` | OP | Stop and restore the arena |
| `/bp status <id>` | Everyone | Show arena status and round |
| `/bp list` | OP | List saved arenas |
| `/bp remove <id>` | OP | Remove an unused arena definition |
| `/bp create <id>` | OP | Create a manual arena definition |
| `/bp pos1 <id>` / `/bp pos2 <id>` | OP | Set manual floor corners |
| `/bp setlobby <id>` | OP | Set manual lobby spawn |
| `/bp help` | Everyone | Show command help |

The long command root `/blockparty` is also available.

## Notes

- Arena definitions are saved in `blockparty-arenas.properties` in the world save folder.
- The generator replaces blocks inside its stadium, lobby and fall-pit footprint. Use an open build area.
- Generated dance floors support 12×12 through 32×32 tiles.
- Manual arenas still work; their floor must be a filled rectangle of at least two colors of wool, concrete or terracotta.
- This is an alpha release. Test in a copy of your world before using it in a long-running save.
