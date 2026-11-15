package no.bangerfabrikken.blockparty.client;

import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads immutable BlockParty music assets to the local client cache.
 *
 * This is the first, deliberately isolated step toward remote playback. The
 * existing Minecraft sound player remains the active playback path until the
 * custom audio engine has been implemented and verified.
 */
public final class RemoteMusicCache {
    private static final String REVISION = "de6188b409c247dd5418055228a92e199d2138b0";
    private static final String BASE_URL =
            "https://raw.githubusercontent.com/Kjelbotn/BlockParty/" + REVISION
                    + "/BlockParty-NeoForge-1.21.1/src/main/resources/assets/blockparty/sounds/music/";
    private static final long MAX_TRACK_BYTES = 100L * 1024L * 1024L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private RemoteMusicCache() { }

    /** Starts a background cache download. It never blocks the Minecraft client thread. */
    public static CompletableFuture<Path> warmUp(String trackId) {
        if (trackId == null || !trackId.matches("[a-z0-9_]{1,48}")) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid track id"));
        Path target = cacheDirectory().resolve(trackId + ".ogg");
        if (Files.isRegularFile(target)) {
            try {
                long size = Files.size(target);
                if (size > 4 && size <= MAX_TRACK_BYTES) return CompletableFuture.completedFuture(target);
                Files.deleteIfExists(target);
            } catch (IOException exception) {
                BlockPartyMod.LOGGER.warn("Could not inspect cached BlockParty track {}.", trackId, exception);
                return CompletableFuture.failedFuture(exception);
            }
        }
        if (!IN_FLIGHT.add(trackId)) return CompletableFuture.failedFuture(new IllegalStateException("Track download already in progress"));

        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + trackId + ".ogg"))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", "BlockParty-Minecraft-Mod/1.4.19")
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    byte[] data = response.body();
                    if (data.length < 4 || data.length > MAX_TRACK_BYTES
                            || data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S') {
                        throw new IllegalStateException("Downloaded file is not a valid-sized OGG stream");
                    }
                    writeAtomically(target, data);
                    BlockPartyMod.LOGGER.info("Cached remote BlockParty track {} ({} KiB).",
                            trackId, data.length / 1024);
                    return target;
                })
                .exceptionally(error -> {
                    BlockPartyMod.LOGGER.warn("Could not download remote BlockParty track {}. "
                            + "The bundled music player remains available.", trackId, error);
                    return null;
                })
                .whenComplete((ignored, error) -> IN_FLIGHT.remove(trackId));
    }

    private static Path cacheDirectory() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("blockparty").resolve("music-cache");
    }

    private static void writeAtomically(Path target, byte[] data) {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            throw new IllegalStateException("Could not write BlockParty music cache", exception);
        }
    }
}
