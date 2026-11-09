# BlockParty 0.4.0-alpha

## New
- Added a 60-second automatic lobby countdown.
- Added a Music Voting Book: right-click to open a clickable chat menu showing each track and vote count; each player can change their vote until the match begins.
- Added `/bp vote <songId>` and admin `/bp music reload` commands.
- Expanded custom playlist support to eight resource-pack OGG slots (`custom_1.ogg`–`custom_8.ogg`). The world-local `blockparty-music.properties` controls track names and which IDs appear in voting.
- Added a separate indoor lobby with large Text Display signage and a dedicated voting hint.
- Reworked the generated arena around a single continuous spectator promenade four blocks above the floor, with inner/outer glass railings and no spectator stands.
- Eliminated players are teleported to the promenade and remain in Adventure Mode so they can run around and watch without interfering with active players.
- Added win streak, best streak, personal best, MVP awards and `/bp profile`.
- Added a centred winner title and match-end MVP announcement.
- Night is enabled and daylight-cycle is paused during a match; original time and gamerule are restored afterward.
- Added four more custom music slots, for eight custom resource-pack tracks total.

## Gameplay and presentation
- Arena text uses one-sided Text Display entities with explicit orientation; the generator removes previous BlockParty text/leaderboard entities inside its footprint before rebuilding.
- Lobby display faces the lobby side of the wall; the arena colour display faces the playing floor.
- Five power-up beacons are placed at the start of every round, spaced across the floor and one block above separate concrete tiles.
- Power-up effects are removed at the end of the round.
- The target-colour concrete blocks remain visible during the three-second round transition. No black/empty intermission floor is inserted.
- Scoreboard adds current streak and best streak lines.
- Lobby leaderboard is rebuilt when players join and after rounds.

## Known limitation
- The selected music stops between rounds and plays again when the next round begins, but this alpha restarts the track from the beginning. Exact timestamp-preserving pause/resume requires a dedicated client-side music player and is not implemented here.

## Compatibility
- Minecraft Java 1.21.1
- NeoForge 21.1.x
- Java 21

## Final implementation notes
- The 14-block elimination pit is fully hollow below the concrete tiles, with its support floor only at the bottom. This keeps the collapse/fall mechanic from landing players on an unintended blackstone layer directly below the dance floor.
