# BlockParty 0.13.7-alpha

## Music voting GUI
- Replaced clickable chat song links with a 3-row chest-style song voting GUI.
- Song choices use colored concrete blocks and are arranged symmetrically in the center of the GUI (2x2 for the current four-song playlist).
- Hover tooltips show the song title, artist, live vote count, and whether the player has selected that song.
- Clicking a song casts or changes the player's vote; GUI icons cannot be picked up, moved, shift-clicked, swapped, or thrown.
- Vote counts refresh for all players who currently have the voting GUI open.
- Voting GUI closes when the match countdown begins.
- Existing vote threshold, tie-breaking, playlist, and music playback logic are preserved.
