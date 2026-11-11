package no.bangerfabrikken.blockparty.arena;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import no.bangerfabrikken.blockparty.BlockPartyMod;
import no.bangerfabrikken.blockparty.sound.ModSounds;

import java.io.InputStream;
import java.util.Properties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The official, built-in BlockParty playlist. */
public final class MusicTrackManager {
    private static final Map<String, Track> TRACKS = new LinkedHashMap<>();

    private MusicTrackManager() { }

    public record Track(String id, String name, String artist, int bpm, double durationSeconds,
                        Holder<SoundEvent> sound, ResourceLocation soundId) {
        /** Audio playback is advanced in server ticks, each representing 1/20 second. */
        public int durationTicks() {
            return Math.max(1, (int) Math.round(durationSeconds * 20.0));
        }
    }

    /** Kept as a server lifecycle hook; the official playlist is intentionally fixed. */
    public static synchronized void load(MinecraftServer server) {
        TRACKS.clear();
        Properties durations = loadBundledDurations();
        add("bangerfabrikken", "BlockParty", "Bangerfabrikken", 128, duration(durations, "bangerfabrikken", 216.240),
                ModSounds.MUSIC_BANGERFABRIKKEN, "music_bangerfabrikken");
        add("odin_moon", "Odin er en alkis - Moon Edition", "Bangerfabrikken", 128, duration(durations, "odin_moon", 120.000),
                ModSounds.MUSIC_ODIN_MOON, "music_odin_moon");
        add("stille_anders", "Stille Anders [Indiepop]", "Bangerfabrikken", 0, duration(durations, "stille_anders", 211.960),
                ModSounds.MUSIC_STILLE_ANDERS, "music_stille_anders");
        add("hjulene_pa_bussen_olli", "Hjulene på bussen (til Olli)", "Bangerfabrikken", 0, duration(durations, "hjulene_pa_bussen_olli", 74.560),
                ModSounds.MUSIC_HJULENE_OLLI, "music_hjulene_pa_bussen_olli");
        add("odin_terrorist", "Odin er en terrorist", "Bangerfabrikken", 0, duration(durations, "odin_terrorist", 176.800),
                ModSounds.MUSIC_ODIN_TERRORIST, "music_odin_terrorist");
        add("exige_2014_launchpad_mix", "2014 Launchpad Mix", "Exige", 0, duration(durations, "exige_2014_launchpad_mix", 364.042449),
                ModSounds.MUSIC_EXIGE_2014_LAUNCHPAD_MIX, "music_exige_2014_launchpad_mix");
        add("hau_hau_hau", "Hau Hau Hau", "Bangerfabrikken", 0, duration(durations, "hau_hau_hau", 110.999979),
                ModSounds.MUSIC_HAU_HAU_HAU, "music_hau_hau_hau");
        add("fest_hos_toby", "Fest hos Toby", "Bangerfabrikken", 0, duration(durations, "fest_hos_toby", 145.840167),
                ModSounds.MUSIC_FEST_HOS_TOBY, "music_fest_hos_toby");
        add("konfliktpartiet", "Konfliktpartiet", "Bangerfabrikken", 0, duration(durations, "konfliktpartiet", 53.400),
                ModSounds.MUSIC_KONFLIKTPARTIET, "music_konfliktpartiet");
        add("hms_er_best", "HMS er best!", "Bangerfabrikken", 0, duration(durations, "hms_er_best", 119.797551),
                ModSounds.MUSIC_HMS_ER_BEST, "music_hms_er_best");
        BlockPartyMod.LOGGER.info("Loaded {} official BlockParty music track(s).", TRACKS.size());
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
        BlockPartyMod.LOGGER.warn("Invalid measured duration for BlockParty track '{}': {}; using fallback {}s.", id, raw, fallback);
        return fallback;
    }

    private static void add(String id, String name, String artist, int bpm, double durationSeconds,
                            Holder<SoundEvent> sound, String eventPath) {
        ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath(ModSounds.MUSIC_NAMESPACE, eventPath);
        TRACKS.put(id, new Track(id, name, artist, bpm, durationSeconds, sound, soundId));
    }

    public static synchronized List<Track> getTracks() { return List.copyOf(TRACKS.values()); }
    public static synchronized Track get(String id) { return TRACKS.get(id); }
}
