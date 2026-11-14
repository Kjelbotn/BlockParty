# BlockParty 1.4.8

- Fixed the Xmas arena screen creating a new Text Display every refresh. Existing tagged displays are now reused and updated in place, while misplaced and duplicate displays are removed. This prevents unbounded entity buildup and the resulting client/server performance degradation.

# BlockParty 1.4.7

- Fixed the Xmas arena screen entity lookup so persisted displays are found across the full world height instead of being respawned every 20 ticks.
- Retained the separate eight-track Xmas playlist and its own voting catalogue.

# BlockParty 0.5.0-alpha

- Classic Mode remains the only available game mode; Hardcore Mode is intentionally deferred.
- Added the Leap power-up: an immediate high jump to help players reposition during a round.
- Added the Color Teleporter power-up: teleports the player to an unoccupied, safe tile in the currently announced target color.
- Power-up waves are now a bonus rather than a guaranteed event every round: 35% spawn chance, at least one full round between successful waves, up to five beacons, and wider spacing between them.
- Reduced arena floor limits: `/bp generate <id>` now creates a 40x40 floor by default; allowed sizes are 24x24 through 50x50.
- The game now snapshots the complete main inventory, armor, offhand and selected hotbar slot before a match, clears the inventory for play, and restores the snapshot when the player leaves, is eliminated from the match, or the match ends. A normal disconnect is handled by the logout event. Abrupt process crashes are not yet covered by a disk-persisted inventory journal.
- Retained the existing lobby music voting / DJ playlist, player profiles, ranks, leaderboard and 15-round match structure.

This is a source update. Use GitHub Actions to compile before installing the JAR. A full NeoForge Gradle compile has not been run in this environment.
