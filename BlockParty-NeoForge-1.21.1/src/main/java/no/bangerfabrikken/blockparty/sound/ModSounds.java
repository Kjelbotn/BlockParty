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

    public static final DeferredHolder<SoundEvent, SoundEvent> ROUND_START = sound("round_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROUND_END = sound("round_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOR_REVEAL = sound("color_reveal");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELIMINATION = sound("elimination");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEACON_SPAWN = sound("beacon_spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWERUP_PICKUP = sound("powerup_pickup");
    public static final DeferredHolder<SoundEvent, SoundEvent> VICTORY = sound("victory");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_NEON = sound("music_neon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ARCADE = sound("music_arcade");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_RUSH = sound("music_rush");

    private ModSounds() { }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BlockPartyMod.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
