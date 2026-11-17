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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
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
    /** Categories explicitly disabled by each player; all newly equipped cosmetics start enabled. */
    private static final Map<UUID, EnumSet<Category>> DISABLED = new HashMap<>();
    private static long ticks;
    private static final Set<UUID> SESSION_SEEN = new HashSet<>();
    public enum Category { TRAILS, ARMOR, KILL_SOUNDS, FOOTSTEPS, PETS, WIN_EFFECTS, ELIMINATION_EFFECTS, CROWNS, JOIN_EFFECTS, JOIN_SOUNDS, WIN_SOUNDS }
    public record Cosmetic(String id, String name, int price, Category category, int icon,
                           String description, ParticleOptions particle, SoundEvent sound) {}

    static {
        add("trail_sparkle", "Stjernestøv", 500, Category.TRAILS, Items.FIREWORK_STAR, "Et glitrende spor", ParticleTypes.FIREWORK);
        add("trail_flame", "Flammespor", 1000, Category.TRAILS, Items.BLAZE_POWDER, "Flammer følger stegene dine", ParticleTypes.FLAME);
        add("trail_hearts", "Hjertestøv", 1500, Category.TRAILS, Items.POPPY, "Små hjerter bak deg", ParticleTypes.HEART);
        add("trail_notes", "Musikkspor", 2000, Category.TRAILS, Items.NOTE_BLOCK, "Musikknoter følger deg", ParticleTypes.NOTE);
        add("armor_glimmer", "Gullstøvler", 1000, Category.ARMOR, Items.GOLDEN_BOOTS, "Gullstøvler vises i fotslotten", null);
        add("armor_soul", "Diamantstøvler", 1500, Category.ARMOR, Items.DIAMOND_BOOTS, "Diamantstøvler vises i fotslotten", null);
        add("kill_bell", "Seiersklokke", 500, Category.KILL_SOUNDS, Items.BELL, "En kort klokketøy ved eliminering", null, SoundEvents.NOTE_BLOCK_BELL.value());
        add("kill_levelup", "Level Up", 1000, Category.KILL_SOUNDS, Items.EXPERIENCE_BOTTLE, "En triumferende lyd ved eliminering", null, SoundEvents.PLAYER_LEVELUP);
        add("kill_pling", "Arcade Pling", 1500, Category.KILL_SOUNDS, Items.AMETHYST_SHARD, "En retro spilllyd ved eliminering", null, SoundEvents.EXPERIENCE_ORB_PICKUP);
        add("step_soul", "Sjelesteg", 500, Category.FOOTSTEPS, Items.SOUL_SAND, "Sjelepartikler når du går", ParticleTypes.SOUL);
        add("step_snow", "Snøfnuggsteg", 1000, Category.FOOTSTEPS, Items.SNOWBALL, "Snøfnugg når du går", ParticleTypes.SNOWFLAKE);
        add("step_music", "Rytmesteg", 1500, Category.FOOTSTEPS, Items.JUKEBOX, "Noter når du går", ParticleTypes.NOTE);
        add("pet_allay", "Allay-følgesvenn", 1800, Category.PETS, Items.ALLAY_SPAWN_EGG, "Svevende partikkelfølgesvenn", ParticleTypes.END_ROD);
        add("pet_bee", "Bi-følgesvenn", 1800, Category.PETS, Items.BEE_SPAWN_EGG, "Gyllen partikkelfølgesvenn", ParticleTypes.HAPPY_VILLAGER);
        add("pet_slime", "Slim-følgesvenn", 2000, Category.PETS, Items.SLIME_BALL, "Grønn partikkelfølgesvenn", ParticleTypes.ITEM_SLIME);
        add("win_firework", "Fyrverkeriseier", 1500, Category.WIN_EFFECTS, Items.FIREWORK_ROCKET, "Fyrverkeri når du vinner", ParticleTypes.FIREWORK);
        add("win_hearts", "Hjerteseier", 1200, Category.WIN_EFFECTS, Items.POPPY, "Hjerter ved seier", ParticleTypes.HEART);
        add("win_totem", "Totemseier", 2000, Category.WIN_EFFECTS, Items.TOTEM_OF_UNDYING, "Gylden totemglød ved seier", ParticleTypes.TOTEM_OF_UNDYING);
        add("elim_lightning", "Lynavslutning", 1500, Category.ELIMINATION_EFFECTS, Items.LIGHTNING_ROD, "Lynpartikler ved eliminering", ParticleTypes.ELECTRIC_SPARK);
        add("elim_smoke", "Røykpuff", 900, Category.ELIMINATION_EFFECTS, Items.CAMPFIRE, "Røyk ved eliminering", ParticleTypes.CAMPFIRE_COSY_SMOKE);
        add("elim_soul", "Sjeleeksplosjon", 1800, Category.ELIMINATION_EFFECTS, Items.SOUL_LANTERN, "Sjeleflammer ved eliminering", ParticleTypes.SOUL_FIRE_FLAME);
        add("crown_gold", "Gullkrone", 1800, Category.CROWNS, Items.GOLD_INGOT, "Gyllen glorie i lobbyen", ParticleTypes.WAX_ON);
        add("crown_diamond", "Diamantkrone", 2400, Category.CROWNS, Items.DIAMOND, "Glitrende glorie i lobbyen", ParticleTypes.END_ROD);
        add("crown_flame", "Flammekrone", 2000, Category.CROWNS, Items.BLAZE_POWDER, "Flammeglorie i lobbyen", ParticleTypes.FLAME);
        add("join_ender", "Ender-inntreden", 1000, Category.JOIN_EFFECTS, Items.ENDER_PEARL, "Enderpartikler ved inngang", ParticleTypes.PORTAL);
        add("join_star", "Stjerneinntreden", 1200, Category.JOIN_EFFECTS, Items.NETHER_STAR, "Stjerner ved inngang", ParticleTypes.FIREWORK);
        add("join_bell", "Lobbyklokke", 900, Category.JOIN_SOUNDS, Items.BELL, "Klokkelyd ved inngang", null, SoundEvents.NOTE_BLOCK_BELL.value());
        add("join_chime", "Portalpling", 1200, Category.JOIN_SOUNDS, Items.AMETHYST_SHARD, "Magisk lyd ved inngang", null, SoundEvents.AMETHYST_BLOCK_CHIME);
        add("win_chime", "Seierspling", 1000, Category.WIN_SOUNDS, Items.EXPERIENCE_BOTTLE, "Lyd når du vinner", null, SoundEvents.PLAYER_LEVELUP);
        add("win_dragon", "Dragebrøl", 1800, Category.WIN_SOUNDS, Items.DRAGON_BREATH, "Dragebrøl når du vinner", null, SoundEvents.ENDER_DRAGON_GROWL);
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

    public static synchronized boolean isEnabled(UUID uuid, Category category) {
        return !DISABLED.getOrDefault(uuid, EnumSet.noneOf(Category.class)).contains(category);
    }

    /** Toggle the selected cosmetic category without losing the player's equipped item. */
    public static synchronized void toggle(ServerPlayer player, Category category) {
        UUID uuid = player.getUUID();
        EnumSet<Category> disabled = DISABLED.computeIfAbsent(uuid, ignored -> EnumSet.noneOf(Category.class));
        boolean enabled;
        if (disabled.remove(category)) enabled = true;
        else { disabled.add(category); enabled = false; }
        save(player.getServer());
        player.sendSystemMessage(Component.literal(
                category.name().replace('_', ' ') + (enabled ? " er slått PÅ." : " er slått AV."))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    public static synchronized void load(MinecraftServer server) {
        OWNED.clear(); EQUIPPED.clear(); DISABLED.clear();
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
                EnumSet<Category> disabled = EnumSet.noneOf(Category.class);
                for (Category category : Category.values()) {
                    if (Boolean.parseBoolean(properties.getProperty(prefix + "disabled." + category.name(), "false"))) {
                        disabled.add(category);
                    }
                }
                if (!disabled.isEmpty()) DISABLED.put(uuid, disabled);
            }
        } catch (Exception exception) {
            BlockPartyMod.LOGGER.error("Could not load BlockParty cosmetics from {}", file, exception);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Properties properties = new Properties();
        Set<UUID> all = new HashSet<>(OWNED.keySet()); all.addAll(EQUIPPED.keySet()); all.addAll(DISABLED.keySet());
        StringJoiner ids = new StringJoiner(",");
        for (UUID uuid : all) {
            ids.add(uuid.toString());
            String prefix = "player." + uuid + ".";
            properties.setProperty(prefix + "owned", String.join(",", OWNED.getOrDefault(uuid, Set.of())));
            for (Category category : Category.values()) {
                String selected = equipped(uuid, category);
                properties.setProperty(prefix + "equipped." + category.name(), selected == null ? "" : selected);
                properties.setProperty(prefix + "disabled." + category.name(),
                        Boolean.toString(!isEnabled(uuid, category)));
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
        if (cosmetic.category() == Category.ARMOR && !equipArmorBoots(player, id)) return;
        EQUIPPED.computeIfAbsent(uuid, ignored -> new EnumMap<>(Category.class)).put(cosmetic.category(), id);
        DISABLED.computeIfAbsent(uuid, ignored -> EnumSet.noneOf(Category.class)).remove(cosmetic.category());
        save(player.getServer());
        player.sendSystemMessage(Component.literal(cosmetic.name() + " er nå utstyrt.").withStyle(ChatFormatting.YELLOW));
    }

    public static synchronized void unequip(ServerPlayer player, Category category) {
        EnumMap<Category, String> equipped = EQUIPPED.get(player.getUUID());
        String previousId = equipped == null ? null : equipped.remove(category);
        if (category == Category.ARMOR && previousId != null) {
            Item item = armorItem(previousId);
            ItemStack worn = player.getItemBySlot(EquipmentSlot.FEET);
            if (item != null && worn.is(item)) player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        }
        DISABLED.computeIfAbsent(player.getUUID(), ignored -> EnumSet.noneOf(Category.class)).add(category);
        save(player.getServer());
        player.sendSystemMessage(Component.literal("Kosmetikken er fjernet.").withStyle(ChatFormatting.GRAY));
    }

    public static synchronized void playKillSound(ServerPlayer killer) {
        Arena activeArena = ArenaManager.get().findArenaForLeave(killer);
        if (activeArena == null || !activeArena.isMatchParticipant(killer)
                || !isEnabled(killer.getUUID(), Category.KILL_SOUNDS)) return;
        String id = equipped(killer.getUUID(), Category.KILL_SOUNDS);
        Cosmetic cosmetic = id == null ? null : SHOP.get(id);
        if (cosmetic != null && cosmetic.sound() != null) {
            killer.serverLevel().playSound(null, killer.blockPosition(), cosmetic.sound(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    public static synchronized void tick(MinecraftServer server) {
        if (++ticks % 4 != 0) return;
        Set<UUID> onlineInSession = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Cosmetic particles are BlockParty-only. A saved selection never leaks
            // into ordinary survival after /bp leave, logout, or a teleport away.
            Arena session = ArenaManager.get().findArenaForLeave(player);
            if (session == null || !session.hasReturnLocation(player.getUUID())) continue;
            onlineInSession.add(player.getUUID());
            if (SESSION_SEEN.add(player.getUUID())) playJoinEffects(player);
            emit(player, Category.TRAILS);
            emitPetAndCrown(player);
            if (player.getDeltaMovement().horizontalDistanceSqr() > 0.0025 && !player.isCrouching()) {
                emit(player, Category.FOOTSTEPS);
            }
        }
    }

    private static Item armorItem(String id) {
        return switch (id) {
            case "armor_glimmer" -> Items.GOLDEN_BOOTS;
            case "armor_soul" -> Items.DIAMOND_BOOTS;
            default -> null;
        };
    }

    private static boolean equipArmorBoots(ServerPlayer player, String id) {
        Item item = armorItem(id);
        if (item == null) return false;
        ItemStack current = player.getItemBySlot(EquipmentSlot.FEET);
        String oldId = equipped(player.getUUID(), Category.ARMOR);
        Item oldCosmetic = oldId == null ? null : armorItem(oldId);
        if (!current.isEmpty() && (oldCosmetic == null || !current.is(oldCosmetic))) {
            if (!player.getInventory().add(current.copy())) {
                player.sendSystemMessage(Component.literal("Du har ikke plass i inventaret til å ta vare på støvlene dine.").withStyle(ChatFormatting.RED));
                return false;
            }
        }
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(item));
        return true;
    }

    public static synchronized void playJoinEffects(ServerPlayer player) {
        playParticleEffect(player, Category.JOIN_EFFECTS, 32);
        playCosmeticSound(player, Category.JOIN_SOUNDS);
    }

    public static synchronized void playWinEffects(ServerPlayer player) {
        playParticleEffect(player, Category.WIN_EFFECTS, 90);
        playCosmeticSound(player, Category.WIN_SOUNDS);
    }

    public static synchronized void playEliminationEffects(ServerPlayer player) {
        playParticleEffect(player, Category.ELIMINATION_EFFECTS, 35);
    }

    private static void playParticleEffect(ServerPlayer player, Category category, int count) {
        if (!isEnabled(player.getUUID(), category)) return;
        String id = equipped(player.getUUID(), category);
        Cosmetic cosmetic = id == null ? null : SHOP.get(id);
        if (cosmetic != null && cosmetic.particle() != null) {
            player.serverLevel().sendParticles(cosmetic.particle(), player.getX(), player.getY() + 1.0,
                    player.getZ(), count, 0.65, 0.8, 0.65, 0.12);
        }
    }

    private static void playCosmeticSound(ServerPlayer player, Category category) {
        if (!isEnabled(player.getUUID(), category)) return;
        String id = equipped(player.getUUID(), category);
        Cosmetic cosmetic = id == null ? null : SHOP.get(id);
        if (cosmetic != null && cosmetic.sound() != null) {
            player.serverLevel().playSound(null, player.blockPosition(), cosmetic.sound(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static void emitPetAndCrown(ServerPlayer player) {
        long phase = player.tickCount * 0.12;
        if (isEnabled(player.getUUID(), Category.PETS)) {
            String id = equipped(player.getUUID(), Category.PETS);
            Cosmetic pet = id == null ? null : SHOP.get(id);
            if (pet != null && pet.particle() != null) {
                player.serverLevel().sendParticles(pet.particle(),
                        player.getX() + Math.cos(phase) * 0.8,
                        player.getY() + 0.9 + Math.sin(phase * 2) * 0.12,
                        player.getZ() + Math.sin(phase) * 0.8, 1, 0, 0, 0, 0);
            }
        }
        if (isEnabled(player.getUUID(), Category.CROWNS)) {
            String id = equipped(player.getUUID(), Category.CROWNS);
            Cosmetic crown = id == null ? null : SHOP.get(id);
            if (crown != null && crown.particle() != null) {
                player.serverLevel().sendParticles(crown.particle(),
                        player.getX() + Math.cos(phase) * 0.34,
                        player.getY() + 2.2, player.getZ() + Math.sin(phase) * 0.34,
                        1, 0, 0, 0, 0);
            }
        }
    }

    private static void emit(ServerPlayer player, Category category) {
        if (!isEnabled(player.getUUID(), category)) return;
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
