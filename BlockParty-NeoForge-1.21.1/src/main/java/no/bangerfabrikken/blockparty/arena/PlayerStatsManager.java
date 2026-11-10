package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Persistent per-world BlockParty progression and player records. */
public final class PlayerStatsManager {
    private static final String FILE_NAME = "blockparty-player-stats.properties";
    private static final int STATS_SCHEMA_VERSION = 2;
    private static final Map<UUID, PlayerRecord> PLAYERS = new HashMap<>();
    private static final int[] RANK_THRESHOLDS = {0, 100, 250, 500, 1_000, 2_500, 5_000, 10_000};
    private static final String[] RANK_NAMES = {
            "Rookie", "Bronze Dancer", "Silver Dancer", "Gold Dancer",
            "Platinum Dancer", "Diamond Dancer", "Superstar", "BlockParty Legend"
    };

    private PlayerStatsManager() { }

    public record PlayerRecord(UUID uuid, String name, int points, int currentStreak,
                               int bestStreak, int personalBest, int wins, int mvpAwards) { }
    public record Rank(String name, int threshold, int nextThreshold) {
        public boolean isMaxRank() { return nextThreshold < 0; }
        public int pointsToNext(int points) { return isMaxRank() ? 0 : Math.max(0, nextThreshold - points); }
    }

