# BlockParty 0.19.15-alpha — Fixed Round Curve, Color Wait and COLOR RAIN

- All matches now use the exact fixed 25-round countdown schedule: 6, 5.5, 5.5, 5, 5, 4.5, 4.5, 4, 4, 3.5, 3.5, 3, 3, 2.5, 2.5, 2, 2, 1.5, 1.5, 1, 1, 0.5, 0.5, 0.5, 0.5 seconds. Six seconds is the hard maximum.
- The full floor returns after a round and remains visible for two seconds in a separate WAITING FOR COLOR phase before the next countdown starts. The first round has no extra wait.
- Added COLOR RAIN. Collecting it only queues the effect; it does not alter the active round. On the next round, about eight percent of floor tiles receive new random palette colors, distributed over the entire floor, with falling-color particles during the two-second wait.

# BlockParty 0.19.14-alpha — Reliable Music, Track Timing and Full Podium

- Kept every song fully built into the mod under the original `blockparty` namespace. No external resource pack is required.
- The build normalizes and fully decodes all ten official OGG tracks, verifies every sound-event/file mapping, measures the final audio duration, and packages those measurements with the mod.
- Round lengths now use the measured duration of each packaged OGG file. Long tracks are no longer clamped to ten seconds per round; remaining time is smoothly distributed across the middle rounds. The 10-second first round and 2.5-second final round remain fixed.
- All ten tracks are checked for decodable, non-silent audio and are loudness-normalized to a consistent playback level before packaging.
- The lobby podium now places first, second and third on gold, iron and copper blocks respectively.
- Players attempting to join after match start are told to wait for the next match and are not added to the active match.
# BlockParty 0.19.13-alpha — Normalize All Music Audio

- Restored all music sound events to the mod's original `blockparty` registry and built-in `assets/blockparty/sounds.json` flow. No external resource pack or separate music namespace is required.
- The build now normalizes all ten official OGG tracks to a consistent 44.1 kHz, stereo, Vorbis profile and removes embedded metadata before packaging.
- Added an FFprobe validation step that fails the build if any official track is missing or does not match the expected Vorbis format.
- This targets potential decoder incompatibilities between individually encoded OGG streams while retaining each original track's audio content and duration.

# BlockParty 0.19.12-alpha — Isolated Music Resource Namespace

- Moved all ten music sound events into a dedicated `blockparty_music` namespace, separate from the general `blockparty` sound definitions.
- Added a dedicated `assets/blockparty_music/sounds.json` mapping for every official track. The mappings reuse the existing packaged OGG files, so no audio was removed or replaced.
- Prevents an older resource pack that overrides `assets/blockparty/sounds.json` from hiding newer playlist tracks.
- Kept the original `blockparty` namespace for gameplay sound effects.
- Confirmed that all ten packaged OGG music assets are present and decode successfully.

# BlockParty 0.19.11-alpha — Track-Based Round Counts and Inventory Restore

- Songs shorter than 1:30 now use 10 rounds; songs lasting 1:30 or longer use 20 rounds.
- Round timers are fitted to the selected track duration and the active round count, keeping round 1 at 10 seconds and the final round at 2.5 seconds.
- The 53.4-second “Konfliktpartiet” now has 10 rounds whose timers total the track length, so the song no longer ends around round 6.
- The round counter, match-end checks, arena status and voting menu now display the correct 10- or 20-round limit.
- Lobby inventories remain restricted to BlockParty items. The pre-lobby Survival inventory, including armor and offhand, is restored only when the player leaves via Redstone Dust or /bp leave.
- Inventory restoration now happens after returning to the saved Survival location and explicitly marks all inventory containers dirty before synchronization, preventing armor/offhand state from being overwritten by a teleport update.

# BlockParty 0.19.10-alpha — Smooth Round Timing

- Reworked short-song pacing so the 20 rounds descend much more evenly from 10 seconds on round 1 to 2.5 seconds on round 20.
- Raised the minimum combined round-timer budget from 70 to 125 seconds, matching a linear 10-to-2.5-second progression.
- Round 10 is now approximately 6.45 seconds for tracks shorter than 125 seconds, instead of collapsing to approximately 2.55 seconds.
- Longer tracks continue to stretch the intermediate rounds according to their duration; the fixed first and last round times remain unchanged.

