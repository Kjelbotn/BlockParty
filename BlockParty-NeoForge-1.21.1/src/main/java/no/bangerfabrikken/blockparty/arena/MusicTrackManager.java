package no.bangerfabrikken.blockparty.arena;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import no.bangerfabrikken.blockparty.BlockPartyMod;
import no.bangerfabrikken.blockparty.sound.ModSounds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The official, built-in BlockParty playlist. */
public final class MusicTrackManager {
    private static final Map<String, Track> TRACKS = new LinkedHashMap<>();

    private MusicTrackManager() { }

    public record Track(String id, String name, String artist, int bpm, int durationSeconds,
                        Holder<SoundEvent> sound, ResourceLocation soundId) { }

    /** Kept as a server lifecycle hook; the official playlist is intentionally fixed. */
    public static synchronized void load(MinecraftServer server) {
        TRACKS.clear();
        add("bangerfabrikken", "BlockParty", "Bangerfabrikken", 128, 216,
                ModSounds.MUSIC_BANGERFABRIKKEN, "music_bangerfabrikken");
        add("odin_moon", "Odin er en alkis - Moon Edition", "Bangerfabrikken", 128, 120,
                ModSounds.MUSIC_ODIN_MOON, "music_odin_moon");
        add("stille_anders", "Stille Anders [Indiepop]", "Bangerfabrikken", 0, 212,
                ModSounds.MUSIC_STILLE_ANDERS, "music_stille_anders");
        add("hjulene_pa_bussen_olli", "Hjulene på bussen (til Olli)", "Bangerfabrikken", 0, 75,
                ModSounds.MUSIC_HJULENE_OLLI, "music_hjulene_pa_bussen_olli");
        add("odin_terrorist", "Odin er en terrorist", "Bangerfabrikken", 0, 177,
                ModSounds.MUSIC_ODIN_TERRORIST, "music_odin_terrorist");
        add("exige_2014_launchpad_mix", "2014 Launchpad Mix", "Exige", 0, 364,
                ModSounds.MUSIC_EXIGE_2014_LAUNCHPAD_MIX, "music_exige_2014_launchpad_mix");
        add("hau_hau_hau", "Hau Hau Hau", "Bangerfabrikken", 0, 111,
                ModSounds.MUSIC_HAU_HAU_HAU, "music_hau_hau_hau");
        add("fest_hos_toby", "Fest hos Toby", "Bangerfabrikken", 0, 146,
                ModSounds.MUSIC_FEST_HOS_TOBY, "music_fest_hos_toby");
        add("konfliktpartiet", "Konfliktpartiet", "Bangerfabrikken", 0, 53,
                ModSounds.MUSIC_KONFLIKTPARTIET, "music_konfliktpartiet");
        add("hms_er_best", "HMS er best!", "Bangerfabrikken", 0, 120,
                ModSounds.MUSIC_HMS_ER_BEST, "music_hms_er_best");
        BlockPartyMod.LOGGER.info("Loaded {} official BlockParty music track(s).", TRACKS.size());
    }

    private static void add(String id, String name, String artist, int bpm, int durationSeconds,
                            Holder<SoundEvent> sound, String eventPath) {
        ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath(BlockPartyMod.MOD_ID, eventPath);
        TRACKS.put(id, new Track(id, name, artist, bpm, durationSeconds, sound, soundId));
    }

    public static synchronized List<Track> getTracks() { return List.copyOf(TRACKS.values()); }
    public static synchronized Track get(String id) { return TRACKS.get(id); }
}
