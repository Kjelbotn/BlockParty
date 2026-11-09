# BlockParty — NeoForge 1.21.1

**Version 0.3.0-alpha** · Java 21 · Singleplayer-first · NeoForge 21.1.x

BlockParty is a Minecraft minigame inspired by classic Block Party arenas. This alpha expands the arena, changes the dance floor to concrete, adds a block-built target-colour display, random one-round beacon power-ups, and original music/SFX embedded in the mod.

## 0.3.0-alpha features

- `/bp generate <id> [size]` builds a larger stadium, 40x40 default concrete dance floor, all-around spectator promenade, stepped spectator stands, outer walls, light pylons, target-colour display and attached lobby.
- Floor palette uses all 16 concrete colours. The layout and pattern are randomized each round.
- Target colour is shown as a concrete block in the selected hotbar slot and as large block lettering on the far stadium wall.
- 15 round durations are preserved: 10, 9, 8, 7, 6, 5, 4.5, 4, 3.5, 3, 2.5, 2, 1.5, 1 and 0.5 seconds.
- A beacon may randomly appear during some rounds. Right-click it to collect one random power-up: Speed II, Night Vision or Jump Boost II. The effect is cleared at the end of that round. There is no Second Chance power-up.
- Three original electronic music cues are chosen at random per round. Music is stopped when the round resolves and starts again for the next round. Music voting is not included in this alpha and is reserved for a later version.
- Original round, target-colour, elimination, beacon, pickup and victory SFX are included in the mod JAR. A separate resource pack is not required.
- Singleplayer support: a solo player can complete all 15 rounds.

## Generate and play

1. Back up your world.
2. Open a large, empty flat area. The generator clears the stadium and attached lobby footprint before constructing it, so do not run it near buildings you want to keep.
3. Enable cheats in singleplayer (Esc → Open to LAN → Allow Cheats: ON → Start LAN World).
4. Stand at the centre of where you want the dance floor and run:

```mcfunction
/bp generate main
```

This creates a 40x40 arena by default. You can choose a size from 24 to 56:

```mcfunction
/bp generate main 48
```

Then start playing:

```mcfunction
/bp join main
```

The lobby countdown begins automatically. To start immediately:

```mcfunction
/bp start main
```

Useful commands:

```text
/bp help
/bp list
/bp status main
/bp leave
/bp stop main
```

## Power-ups

- **Speed Boost:** Speed II for the remainder of the current round.
- **Night Vision:** Night Vision for the remainder of the current round.
- **Jump Boost:** Jump Boost II for the remainder of the current round.

Power-ups are removed when the round ends. They do not persist into later rounds. A beacon is not guaranteed to spawn every round.

## Audio

The mod includes three original electronic music cues and seven original sound effects under `assets/blockparty/sounds`. Music uses Minecraft's Music volume category; sound effects use the Players category. The current alpha selects a random music cue for each round and stops it at the round transition. Per-song voting is planned for a later update.

## Build with GitHub Actions

The `.github/workflows/build.yml` workflow lives at the repository root. It installs Java 21 and Gradle 8.10.2, runs `gradle build --no-daemon`, and uploads the JAR as an artifact.

1. Upload the contents of this project folder to the root of a new or existing GitHub repository (not an extra nested folder).
2. Commit to the `main` branch.
3. Open **Actions → Build BlockParty** and wait for a green check.
4. Open the successful run and download `BlockParty-NeoForge-1.21.1-0.3.0-alpha` under Artifacts.
5. Extract the artifact ZIP. It contains `blockparty-0.3.0-alpha.jar`.
6. Close Minecraft, remove older BlockParty JARs from the Modrinth instance's `mods` folder, add the new JAR, and launch NeoForge 1.21.1.

## Verification note

This is a source project prepared for GitHub Actions. A full NeoForge compile and in-game smoke test have not been run in this environment. Treat the first successful GitHub Actions build as the compile check, then test arena generation, power-up pickup and music transitions in a backup singleplayer world before using it in your main world.
