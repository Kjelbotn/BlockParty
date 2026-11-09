# BlockParty 0.4.2-alpha

- Removed the arena and lobby roofs: both areas are open to the sky.
- Added invisible light level 15 sources and visible Sea Lantern accents around the floor, spectator ring, walls and lobby.
- Active matches freeze the world at daytime; the previous day time and daylight-cycle rule are restored after the match.
- Automatic matchmaking requires 2 players and supports up to 10.
- Admin `/bp forcestart <id>` starts a solo test with exactly one player. Regular `/bp start <id>` requires at least 2 players.
- The round actionbar shows the target color, live seconds remaining, and a 10-segment countdown bar refreshed during the round.
- The sidebar displays the live remaining time.
- The lobby leaderboard is a larger wall display with the Top 10 entries, rendered using Text Display entities.
- A tiebreak rule selects the last processed falling player if a multiplayer final elimination window leaves no one alive.
- Existing player profiles, rank progression, streaks, personal bests, power-ups and music voting are retained from the 0.4.x source line.

This is a source update. Use GitHub Actions to compile before installing the JAR.
