# BlockParty 0.4.1-alpha

NeoForge mod for Minecraft 1.21.1. Designed for singleplayer (integrated server) and compatible with multiplayer testing.

## Lobby controls

When you stand in the generated BlockParty lobby, the mod automatically gives you two items: 

- **BlockParty Join Game** (Nether Star): right-click to join the nearby arena and enter the 60-second lobby countdown.
- **BlockParty Music Vote** (Book): right-click to open the clickable chat song list. You can vote before joining, while the lobby is open.

The lobby screen is informational only; there are no clickable screen buttons. The physical leaderboard remains on the lobby's side wall.

If needed, the command `/bp join <id>` remains as a fallback. Admin setup commands include `/bp generate <id> [size]`, `/bp start <id>`, `/bp stop <id>`, `/bp status <id>`, and `/bp music reload`.

## GitHub update instructions

For the existing `BlockParty-Rebuild` repository, upload the contents of the GitHub Update ZIP to the repository root (the folder where `build.gradle` and `src/` are already visible). Keep your existing `.github/workflows/build.yml`. Commit to `main`; GitHub Actions should build the JAR.

The version is `0.4.1-alpha`. After a successful build, download the artifact from Actions → run → Summary → Artifacts.

## Important

Back up your Minecraft world before regenerating the arena. The generator replaces blocks in its build volume.
