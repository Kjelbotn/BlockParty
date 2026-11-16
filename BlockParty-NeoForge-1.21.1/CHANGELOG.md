# Changelog

## 1.4.48
- Fixed the TNT penalty sound event reference so the mod compiles on NeoForge 1.21.1.

## 1.4.47
- Replaced the generic survivor cue with the bundled YAY.ogg for players still alive when the floor returns.
- Added TNT penalty power-up: launches the player high into the air with an explosion effect.

## 1.4.46
- Keep the player statistics sidebar visible only while physically inside a configured BlockParty lobby; hide it after leaving the lobby area.

## 1.4.45
- Added FREEZE: locks the collector in place for 2 seconds.
- Made Lucky Landing a persistent one-use rescue with a safe-floor fallback.
- FULL SOM FAEN now uses Minecraft's native Nausea camera distortion.
- Made COLOR RAIN pickups stack independently, increasing the next-round rain amount.
- Replaced the victory divider with solid underscore characters.

# Changelog

## 1.4.44
- Fixed lobby statistics sidebar refresh on dedicated multiplayer servers.
- Added a strong camera sway to FULL SOM FAEN alongside vanilla nausea.
- Kept Pumpkin Vision at exactly 10 seconds.
- Fixed Lucky Landing being cleared before the fall phase and added a void-death rescue path.

# BlockParty 1.4.36 — Match feedback and lobby profile stats

- Lobby stats include rank, personal record, podium finishes, matches played, wins and points.
- Added a chat announcement each time the round timer gets shorter, including the new round duration.
- Added a Norwegian “NÅ SPILLES” announcement with song title and artist when the match begins.
- Lowered the final countdown harp note to pitch 0.5946 in Classic and Memory Mode.
- Extended the podium fireworks presentation to 10 seconds and added vanilla Minecraft firework launch/blast sounds.
- Preserved the approved chat format: total points | BlockParty rank username ≫ message, with the requested colors.
- Preserved the exact elimination message: “Du er nå en spectator!”.
- Kept FULL SOM FAEN! (12 seconds of Nausea), round milestone messages and lobby-only statistics.
- The supplied yay.ogg is not present in the repository assets; a vanilla experience-orb cue is used for round survivors until that file is available.
- Achievements remain a future feature and are not claimed as implemented.