# BlockParty 0.19.9-alpha — Short Song Round Timing Hotfix

- Fixed short-song timing curves that caused nearly every round after round 1 to fall to 2.5 seconds.
- Raised the minimum combined round-timer budget to 70 seconds so short tracks retain a visible, gradual progression through the middle rounds.
- Round 1 remains exactly 10 seconds and round 20 remains exactly 2.5 seconds for every track.
- Tracks longer than 70 seconds continue to scale their intermediate round timers according to their actual duration, up to the 192.5-second maximum pacing budget.

# BlockParty 0.19.8-alpha — Song-Scaled Round Timing Hotfix

- Restored song-length-based round scaling while keeping fixed endpoints: round 1 is always 10 seconds and round 20 is always 2.5 seconds.
- The 18 middle-round timers now adapt to the selected track using a duration-fitted curve: shorter tracks descend faster and produce shorter matches; longer tracks retain more time in the middle rounds.
- Every round remains within the 2.5–10 second range.
- The achievable 20-round timer budget is clamped to 57.5–192.5 seconds because the first and last round durations are fixed and every round has a 2.5-second minimum.

# BlockParty 0.19.7-alpha — Lobby Information Panels

- Added a dedicated “NYTTIGE KOMMANDOER” panel beside the lobby leaderboard with /bp leaderboard, /bp ranklist and /bp profile plus short descriptions.
- Split the east lobby information wall into two balanced panels: commands and leaderboard.
- Moved leaderboard text lower and vertically centres the full content dynamically, regardless of whether zero, one or five players are listed.
- Adjusted text scales to fit the two-panel layout.
- Both panels refresh and clean up together to prevent duplicate holograms.

# BlockParty 0.19.6-alpha — Gameplay Hotfix

- Round timers now follow a fixed gradual progression from 10 seconds on round 1 to 2.5 seconds on round 20, regardless of song length.
- Reduced long-song opening rounds so rounds 1–10 no longer stretch excessively.
- Winner podium and all podium hologram text are removed 20 seconds after participants return to the lobby.
- Restores the exact lobby floor blocks underneath the podium when its display period expires.
- Reduced the in-game lobby leaderboard and /bp leaderboard command to the top 5 players.
- Added /bp ranklist to display every rank and its required point threshold.
- Updated /bp help with the new round timing, top-five leaderboard and rank-list command.

# BlockParty 0.19.5-alpha

- Rebuilt the lobby dance floor as a framed, symmetrical neon panel design with a permanent podium plinth.
- Reworked the DJ voting wall: larger, lighter text; correct left-to-right song numbering; and all ten playlist entries displayed.
- The arena's main screen now displays “BlockParty” during the pre-round countdown, then switches to the target color on round one.
- Simplified lobby item labels to “Join game”, “Leave lobby”, “Menu” and “Vote”; removed bold/italic styling and normalized already-issued items.
- Removed bold styling from player-facing BlockParty chat notifications and music-vote confirmations.
- Fixed winner podium placement so it only replaces its three support columns and restores the original lobby floor when removed; the refresh pass also repairs old podium-footprint holes.

# BlockParty 0.19.4-alpha

- Fixed song-length pacing: the 20 round timers now add up to the track's full playback duration, rounded only to the nearest 0.05-second server tick.
- Fall and intermission phases pause music and are no longer incorrectly counted as song playback time; their transition durations remain fixed.
- Registered playlist lengths from the embedded OGG audio metadata and removed the 3:30 cap from music playback, round pacing and the voting-menu duration display. Long tracks now use their complete duration.

# BlockParty 0.19.3-alpha

- Added difficulty and match-duration details to each song's voting-menu tooltip.
- Difficulty bands: EASY (3:00–3:30), MEDIUM (2:00–2:59), HARD (1:30–1:59), EXPERT (under 1:30).
- Displayed duration is the playable match duration, capped at 3:30 even when the audio file is longer.
- Difficulty colors: green, aqua, gold and red respectively.

