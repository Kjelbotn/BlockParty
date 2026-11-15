package no.bangerfabrikken.blockparty.client;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Experimental remote OGG player built on Minecraft's own JOrbis decoder and
 * OpenAL channel pool. It deliberately remains opt-in until verified in-game.
 */
public final class RemoteAudioEngine {
    private static volatile ChannelAccess.ChannelHandle activeHandle;
    private static volatile boolean paused;

    private RemoteAudioEngine() { }

    public static CompletableFuture<Boolean> play(Path oggFile) {
        if (oggFile == null || !Files.isRegularFile(oggFile)) {
            return CompletableFuture.completedFuture(false);
        }
        stop();
        try {
            ChannelAccess access = getChannelAccess();
            return access.createHandle(Library.Pool.STREAMING).thenApply(handle -> {
                if (handle == null) return false;
                try {
                    handle.execute(channel -> {
                        try {
                            InputStream input = Files.newInputStream(oggFile);
                            JOrbisAudioStream stream = new JOrbisAudioStream(input);
                            channel.setRelative(true);
                            channel.setVolume(1.0F);
                            channel.setPitch(1.0F);
                            channel.attachBufferStream(stream);
                            channel.play();
                            activeHandle = handle;
                            paused = false;
                            BlockPartyMod.LOGGER.info("Started experimental cached BlockParty OGG playback: {}", oggFile.getFileName());
                        } catch (Exception exception) {
                            BlockPartyMod.LOGGER.error("Could not decode cached BlockParty OGG {}.", oggFile, exception);
                            handle.release();
                        }
                    });
                    return true;
                } catch (RuntimeException exception) {
                    handle.release();
                    BlockPartyMod.LOGGER.error("Could not attach remote BlockParty audio channel.", exception);
                    return false;
                }
            }).exceptionally(error -> {
                BlockPartyMod.LOGGER.error("Could not allocate a Minecraft streaming audio channel for BlockParty.", error);
                return false;
            });
        } catch (ReflectiveOperationException | RuntimeException exception) {
            BlockPartyMod.LOGGER.error("Could not access Minecraft's native audio channel pool.", exception);
            return CompletableFuture.completedFuture(false);
        }
    }

    public static void pause() {
        ChannelAccess.ChannelHandle handle = activeHandle;
        if (handle == null || handle.isStopped() || paused) return;
        paused = true;
        handle.execute(channel -> channel.pause());
    }

    public static void resume() {
        ChannelAccess.ChannelHandle handle = activeHandle;
        if (handle == null || handle.isStopped() || !paused) return;
        paused = false;
        handle.execute(channel -> channel.unpause());
    }

    public static void stop() {
        ChannelAccess.ChannelHandle handle = activeHandle;
        activeHandle = null;
        paused = false;
        if (handle != null && !handle.isStopped()) handle.release();
    }

    private static ChannelAccess getChannelAccess() throws ReflectiveOperationException {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        Field engineField = findField(SoundManager.class, SoundEngine.class);
        engineField.setAccessible(true);
        SoundEngine engine = (SoundEngine) engineField.get(manager);
        Field accessField = findField(SoundEngine.class, ChannelAccess.class);
        accessField.setAccessible(true);
        return (ChannelAccess) accessField.get(engine);
    }

    private static Field findField(Class<?> owner, Class<?> fieldType) throws NoSuchFieldException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (fieldType.isAssignableFrom(field.getType())) return field;
            }
        }
        throw new NoSuchFieldException(owner.getName() + " -> " + fieldType.getName());
    }
}
