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

/** Persistent per-world BlockParty progression, ranks and leaderboard data. */
public final class PlayerStatsManager {
    private static final String FILE_NAME = "blockparty-player-stats.properties";
    private static final Map<UUID, PlayerRecord> PLAYERS = new HashMap<>();

    private static final int[] RANK_THRESHOLDS = {0, 100, 250, 500, 1_000, 2_500, 5_000, 10_000};
    private static final String[] RANK_NAMES = {
            "Rookie", "Bronze Dancer", "Silver Dancer", "Gold Dancer",
            "Platinum Dancer", "Diamond Dancer", "Superstar", "BlockParty Legend"
    };

    private PlayerStatsManager() { }

    public record PlayerRecord(UUID uuid, String name, int points) { }
    public record Rank(String name, int threshold, int nextThreshold) {
        public boolean isMaxRank() { return nextThreshold < 0; }
        public int pointsToNext(int points) { return isMaxRank() ? 0 : Math.max(0, nextThreshold - points); }
    }

    public static synchronized void load(MinecraftServer server) {
        PLAYERS.clear();
        Path file = getFile(server);
        if (!Files.exists(file)) {
            BlockPartyMod.LOGGER.info("No BlockParty player stats found yet.");
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            String ids = properties.getProperty("player.ids", "");
            for (String raw : ids.split(",")) {
                if (raw.isBlank()) continue;
                UUID uuid = UUID.fromString(raw.trim());
                String prefix = "player." + uuid + ".";
                String name = properties.getProperty(prefix + "name", "Unknown");
                int points = Math.max(0, Integer.parseInt(properties.getProperty(prefix + "points", "0")));
                PLAYERS.put(uuid, new PlayerRecord(uuid, name, points));
            }
        } catch (Exception exception) {
            BlockPartyMod.LOGGER.error("Could not load BlockParty player stats from {}", file, exception);
        }
        BlockPartyMod.LOGGER.info("Loaded {} BlockParty player profile(s).", PLAYERS.size());
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
        }
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

    /** Awards one permanent point for a round the player survives. */
    public static synchronized int awardRoundPoint(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerRecord old = PLAYERS.get(uuid);
        int nextPoints = (old == null ? 0 : old.points()) + 1;
        PLAYERS.put(uuid, new PlayerRecord(uuid, player.getGameProfile().getName(), nextPoints));
        save(server);
        return nextPoints;
    }

    public static synchronized int getPoints(UUID uuid) {
        PlayerRecord record = PLAYERS.get(uuid);
        return record == null ? 0 : record.points();
    }

    public static synchronized String getPlayerName(UUID uuid) {
        PlayerRecord record = PLAYERS.get(uuid);
        return record == null ? "Unknown" : record.name();
    }

    public static Rank getRank(int points) {
        int safePoints = Math.max(0, points);
        int index = 0;
        for (int i = 0; i < RANK_THRESHOLDS.length; i++) {
            if (safePoints >= RANK_THRESHOLDS[i]) index = i;
            else break;
        }
        int next = index >= RANK_THRESHOLDS.length - 1 ? -1 : RANK_THRESHOLDS[index + 1];
        return new Rank(RANK_NAMES[index], RANK_THRESHOLDS[index], next);
    }

    public static synchronized List<PlayerRecord> topPlayers(int limit) {
        List<PlayerRecord> sorted = new ArrayList<>(PLAYERS.values());
        sorted.sort(Comparator.comparingInt(PlayerRecord::points).reversed()
                .thenComparing(PlayerRecord::name, String.CASE_INSENSITIVE_ORDER));
        return sorted.subList(0, Math.min(Math.max(0, limit), sorted.size()));
    }

    public static void sendLeaderboard(ServerPlayer player) {
        List<PlayerRecord> top = topPlayers(10);
        player.sendSystemMessage(Component.literal("BLOCKPARTY LEADERBOARD")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("TOP 10 DANCERS")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (top.isEmpty()) {
            player.sendSystemMessage(Component.literal("Ingen poeng registrert ennå. Overlev en runde for å få ditt første poeng!")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            PlayerRecord record = top.get(i);
            ChatFormatting rankColor = i == 0 ? ChatFormatting.GOLD : i == 1 ? ChatFormatting.WHITE
                    : i == 2 ? ChatFormatting.RED : ChatFormatting.GRAY;
            Rank rank = getRank(record.points());
            Component line = Component.literal("#" + (i + 1) + " ").withStyle(rankColor, ChatFormatting.BOLD)
                    .append(Component.literal(record.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal("  " + record.points() + " pts ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("[" + rank.name() + "]").withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(line);
        }
    }

    public static void sendPlayerRank(ServerPlayer player) {
        int points = getPoints(player.getUUID());
        Rank rank = getRank(points);
        player.sendSystemMessage(Component.literal("YOUR BLOCKPARTY STATS")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Points: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Integer.toString(points)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        player.sendSystemMessage(Component.literal("Rank: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(rank.name()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        if (rank.isMaxRank()) {
            player.sendSystemMessage(Component.literal("Du har nådd høyeste rank!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            player.sendSystemMessage(Component.literal("Neste rank: " + rank.nextThreshold() + " poeng (" + rank.pointsToNext(points) + " igjen)")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static Path getFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}
