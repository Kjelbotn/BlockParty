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

    /**
     * Keep music event definitions in a dedicated resource namespace. This prevents
     * older server/client resource packs that override blockparty/sounds.json from
     * hiding newer music entries while leaving the rest of the mod untouched.
     */
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

    private ModSounds() { }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BlockPartyMod.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> musicSound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MUSIC_NAMESPACE, name);
        return MUSIC_SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
        MUSIC_SOUND_EVENTS.register(modEventBus);
    }
}
