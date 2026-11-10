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
