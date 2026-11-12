package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Three daily objectives with saved player progress, completion checkmarks and bonus-point rewards. */
public final class DailyChallengeManager {
    private static final String FILE_NAME = "blockparty-daily-challenges.properties";
    private static final ZoneId RESET_ZONE = ZoneId.of("Europe/Oslo");

    public enum Event {
        ROUND_SURVIVED, ROUND_STREAK, POWERUP_COLLECTED, MATCH_WON, MATCH_FINISHED, SONG_VOTED
    }

    public record Challenge(String id, String title, String description, Event event,
                            int goal, int reward, Item icon) { }

    private static final List<Challenge> POOL = List.of(
            new Challenge("streak_3", "Overlev 3 runder på rad",
                    "Overlev tre runder etter hverandre uten å bli slått ut.",
                    Event.ROUND_STREAK, 3, 35, Items.GOLDEN_SWORD),
            new Challenge("survive_8", "Overlev 8 runder",
                    "Overlev totalt åtte runder i BlockParty.",
                    Event.ROUND_SURVIVED, 8, 40, Items.IRON_CHESTPLATE),
            new Challenge("powerups_3", "Samle 3 power-ups",
                    "Plukk opp tre positive power-ups fra beacons.",
                    Event.POWERUP_COLLECTED, 3, 25, Items.BEACON),
            new Challenge("win_1", "Vinn en match",
                    "Bli kåret til vinner av en BlockParty-match.",
                    Event.MATCH_WON, 1, 60, Items.GOLD_BLOCK),
            new Challenge("matches_2", "Fullfør 2 matcher",
                    "Fullfør to matcher. Du trenger ikke å vinne.",
                    Event.MATCH_FINISHED, 2, 35, Items.CLOCK),
            new Challenge("vote_1", "Stem på en sang",
                    "Bruk musikkboken og stem på en sang i lobbyen.",
                    Event.SONG_VOTED, 1, 15, Items.MUSIC_DISC_CAT)
    );

    private static final Map<UUID, Map<String, Integer>> PROGRESS = new HashMap<>();
    private static final Map<UUID, Set<String>> COMPLETED = new HashMap<>();
    private static LocalDate currentDate;
    private static List<Challenge> activeChallenges = List.of();

    private DailyChallengeManager() { }

