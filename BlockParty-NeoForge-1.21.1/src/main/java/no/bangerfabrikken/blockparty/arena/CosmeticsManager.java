package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class CosmeticsManager {
    private static final String FILE_NAME = "blockparty-cosmetics.properties";
    private static final Map<String, Cosmetic> SHOP = new LinkedHashMap<>();
    private static final Map<UUID, EnumMap<Category, String>> EQUIPPED = new HashMap<>();
    private static final Map<UUID, Set<String>> OWNED = new HashMap<>();
    private static long ticks;
    public enum Category { TRAILS, ARMOR, KILL_SOUNDS, FOOTSTEPS }
    public record Cosmetic(String id, String name, int price, Category category, int icon,
                           String description, ParticleOptions particle, SoundEvent sound) {}

    static {
        add("trail_sparkle", "Stjernestøv", 500, Category.TRAILS, Items.FIREWORK_STAR, "Et glitrende spor", ParticleTypes.FIREWORK);
        add("trail_flame", "Flammespor", 1000, Category.TRAILS, Items.BLAZE_POWDER, "Flammer følger stegene dine", ParticleTypes.FLAME);
        add("trail_hearts", "Hjertestøv", 1500, Category.TRAILS, Items.POPPY, "Små hjerter bak deg", ParticleTypes.HEART);
        add("trail_notes", "Musikkspor", 2000, Category.TRAILS, Items.NOTE_BLOCK, "Musikknoter følger deg", ParticleTypes.NOTE);
        add("armor_glimmer", "Rustningsglød", 1000, Category.ARMOR, Items.DIAMOND_CHESTPLATE, "En glitrende rustningsaura", ParticleTypes.END_ROD);
        add("armor_soul", "Sjelepanser", 1500, Category.ARMOR, Items.NETHERITE_CHESTPLATE, "Blå sjelpartikler rundt rustningen", ParticleTypes.SOUL_FIRE_FLAME);
        add("kill_bell", "Seiersklokke", 500, Category.KILL_SOUNDS, Items.BELL, "En kort klokketøy ved eliminering", null, SoundEvents.NOTE_BLOCK_BELL.value());
        add("kill_levelup", "Level Up", 1000, Category.KILL_SOUNDS, Items.EXPERIENCE_BOTTLE, "En triumferende lyd ved eliminering", null, SoundEvents.PLAYER_LEVELUP.value());
        add("kill_pling", "Arcade Pling", 1500, Category.KILL_SOUNDS, Items.AMETHYST_SHARD, "En retro spilllyd ved eliminering", null, SoundEvents.EXPERIENCE_ORB_PICKUP.value());
        add("step_soul", "Sjelesteg", 500, Category.FOOTSTEPS, Items.SOUL_SAND, "Sjelepartikler når du går", ParticleTypes.SOUL);
        add("step_snow", "Snøfnuggsteg", 1000, Category.FOOTSTEPS, Items.SNOWBALL, "Snøfnugg når du går", ParticleTypes.SNOWFLAKE);
        add("step_music", "Rytmesteg", 1500, Category.FOOTSTEPS, Items.JUKEBOX, "Noter når du går", ParticleTypes.NOTE);
    }

    private CosmeticsManager() {}
    private static void add(String id, String name, int price, Category category, Item icon,
                            String description, ParticleOptions particle) {
        add(id, name, price, category, icon, description, particle, null);
    }
    private static void add(String id, String name, int price, Category category, Item icon,
                            String description, ParticleOptions particle, SoundEvent sound) {
        SHOP.put(id, new Cosmetic(id, name, Math.min(2000, price), category,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(icon), description, particle, sound));
    }

    public static synchronized Collection<Cosmetic> items(Category category) {
        return SHOP.values().stream().filter(c -> c.category() == category).toList();
    }
    public static synchronized Collection<Cosmetic> owned(UUID uuid) {
        Set<String> ids = OWNED.getOrDefault(uuid, Set.of());
        return SHOP.values().stream().filter(c -> ids.contains(c.id())).toList();
    }
    public static synchronized boolean owns(UUID uuid, String id) {
        return OWNED.getOrDefault(uuid, Set.of()).contains(id);
    }
    public static synchronized String equipped(UUID uuid, Category category) {
        return EQUIPPED.getOrDefault(uuid, new EnumMap<>(Category.class)).get(category);
    }

    public static synchronized void load(MinecraftServer server) {
        OWNED.clear(); EQUIPPED.clear();
        Path file = getFile(server);
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            for (String raw : properties.getProperty("player.ids", "").split(",")) {
                if (raw.isBlank()) continue;
                UUID uuid = UUID.fromString(raw.trim());
                String prefix = "player." + uuid + ".";
                Set<String> owned = new HashSet<>();
                for (String id : properties.getProperty(prefix + "owned", "").split(",")) if (SHOP.containsKey(id)) owned.add(id);
                OWNED.put(uuid, owned);
                EnumMap<Category, String> equipped = new EnumMap<>(Category.class);
                for (Category category : Category.values()) {
                    String id = properties.getProperty(prefix + "equipped." + category.name(), "");
                    Cosmetic cosmetic = SHOP.get(id);
                    if (cosmetic != null && cosmetic.category() == category && owned.contains(id)) equipped.put(category, id);
                }
                if (!equipped.isEmpty()) EQUIPPED.put(uuid, equipped);
            }
        } catch (Exception exception) {
            BlockPartyMod.LOGGER.error("Could not load BlockParty cosmetics from {}", file, exception);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Properties properties = new Properties();
        Set<UUID> all = new HashSet<>(OWNED.keySet()); all.addAll(EQUIPPED.keySet());
        StringJoiner ids = new StringJoiner(",");
        for (UUID uuid : all) {
            ids.add(uuid.toString());
            String prefix = "player." + uuid + ".";
            properties.setProperty(prefix + "owned", String.join(",", OWNED.getOrDefault(uuid, Set.of())));
            for (Category category : Category.values()) {
                String selected = equipped(uuid, category);
                properties.setProperty(prefix + "equipped." + category.name(), selected == null ? "" : selected);
            }
        }
        properties.setProperty("player.ids", ids.toString());
        Path file = getFile(server);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) { properties.store(output, "BlockParty owned and equipped cosmetics"); }
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.error("Could not save BlockParty cosmetics to {}", file, exception);
        }
    }

    public static void openShop(ServerPlayer player) { CosmeticsMenu.open(player, null); }

    public static synchronized void select(ServerPlayer player, String id) {
        Cosmetic cosmetic = SHOP.get(id);
        if (cosmetic == null) return;
        UUID uuid = player.getUUID();
        Set<String> owned = OWNED.computeIfAbsent(uuid, ignored -> new HashSet<>());
        if (!owned.contains(id)) {
            if (!PlayerStatsManager.spendPoints(player.getServer(), player, cosmetic.price())) {
                player.sendSystemMessage(Component.literal("Du har ikke nok poeng. Du trenger " + cosmetic.price() + " poeng.").withStyle(ChatFormatting.RED));
                return;
            }
            owned.add(id);
            player.sendSystemMessage(Component.literal("Du kjøpte " + cosmetic.name() + "!").withStyle(ChatFormatting.GREEN));
        }
        EQUIPPED.computeIfAbsent(uuid, ignored -> new EnumMap<>(Category.class)).put(cosmetic.category(), id);
        save(player.getServer());
        player.sendSystemMessage(Component.literal(cosmetic.name() + " er nå utstyrt.").withStyle(ChatFormatting.YELLOW));
    }

    public static synchronized void unequip(ServerPlayer player, Category category) {
        EnumMap<Category, String> equipped = EQUIPPED.get(player.getUUID());
        if (equipped != null) equipped.remove(category);
        save(player.getServer());
        player.sendSystemMessage(Component.literal("Kosmetikken er fjernet.").withStyle(ChatFormatting.GRAY));
    }

    public static synchronized void playKillSound(ServerPlayer killer) {
        String id = equipped(killer.getUUID(), Category.KILL_SOUNDS);
        Cosmetic cosmetic = id == null ? null : SHOP.get(id);
        if (cosmetic != null && cosmetic.sound() != null) {
            killer.serverLevel().playSound(null, killer.blockPosition(), cosmetic.sound(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    public static synchronized void tick(MinecraftServer server) {
        if (++ticks % 4 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            emit(player, Category.TRAILS);
            emit(player, Category.ARMOR);
            if (player.getDeltaMovement().horizontalDistanceSqr() > 0.0025 && !player.isCrouching()) emit(player, Category.FOOTSTEPS);
        }
    }

    private static void emit(ServerPlayer player, Category category) {
        String id = equipped(player.getUUID(), category);
        Cosmetic cosmetic = id == null ? null : SHOP.get(id);
        if (cosmetic == null || cosmetic.particle() == null) return;
        double y = category == Category.FOOTSTEPS ? player.getY() + 0.08 : player.getY() + 0.9;
        player.serverLevel().sendParticles(cosmetic.particle(), player.getX(), y, player.getZ(),
                category == Category.ARMOR ? 2 : 1, 0.28,
                category == Category.ARMOR ? 0.55 : 0.12, 0.28, 0.01);
    }

    private static Path getFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}
