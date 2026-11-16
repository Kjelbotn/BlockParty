package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Persistent points shop and cosmetic particle auras. Cosmetics are visual only. */
public final class CosmeticsManager {
    private static final String FILE_NAME = "blockparty-cosmetics.properties";
    private static final Map<String, Cosmetic> SHOP = new java.util.LinkedHashMap<>();
    private static final Map<UUID, String> EQUIPPED_AURAS = new HashMap<>();
    private static final Map<UUID, java.util.Set<String>> OWNED = new HashMap<>();
    private static long tick;
    public record Cosmetic(String id, String name, int price, net.minecraft.core.particles.ParticleOptions particle) {}

    static {
        SHOP.put("gold", new Cosmetic("gold", "Gullglød", 1500, ParticleTypes.GLOW));
        SHOP.put("flame", new Cosmetic("flame", "Flammeaura", 2500, ParticleTypes.FLAME));
        SHOP.put("hearts", new Cosmetic("hearts", "Hjerter", 3500, ParticleTypes.HEART));
        SHOP.put("music", new Cosmetic("music", "Musikkgnister", 5000, ParticleTypes.NOTE));
        SHOP.put("snow", new Cosmetic("snow", "Snøfnugg", 5000, ParticleTypes.SNOWFLAKE));
    }
    private CosmeticsManager() {}

    public static synchronized void load(MinecraftServer server) {
        OWNED.clear(); EQUIPPED_AURAS.clear();
        Path file = getFile(server);
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            for (String raw : properties.getProperty("player.ids", "").split(",")) {
                if (raw.isBlank()) continue;
                UUID uuid = UUID.fromString(raw.trim());
                String prefix = "player." + uuid + ".";
                java.util.Set<String> owned = new java.util.HashSet<>();
                for (String id : properties.getProperty(prefix + "owned", "").split(",")) if (SHOP.containsKey(id)) owned.add(id);
                OWNED.put(uuid, owned);
                String equipped = properties.getProperty(prefix + "aura", "");
                if (SHOP.containsKey(equipped) && owned.contains(equipped)) EQUIPPED_AURAS.put(uuid, equipped);
            }
        } catch (Exception exception) { BlockPartyMod.LOGGER.error("Could not load BlockParty cosmetics from {}", file, exception); }
    }

    public static synchronized void save(MinecraftServer server) {
        Properties properties = new Properties();
        StringBuilder ids = new StringBuilder();
        java.util.Set<UUID> all = new java.util.HashSet<>(OWNED.keySet()); all.addAll(EQUIPPED_AURAS.keySet());
        for (UUID uuid : all) {
            if (ids.length() > 0) ids.append(',');
            ids.append(uuid);
            String prefix = "player." + uuid + ".";
            properties.setProperty(prefix + "owned", String.join(",", OWNED.getOrDefault(uuid, java.util.Set.of())));
            properties.setProperty(prefix + "aura", EQUIPPED_AURAS.getOrDefault(uuid, ""));
        }
        properties.setProperty("player.ids", ids.toString());
        Path file = getFile(server);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) { properties.store(output, "BlockParty owned cosmetics and equipped effects"); }
        } catch (IOException exception) { BlockPartyMod.LOGGER.error("Could not save BlockParty cosmetics to {}", file, exception); }
    }

    public static synchronized void openShop(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ BLOCKPARTY KOSMETIKKBUTIK ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Valuta: " + PlayerStatsManager.getPoints(player.getUUID()) + " poeng").withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("Kjøp: /bp cosmetics buy <id> | Utstyr: /bp cosmetics equip <id> | Fjern: /bp cosmetics clear").withStyle(ChatFormatting.GRAY));
        for (Cosmetic cosmetic : SHOP.values()) {
            boolean owned = OWNED.getOrDefault(player.getUUID(), java.util.Set.of()).contains(cosmetic.id());
            boolean equipped = cosmetic.id().equals(EQUIPPED_AURAS.get(player.getUUID()));
            player.sendSystemMessage(Component.literal((equipped ? "» " : "• ") + cosmetic.name() + " [" + cosmetic.id() + "] — "
                    + (owned ? "KJØPT" : cosmetic.price() + " poeng"))
                    .withStyle(equipped ? ChatFormatting.GREEN : owned ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        }
    }

    public static synchronized boolean buy(MinecraftServer server, ServerPlayer player, String id) {
        Cosmetic cosmetic = SHOP.get(id.toLowerCase(java.util.Locale.ROOT));
        if (cosmetic == null) return false;
        java.util.Set<String> owned = OWNED.computeIfAbsent(player.getUUID(), ignored -> new java.util.HashSet<>());
        if (owned.contains(id)) { player.sendSystemMessage(Component.literal("Du eier allerede " + cosmetic.name() + ".").withStyle(ChatFormatting.YELLOW)); return true; }
        if (!PlayerStatsManager.spendPoints(server, player, cosmetic.price())) {
            player.sendSystemMessage(Component.literal("Du har ikke nok poeng. " + cosmetic.price() + " poeng kreves.").withStyle(ChatFormatting.RED)); return true;
        }
        owned.add(cosmetic.id()); save(server);
        player.sendSystemMessage(Component.literal("Du kjøpte " + cosmetic.name() + "! Bruk /bp cosmetics equip " + cosmetic.id())
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        return true;
    }

    public static synchronized boolean equip(MinecraftServer server, ServerPlayer player, String id) {
        Cosmetic cosmetic = SHOP.get(id.toLowerCase(java.util.Locale.ROOT));
        if (cosmetic == null) return false;
        if (!OWNED.getOrDefault(player.getUUID(), java.util.Set.of()).contains(cosmetic.id())) {
            player.sendSystemMessage(Component.literal("Du eier ikke denne kosmetikken ennå.").withStyle(ChatFormatting.RED)); return true;
        }
        EQUIPPED_AURAS.put(player.getUUID(), cosmetic.id()); save(server);
        player.sendSystemMessage(Component.literal("Utstyrte: " + cosmetic.name()).withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static synchronized void clear(MinecraftServer server, ServerPlayer player) {
        EQUIPPED_AURAS.remove(player.getUUID()); save(server);
        player.sendSystemMessage(Component.literal("Kosmetisk aura fjernet.").withStyle(ChatFormatting.YELLOW));
    }

    public static synchronized void tick(MinecraftServer server) {
        if (++tick % 4 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Cosmetic cosmetic = SHOP.get(EQUIPPED_AURAS.get(player.getUUID()));
            if (cosmetic == null) continue;
            ServerLevel level = player.serverLevel();
            level.sendParticles(cosmetic.particle(), player.getX(), player.getY() + 0.8, player.getZ(),
                    3, 0.28, 0.45, 0.28, 0.01);
        }
    }

    private static Path getFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}
