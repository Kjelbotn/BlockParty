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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Downloads immutable BlockParty music assets into a local client cache. */
public final class RemoteMusicCache {
    private static final String REVISION = "de6188b409c247dd5418055228a92e199d2138b0";
    private static final String BASE_URL =
            "https://raw.githubusercontent.com/Kjelbotn/BlockParty/" + REVISION
                    + "/BlockParty-NeoForge-1.21.1/src/main/resources/assets/blockparty/sounds/music/";
    private record ExpectedAsset(long size, String gitBlobSha1) { }

    // GitHub's immutable Git blob metadata for every selectable track in the
    // main and Xmas playlists. This verifies the complete download, not just OggS.
    private static final Map<String, ExpectedAsset> EXPECTED_ASSETS = Map.ofEntries(
            Map.entry("andy_most_wonderful", new ExpectedAsset(2423318L, "38070f41cf19b564baa4e10aa2c6a71c90109db0")),
            Map.entry("bangerfabrikken", new ExpectedAsset(2898644L, "697ca134949d0a16ebfd3b36e4e482445deb9100")),
            Map.entry("bobby_jingle_bell_rock", new ExpectedAsset(1706183L, "9de781ec2fbce60a26ce91557ddc3201a855516a")),
            Map.entry("exige_2014_launchpad_mix", new ExpectedAsset(4872067L, "8095f1c637e276dfdeffd473064749f86fd55069")),
            Map.entry("fest_hos_toby", new ExpectedAsset(1994087L, "b58bbf10f8bb1b57f14bd4b6f5278bcadc672637")),
            Map.entry("hau_hau_hau", new ExpectedAsset(1493175L, "dd9f6e67b611c4a7f71a99078ebdb6e54ffb3d33")),
            Map.entry("hjulene_pa_bussen_olli", new ExpectedAsset(982319L, "1667c87983472cb45d77882944f00da8e00dd86c")),
            Map.entry("hms_er_best", new ExpectedAsset(1629302L, "b77d5ecb23e55a7c068c8857ab4bd6c88187bd75")),
            Map.entry("inni_hodet_ditt", new ExpectedAsset(2804851L, "50723576eb9549efe1b357e4c8090a0ae477a8c6")),
            Map.entry("kaptein_sabeltann_pinkys_sang", new ExpectedAsset(1734237L, "43a8b4eab34998bf674983670a3ecd2708f4eb94")),
            Map.entry("konfliktpartiet", new ExpectedAsset(645264L, "fcce620f2346c012bb1100e99b58562eeea4ee91")),
            Map.entry("luxus_leverpostei_195", new ExpectedAsset(2479726L, "c8ac57d65020bacb68ceb14b1fa6f2c5f863d4a7")),
            Map.entry("luxus_leverpostei_plastisk_kirurgi", new ExpectedAsset(2499226L, "74f2a7725738f9ec1a6b7e498dc5443ae85f3aed")),
            Map.entry("luxus_leverpostei_sur_mus", new ExpectedAsset(2735981L, "90263ee0578d5907b422c9d421c0d3bd029d3e77")),
            Map.entry("mariah_all_i_want", new ExpectedAsset(3840139L, "c5cb9771e6f568fe393b15705ab60fca47f58c3d")),
            Map.entry("odin_alkis_dubstep", new ExpectedAsset(1342766L, "8a3131dd5066be35fb98e67e8b20eecb8ade6086")),
            Map.entry("odin_jul", new ExpectedAsset(935627L, "0f71178cb75522dd7bbd274374d9ea5c741fd031")),
            Map.entry("odin_moon", new ExpectedAsset(1553920L, "02709841769540c91ca67cb23710848362f93eac")),
            Map.entry("odin_terrorist", new ExpectedAsset(2319945L, "36c5b41f4a2fdcd59ece320d31fc567da7e1e7d7")),
            Map.entry("oingo_boingo_little_girls_pedobear", new ExpectedAsset(1098975L, "fcadb1cb30202dd449c6221dba28fb42113c3716")),
            Map.entry("per_asplin_spikke_sage_lime_banke", new ExpectedAsset(3054647L, "665e5d41099029cbaabb4b301f78509694fb071e")),
            Map.entry("prebz_la_det_sno", new ExpectedAsset(3049623L, "b5edb0bd13061b478836c12114f07e24f0c02527")),
            Map.entry("prebz_nissen_skarpe", new ExpectedAsset(3028562L, "a0c4a8fa2dd4e9c1d76498525525f744bca55aa2")),
            Map.entry("stianorway_damer_objekter", new ExpectedAsset(992469L, "3fd1a10c24951f61c4723a2fef1d2c30b2aab52d")),
            Map.entry("stianorway_vi_maa_ikke_si_et_ord", new ExpectedAsset(2063905L, "ee029719c256e2679a192b3a82ad7c98d6e59b3e")),
            Map.entry("stille_anders", new ExpectedAsset(2823123L, "bf64b22d8d626cd187a26e5745aa897d4a6ffe2b")),
            Map.entry("travellin_spikk_a_bitte_little", new ExpectedAsset(2100433L, "fc2cf489dc325db83fb7177b082c928c0f1431ee")),
            Map.entry("wham_last_christmas", new ExpectedAsset(3861456L, "e2094340b43061dae0382efd4def865a530fdc24"))
    );
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
        ExpectedAsset expected = EXPECTED_ASSETS.get(trackId);
        if (expected == null) return null;
        Path target = cacheDirectory().resolve(trackId + ".ogg");
        Path marker = target.resolveSibling(target.getFileName() + ".sha1");
        try {
            if (isValidCachedAsset(target, marker, expected)) return target;
            Files.deleteIfExists(target);
            Files.deleteIfExists(marker);
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
        ExpectedAsset expected = EXPECTED_ASSETS.get(trackId);
        if (expected == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown BlockParty track id"));
        }
        Path target = cacheDirectory().resolve(trackId + ".ogg");
        Path marker = target.resolveSibling(target.getFileName() + ".sha1");
        try {
            if (isValidCachedAsset(target, marker, expected)) return CompletableFuture.completedFuture(target);
            Files.deleteIfExists(target);
            Files.deleteIfExists(marker);
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.warn("Could not inspect cached BlockParty track {}.", trackId, exception);
            return CompletableFuture.failedFuture(exception);
        }

        // Publish the shared future before starting any work. This also handles
        // immediate setup failures without leaving a permanently failed map entry.
        CompletableFuture<Path> pending = new CompletableFuture<>();
        CompletableFuture<Path> existing = IN_FLIGHT.putIfAbsent(trackId, pending);
        if (existing != null) return existing;

        download(trackId, target, marker, expected).whenComplete((path, error) -> {
            if (error == null) pending.complete(path);
            else pending.completeExceptionally(error);
            IN_FLIGHT.remove(trackId, pending);
        });
        return pending;
    }

