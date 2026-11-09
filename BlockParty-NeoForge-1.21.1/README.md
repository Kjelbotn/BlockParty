# BlockParty — NeoForge 1.21.1

**Version 0.3.2-alpha** · Java 21 · Singleplayer-first · NeoForge 21.1.x

BlockParty is a Minecraft minigame inspired by classic Block Party arenas. This update focuses on the play experience: a larger 80×80 concrete floor by default, wider stadium facilities, a two-sided target display, stable target HUD, sidebar scoreboard, elimination lightning, and a softer redesigned original soundtrack and sound-effect set.

## 0.3.2-alpha changes

- Default dance floor expanded to **80×80**. Custom sizes from **40 to 96** are supported.
- Larger spectator promenade and stepped spectator stands around all four sides.
- Arena colour sign is built as a two-sided display. `FIND` and the selected colour are redrawn at the beginning of every round so the target does not disappear during play.
- A persistent action-bar target cue (`■  YELLOW  ■`) is shown above the hotbar while a round is active. The target concrete block is also placed in the selected hotbar slot.
- Only concrete is used on the dance floor, between rounds and during play. Wool and terracotta are rejected when configuring a manual floor.
- Random floor patterns still change each round, but their pattern names are not announced.
- Sidebar scoreboard shows dancers left, round, time left, points and collected power-ups.
- Elimination posts `<player> tapte konkurransen!` in chat and triggers a short visual-only lightning strike at the falling player's position.
- Beacon power-ups float one block above a concrete tile and are collected with a right-click. Speed II, Night Vision and Jump Boost II last only until that round ends.
- Replaced the previous music and effects with new original, softer electronic loops and cleaner tonal effects. Music starts with a round and stops at the round transition. Music voting is not included yet.
- Solo players can complete all 15 rounds. The round timings remain 10, 9, 8, 7, 6, 5, 4.5, 4, 3.5, 3, 2.5, 2, 1.5, 1 and 0.5 seconds.

## Generate and play

1. Back up your world before generating a stadium.
2. Go to a large, empty flat area. The generator clears the stadium and attached lobby footprint before building, so do not run it beside structures you want to keep.
3. Enable cheats in singleplayer (Esc → Open to LAN → Allow Cheats: ON → Start LAN World).
4. Stand at the centre of the desired dance-floor area and run:

```mcfunction
/bp generate main
```

This generates an **80×80** concrete floor by default. You can choose a size from 40 to 96:

```mcfunction
/bp generate main 96
```

Join and start:

```mcfunction
/bp join main
/bp start main
```

The lobby countdown starts automatically after joining. `/bp start main` starts immediately.

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

Power-ups are removed at the end of the round. A beacon is not guaranteed to spawn every round.

## Audio

The mod contains three original electronic music cues and seven original sound effects. Music uses Minecraft's Music volume category; effects use Players. Tracks are selected randomly each round and stopped at the round transition. Music voting is reserved for a later version.

## Build with GitHub Actions

The full source project includes `.github/workflows/build.yml` at the repository root. The workflow installs Java 21 and Gradle 8.10.2, runs `gradle build --no-daemon`, and uploads the JAR artifact.

1. Upload the contents of the source project to the root of a GitHub repository (not an extra nested folder).
2. Commit to `main`.
3. Open **Actions** and wait for a green check.
4. Download the artifact from the successful run. It contains `blockparty-0.3.2-alpha.jar`.
5. Close Minecraft, remove older BlockParty JARs from the Modrinth instance's `mods` folder, add the new JAR and launch NeoForge 1.21.1.

For the **GitHub update ZIP** supplied with this release, keep your already-working `.github/workflows/build.yml` in the repository root; the update ZIP intentionally focuses on the source files and build metadata so you do not need to upload the hidden `.github` folder again.

## Verification note

The source files and package structure have been checked, but a full NeoForge compile and in-game smoke test could not be run in this environment because Gradle is not installed and the Gradle distribution cannot be fetched here. Treat the first successful GitHub Actions build as the compile check. Test arena generation and gameplay in a backup singleplayer world before using the mod in your main world.
