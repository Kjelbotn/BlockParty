# Add your own BlockParty music

BlockParty has eight custom music slots. You do not need to rebuild the mod to change the audio files or the displayed song names.

## A. Add audio with a resource pack

Use the separate `BlockParty-Custom-Music-Pack-1.21.1-v0.6.0.zip` template. Inside the pack, place OGG audio files in:

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
tracks=neon_beat,pixel_rush,night_drive,custom_1,custom_2,custom_3
name.custom_1=Friday Night Mix
name.custom_2=My Chiptune Track
name.custom_3=Final Lap
```

`tracks` is the comma-separated list of IDs shown in the voting menu. Add `custom_1` through `custom_8` when you are ready to use those slots; remove an ID from the list to hide it. The built-in IDs are `neon_beat`, `pixel_rush` and `night_drive`. A custom slot is ignored until you give it a non-placeholder song name or artist.

For example, to enable three of your own songs, add their IDs to the `tracks` line, then set at least a title or artist for each slot:

```properties
tracks=neon_beat,pixel_rush,night_drive,custom_1,custom_2,custom_3
name.custom_1=Friday Night Mix
artist.custom_1=Kim's Playlist
bpm.custom_1=128
duration.custom_1=142
```

After saving the properties file, run this command in Minecraft as an admin:

```mcfunction
/bp music reload
```

Then right-click the Music Voting Book in the lobby to see the refreshed playlist.

## C. Audio timing and DJ effects

The winning track starts during the six-second DJ intro before round one. Music continues across rounds and restarts from the beginning after the configured `duration.<id>` value. This alpha does not pause and resume at an exact audio timestamp.

The `bpm.<id>` value controls the rhythm of the lobby/arena redstone-lamp pulses and particle effects. It is a tempo-based visual pulse, not audio waveform analysis, so set BPM close to the actual track tempo for the best synchronisation.


A ready-to-fill resource-pack template is distributed separately as `BlockParty-Custom-Music-Pack-1.21.1-v0.6.0.zip`.
