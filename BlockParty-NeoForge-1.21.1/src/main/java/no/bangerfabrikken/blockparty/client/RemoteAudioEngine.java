package no.bangerfabrikken.blockparty.client;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Experimental remote OGG player built on Minecraft's native JOrbis decoder
 * and streaming OpenAL channel pool. Kept opt-in until verified in-game.
 */
public final class RemoteAudioEngine {
    private static final Object STATE_LOCK = new Object();
    private static final AtomicLong PLAY_GENERATION = new AtomicLong();
    private static ChannelAccess.ChannelHandle activeHandle;
    private static boolean paused;

    private RemoteAudioEngine() { }

    public static CompletableFuture<Boolean> play(Path oggFile) {
        long generation;
        ChannelAccess.ChannelHandle previous;
        synchronized (STATE_LOCK) {
            generation = PLAY_GENERATION.incrementAndGet();
            previous = activeHandle;
            activeHandle = null;
            paused = false;
        }
        releaseHandle(previous);
        if (oggFile == null || !Files.isRegularFile(oggFile)) {
            return CompletableFuture.completedFuture(false);
        }

        final ChannelAccess access;
        try {
            access = getChannelAccess();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            BlockPartyMod.LOGGER.error("Could not access Minecraft's native audio channel pool.", exception);
            return CompletableFuture.completedFuture(false);
        }

        return access.createHandle(Library.Pool.STREAMING).thenCompose(handle -> {
            if (handle == null) return CompletableFuture.completedFuture(false);
            if (PLAY_GENERATION.get() != generation) {
                handle.release();
                return CompletableFuture.completedFuture(false);
            }

            final InputStream input;
            final JOrbisAudioStream stream;
            try {
                input = Files.newInputStream(oggFile);
                try {
                    stream = new JOrbisAudioStream(input);
                } catch (IOException | RuntimeException exception) {
                    input.close();
                    throw exception;
                }
            } catch (IOException | RuntimeException exception) {
                handle.release();
                BlockPartyMod.LOGGER.error("Could not open cached BlockParty OGG {}.", oggFile, exception);
                return CompletableFuture.completedFuture(false);
            }

            CompletableFuture<Boolean> attached = new CompletableFuture<>();
            AtomicBoolean streamAttached = new AtomicBoolean(false);
            try {
                // ChannelHandle.execute runs on Minecraft's audio executor. Complete
                // only after the stream has actually been attached and playback started.
                handle.execute(channel -> {
                    synchronized (STATE_LOCK) {
                        if (PLAY_GENERATION.get() != generation || handle.isStopped()) {
                            closeQuietly(stream);
                            handle.release();
                            attached.complete(false);
                            return;
                        }
                        try {
                            channel.setRelative(true);
                            channel.setVolume(1.0F);
                            channel.setPitch(1.0F);
                            channel.attachBufferStream(stream);
                            streamAttached.set(true);
                            channel.play();
                            activeHandle = handle;
                            paused = false;
                            BlockPartyMod.LOGGER.info("Started experimental cached BlockParty OGG playback: {}",
                                    oggFile.getFileName());
                            attached.complete(true);
                        } catch (RuntimeException exception) {
                            if (!streamAttached.get()) closeQuietly(stream);
                            handle.release();
                            BlockPartyMod.LOGGER.error("Could not start cached BlockParty OGG {}.", oggFile, exception);
                            attached.complete(false);
                        }
                    }
                });
            } catch (RuntimeException exception) {
                if (!streamAttached.get()) closeQuietly(stream);
                handle.release();
                BlockPartyMod.LOGGER.error("Could not schedule cached BlockParty audio on Minecraft's audio executor.", exception);
                attached.complete(false);
            }
            return attached;
        }).exceptionally(error -> {
            BlockPartyMod.LOGGER.error("Could not allocate a Minecraft streaming audio channel for BlockParty.", error);
            return false;
        });
    }

    public static void pause() {
        ChannelAccess.ChannelHandle handle;
        synchronized (STATE_LOCK) {
            handle = activeHandle;
            if (handle == null || handle.isStopped() || paused) return;
            paused = true;
        }
        handle.execute(channel -> channel.pause());
    }

    public static void resume() {
        ChannelAccess.ChannelHandle handle;
        synchronized (STATE_LOCK) {
            handle = activeHandle;
            if (handle == null || handle.isStopped() || !paused) return;
            paused = false;
        }
        handle.execute(channel -> channel.unpause());
    }

    public static void stop() {
        ChannelAccess.ChannelHandle handle;
        synchronized (STATE_LOCK) {
            PLAY_GENERATION.incrementAndGet();
            handle = activeHandle;
            activeHandle = null;
            paused = false;
        }
        releaseHandle(handle);
    }

    private static void releaseHandle(ChannelAccess.ChannelHandle handle) {
        if (handle != null && !handle.isStopped()) handle.release();
    }

    private static void closeQuietly(JOrbisAudioStream stream) {
        try {
            stream.close();
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.warn("Could not close an unused BlockParty OGG stream.", exception);
        }
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
