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

/** Loads independent main and Xmas playlists from bundled JSON manifests. */
public final class MusicTrackManager {
    private static final Map<String, Map<String, Track>> PLAYLISTS = new LinkedHashMap<>();

    private MusicTrackManager() { }

    public record Track(String id, String name, String artist, int bpm, double durationSeconds,
                        ResourceLocation soundId) {
        public int durationTicks() {
            return Math.max(1, (int) Math.round(durationSeconds * 20.0));
        }
    }

    public static synchronized void load(MinecraftServer server) {
        PLAYLISTS.clear();
        Properties durations = loadBundledDurations();
        loadPlaylist("main", "/assets/blockparty/music_playlist.json", durations);
        loadPlaylist("xmas", "/assets/blockparty/xmas_music_playlist.json", durations);
        BlockPartyMod.LOGGER.info("Loaded {} main and {} Xmas BlockParty music tracks.",
                getTracks("main").size(), getTracks("xmas").size());
    }

    private static void loadPlaylist(String playlistId, String resource, Properties durations) {
        Map<String, Track> tracks = new LinkedHashMap<>();
        PLAYLISTS.put(playlistId, tracks);
        try (InputStream input = MusicTrackManager.class.getResourceAsStream(resource)) {
            if (input == null) {
                BlockPartyMod.LOGGER.error("BlockParty {} playlist is missing: {}", playlistId, resource);
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray entries = root.getAsJsonArray("tracks");
            if (entries == null) {
                BlockPartyMod.LOGGER.error("BlockParty {} playlist has no 'tracks' array.", playlistId);
                return;
            }
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                String id = readString(entry, "id");
                String name = readString(entry, "name");
                String artist = readString(entry, "artist");
                if (!id.matches("[a-z0-9_]{1,48}") || name.isBlank() || artist.isBlank()) {
                    BlockPartyMod.LOGGER.warn("Skipping invalid {} music entry '{}'.", playlistId, id);
                    continue;
                }
                if (tracks.containsKey(id)) {
                    BlockPartyMod.LOGGER.warn("Skipping duplicate {} music track '{}'.", playlistId, id);
                    continue;
                }
                ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath(
                        ModSounds.MUSIC_NAMESPACE, "music_" + id);
                if (BuiltInRegistries.SOUND_EVENT.getOptional(soundId).isEmpty()) {
                    BlockPartyMod.LOGGER.warn("Skipping {} track '{}' because sound '{}' is not registered.",
                            playlistId, id, soundId);
                    continue;
                }
                int bpm = readInt(entry, "bpm", 0);
                if (bpm < 0 || bpm > 400) bpm = 0;
                tracks.put(id, new Track(id, name, artist, bpm, duration(durations, id, 180.0), soundId));
            }
        } catch (Exception exception) {
            BlockPartyMod.LOGGER.error("Could not load BlockParty {} playlist.", playlistId, exception);
        }
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement value = entry.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        try {
            JsonElement value = entry.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) { return fallback; }
    }

    private static Properties loadBundledDurations() {
        Properties durations = new Properties();
        try (InputStream input = MusicTrackManager.class.getResourceAsStream(
                "/assets/blockparty/music_durations.properties")) {
            if (input != null) durations.load(input);
        } catch (Exception exception) {
            BlockPartyMod.LOGGER.error("Could not read bundled music durations; using fallback values.", exception);
        }
        return durations;
    }

    private static double duration(Properties durations, String id, double fallback) {
        String raw = durations.getProperty(id);
        if (raw == null) return fallback;
        try {
            double measured = Double.parseDouble(raw.trim());
            if (Double.isFinite(measured) && measured > 0.0 && measured <= 3600.0) return measured;
        } catch (NumberFormatException ignored) { }
        return fallback;
    }

    public static MutableComponent displayNameComponent(String name) {
        return Component.literal(name.replace("§", "§​"));
    }

    /** Xmas arenas use their own catalogue; every other arena uses the main catalogue. */
    private static String playlistKey(String arenaId) {
        return "xmas".equalsIgnoreCase(arenaId) ? "xmas" : "main";
    }

    public static synchronized List<Track> getTracks(String arenaId) {
        return List.copyOf(PLAYLISTS.getOrDefault(playlistKey(arenaId), Map.of()).values());
    }

    public static synchronized Track get(String arenaId, String id) {
        return PLAYLISTS.getOrDefault(playlistKey(arenaId), Map.of()).get(id);
    }

    /** Compatibility accessors for integrations that explicitly need the main playlist. */
    public static synchronized List<Track> getTracks() { return getTracks("main"); }
    public static synchronized Track get(String id) { return get("main", id); }
}
