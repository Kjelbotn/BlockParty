package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player daily objectives, saved with the world and rotated at Oslo midnight. */
public final class DailyChallengeManager {
    private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
    private static final String FILE = "blockparty-daily-challenges.properties";
    private static final int COMPLETION_BONUS = 50;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d. MMMM", Locale.forLanguageTag("nb-NO"));
    private static final List<Challenge> CATALOGUE = List.of(
            new Challenge("survive_8", "Survivor", "Overlev 8 runder", 8, 15, Kind.ROUND),
            new Challenge("survive_15", "Endurance", "Overlev 15 runder", 15, 25, Kind.ROUND),
            new Challenge("powerups_3", "Power Hunter", "Samle 3 power-ups", 3, 10, Kind.POWERUP),
            new Challenge("powerups_5", "Power Collector", "Samle 5 power-ups", 5, 15, Kind.POWERUP),
            new Challenge("powerups_8", "Power Master", "Samle 8 power-ups", 8, 25, Kind.POWERUP),
            new Challenge("win_1", "Match Winner", "Vinn 1 match", 1, 20, Kind.WIN),
            new Challenge("win_2", "Champion", "Vinn 2 matcher", 2, 25, Kind.WIN)
    );
    private static final Map<UUID, Progress> DATA = new HashMap<>();
    private static MinecraftServer server;

    private DailyChallengeManager() {}
    private enum Kind { ROUND, POWERUP, WIN }
    private record Challenge(String id, String title, String description, int target, int reward, Kind kind) {}
    private static final class Progress {
        String date;
        List<String> ids = new ArrayList<>();
        Map<String,Integer> values = new HashMap<>();
        Set<String> claimed = new HashSet<>();
        boolean bonusClaimed;
    }

