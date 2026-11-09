# BlockParty 0.4.1-alpha

## Lobby join item and item-only lobby controls
- Removed the decorative `MUSIC • LEADERBOARD • PLAY` pseudo-navigation line from the lobby screen.
- The lobby wall now clearly explains the actual inventory controls: right-click the Nether Star to join and the book to vote for music.
- Players near an open BlockParty lobby automatically receive a uniquely named **BlockParty Join Game** Nether Star and **BlockParty Music Vote** book.
- Right-clicking the Join Star joins the nearby arena; `/bp join <id>` remains available as an admin/debug fallback.
- Right-clicking the Music Vote book works while physically in an open lobby, even before joining the match.
- The join star and music book are removed from participants' inventories when the match starts and returned when they return to the lobby.
- The existing physical leaderboard remains in the lobby; no leaderboard button has been added.
- The generator gives the creator their lobby items immediately. Lobby visitors are checked once per second to receive missing items.

## Compatibility
- Minecraft 1.21.1, NeoForge 21.1.x, Java 21.
- No new registered item is required; vanilla Nether Star and Book items are identified by their custom names.