# BlockParty 0.19.2-alpha

- Every match has a maximum of 20 rounds, regardless of song length.
- The match timing budget is capped at 3:30. Shorter tracks proportionally compress round timers and fall/intermission transitions, making short-song matches faster and harder without reducing the round count.
- Each selected track is played once only. Audio completion never ends the match early and the song is not restarted; gameplay continues to the normal winner or round-20 result if tick rounding causes a small timing difference.
- Tracks longer than 3:30 use a 3:30 timing budget.

# BlockParty 0.19.1-alpha

- Music tracks are played once only; removed automatic replay when a track ends.
- The match now ends when the selected track reaches its full declared duration. If multiple players remain, the highest-scoring survivor wins.
- Music duration is no longer capped at 210 seconds; the full track duration is used for match pacing.
- The match-duration countdown pauses while the music is paused during floor-collapse transitions, so gameplay time stays aligned with playback.

# BlockParty 0.19.0-alpha

- Rebuilt the lobby interior as a coherent nightclub: symmetrical lounge bays, a proper front bar, framed wall panels, an architectural entrance, aligned ceiling trusses and speaker stacks.
- Preserved the DJ stage, voting wall, leaderboard, podium footprint and central dance floor.
- Refreshed the DJ voting screen's leader panel: it now says 'MEST STEMTE LÅT' and displays the leading track, artist and vote count instead of the old 'FOLKETS FAVORITT' / 'INGEN LEDER ENNÅ' wording.
- Keeps the playlist title and artist metadata in separate text displays to avoid literal newline artifacts.

# BlockParty 0.18.0-alpha

- Rebuilt the music voting wall layout: song title, artist and vote count are separate text displays; removed the literal \\n rendering issue; now shows up to eight tracks with more spacing.
- Reworked the HUB chest GUI with a dark frame, purple separators, centered category items, clearer labels and descriptions.
- Updated click routing to match the new HUB slot positions.

# BlockParty 0.17.5-alpha

- Fixed the remaining duplicate walkwayY declaration in ArenaGenerator.refreshVisuals.
- Keeps the early declaration used by the spectator cleanup loop and removes the later duplicate.

# BlockParty 0.17.4-alpha

- Fixed the compile error in ArenaGenerator.refreshVisuals: walkwayY is now declared before the spectator-walkway cleanup loop.
- Includes all 0.17.1, 0.17.2 and 0.17.3 changes.

# BlockParty 0.17.3-alpha\n\n- Combined the 0.17.1 DJ screen corrections and 0.17.2 arena/lobby cleanup.\n- Winner podium is now hidden during waiting and gameplay, then rebuilt in front of the DJ booth after a completed match victory.\n- The match winner is teleported onto the center gold block and held in place for 5 seconds before free lobby movement is restored.\n- The podium is cleared when the next match starts.\n\n# BlockParty 0.17.2-alpha\n\n- Combined the previous DJ-screen/lobby fixes with this arena and lobby polish pass.\n- Removed accidental blocks beneath the spectator ring, both on fresh generation and in-place upgrades.\n- Restored a coherent neon feature wall on the far side of the arena.\n- Forced tracked redstone lamps to remain lit and added adjacent sea-lantern lighting.\n- Reworked lobby decor into a consistent, symmetrical club layout with a compact cafe, mirrored lounge bays, aligned wall panels and speaker towers.\n\n# Changelog

## 0.17.0-alpha
- Expanded the lobby into a neon club/cafe interior inspired by the supplied reference images.
- Added dark ceiling beams, magenta/purple glass wall panels, cyan accent lighting, and additional architectural light strips.
- Added a quartz cafe counter and display wall on one side of the lobby.
- Added lounge booth seating, low tables, and decorative lighting on the opposite side.
- Framed the dance floor with neon borders and added speaker/light towers.
- Applied the interior pass both to newly generated lobbies and to existing arenas through the in-place visual refresh.

