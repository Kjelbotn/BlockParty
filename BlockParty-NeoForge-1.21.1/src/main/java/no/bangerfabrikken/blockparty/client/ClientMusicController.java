package no.bangerfabrikken.blockparty.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import no.bangerfabrikken.blockparty.network.MusicControlPayload;
import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Owns the BlockParty music sound instance on each client.
 *
 * The sound itself remains a normal Minecraft streamed OGG sound. We pause its
 * individual OpenAL channel, rather than stopping/restarting the event, so the
 * decoder and playback cursor remain alive at the exact sample position.
 */
@EventBusSubscriber(modid = BlockPartyMod.MOD_ID, value = Dist.CLIENT)
public final class ClientMusicController {
    private static SoundInstance currentSound;
    private static boolean paused;
    private static boolean pauseStateApplied;
    private static boolean reflectionWarningLogged;
    private static ResourceLocation activeSoundId;
    private static int ticksWaitingForChannel;
    private static int playbackRetries;
    private static boolean playbackFailureLogged;

    private ClientMusicController() { }

    public static void handle(MusicControlPayload payload) {
        switch (payload.action()) {
            case MusicControlPayload.PLAY -> play(payload.soundId());
            case MusicControlPayload.PAUSE -> pause();
            case MusicControlPayload.RESUME -> resume();
            case MusicControlPayload.STOP -> stop();
            default -> BlockPartyMod.LOGGER.warn("Unknown BlockParty music action: {}", payload.action());
        }
    }

    private static void play(ResourceLocation soundId) {
        Minecraft minecraft = Minecraft.getInstance();
        stop();
        if (BuiltInRegistries.SOUND_EVENT.getOptional(soundId).isEmpty()) {
            BlockPartyMod.LOGGER.warn("BlockParty client is missing music sound event {}", soundId);
            return;
        }

        // Use the Records/Jukebox category, matching the previous BlockParty music setup.
        activeSoundId = soundId;
        playbackRetries = 0;
        ticksWaitingForChannel = 0;
        playbackFailureLogged = false;
        startSound(minecraft);
    }

    private static void startSound(Minecraft minecraft) {
        currentSound = new SimpleSoundInstance(
                activeSoundId, SoundSource.RECORDS, 1.0F, 1.0F, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true
        );
        paused = false;
        pauseStateApplied = true;
        minecraft.getSoundManager().play(currentSound);
        BlockPartyMod.LOGGER.info("Requested built-in BlockParty music event {} (attempt {}).",
                activeSoundId, playbackRetries + 1);
    }

    private static void pause() {
        if (currentSound == null) return;
        paused = true;
        pauseStateApplied = false;
        BlockPartyMod.LOGGER.info("Pausing BlockParty music channel at floor collapse.");
        applyPauseState();
    }

    private static void resume() {
        if (currentSound == null) return;
        paused = false;
        pauseStateApplied = false;
        BlockPartyMod.LOGGER.info("Resuming BlockParty music channel for next round.");
        applyPauseState();
    }

    private static void stop() {
        if (currentSound != null) Minecraft.getInstance().getSoundManager().stop(currentSound);
        currentSound = null;
        activeSoundId = null;
        ticksWaitingForChannel = 0;
        playbackRetries = 0;
        playbackFailureLogged = false;
        paused = false;
        pauseStateApplied = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (currentSound == null) return;
        if (!paused && !playbackFailureLogged) {
            ChannelAccess.ChannelHandle handle = findCurrentChannel();
            if (handle != null && !handle.isStopped()) {
                ticksWaitingForChannel = 0;
            } else if (++ticksWaitingForChannel >= 60) {
                if (playbackRetries < 2 && activeSoundId != null) {
                    playbackRetries++;
                    BlockPartyMod.LOGGER.warn("BlockParty music event {} has not opened an audio channel; retrying ({}/2).",
                            activeSoundId, playbackRetries);
                    startSound(Minecraft.getInstance());
                    ticksWaitingForChannel = 0;
                } else {
                    playbackFailureLogged = true;
                    BlockPartyMod.LOGGER.error("BlockParty music event {} did not start after 3 attempts. Check the bundled OGG and sound event mapping.",
                            activeSoundId);
                }
            }
        }
        if (!pauseStateApplied) applyPauseState();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    private static void applyPauseState() {
        if (currentSound == null || pauseStateApplied) return;
        ChannelAccess.ChannelHandle handle = findCurrentChannel();
        if (handle == null || handle.isStopped()) return;

        handle.execute(channel -> {
            if (paused) channel.pause();
            else channel.unpause();
        });
        pauseStateApplied = true;
    }

    @SuppressWarnings("unchecked")
    private static ChannelAccess.ChannelHandle findCurrentChannel() {
        try {
            SoundManager manager = Minecraft.getInstance().getSoundManager();
            Field engineField = null;
            for (Field field : SoundManager.class.getDeclaredFields()) {
                if (SoundEngine.class.isAssignableFrom(field.getType())) {
                    engineField = field;
                    break;
                }
            }
            if (engineField == null) throw new NoSuchFieldException("SoundManager -> SoundEngine");
            engineField.setAccessible(true);
            SoundEngine engine = (SoundEngine) engineField.get(manager);

            // Do not depend on a single obfuscated field name: mappings differ between
            // NeoForge/Minecraft builds. Find the live SoundInstance -> ChannelHandle map.
            for (Class<?> type = SoundEngine.class; type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object value = field.get(engine);
                    if (!(value instanceof Map<?, ?> channels)) continue;
                    Object handle = channels.get(currentSound);
                    if (handle instanceof ChannelAccess.ChannelHandle channelHandle) return channelHandle;
                }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!reflectionWarningLogged) {
                reflectionWarningLogged = true;
                BlockPartyMod.LOGGER.error("Could not access the active BlockParty music channel; pause/resume may not work.", exception);
            }
            return null;
        }
    }
}
