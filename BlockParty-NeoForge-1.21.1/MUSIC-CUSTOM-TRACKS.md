# Add your own BlockParty music

BlockParty has eight custom music slots. You do not need to rebuild the mod to change the audio files or the displayed song names.

## A. Add audio with a resource pack

Use the separate `BlockParty-Custom-Music-Pack-1.21.1.zip` template. Inside the pack, place OGG audio files in:

`assets/blockparty/sounds/music/`

Use these exact file names:

- `custom_1.ogg`
- `custom_2.ogg`
- `custom_3.ogg`
- `custom_4.ogg`
- `custom_5.ogg`
- `custom_6.ogg`
- `custom_7.ogg`
- `custom_8.ogg`

Enable the pack in Options -> Resource Packs. If you replace files while Minecraft is open, press F3+T or restart the game to reload audio assets. In multiplayer, every player should use the same pack.

## B. Rename tracks and choose what appears in voting

Start the world once with BlockParty installed. The mod creates `blockparty-music.properties` in the active world save folder (usually `<instance>/.minecraft/saves/<world-name>/`).

Example:

```properties
tracks=neon_beat,pixel_rush,night_drive,custom_1,custom_2,custom_3,custom_4,custom_5,custom_6,custom_7,custom_8
name.custom_1=Friday Night Mix
name.custom_2=My Chiptune Track
name.custom_3=Final Lap
```

`tracks` is the comma-separated list of IDs shown in the voting menu. Remove an ID from this list to hide it. The supported built-in IDs are `neon_beat`, `pixel_rush`, `night_drive`, and `custom_1` through `custom_8`.

After saving the properties file, run this command in Minecraft as an admin:

```mcfunction
/bp music reload
```

Then right-click the Music Voting Book in the lobby to see the refreshed playlist.

## C. Audio timing note

The current alpha stops music at the end of each round and starts the selected track again for the next round. It currently restarts that song from the beginning rather than resuming at the exact same timestamp. A dedicated client-side music player is needed for exact timestamp-preserving pause/resume.


A ready-to-fill resource-pack template is distributed separately as `BlockParty-Custom-Music-Pack-1.21.1.zip`.
