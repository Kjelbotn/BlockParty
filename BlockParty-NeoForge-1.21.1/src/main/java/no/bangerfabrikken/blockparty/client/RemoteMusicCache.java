package no.bangerfabrikken.blockparty.client;

import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Downloads immutable BlockParty music assets into a local client cache. */
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
    private static final ConcurrentHashMap<String, CompletableFuture<Path>> IN_FLIGHT =
            new ConcurrentHashMap<>();

    private RemoteMusicCache() { }

    /** Returns a verified cached track, or null when it has not been downloaded yet. */
    public static Path getCached(String trackId) {
        if (trackId == null || !trackId.matches("[a-z0-9_]{1,48}")) return null;
        Path target = cacheDirectory().resolve(trackId + ".ogg");
        try {
            if (isValidOgg(target)) return target;
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.warn("Could not inspect cached BlockParty track {}.", trackId, exception);
        }
        return null;
    }

    /** Starts or joins a background cache download. Never blocks the client thread. */
    public static CompletableFuture<Path> warmUp(String trackId) {
        if (trackId == null || !trackId.matches("[a-z0-9_]{1,48}")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid track id"));
        }
        Path target = cacheDirectory().resolve(trackId + ".ogg");
        try {
            if (isValidOgg(target)) return CompletableFuture.completedFuture(target);
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.warn("Could not inspect cached BlockParty track {}.", trackId, exception);
            return CompletableFuture.failedFuture(exception);
        }

        // Publish the shared future before starting any work. This also handles
        // immediate setup failures without leaving a permanently failed map entry.
        CompletableFuture<Path> pending = new CompletableFuture<>();
        CompletableFuture<Path> existing = IN_FLIGHT.putIfAbsent(trackId, pending);
        if (existing != null) return existing;

        download(trackId, target).whenComplete((path, error) -> {
            if (error == null) pending.complete(path);
            else pending.completeExceptionally(error);
            IN_FLIGHT.remove(trackId, pending);
        });
        return pending;
    }

    private static CompletableFuture<Path> download(String trackId, Path target) {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + trackId + ".ogg"))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", "BlockParty-Minecraft-Mod/1.4.19")
                .GET().build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    try (InputStream body = response.body()) {
                        if (response.statusCode() != 200) {
                            throw new IllegalStateException("HTTP " + response.statusCode());
                        }
                        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                        if (declaredLength > MAX_TRACK_BYTES) {
                            throw new IllegalStateException("Remote music file exceeds the 100 MiB safety limit");
                        }

                        long written = 0L;
                        byte[] buffer = new byte[16 * 1024];
                        try (OutputStream output = Files.newOutputStream(temporary,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)) {
                            int read;
                            while ((read = body.read(buffer)) != -1) {
                                written += read;
                                if (written > MAX_TRACK_BYTES) {
                                    throw new IllegalStateException("Remote music stream exceeded the 100 MiB safety limit");
                                }
                                output.write(buffer, 0, read);
                            }
                        }

                        if (!isValidOgg(temporary)) {
                            throw new IllegalStateException("Downloaded file is not a valid-sized OGG stream");
                        }
                        moveAtomically(temporary, target);
                        BlockPartyMod.LOGGER.info("Cached remote BlockParty track {} ({} KiB).",
                                trackId, written / 1024);
                        return target;
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not validate or store BlockParty music cache", exception);
                    }
                })
                .whenComplete((result, error) -> {
                    if (error != null) {
                        try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                        BlockPartyMod.LOGGER.warn("Could not download remote BlockParty track {}. "
                                + "The bundled music player remains available.", trackId, error);
                    }
                });
    }

    private static boolean isValidOgg(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return false;
        long size = Files.size(file);
        if (size < 4 || size > MAX_TRACK_BYTES) return false;
        try (InputStream input = Files.newInputStream(file)) {
            return input.read() == 'O' && input.read() == 'g'
                    && input.read() == 'g' && input.read() == 'S';
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path cacheDirectory() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("blockparty").resolve("music-cache");
    }
}