    public static synchronized void load(MinecraftServer server) {
        PROGRESS.clear();
        COMPLETED.clear();
        LocalDate today = LocalDate.now(RESET_ZONE);
        currentDate = today;
        activeChallenges = chooseChallenges(today);
        Path file = getFile(server);

        if (Files.exists(file)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
                if (today.toString().equals(properties.getProperty("daily.date", ""))) {
                    List<Challenge> restored = restoreChallenges(properties.getProperty("daily.challenge.ids", ""));
                    if (restored.size() == 3) activeChallenges = restored;
                    for (String raw : properties.getProperty("player.ids", "").split(",")) {
                        if (raw.isBlank()) continue;
                        UUID uuid = UUID.fromString(raw.trim());
                        String prefix = "player." + uuid + ".";
                        Map<String, Integer> progress = new HashMap<>();
                        for (Challenge challenge : activeChallenges) {
                            int amount = number(properties, prefix + "progress." + challenge.id());
                            if (amount > 0) progress.put(challenge.id(), Math.min(amount, challenge.goal()));
                        }
                        PROGRESS.put(uuid, progress);
                        Set<String> completed = new HashSet<>();
                        for (String id : properties.getProperty(prefix + "completed", "").split(",")) {
                            if (!id.isBlank() && findChallenge(id.trim()) != null) completed.add(id.trim());
                        }
                        COMPLETED.put(uuid, completed);
                    }
                }
            } catch (Exception exception) {
                BlockPartyMod.LOGGER.error("Could not load BlockParty daily challenges from {}.", file, exception);
                PROGRESS.clear();
                COMPLETED.clear();
                activeChallenges = chooseChallenges(today);
            }
        }
        save(server);
        BlockPartyMod.LOGGER.info("Loaded three daily BlockParty challenges for {}.", currentDate);
    }

    public static synchronized void save(MinecraftServer server) {
        if (currentDate == null) currentDate = LocalDate.now(RESET_ZONE);
        if (activeChallenges.isEmpty()) activeChallenges = chooseChallenges(currentDate);

        Properties properties = new Properties();
        properties.setProperty("daily.date", currentDate.toString());
        properties.setProperty("daily.challenge.ids",
                String.join(",", activeChallenges.stream().map(Challenge::id).toList()));

        Set<UUID> ids = new HashSet<>(PROGRESS.keySet());
        ids.addAll(COMPLETED.keySet());
        properties.setProperty("player.ids", String.join(",",
                ids.stream().map(UUID::toString).sorted().toList()));

        for (UUID uuid : ids) {
            String prefix = "player." + uuid + ".";
            Map<String, Integer> values = PROGRESS.getOrDefault(uuid, Map.of());
            for (Challenge challenge : activeChallenges) {
                int amount = values.getOrDefault(challenge.id(), 0);
                if (amount > 0) {
                    properties.setProperty(prefix + "progress." + challenge.id(), Integer.toString(amount));
                }
            }
            properties.setProperty(prefix + "completed",
                    String.join(",", COMPLETED.getOrDefault(uuid, Set.of()).stream().sorted().toList()));
        }

        Path file = getFile(server);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "BlockParty daily challenges and individual player progress");
            }
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.error("Could not save BlockParty daily challenges to {}.", file, exception);
        }
    }

    public static synchronized List<Challenge> getChallenges(MinecraftServer server) {
        ensureToday(server);
        return List.copyOf(activeChallenges);
    }

    public static synchronized int getProgress(MinecraftServer server, UUID uuid, String challengeId) {
        ensureToday(server);
        return PROGRESS.getOrDefault(uuid, Map.of()).getOrDefault(challengeId, 0);
    }

    public static synchronized boolean isCompleted(MinecraftServer server, UUID uuid, String challengeId) {
        ensureToday(server);
        return COMPLETED.getOrDefault(uuid, Set.of()).contains(challengeId);
    }

    public static synchronized void recordEvent(MinecraftServer server, ServerPlayer player, Event event) {
        ensureToday(server);
        advance(server, player, event);
    }

    /** Streak tasks advance on survived rounds and reset as soon as a player is eliminated. */
    public static synchronized void recordRoundResult(MinecraftServer server, ServerPlayer player, boolean survived) {
        ensureToday(server);
        if (survived) {
            advance(server, player, Event.ROUND_SURVIVED);
            advance(server, player, Event.ROUND_STREAK);
            return;
        }

        boolean changed = false;
        Map<String, Integer> values = PROGRESS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        for (Challenge challenge : activeChallenges) {
            if (challenge.event() == Event.ROUND_STREAK
                    && !COMPLETED.getOrDefault(player.getUUID(), Set.of()).contains(challenge.id())
                    && values.getOrDefault(challenge.id(), 0) != 0) {
                values.put(challenge.id(), 0);
                changed = true;
            }
        }
        if (changed) save(server);
    }

    private static void advance(MinecraftServer server, ServerPlayer player, Event event) {
        UUID uuid = player.getUUID();
        Map<String, Integer> values = PROGRESS.computeIfAbsent(uuid, ignored -> new HashMap<>());
        Set<String> completed = COMPLETED.computeIfAbsent(uuid, ignored -> new HashSet<>());
        boolean changed = false;

        for (Challenge challenge : activeChallenges) {
            if (challenge.event() != event || completed.contains(challenge.id())) continue;
            int before = values.getOrDefault(challenge.id(), 0);
            if (before >= challenge.goal()) continue;
            int after = Math.min(challenge.goal(), before + 1);
            values.put(challenge.id(), after);
            changed = true;

            if (after >= challenge.goal() && completed.add(challenge.id())) {
                PlayerStatsManager.addBonusPoints(server, player, challenge.reward());
                player.sendSystemMessage(Component.literal("DAILY CHALLENGE FULLFØRT! ")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal(challenge.title()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("  +" + challenge.reward() + " poeng!")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
            }
        }
        if (changed) save(server);
    }

    private static void ensureToday(MinecraftServer server) {
        LocalDate today = LocalDate.now(RESET_ZONE);
        if (currentDate == null || !today.equals(currentDate)) {
            currentDate = today;
            activeChallenges = chooseChallenges(today);
            PROGRESS.clear();
            COMPLETED.clear();
            save(server);
        }
    }

    private static List<Challenge> chooseChallenges(LocalDate date) {
        List<Challenge> shuffled = new ArrayList<>(POOL);
        Collections.shuffle(shuffled, new Random(date.toEpochDay()));
        return List.copyOf(shuffled.subList(0, 3));
    }

    private static List<Challenge> restoreChallenges(String ids) {
        List<Challenge> restored = new ArrayList<>();
        for (String id : ids.split(",")) {
            Challenge challenge = findChallenge(id.trim());
            if (challenge != null && restored.stream().noneMatch(existing -> existing.id().equals(challenge.id()))) {
                restored.add(challenge);
            }
        }
        return restored;
    }

    private static Challenge findChallenge(String id) {
        return POOL.stream().filter(challenge -> challenge.id().equals(id)).findFirst().orElse(null);
    }

    private static int number(Properties properties, String key) {
        try { return Math.max(0, Integer.parseInt(properties.getProperty(key, "0"))); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static Path getFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}
