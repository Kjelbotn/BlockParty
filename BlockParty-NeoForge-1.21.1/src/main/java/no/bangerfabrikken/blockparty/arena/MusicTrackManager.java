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
