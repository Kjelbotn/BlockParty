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
