# BlockParty 1.4.23

- Prevented song-vote chat spam: each successful song selection is announced only once; repeated clicks on the current song do not broadcast or consume a change.
- Limited each player to three distinct song selections per voting session (initial vote plus two changes). After the third selection, a light-pink message says `Du kan ikke stemme flere ganger.. :-)` and further changes are blocked.
- Reset vote-change counters when players leave the arena/BlockParty area and when a match ends.

# BlockParty 1.4.22

- After a player successfully votes for a song, immediately open the separate Classic / Memory Mode GUI so the mode vote cannot be overlooked.
- Keep mode voting available during the automatic 10-second countdown, allowing players to choose after the second song vote has started the countdown.
- Preserve the existing song vote when the mode GUI opens; Classic remains the default if nobody votes for a mode.

# BlockParty 1.4.21

- Changed `/bp` into a map-selection chest GUI with exactly two choices: dark-green concrete for Classic/Main and red concrete for Xmas.
- Selecting a map saves/restores the player's normal inventory through the existing arena session flow, then teleports them to the selected lobby and supplies the standard BlockParty lobby items.
- Added `/bp main` as a direct lobby command; retained `/bp xmas` and `/bp join <id>`.

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
