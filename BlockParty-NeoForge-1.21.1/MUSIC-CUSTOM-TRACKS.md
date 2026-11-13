# Add songs to the BlockParty playlist

BlockParty has **eight reserved custom song slots**. Adding a song only requires two files to be changed. You do not need to edit Java, `sounds.json`, the duration file, or the GitHub Actions workflow.

## Steps

1. Choose a free slot from `custom_1` to `custom_8`.
2. Replace that slot's OGG file. For example:
   `src/main/resources/assets/blockparty/sounds/music/custom_1.ogg`
3. Open `src/main/resources/assets/blockparty/music_playlist.json` and add an entry inside the `tracks` array:

```json
{
  "id": "custom_1",
  "name": "Song title",
  "artist": "Artist name",
  "bpm": 128
}
```

Use the exact same slot ID in both places. The ID must be one of `custom_1` through `custom_8`. Set `bpm` to `0` when it is unknown.

## What the build does automatically

- Normalizes the OGG to stereo Vorbis at 44.1 kHz.
- Measures the exact track duration for the match timer.
- Checks that the audio is decodable and not silent.
- Confirms that the track is connected to its registered sound event.
- Verifies that every enabled song is packaged in the final JAR.

The playlist currently contains 17 built-in songs. The eight custom slots allow up to 25 selectable songs in total. Unlisted custom slots are ignored by the playlist.
