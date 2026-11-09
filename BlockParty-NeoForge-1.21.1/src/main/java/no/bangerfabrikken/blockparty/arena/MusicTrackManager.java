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

    public record Track(String id, String name, Holder<SoundEvent> sound, ResourceLocation soundId) { }

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
        String configured = properties.getProperty("tracks", String.join(",", all.keySet()));
        List<String> enabled = new ArrayList<>();
        for (String raw : configured.split(",")) {
            String id = raw.trim().toLowerCase(java.util.Locale.ROOT);
            Track track = all.get(id);
            if (track == null) continue;
            String name = properties.getProperty("name." + id, track.name()).trim();
            if (name.isBlank()) name = track.name();
            enabled.add(id);
            all.put(id, new Track(id, name, track.sound(), track.soundId()));
        }
        TRACKS.clear();
        for (String id : enabled) TRACKS.put(id, all.get(id));
        if (TRACKS.isEmpty()) TRACKS.putAll(defaults());
        BlockPartyMod.LOGGER.info("Loaded {} BlockParty music track(s).", TRACKS.size());
    }

    private static Map<String, Track> defaults() {
        Map<String, Track> tracks = new LinkedHashMap<>();
        add(tracks, "neon_beat", "Neon Beat", ModSounds.MUSIC_NEON);
        add(tracks, "pixel_rush", "Pixel Rush", ModSounds.MUSIC_ARCADE);
        add(tracks, "night_drive", "Night Drive", ModSounds.MUSIC_RUSH);
        add(tracks, "custom_1", "Custom Track 1", ModSounds.MUSIC_CUSTOM_1);
        add(tracks, "custom_2", "Custom Track 2", ModSounds.MUSIC_CUSTOM_2);
        add(tracks, "custom_3", "Custom Track 3", ModSounds.MUSIC_CUSTOM_3);
        add(tracks, "custom_4", "Custom Track 4", ModSounds.MUSIC_CUSTOM_4);
        add(tracks, "custom_5", "Custom Track 5", ModSounds.MUSIC_CUSTOM_5);
        add(tracks, "custom_6", "Custom Track 6", ModSounds.MUSIC_CUSTOM_6);
        add(tracks, "custom_7", "Custom Track 7", ModSounds.MUSIC_CUSTOM_7);
        add(tracks, "custom_8", "Custom Track 8", ModSounds.MUSIC_CUSTOM_8);
        return tracks;
    }

    private static void add(Map<String, Track> target, String id, String name, Holder<SoundEvent> sound) {
        target.put(id, new Track(id, name, sound, ResourceLocation.fromNamespaceAndPath(BlockPartyMod.MOD_ID, "music_" + id)));
    }

    private static void writeTemplate(Path file) {
        Properties properties = new Properties();
        properties.setProperty("instructions", "Edit names and enabled track ids, then run /bp music reload. Replace custom_1.ogg to custom_8.ogg in your BlockParty resource pack.");
        properties.setProperty("tracks", "neon_beat,pixel_rush,night_drive,custom_1,custom_2,custom_3,custom_4,custom_5,custom_6,custom_7,custom_8");
        properties.setProperty("name.neon_beat", "Neon Beat");
        properties.setProperty("name.pixel_rush", "Pixel Rush");
        properties.setProperty("name.night_drive", "Night Drive");
        properties.setProperty("name.custom_1", "Custom Track 1");
        properties.setProperty("name.custom_2", "Custom Track 2");
        properties.setProperty("name.custom_3", "Custom Track 3");
        properties.setProperty("name.custom_4", "Custom Track 4");
        properties.setProperty("name.custom_5", "Custom Track 5");
        properties.setProperty("name.custom_6", "Custom Track 6");
        properties.setProperty("name.custom_7", "Custom Track 7");
        properties.setProperty("name.custom_8", "Custom Track 8");
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