    private static CompletableFuture<Path> download(String trackId, Path target, Path marker, ExpectedAsset expected) {
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
                        MessageDigest digest = newGitBlobDigest(expected.size());
                        byte[] buffer = new byte[16 * 1024];
                        try (OutputStream output = Files.newOutputStream(temporary,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)) {
                            int read;
                            while ((read = body.read(buffer)) != -1) {
                                written += read;
                                if (written > MAX_TRACK_BYTES || written > expected.size()) {
                                    throw new IllegalStateException("Remote music stream exceeded its expected size");
                                }
                                digest.update(buffer, 0, read);
                                output.write(buffer, 0, read);
                            }
                        }

                        String actualSha1 = HexFormat.of().formatHex(digest.digest());
                        if (written != expected.size() || !actualSha1.equals(expected.gitBlobSha1())) {
                            throw new IllegalStateException("Downloaded music failed its immutable Git blob checksum "
                                    + "(size " + written + "/" + expected.size() + ", SHA-1 " + actualSha1 + ")");
                        }
                        if (!isValidOgg(temporary)) {
                            throw new IllegalStateException("Downloaded file is not a valid OGG stream");
                        }
                        moveAtomically(temporary, target);
                        Files.writeString(marker, expected.gitBlobSha1(),
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE);
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

    private static boolean isValidCachedAsset(Path file, Path marker, ExpectedAsset expected) throws IOException {
        return Files.isRegularFile(file) && Files.size(file) == expected.size()
                && Files.isRegularFile(marker)
                && Files.readString(marker).trim().equals(expected.gitBlobSha1())
                && isValidOgg(file);
    }

    private static MessageDigest newGitBlobDigest(long size) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + size + "\0")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return digest;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        }
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
                .resolve("config").resolve("blockparty").resolve("music-cache")
                // A new immutable asset revision gets its own cache namespace, so a
                // future song update can never silently reuse an older OGG with the
                // same track id.
                .resolve(REVISION);
    }
}