    public static synchronized void load(MinecraftServer server) {
        PLAYERS.clear();
        Path file = getFile(server);
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        boolean migrateLegacyRoundStreaks = false;
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            int schemaVersion = number(properties, "schema.version");
            migrateLegacyRoundStreaks = schemaVersion < STATS_SCHEMA_VERSION;
            for (String raw : properties.getProperty("player.ids", "").split(",")) {
                if (raw.isBlank()) continue;
                UUID uuid = UUID.fromString(raw.trim());
                String prefix = "player." + uuid + ".";
                PLAYERS.put(uuid, new PlayerRecord(uuid,
                        properties.getProperty(prefix + "name", "Unknown"),
                        number(properties, prefix + "points"),
                        migrateLegacyRoundStreaks ? 0 : number(properties, prefix + "streak"),
                        migrateLegacyRoundStreaks ? 0 : number(properties, prefix + "best_streak"),
                        number(properties, prefix + "personal_best"),
                        number(properties, prefix + "wins"),
                        number(properties, prefix + "mvp")));
            }
        } catch (Exception exception) {
            BlockPartyMod.LOGGER.error("Could not load BlockParty player stats from {}", file, exception);
        }
        if (migrateLegacyRoundStreaks) {
            // Persist the migration immediately so old round-based streaks cannot reappear.
            save(server);
            BlockPartyMod.LOGGER.info("Migrated legacy round-based streaks to match-win streaks.");
        }
        BlockPartyMod.LOGGER.info("Loaded {} BlockParty player profile(s).", PLAYERS.size());
    }

    private static int number(Properties properties, String key) {
        try { return Math.max(0, Integer.parseInt(properties.getProperty(key, "0"))); }
        catch (NumberFormatException ignored) { return 0; }
    }

    public static synchronized void save(MinecraftServer server) {
        Properties properties = new Properties();
        StringBuilder ids = new StringBuilder();
        for (PlayerRecord record : PLAYERS.values()) {
            if (ids.length() > 0) ids.append(',');
            ids.append(record.uuid());
            String prefix = "player." + record.uuid() + ".";
            properties.setProperty(prefix + "name", record.name());
            properties.setProperty(prefix + "points", Integer.toString(record.points()));
            properties.setProperty(prefix + "streak", Integer.toString(record.currentStreak()));
            properties.setProperty(prefix + "best_streak", Integer.toString(record.bestStreak()));
            properties.setProperty(prefix + "personal_best", Integer.toString(record.personalBest()));
            properties.setProperty(prefix + "wins", Integer.toString(record.wins()));
            properties.setProperty(prefix + "mvp", Integer.toString(record.mvpAwards()));
        }
        properties.setProperty("schema.version", Integer.toString(STATS_SCHEMA_VERSION));
        properties.setProperty("player.ids", ids.toString());
        Path file = getFile(server);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "BlockParty persistent player progression");
            }
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.error("Could not save BlockParty player stats to {}", file, exception);
        }
    }

    /**
     * Adds one permanent point for each round survived and updates the personal round record.
     * Deliberately does not change currentStreak/bestStreak: those count match victories only.
     */
    public static synchronized int recordRoundSurvived(MinecraftServer server, ServerPlayer player, int round) {
        PlayerRecord old = getOrCreate(player);
        int points = old.points() + 1;
        int personalBest = Math.max(old.personalBest(), round);
        PLAYERS.put(player.getUUID(), new PlayerRecord(player.getUUID(), player.getGameProfile().getName(),
                points, old.currentStreak(), old.bestStreak(), personalBest, old.wins(), old.mvpAwards()));
        save(server);
        return points;
    }

    public static synchronized void breakStreak(MinecraftServer server, UUID uuid) {
        PlayerRecord old = PLAYERS.get(uuid);
        if (old == null || old.currentStreak() == 0) return;
        PLAYERS.put(uuid, new PlayerRecord(uuid, old.name(), old.points(), 0, old.bestStreak(),
                old.personalBest(), old.wins(), old.mvpAwards()));
        save(server);
    }

    /** Records exactly one consecutive win for a completed match (never for an individual round). */
    public static synchronized void recordWin(MinecraftServer server, ServerPlayer player) {
        PlayerRecord old = getOrCreate(player);
        int winStreak = old.currentStreak() + 1;
        int bestWinStreak = Math.max(old.bestStreak(), winStreak);
        PLAYERS.put(player.getUUID(), new PlayerRecord(player.getUUID(), player.getGameProfile().getName(),
                old.points(), winStreak, bestWinStreak, old.personalBest(), old.wins() + 1, old.mvpAwards()));
        save(server);
    }

    public static synchronized void recordMvp(MinecraftServer server, ServerPlayer player) {
        PlayerRecord old = getOrCreate(player);
        PLAYERS.put(player.getUUID(), new PlayerRecord(player.getUUID(), player.getGameProfile().getName(),
                old.points(), old.currentStreak(), old.bestStreak(), old.personalBest(), old.wins(), old.mvpAwards() + 1));
        save(server);
    }

    private static PlayerRecord getOrCreate(ServerPlayer player) {
        return PLAYERS.computeIfAbsent(player.getUUID(), uuid -> new PlayerRecord(uuid,
                player.getGameProfile().getName(), 0, 0, 0, 0, 0, 0));
    }

    /**
     * Resets a player's persistent progression by exact, case-insensitive player name.
     * Online players without a previous record can also be reset/initialized.
     */
    public static synchronized boolean resetByName(MinecraftServer server, String requestedName) {
        if (requestedName == null || requestedName.isBlank()) return false;
        String target = requestedName.trim();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getGameProfile().getName().equalsIgnoreCase(target)) {
                PLAYERS.put(online.getUUID(), new PlayerRecord(online.getUUID(), online.getGameProfile().getName(),
                        0, 0, 0, 0, 0, 0));
                save(server);
                return true;
            }
        }
        for (PlayerRecord record : PLAYERS.values()) {
            if (record.name().equalsIgnoreCase(target)) {
                PLAYERS.put(record.uuid(), new PlayerRecord(record.uuid(), record.name(),
                        0, 0, 0, 0, 0, 0));
                save(server);
                return true;
            }
        }
        return false;
    }

    public static synchronized int getPoints(UUID uuid) { PlayerRecord r = PLAYERS.get(uuid); return r == null ? 0 : r.points(); }
    public static synchronized PlayerRecord getRecord(UUID uuid) { return PLAYERS.getOrDefault(uuid, new PlayerRecord(uuid, "Unknown", 0, 0, 0, 0, 0, 0)); }
    public static synchronized String getPlayerName(UUID uuid) { PlayerRecord r = PLAYERS.get(uuid); return r == null ? "Unknown" : r.name(); }

    public static Rank getRank(int points) {
        int safePoints = Math.max(0, points), index = 0;
        for (int i = 0; i < RANK_THRESHOLDS.length; i++) {
            if (safePoints >= RANK_THRESHOLDS[i]) index = i; else break;
        }
        return new Rank(RANK_NAMES[index], RANK_THRESHOLDS[index], index >= RANK_THRESHOLDS.length - 1 ? -1 : RANK_THRESHOLDS[index + 1]);
    }

    public static synchronized List<PlayerRecord> topPlayers(int limit) {
        List<PlayerRecord> sorted = new ArrayList<>(PLAYERS.values());
        sorted.sort(Comparator.comparingInt(PlayerRecord::points).reversed().thenComparing(PlayerRecord::name, String.CASE_INSENSITIVE_ORDER));
        return sorted.subList(0, Math.min(Math.max(0, limit), sorted.size()));
    }

    public static void sendLeaderboard(ServerPlayer player) {
        List<PlayerRecord> top = topPlayers(10);
        player.sendSystemMessage(Component.literal("BLOCKPARTY LEADERBOARD").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("TOP 10 DANCERS").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (top.isEmpty()) {
            player.sendSystemMessage(Component.literal("Ingen poeng registrert ennå. Overlev en runde for å få ditt første poeng!").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            PlayerRecord record = top.get(i);
            Rank rank = getRank(record.points());
            ChatFormatting place = i == 0 ? ChatFormatting.GOLD : i == 1 ? ChatFormatting.WHITE : i == 2 ? ChatFormatting.RED : ChatFormatting.GRAY;
            player.sendSystemMessage(Component.literal("#" + (i + 1) + " ").withStyle(place, ChatFormatting.BOLD)
                    .append(Component.literal(record.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal("  " + record.points() + " pts ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("[" + rank.name() + "]").withStyle(ChatFormatting.GREEN)));
        }
    }

    public static void sendProfile(ServerPlayer player) { sendPlayerRank(player); }

    public static void sendPlayerRank(ServerPlayer player) {
        PlayerRecord record = getRecord(player.getUUID());
        Rank rank = getRank(record.points());
        player.sendSystemMessage(Component.literal("YOUR BLOCKPARTY PROFILE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Points: " + record.points()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Rank: " + rank.name()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Win streak: " + record.currentStreak()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Best win streak: " + record.bestStreak()).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal("Personal best: " + record.personalBest() + "/" + Arena.MAX_ROUNDS + " rounds").withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("Wins: " + record.wins() + "  |  MVP awards: " + record.mvpAwards()).withStyle(ChatFormatting.GRAY));
        if (rank.isMaxRank()) player.sendSystemMessage(Component.literal("Du har nådd høyeste rank!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        else player.sendSystemMessage(Component.literal("Neste rank: " + rank.nextThreshold() + " poeng (" + rank.pointsToNext(record.points()) + " igjen)").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static Path getFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}