    public static synchronized void load(MinecraftServer minecraftServer) {
        server = minecraftServer;
        DATA.clear();
        Path file = file();
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            for (String raw : p.getProperty("players", "").split(",")) {
                if (raw.isBlank()) continue;
                UUID uuid = UUID.fromString(raw);
                String prefix = "p." + uuid + ".";
                Progress d = new Progress();
                d.date = p.getProperty(prefix + "date", "");
                d.ids = new ArrayList<>(Arrays.asList(p.getProperty(prefix + "ids", "").split(",")));
                d.ids.removeIf(String::isBlank);
                for (String id : d.ids) d.values.put(id, number(p, prefix + "progress." + id));
                d.claimed.addAll(Arrays.asList(p.getProperty(prefix + "claimed", "").split(",")));
                d.claimed.removeIf(String::isBlank);
                d.bonusClaimed = Boolean.parseBoolean(p.getProperty(prefix + "bonus", "false"));
                DATA.put(uuid, d);
            }
        } catch (Exception e) {
            no.bangerfabrikken.blockparty.BlockPartyMod.LOGGER.error("Could not load daily challenges", e);
        }
    }

    private static int number(Properties p, String key) {
        try { return Math.max(0, Integer.parseInt(p.getProperty(key, "0"))); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
    }

    public static synchronized void save() {
        if (server == null) return;
        Properties p = new Properties();
        StringJoiner players = new StringJoiner(",");
        DATA.forEach((uuid,d) -> {
            players.add(uuid.toString());
            String prefix = "p." + uuid + ".";
            p.setProperty(prefix + "date", d.date == null ? "" : d.date);
            p.setProperty(prefix + "ids", String.join(",", d.ids));
            p.setProperty(prefix + "claimed", String.join(",", d.claimed));
            p.setProperty(prefix + "bonus", Boolean.toString(d.bonusClaimed));
            d.values.forEach((id,value) -> p.setProperty(prefix + "progress." + id, Integer.toString(value)));
        });
        p.setProperty("players", players.toString());
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "BlockParty daily challenges"); }
        } catch (IOException e) {
            no.bangerfabrikken.blockparty.BlockPartyMod.LOGGER.error("Could not save daily challenges", e);
        }
    }

    private static Progress get(ServerPlayer player) {
        String today = LocalDate.now(ZONE).toString();
        Progress d = DATA.computeIfAbsent(player.getUUID(), ignored -> new Progress());
        if (!today.equals(d.date)) {
            d.date = today;
            d.ids = choose(player.getUUID(), today);
            d.values.clear();
            d.claimed.clear();
            d.bonusClaimed = false;
            save();
        }
        return d;
    }

    private static List<String> choose(UUID uuid, String date) {
        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ date.hashCode();
        Random random = new Random(seed);
        List<String> selected = new ArrayList<>(3);
        // Always one objective of each type: no duplicate "survive X rounds"
        // challenge can appear on the same player's daily set.
        for (Kind kind : List.of(Kind.ROUND, Kind.POWERUP, Kind.WIN)) {
            List<Challenge> pool = CATALOGUE.stream().filter(c -> c.kind == kind).toList();
            selected.add(pool.get(random.nextInt(pool.size())).id);
        }
        return List.copyOf(selected);
    }

    public static synchronized void recordRound(MinecraftServer server, ServerPlayer player) { record(server, player, Kind.ROUND); }
    public static synchronized void recordPowerup(MinecraftServer server, ServerPlayer player) { record(server, player, Kind.POWERUP); }
    public static synchronized void recordWin(MinecraftServer server, ServerPlayer player) { record(server, player, Kind.WIN); }

    private static void record(MinecraftServer minecraftServer, ServerPlayer player, Kind kind) {
        DailyChallengeManager.server = minecraftServer;
        Progress d = get(player);
        boolean changed = false;
        for (String id : d.ids) {
            Challenge c = find(id);
            if (c.kind != kind || d.claimed.contains(id)) continue;
            int old = d.values.getOrDefault(id, 0);
            if (old < c.target) {
                int next = Math.min(c.target, old + 1);
                d.values.put(id, next);
                changed = true;
                if (next >= c.target) {
                    d.claimed.add(id);
                    PlayerStatsManager.addPoints(minecraftServer, player, c.reward);
                    player.sendSystemMessage(Component.literal("Daily Challenge fullført: " + c.title + "  +" + c.reward + " poeng!")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
            }
        }
        if (changed && d.ids.stream().allMatch(d.claimed::contains) && !d.bonusClaimed) {
            d.bonusClaimed = true;
            PlayerStatsManager.addPoints(minecraftServer, player, COMPLETION_BONUS);
            player.sendSystemMessage(Component.literal("Alle Daily Challenges fullført! Bonus: +50 poeng!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        if (changed) save();
    }

    private static Challenge find(String id) {
        return CATALOGUE.stream().filter(c -> c.id.equals(id)).findFirst().orElseThrow();
    }

    public static synchronized List<Component> getLines(ServerPlayer player) {
        Progress d = get(player);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("DAILY CHALLENGES  •  " + LocalDate.parse(d.date).format(DATE_FORMAT))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal("Fullfør alle tre for +50 bonuspoeng.").withStyle(ChatFormatting.GRAY));
        for (String id : d.ids) {
            Challenge c = find(id);
            int progress = d.values.getOrDefault(id, 0);
            boolean done = d.claimed.contains(id);
            lines.add(Component.literal((done ? "✔ " : "▸ ") + c.title + " — " + c.description)
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            lines.add(Component.literal("   " + progress + "/" + c.target + "   •   +" + c.reward + " poeng")
                    .withStyle(ChatFormatting.GRAY));
        }
        long completed = d.claimed.size();
        lines.add(Component.literal("Fullført: " + completed + "/3").withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Fullføringsbonus: " + (d.bonusClaimed ? "MOTTATT" : "+50 poeng"))
                .withStyle(d.bonusClaimed ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        return lines;
    }

    public static synchronized void open(ServerPlayer player) {
        get(player);
        player.sendSystemMessage(Component.empty());
        for (Component line : getLines(player)) player.sendSystemMessage(line);
    }
}
