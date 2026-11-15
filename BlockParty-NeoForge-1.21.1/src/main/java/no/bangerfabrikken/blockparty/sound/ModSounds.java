package no.bangerfabrikken.blockparty.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import no.bangerfabrikken.blockparty.BlockPartyMod;

/** Sound events shipped inside the mod; no separate resource pack is required. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, BlockPartyMod.MOD_ID);

    /** Separate built-in event namespace; audio files remain packaged in this mod JAR. */
    public static final String MUSIC_NAMESPACE = "blockparty_music";
    public static final DeferredRegister<SoundEvent> MUSIC_SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MUSIC_NAMESPACE);

    public static final DeferredHolder<SoundEvent, SoundEvent> ROUND_START = sound("round_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROUND_END = sound("round_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOR_REVEAL = sound("color_reveal");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELIMINATION = sound("elimination");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEACON_SPAWN = sound("beacon_spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWERUP_PICKUP = sound("powerup_pickup");
    public static final DeferredHolder<SoundEvent, SoundEvent> VICTORY = sound("victory");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_BANGERFABRIKKEN = musicSound("music_bangerfabrikken");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ODIN_MOON = musicSound("music_odin_moon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_STILLE_ANDERS = musicSound("music_stille_anders");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HJULENE_OLLI = musicSound("music_hjulene_pa_bussen_olli");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ODIN_TERRORIST = musicSound("music_odin_terrorist");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_EXIGE_2014_LAUNCHPAD_MIX = musicSound("music_exige_2014_launchpad_mix");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HAU_HAU_HAU = musicSound("music_hau_hau_hau");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FEST_HOS_TOBY = musicSound("music_fest_hos_toby");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_KONFLIKTPARTIET = musicSound("music_konfliktpartiet");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HMS_ER_BEST = musicSound("music_hms_er_best");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_STIANORWAY_DAMER_OBJEKTER = musicSound("music_stianorway_damer_objekter");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_STIANORWAY_VI_MAA_IKKE_SI_ET_ORD = musicSound("music_stianorway_vi_maa_ikke_si_et_ord");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_LUXUS_PLASTISK_KIRURGI = musicSound("music_luxus_leverpostei_plastisk_kirurgi");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_KAPTEIN_SABELTANN_PINKYS_SANG = musicSound("music_kaptein_sabeltann_pinkys_sang");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_LUXUS_195 = musicSound("music_luxus_leverpostei_195");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_LUXUS_SUR_MUS = musicSound("music_luxus_leverpostei_sur_mus");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_OINGO_BOINGO_LITTLE_GIRLS_PEDOBEAR = musicSound("music_oingo_boingo_little_girls_pedobear");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_INNI_HODET_DITT = musicSound("music_inni_hodet_ditt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_AMBIENT = musicSound("ambient_christmas");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_ANDY_MOST_WONDERFUL = musicSound("music_andy_most_wonderful");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_MARIAH_ALL_I_WANT = musicSound("music_mariah_all_i_want");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_PREBZ_NISSEN = musicSound("music_prebz_nissen_skarpe");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_PREBZ_LA_DET_SNO = musicSound("music_prebz_la_det_sno");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_WHAM_LAST_CHRISTMAS = musicSound("music_wham_last_christmas");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_BOBBY_JINGLE_BELL = musicSound("music_bobby_jingle_bell_rock");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_TRAVELLIN_SPIKK = musicSound("music_travellin_spikk_a_bitte_little");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_XMAS_PER_ASPLIN_SPIKKE = musicSound("music_per_asplin_spikke_sage_lime_banke");

    /** Eight pre-registered extension slots. Add tracks through music_playlist.json without touching Java. */
    public static final java.util.List<DeferredHolder<SoundEvent, SoundEvent>> CUSTOM_MUSIC_SOUNDS =
            java.util.stream.IntStream.rangeClosed(1, 8)
                    .mapToObj(index -> musicSound("music_custom_" + index))
                    .toList();

    private ModSounds() { }

    private static DeferredHolder<SoundEvent, SoundEvent> musicSound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MUSIC_NAMESPACE, name);
        return MUSIC_SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BlockPartyMod.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
        MUSIC_SOUND_EVENTS.register(modEventBus);
    }
}
