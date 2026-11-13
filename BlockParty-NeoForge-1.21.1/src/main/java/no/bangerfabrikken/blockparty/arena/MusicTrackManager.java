package no.bangerfabrikken.blockparty.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import no.bangerfabrikken.blockparty.BlockPartyMod;
import no.bangerfabrikken.blockparty.sound.ModSounds;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Loads the BlockParty playlist from one JSON manifest instead of hard-coded Java entries. */
public final class MusicTrackManager {
    private static final String PLAYLIST_RESOURCE = "/assets/blockparty/music_playlist.json";
    private static final Map<String, Track> TRACKS = new LinkedHashMap<>();

    private MusicTrackManager() { }

    public record Track(String id, String name, String artist, int bpm, double durationSeconds,
                        ResourceLocation soundId) {
        /** Audio playback is advanced in server ticks, each representing 1/20 second. */
        public int durationTicks() {
            return Math.max(1, (int) Math.round(durationSeconds * 20.0));
        }
    }

    /** Loads all enabled built-in and custom tracks from the bundled playlist manifest. */
    public static synchronized void load(MinecraftServer server) {
        TRACKS.clear();
        Properties durations = loadBundledDurations();

        try (InputStream input = MusicTrackManager.class.getResourceAsStream(PLAYLIST_RESOURCE)) {
            if (input == null) {
                BlockPartyMod.LOGGER.error("BlockParty music playlist is missing: {}", PLAYLIST_RESOURCE);
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray entries = root.getAsJsonArray("tracks");
            if (entries == null) {
                BlockPartyMod.LOGGER.error("BlockParty music playlist has no 'tracks' array.");
                return;
            }

            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    BlockPartyMod.LOGGER.warn("Skipping invalid non-object entry in BlockParty music playlist.");
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String id = readString(entry, "id");
                String name = readString(entry, "name");
                String artist = readString(entry, "artist");

                if (!id.matches("[a-z0-9_]{1,48}") || name.isBlank() || artist.isBlank()) {
                    BlockPartyMod.LOGGER.warn("Skipping invalid BlockParty music entry: id='{}', name='{}'.", id, name);
                    continue;
                }
                if (TRACKS.containsKey(id)) {
                    BlockPartyMod.LOGGER.warn("Skipping duplicate BlockParty music track id '{}'.", id);
                    continue;
                }

                ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath(
                        ModSounds.MUSIC_NAMESPACE, "music_" + id);
                if (BuiltInRegistries.SOUND_EVENT.getOptional(soundId).isEmpty()) {
                    BlockPartyMod.LOGGER.warn("Skipping playlist track '{}' because sound event '{}' is not registered.",
                            id, soundId);
                    continue;
                }

                int bpm = readInt(entry, "bpm", 0);
                if (bpm < 0 || bpm > 400) bpm = 0;
                double duration = duration(durations, id, 180.0);
                TRACKS.put(id, new Track(id, name, artist, bpm, duration, soundId));
            }
        } catch (Exception exception) {
            BlockPartyMod.LOGGER.error("Could not load BlockParty music playlist.", exception);
        }

        BlockPartyMod.LOGGER.info("Loaded {} BlockParty music track(s) from the playlist manifest.", TRACKS.size());
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement value = entry.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        try {
            JsonElement value = entry.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Properties loadBundledDurations() {
        Properties durations = new Properties();
        try (InputStream input = MusicTrackManager.class.getResourceAsStream(
                "/assets/blockparty/music_durations.properties")) {
            if (input == null) {
                BlockPartyMod.LOGGER.warn("Bundled music duration manifest is missing; using fallback durations.");
                return durations;
            }
            durations.load(input);
        } catch (Exception exception) {
            BlockPartyMod.LOGGER.error("Could not read bundled music durations; using fallback values.", exception);
        }
        return durations;
    }

    private static double duration(Properties durations, String id, double fallback) {
        String raw = durations.getProperty(id);
        if (raw == null) {
            BlockPartyMod.LOGGER.warn("No measured duration for BlockParty track '{}'; using fallback {}s.", id, fallback);
            return fallback;
        }
        try {
            double measured = Double.parseDouble(raw.trim());
            if (Double.isFinite(measured) && measured > 0.0 && measured <= 3600.0) return measured;
        } catch (NumberFormatException ignored) { }
        BlockPartyMod.LOGGER.warn("Invalid measured duration for BlockParty track '{}': {}; using fallback {}s.",
                id, raw, fallback);
        return fallback;
    }

    /** Prevents a literal section sign in a song title from being parsed as a legacy colour code. */
    public static MutableComponent displayNameComponent(String name) {
        return Component.literal(name.replace("§", "§​"));
    }

    public static synchronized List<Track> getTracks() { return List.copyOf(TRACKS.values()); }
    public static synchronized Track get(String id) { return TRACKS.get(id); }
}
