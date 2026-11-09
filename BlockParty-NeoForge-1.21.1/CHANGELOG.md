# BlockParty 0.6.0-alpha

- Classic Mode remains the only available game mode; Hardcore Mode is intentionally deferred.
- Added the Leap power-up: an immediate high jump to help players reposition during a round.
- Added the Color Teleporter power-up: teleports the player to an unoccupied, safe tile in the currently announced target color.
- Power-up waves are now a bonus rather than a guaranteed event every round: 35% spawn chance, at least one full round between successful waves, up to five beacons, and wider spacing between them.
- Reduced arena floor limits: `/bp generate <id>` now creates a 40x40 floor by default; allowed sizes are 24x24 through 50x50.
- The game now snapshots the complete main inventory, armor, offhand and selected hotbar slot before a match, clears the inventory for play, and restores the snapshot when the player leaves, is eliminated from the match, or the match ends. A normal disconnect is handled by the logout event. Abrupt process crashes are not yet covered by a disk-persisted inventory journal.
- Added a physical DJ screen in the lobby with current track, artist, BPM, live voting counts and a ranked shortlist.
- Added a six-second DJ intro before the first round; the winning playlist track starts during the intro.
- Added music-reactive redstone-lamp pulses and note/end-rod particle effects driven by each track's configured BPM.
- Expanded the playlist to three built-in tracks plus eight replaceable custom OGG slots, with name, artist, BPM and duration configured in the world's `blockparty-music.properties` file.
- Added Speed Upgrade, Super Jump and Slowness (negative/trap) power-ups alongside Leap, Color Teleporter and Night Vision.
- Added denser level-15 arena lighting and visible sea-lantern fixtures; added a lobby DJ booth, speaker columns, neon dance floor, lounge seating and brighter interior lighting.
- Made the arena's purple/magenta concrete and stained-glass accents change to the target colour each round, then restore after the match.
- Moved leaderboard visibility to the lobby only and added wider cleanup to remove stale leaderboard holograms from older arena-wall placements.
- Improved the large target-colour display placement on the black arena panel and recalculates its coordinates from saved floor corners when a match starts.
- Existing arenas receive a one-time non-destructive lighting/lobby-interior upgrade when a player first visits after updating; no full arena regeneration is required.
- Kept player profiles, ranks, round scoring and the 15-round Classic Mode structure. Hardcore Mode remains deferred.

This is a source update. Use GitHub Actions to compile before installing the JAR. A full NeoForge Gradle compile has not been run in this environment.
