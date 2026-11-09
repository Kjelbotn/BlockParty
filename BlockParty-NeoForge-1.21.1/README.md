# BlockParty — NeoForge 1.21.1

**Version 0.3.3-alpha** · Java 21 · Singleplayer-first · NeoForge 21.1.x

BlockParty is a Minecraft minigame inspired by classic Block Party arenas. This update focuses on the round flow, a readable in-arena target display, richer scoreboard styling, persistent player progression and a lobby leaderboard.

## 0.3.3-alpha changes

- The in-arena target sign now displays **only the target colour name** in large concrete letters; the word `FIND` has been removed. The arena-facing text uses the corrected mirrored drawing order.
- Power-up beacons, when selected for a round, spawn immediately as the round starts. They remain occasional (30% chance), but no longer appear late in the countdown. A guard prevents duplicate spawn announcements.
- The floor collapse and fall phase lasts 35 ticks, followed by a short neutral-black floor transition. The next coloured pattern appears after about 3 seconds total from the collapse. The black transition floor keeps survivors safe during the pause.
- Sidebar styling now matches the requested look: yellow bold `BlockParty`, bold light-pink `Dancers Left`, bold aqua `Game Info`, and bold light-green `Your Stats`.
- Personal stats show permanent world points, current rank, points remaining to the next rank, and collected power-ups.
- Players earn **1 permanent point for every round they survive**. Points are saved per UUID in `blockparty-player-stats.properties` in the world save folder.
- Ranks unlock at 0, 100, 250, 500, 1,000, 2,500, 5,000 and 10,000 points: Rookie, Bronze Dancer, Silver Dancer, Gold Dancer, Platinum Dancer, Diamond Dancer, Superstar and BlockParty Legend.
- A physical top-five leaderboard appears on the right wall of the generated lobby. Players can also use `/bp leaderboard` and `/bp rank`.
- The lobby is wider and deeper, with a framed leaderboard wall. The spectator promenade and stands have been expanded.
- The world is set to night when a match starts and its previous time is restored when the game ends.
- Existing 15-round timings, concrete-only floor, one-round power-ups, elimination lightning and music/effects are retained.

## Generate and play

1. Back up your world before generating a stadium.
2. Go to a large, empty flat area. The generator clears its stadium and lobby footprint before building; do not run it beside structures you want to keep.
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

Joining starts the 10-second lobby countdown automatically. `/bp start main` starts immediately.

## Useful commands

```text
/bp help
/bp list
/bp status main
/bp join main
/bp start main
/bp leave
/bp stop main
/bp leaderboard
/bp rank
```

- `/bp leaderboard` shows the top 10 players in chat.
- `/bp rank` shows your permanent points, current rank and points required for the next rank.

## Points and ranks

A player earns one permanent point for every round they survive. Data is stored in the current world's `blockparty-player-stats.properties` file, keyed by player UUID, and survives game restarts. The generated lobby contains a top-five leaderboard board that refreshes as points are earned and when players return to the lobby.

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

## Power-ups

- **Speed Boost:** Speed II for the remainder of the current round.
- **Night Vision:** Night Vision for the remainder of the current round.
- **Jump Boost:** Jump Boost II for the remainder of the current round.

A beacon has a 30% chance to appear at the beginning of a round and spawns one block above an unoccupied concrete tile. It is not guaranteed every round. The power-up is removed at the end of that round.

## Round flow

The target concrete colour is shown on the large stadium display and in the action-bar indicator above the hotbar. Pattern types are not announced. When a round resolves, the coloured pattern collapses; after the fall sequence and a short pause, the next pattern appears. The world is set to night during the match and returned to its previous time afterward.

## Build with GitHub Actions

The full source project includes `.github/workflows/build.yml` at the repository root. The workflow installs Java 21 and Gradle 8.10.2, runs `gradle build --no-daemon`, and uploads the JAR artifact.

1. Upload the contents of the source project to the root of a GitHub repository (not an extra nested folder).
2. Commit to `main`.
3. Open **Actions** and wait for a green check.
4. Download the artifact from the successful run. It contains `blockparty-0.3.3-alpha.jar`.
5. Close Minecraft, remove older BlockParty JARs from the Modrinth instance's `mods` folder, add the new JAR and launch NeoForge 1.21.1.

For the **GitHub update ZIP**, keep your already-working `.github/workflows/build.yml` in the repository root; the update ZIP intentionally excludes the hidden `.github` folder so you do not need to upload it again.

## Verification note

The source changes and package structure have been checked statically. A full NeoForge compile and in-game test still need to be run by GitHub Actions and in Minecraft. The physical lobby leaderboard, colour-sign orientation and timing changes should be checked in a backup singleplayer world before using the update in your main world.
