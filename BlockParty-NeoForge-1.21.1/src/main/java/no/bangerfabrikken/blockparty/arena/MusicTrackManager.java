package no.bangerfabrikken.blockparty.arena;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.storage.LevelResource;
import no.bangerfabrikken.blockparty.BlockPartyMod;
import no.bangerfabrikken.blockparty.sound.ModSounds;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** World-local playlist. Custom slots are replaceable through a resource pack. */
public final class MusicTrackManager {
    private static final String FILE_NAME = "blockparty-music.properties";
    private static final Map<String, Track> TRACKS = new LinkedHashMap<>();

    private MusicTrackManager() { }

    public record Track(String id, String name, String artist, int bpm, int durationSeconds,
                        Holder<SoundEvent> sound, ResourceLocation soundId) { }

    public static synchronized void load(MinecraftServer server) {
        Properties properties = new Properties();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        if (!Files.exists(file)) writeTemplate(file);
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.error("Could not read BlockParty music catalog {}", file, exception);
        }

        Map<String, Track> all = defaults();
        String configured = properties.getProperty("tracks", "neon_beat,pixel_rush,night_drive");
        List<String> enabled = new ArrayList<>();
        for (String raw : configured.split(",")) {
            String id = raw.trim().toLowerCase(java.util.Locale.ROOT);
            Track track = all.get(id);
            if (track == null) continue;
            String name = properties.getProperty("name." + id, track.name()).trim();
            if (name.isBlank()) name = track.name();
            String artist = properties.getProperty("artist." + id, track.artist()).trim();
            if (artist.isBlank()) artist = track.artist();
            // Earlier alphas listed every unconfigured custom slot, which made the
            // default menu show copies of the built-in songs. Keep custom slots hidden
            // until the owner gives one a real title or artist.
            if (id.startsWith("custom_") && name.equals(track.name()) && artist.equals(track.artist())) continue;
            int bpm = track.bpm();
            int durationSeconds = track.durationSeconds();
            try {
                bpm = Integer.parseInt(properties.getProperty("bpm." + id, Integer.toString(track.bpm())).trim());
            } catch (NumberFormatException ignored) {
                BlockPartyMod.LOGGER.warn("Invalid BPM for BlockParty track '{}'; using {}.", id, track.bpm());
            }
            try {
                durationSeconds = Integer.parseInt(properties.getProperty("duration." + id,
                        Integer.toString(track.durationSeconds())).trim());
            } catch (NumberFormatException ignored) {
                BlockPartyMod.LOGGER.warn("Invalid duration for BlockParty track '{}'; using {} seconds.", id, track.durationSeconds());
            }
            bpm = Math.max(60, Math.min(180, bpm));
            durationSeconds = Math.max(5, Math.min(600, durationSeconds));
            enabled.add(id);
            all.put(id, new Track(id, name, artist, bpm, durationSeconds, track.sound(), track.soundId()));
        }
        TRACKS.clear();
        for (String id : enabled) TRACKS.put(id, all.get(id));
        if (TRACKS.isEmpty()) {
            TRACKS.put("neon_beat", all.get("neon_beat"));
            TRACKS.put("pixel_rush", all.get("pixel_rush"));
            TRACKS.put("night_drive", all.get("night_drive"));
        }
        BlockPartyMod.LOGGER.info("Loaded {} BlockParty music track(s).", TRACKS.size());
    }

    private static Map<String, Track> defaults() {
        Map<String, Track> tracks = new LinkedHashMap<>();
        add(tracks, "neon_beat", "Neon Beat", "BlockParty OST", 128, 17, ModSounds.MUSIC_NEON);
        add(tracks, "pixel_rush", "Pixel Rush", "BlockParty OST", 140, 16, ModSounds.MUSIC_ARCADE);
        add(tracks, "night_drive", "Night Drive", "BlockParty OST", 112, 18, ModSounds.MUSIC_RUSH);
        add(tracks, "custom_1", "Custom Track 1", "Your Artist", 128, 17, ModSounds.MUSIC_CUSTOM_1);
        add(tracks, "custom_2", "Custom Track 2", "Your Artist", 128, 16, ModSounds.MUSIC_CUSTOM_2);
        add(tracks, "custom_3", "Custom Track 3", "Your Artist", 128, 18, ModSounds.MUSIC_CUSTOM_3);
        add(tracks, "custom_4", "Custom Track 4", "Your Artist", 128, 17, ModSounds.MUSIC_CUSTOM_4);
        add(tracks, "custom_5", "Custom Track 5", "Your Artist", 128, 17, ModSounds.MUSIC_CUSTOM_5);
        add(tracks, "custom_6", "Custom Track 6", "Your Artist", 128, 17, ModSounds.MUSIC_CUSTOM_6);
        add(tracks, "custom_7", "Custom Track 7", "Your Artist", 128, 17, ModSounds.MUSIC_CUSTOM_7);
        add(tracks, "custom_8", "Custom Track 8", "Your Artist", 128, 17, ModSounds.MUSIC_CUSTOM_8);
        return tracks;
    }

    private static void add(Map<String, Track> target, String id, String name, String artist, int bpm,
                            int durationSeconds, Holder<SoundEvent> sound) {
        target.put(id, new Track(id, name, artist, bpm, durationSeconds, sound,
                ResourceLocation.fromNamespaceAndPath(BlockPartyMod.MOD_ID, "music_" + id)));
    }

    private static void writeTemplate(Path file) {
        Properties properties = new Properties();
        properties.setProperty("instructions", "Edit name.<id>, artist.<id>, bpm.<id> and tracks, then run /bp music reload. Replace custom_1.ogg to custom_8.ogg in your BlockParty resource pack.");
        properties.setProperty("tracks", "neon_beat,pixel_rush,night_drive");
        properties.setProperty("name.neon_beat", "Neon Beat");
        properties.setProperty("artist.neon_beat", "BlockParty OST");
        properties.setProperty("bpm.neon_beat", "128");
        properties.setProperty("duration.neon_beat", "17");
        properties.setProperty("name.pixel_rush", "Pixel Rush");
        properties.setProperty("artist.pixel_rush", "BlockParty OST");
        properties.setProperty("bpm.pixel_rush", "140");
        properties.setProperty("duration.pixel_rush", "16");
        properties.setProperty("name.night_drive", "Night Drive");
        properties.setProperty("artist.night_drive", "BlockParty OST");
        properties.setProperty("bpm.night_drive", "112");
        properties.setProperty("duration.night_drive", "18");
        properties.setProperty("name.custom_1", "Custom Track 1");
        properties.setProperty("artist.custom_1", "Your Artist");
        properties.setProperty("bpm.custom_1", "128");
        properties.setProperty("duration.custom_1", "17");
        properties.setProperty("name.custom_2", "Custom Track 2");
        properties.setProperty("artist.custom_2", "Your Artist");
        properties.setProperty("bpm.custom_2", "128");
        properties.setProperty("duration.custom_2", "16");
        properties.setProperty("name.custom_3", "Custom Track 3");
        properties.setProperty("artist.custom_3", "Your Artist");
        properties.setProperty("bpm.custom_3", "128");
        properties.setProperty("duration.custom_3", "18");
        properties.setProperty("name.custom_4", "Custom Track 4");
        properties.setProperty("artist.custom_4", "Your Artist");
        properties.setProperty("bpm.custom_4", "128");
        properties.setProperty("duration.custom_4", "17");
        properties.setProperty("name.custom_5", "Custom Track 5");
        properties.setProperty("artist.custom_5", "Your Artist");
        properties.setProperty("bpm.custom_5", "128");
        properties.setProperty("duration.custom_5", "17");
        properties.setProperty("name.custom_6", "Custom Track 6");
        properties.setProperty("artist.custom_6", "Your Artist");
        properties.setProperty("bpm.custom_6", "128");
        properties.setProperty("duration.custom_6", "17");
        properties.setProperty("name.custom_7", "Custom Track 7");
        properties.setProperty("artist.custom_7", "Your Artist");
        properties.setProperty("bpm.custom_7", "128");
        properties.setProperty("duration.custom_7", "17");
        properties.setProperty("name.custom_8", "Custom Track 8");
        properties.setProperty("artist.custom_8", "Your Artist");
        properties.setProperty("bpm.custom_8", "128");
        properties.setProperty("duration.custom_8", "17");
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "BlockParty music voting playlist");
            }
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.error("Could not create BlockParty music catalog {}", file, exception);
        }
    }

    public static synchronized List<Track> getTracks() { return List.copyOf(TRACKS.values()); }
    public static synchronized Track get(String id) { return TRACKS.get(id); }
}
