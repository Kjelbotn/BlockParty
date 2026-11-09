# Changelog

## 0.7.0-alpha
- Simplified match start to a 10-second countdown and removed the DJ intro phase.
- Added majority-vote threshold and random tie resolution.
- Added `/bp` quick lobby join and inventory snapshot at lobby entry.
- Expanded floor mosaic layouts.
- Documented resource-pack replacement for all seven sound effects.

## 0.7.1-alpha
- Removed the polished-blackstone foundation beneath the arena dance floor so the pit remains open.
- Turned arena redstone lamps on by default and kept music-light positions illuminated.
- Added a short target-tile contact grace window to prevent tick-order eliminations when the correct tile disappears at the round boundary.
- Corrected the built-in music event IDs used when stopping/restarting tracks (`neon_beat`, `pixel_rush`, `night_drive`).
- Increased built-in music event gain and added server-side track playback diagnostics.
