# BlockParty 0.13.9-alpha

## Sunset and song voting corrections
- The five-second sunset now starts from the exact Survival-world day time captured after players are teleported, then smoothly animates to 13,500. It no longer jumps to 12,000 first.
- Players can change their song selection any time while voting remains open. Each player still has exactly one active vote; changing selection replaces the previous vote rather than adding another.
- The chest GUI remains interactive after voting, keeps all song icons colored, and marks the player's current selection with a green check and tooltip.

# BlockParty 0.13.7-alpha

## Music voting GUI
- Replaced clickable chat song links with a 3-row chest-style song voting GUI.
- Song choices use colored concrete blocks and are arranged symmetrically in the center of the GUI (2x2 for the current four-song playlist).
- Hover tooltips show the song title, artist, live vote count, and whether the player has selected that song.
- Clicking a song casts or changes the player's vote; GUI icons cannot be picked up, moved, shift-clicked, swapped, or thrown.
- Vote counts refresh for all players who currently have the voting GUI open.
- Voting GUI closes when the match countdown begins.
- Existing vote threshold, tie-breaking, playlist, and music playback logic are preserved.

## 0.13.8-alpha
- Synced the accelerated five-second sunset to arena clients every server tick for much smoother visual motion.
- Replaced the 2x2 song picker with a vertically centered, single-column song list in a six-row chest GUI.
- Locked the voting GUI after the player's first vote; further clicks show a red one-vote-only message.
- End-of-match lobby return now clears match enrollment and voting unlocks. Players receive only the Nether Star and Redstone Dust; the vote book is granted again only after explicitly joining with the Nether Star.
