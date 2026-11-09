# BlockParty 0.3.3-alpha

## Gameplay and UI
- Target sign displays only the target colour in large block letters; removed the `FIND` label.
- Arena-facing target text uses the opposite X drawing direction after the previous in-game mirror report.
- Power-up beacon spawns immediately at the start of a randomly selected round (30% chance), with a per-round guard against duplicate messages.
- New round pattern appears about 3 seconds after the floor collapse begins: fall animation plus a brief neutral floor intermission.
- Scoreboard headers use the requested bold yellow, light-pink, aqua and light-green styling.
- Scoreboard shows permanent points, rank and points to the next rank.
- World is forced to night during a match and restored to its previous time at game end.

## Progression
- One permanent point is awarded for every round a player survives.
- Per-player points and last known names are saved by UUID in the world save folder.
- Rank thresholds: 0, 100, 250, 500, 1,000, 2,500, 5,000, 10,000.
- Added `/bp leaderboard` and `/bp rank` commands.
- Generated lobby includes a dynamic top-five hologram leaderboard.

## Arena
- Expanded spectator promenade and seating tiers.
- Wider/deeper lobby with a dedicated leaderboard wall.