## 0.16.0-alpha
- Revamped lobby DJ screen with a two-column playlist and a larger live-vote leader panel.
- Added six rotating visual patterns to the lobby dance floor while the lobby is idle.
- Added a lobby winner podium showing the top three players from the latest match and their survived rounds.
- Added a clickable BlockParty HUB compass with profile, music voting, leaderboard, achievements placeholder, cosmetics placeholder, and next-match registration.
- Protected the HUB compass from being dropped and restored it after matches.

# BlockParty 0.15.9-alpha

- Added three official tracks: Fest hos Toby, Konfliktpartiet, and HMS er best!.
- Song-length difficulty scaling now treats 3:30 (210 seconds) as the maximum effective match soundtrack duration.
- Tracks shorter than 3:30 retain their original duration for difficulty scaling; longer tracks are capped at 3:30.
- Existing 20-round maximum and progressively tightening round timers are preserved.

# BlockParty 0.15.8-alpha\n\n- Match length is capped at 20 rounds for every song.\n- Round timers now scale with the selected song duration: shorter songs have tighter, harder timing; longer songs are more forgiving.\n- The progression still tightens through all 20 rounds, with a minimum timer guard to keep rounds playable.\n- Retains the 0.15.7 lobby join announcement and lobby fall-damage protection.\n\n# BlockParty 0.15.7-alpha

- Added a one-time public chat announcement when a player first enters the BlockParty lobby using `/bp`; sent only to online players currently in Survival mode.
- Disabled fall damage inside the BlockParty lobby at all times, including during an active match and for visitors who have not enrolled. Normal Survival fall damage outside the lobby is unchanged.
- Match duration is not yet dynamically scaled to song length; this needs a separate design because short songs would require fewer rounds or a different round-timing model.

# BlockParty 0.15.6-alpha – Hotfix\n\n- Night Vision power-up duration reduced from 60 seconds to 10 seconds maximum.\n- Fixed match sidebar visibility: active participants see the BlockParty sidebar; non-participants are explicitly sent no sidebar.\n- Players joining during a match are also kept free of the BlockParty sidebar.\n- At match end, the sidebar is removed for everyone.\n\n# BlockParty 0.15.5-alpha\n\n- Fixed the sidebar disappearing for BlockParty participants after the 0.15.3 visibility change.\n- The server now broadcasts the sidebar objective normally, then immediately restores the previous sidebar for non-participants.\n- Players outside the active match should no longer see BlockParty information; active participants should see the match sidebar again.\n\n# BlockParty 0.15.3-alpha

- Fixed the match sidebar being displayed to every player on the server.
- The BlockParty sidebar is now sent only to players registered in the active match.
- Non-participants keep their normal sidebar and do not receive BlockParty match display updates.
- This is a display-only fix: BlockParty points are still awarded only through match gameplay to registered participants.

# BlockParty 0.15.2-alpha

- Redesigned the lobby DJ screen into a split layout: the complete existing song catalogue is listed on the left in its original playlist order.
- The centre/right panel now highlights the current vote leader in large text, including artist and vote count. Before any votes arrive, it displays a clear no-leader-yet message.
- Removed the misleading rank-by-vote placement that visually reversed song numbers across the screen.
- No tracks were added, removed, renamed, or re-registered; the existing playlist and audio assets are unchanged.

# Changelog

## 0.15.1-alpha
- Reduced the lobby DJ screen width from 55 to 39 blocks.
- Reworked the DJ screen playlist into a larger, readable three-column layout (up to six tracks); the voting menu remains the full catalogue.
- Increased DJ screen text size and spacing.
- Added a dense invisible level-15 light grid three blocks above the lobby floor, plus supplementary mid-height lights to reduce dark patches.
- Kept the existing music catalogue and audio assets unchanged; no tracks were added or registered.

## 0.15.0-alpha
- Added covered lobby roof and interior lighting.
- Expanded lobby DJ display and horizontal voting presentation.

## 0.15.4-alpha
- Added “2014 Launchpad Mix” by Exige.
- Added “Hau Hau Hau” by Bangerfabrikken.
- Existing tracks and voting behavior retained.
