package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import no.bangerfabrikken.blockparty.util.BlockPartyColors;
import no.bangerfabrikken.blockparty.util.BlockPartyText;
import no.bangerfabrikken.blockparty.sound.ModSounds;
import no.bangerfabrikken.blockparty.arena.MusicTrackManager.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class Arena {
    public enum Phase {
        WAITING, COUNTDOWN, PREPARE, FALL, INTERMISSION, FINISHING
    }

    private enum PowerUp {
        SPEED, NIGHT_VISION, JUMP
    }

    public static final int MAX_ROUNDS = 15;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 10;
    private static final int MAX_FLOOR_BLOCKS = 96 * 96;
    private static final int LOBBY_COUNTDOWN_TICKS = 1200;
    private static final int FALL_TICKS = 35;
    private static final int INTERMISSION_TICKS = 25; // 35 + 25 ticks = 3 seconds after the collapse begins
    private static final int[] ROUND_DURATION_TICKS = {
            200, 180, 160, 140, 120, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10
    };

    private final String id;
    private ServerLevel level;
    private BlockPos pos1;
    private BlockPos pos2;
    private BlockPos displayCenter;
    private double lobbyX;
    private double lobbyY;
    private double lobbyZ;
    private float lobbyYaw;
    private float lobbyPitch;
    private boolean lobbySet;

    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> pendingEliminations = new LinkedHashSet<>();
    private final Map<UUID, GameType> originalGameModes = new HashMap<>();
    private final Map<UUID, HeldItemSnapshot> originalHeldItems = new HashMap<>();
    private final Map<UUID, Set<Holder<MobEffect>>> roundPowerupEffects = new HashMap<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Integer> powerupsCollected = new HashMap<>();
    private final Map<BlockPos, BlockState> originalFloorSnapshot = new HashMap<>();
    private final Map<BlockPos, BlockState> floorSnapshot = new HashMap<>();
    private final Set<DyeColor> palette = EnumSet.noneOf(DyeColor.class);
    private final Random random = new Random();

    private Phase phase = Phase.WAITING;
    private int timerTicks;
    private int round;
    private DyeColor targetColor;
    private DyeColor previousTargetColor;
    private int previousPattern = -1;
    private boolean soloMode;
    private UUID lastEliminatedPlayer;

    /** Active beacon blocks are placed one block above five separate floor tiles. */
    private final Map<BlockPos, BlockState> activeBeacons = new HashMap<>();
    private final Map<UUID, String> musicVotes = new HashMap<>();
    private final Set<UUID> fullLobbyInventoryNotices = new HashSet<>();
    private String selectedMusicTrackId;
    private Objective sidebarObjective;
    private Objective previousSidebarObjective;
    private int scoreboardTick;
    private ResourceLocation activeMusicId;
    private long originalDayTime = Long.MIN_VALUE;
    private Boolean originalDaylightCycle;

    public Arena(String id, ServerLevel level) {
        this.id = id;
        this.level = level;
    }

    public String getId() { return id; }
    public ServerLevel getLevel() { return level; }
    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }
    public BlockPos getDisplayCenter() { return displayCenter; }
    public void setDisplayCenter(BlockPos displayCenter) { this.displayCenter = displayCenter == null ? null : displayCenter.immutable(); }
    public Phase getPhase() { return phase; }
    public int getPlayerCount() { return players.size(); }
    public boolean containsPlayer(UUID playerId) { return players.contains(playerId); }

    public boolean isConfigured() {
        return level != null && pos1 != null && pos2 != null && lobbySet;
    }

    public boolean isRunning() {
        return phase != Phase.WAITING;
    }

    public void setCorner(int corner, BlockPos pos) {
        if (corner == 1) pos1 = pos.immutable();
        else pos2 = pos.immutable();
    }

    public void setLobby(ServerPlayer player) {
        setLobby(player.serverLevel(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    public void setLobby(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        this.level = level;
        this.lobbyX = x;
        this.lobbyY = y;
        this.lobbyZ = z;
        this.lobbyYaw = yaw;
        this.lobbyPitch = pitch;
        this.lobbySet = true;
        refreshLobbyLeaderboard();
    }

    public boolean hasLobby() { return lobbySet; }
    public double getLobbyX() { return lobbyX; }
    public double getLobbyY() { return lobbyY; }
    public double getLobbyZ() { return lobbyZ; }
    public float getLobbyYaw() { return lobbyYaw; }
    public float getLobbyPitch() { return lobbyPitch; }

    public void join(ServerPlayer player) {
        if (!isConfigured()) {
            player.sendSystemMessage(Component.literal("Arenaen er ikke ferdig konfigurert. Bruk /bp generate <id> <størrelse>.").withStyle(ChatFormatting.RED));
            return;
        }
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) {
            player.sendSystemMessage(Component.literal("Denne runden har allerede startet. Vent til spillet er ferdig.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!players.contains(player.getUUID()) && players.size() >= MAX_PLAYERS) {
            player.sendSystemMessage(Component.literal("Denne kampen er full (maks " + MAX_PLAYERS + " spillere).").withStyle(ChatFormatting.RED));
            return;
        }
        if (!players.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Du er allerede med i denne arenaen.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        teleportToLobby(player);
        ensureLobbyItems(player);
        refreshLobbyLeaderboard();
        PlayerStatsManager.sendPlayerRank(player);
        player.sendSystemMessage(Component.literal("Du ble med i BlockParty-arenaen '" + id + "'. Spillere: " + players.size() + "/" + MAX_PLAYERS).withStyle(ChatFormatting.GREEN));
        if (players.size() < MIN_PLAYERS) {
            player.sendSystemMessage(Component.literal("Venter på minst én spiller til. Administrator kan bruke /bp forcestart " + id + " for å teste alene.").withStyle(ChatFormatting.YELLOW));
        }
        broadcast(Component.literal(player.getName().getString() + " ble med! (" + players.size() + "/" + MAX_PLAYERS + ")").withStyle(ChatFormatting.YELLOW));
    }

    public void leave(ServerPlayer player, boolean teleport) {
        UUID uuid = player.getUUID();
        boolean wasMember = players.remove(uuid);
        alive.remove(uuid);
        pendingEliminations.remove(uuid);
        removeLobbyItemsFromInventory(player);
        fullLobbyInventoryNotices.remove(uuid);
        musicVotes.remove(uuid);
        restorePlayerState(player);
        if (teleport && wasMember) {
            teleportToLobby(player);
            ensureLobbyItems(player);
        }

        if (wasMember) {
            player.sendSystemMessage(Component.literal("Du forlot BlockParty-arenaen '" + id + "'.").withStyle(ChatFormatting.GRAY));
            if (phase == Phase.COUNTDOWN && players.isEmpty()) {
                phase = Phase.WAITING;
                timerTicks = 0;
            } else if (isGameInProgress() && alive.isEmpty()) {
                declareWinner();
            } else if (isGameInProgress() && !soloMode && alive.size() == 1) {
                declareWinner();
            }
        }
    }

    public void handleLogout(ServerPlayer player) {
        leave(player, false);
    }

    public void start(MinecraftServer server) {
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) {
            throw new IllegalStateException("Arenaen kjører allerede.");
        }
        players.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        if (players.isEmpty()) {
            throw new IllegalStateException("Ingen tilkoblede spillere er med i arenaen.");
        }

        captureFloor();
        if (originalDayTime == Long.MIN_VALUE) {
            originalDayTime = level.getDayTime();
            originalDaylightCycle = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        }
        level.setDayTime(6_000L);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        alive.clear();
        alive.addAll(players);
        pendingEliminations.clear();
        lastEliminatedPlayer = null;
        originalGameModes.clear();
        originalHeldItems.clear();
        points.clear();
        powerupsCollected.clear();
        for (UUID uuid : players) {
            points.put(uuid, 0);
            powerupsCollected.put(uuid, 0);
        }
        round = 0;
        previousTargetColor = null;
        previousPattern = -1;
        soloMode = players.size() == 1;
        activeBeacons.clear();
        activeMusicId = null;
        selectedMusicTrackId = selectWinningSong();
        musicVotes.clear();

        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                originalGameModes.put(uuid, player.gameMode.getGameModeForPlayer());
                removeLobbyItemsFromInventory(player);
                int selectedSlot = player.getInventory().selected;
                originalHeldItems.put(uuid, new HeldItemSnapshot(selectedSlot, player.getInventory().getItem(selectedSlot).copy()));
                player.setGameMode(GameType.ADVENTURE);
                teleportToRandomFloorTile(player);
            }
        }

        showScoreboard(server);
        beginRound(server);
    }

    private void updateColorDisplay() {
        if (displayCenter == null || targetColor == null || level == null) return;
        String tag = "bp_target_" + id;
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                displayCenter.getX() - 64, displayCenter.getY() - 16, displayCenter.getZ() - 8,
                displayCenter.getX() + 64, displayCenter.getY() + 16, displayCenter.getZ() + 8);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                entity -> entity.getTags().contains(tag))) old.discard();

        ChatFormatting textColor = switch (targetColor) {
            case WHITE, LIGHT_GRAY, YELLOW, LIME -> ChatFormatting.WHITE;
            case ORANGE, BROWN -> ChatFormatting.GOLD;
            case MAGENTA, PINK, PURPLE -> ChatFormatting.LIGHT_PURPLE;
            case LIGHT_BLUE, CYAN -> ChatFormatting.AQUA;
            case GRAY, BLACK -> ChatFormatting.WHITE;
            case BLUE -> ChatFormatting.BLUE;
            case GREEN -> ChatFormatting.GREEN;
            case RED -> ChatFormatting.RED;
        };
        Component text = Component.literal(BlockPartyColors.displayName(targetColor))
                .withStyle(textColor, ChatFormatting.BOLD);
        // The north-wall screen faces south into the arena. Text Display has a single, fixed readable face.
        BlockPartyText.spawnTextDisplay(level, displayCenter.getX() + 0.5, displayCenter.getY() - 2.2,
                displayCenter.getZ() + 0.3, 0.0F, text, 7.0F, tag);
    }

    public boolean handleBeaconInteraction(ServerPlayer player, BlockPos clickedPos) {
        if (!activeBeacons.containsKey(clickedPos) || phase != Phase.PREPARE) return false;
        if (!players.contains(player.getUUID()) || !alive.contains(player.getUUID())) return false;

        BlockState restore = activeBeacons.remove(clickedPos);
        level.setBlock(clickedPos, restore == null ? Blocks.AIR.defaultBlockState() : restore, 3);
        PowerUp powerUp = PowerUp.values()[random.nextInt(PowerUp.values().length)];
        Holder<MobEffect> effect;
        int amplifier;
        String label;
        switch (powerUp) {
            case SPEED -> { effect = MobEffects.MOVEMENT_SPEED; amplifier = 1; label = "SPEED BOOST"; }
            case NIGHT_VISION -> { effect = MobEffects.NIGHT_VISION; amplifier = 0; label = "NIGHT VISION"; }
            case JUMP -> { effect = MobEffects.JUMP; amplifier = 1; label = "JUMP BOOST"; }
            default -> throw new IllegalStateException("Unknown power-up");
        }
        if (player.hasEffect(effect)) {
            player.sendSystemMessage(Component.literal("Du har allerede denne effekten. Beaconen er brukt opp.").withStyle(ChatFormatting.YELLOW));
        } else {
            // Removed at the end of this round; the long vanilla duration is only a safety net.
            player.addEffect(new MobEffectInstance(effect, 20 * 60, amplifier, false, true, true));
            roundPowerupEffects.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(effect);
            powerupsCollected.merge(player.getUUID(), 1, Integer::sum);
            player.sendSystemMessage(Component.literal("POWER-UP: " + label + " – varer ut denne runden!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            broadcast(Component.literal(player.getName().getString() + " plukket opp " + label + "!")
                    .withStyle(ChatFormatting.AQUA));
        }
        playSound(ModSounds.POWERUP_PICKUP, SoundSource.PLAYERS, 0.65F, 1.0F);
        updateScoreboard(level.getServer());
        return true;
    }

    private void spawnPowerUpBeacons(int requested) {
        if (level == null || floorSnapshot.isEmpty() || !activeBeacons.isEmpty()) return;
        List<BlockPos> candidates = new ArrayList<>(floorSnapshot.keySet());
        Collections.shuffle(candidates, random);
        List<BlockPos> chosenFloors = new ArrayList<>();
        for (BlockPos candidate : candidates) {
            if (!BlockPartyColors.isConcrete(level.getBlockState(candidate))) continue;
            boolean occupied = false;
            for (UUID uuid : alive) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && player.blockPosition().getX() == candidate.getX()
                        && player.blockPosition().getZ() == candidate.getZ()) { occupied = true; break; }
            }
            if (occupied) continue;
            boolean tooClose = false;
            for (BlockPos other : chosenFloors) {
                if (other.distSqr(candidate) < 8.0 * 8.0) { tooClose = true; break; }
            }
            if (tooClose) continue;
            BlockPos beaconPos = candidate.above();
            if (!level.isEmptyBlock(beaconPos)) continue;
            chosenFloors.add(candidate.immutable());
            activeBeacons.put(beaconPos.immutable(), level.getBlockState(beaconPos));
            level.setBlock(beaconPos, Blocks.BEACON.defaultBlockState(), 3);
            if (chosenFloors.size() >= requested) break;
        }
        if (!chosenFloors.isEmpty()) {
            broadcast(Component.literal("✦ " + chosenFloors.size() + " POWER-UPS HAR SPAWNET! Høyreklikk en beacon!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            playSound(ModSounds.BEACON_SPAWN, SoundSource.PLAYERS, 0.65F, 1.0F);
        }
    }

    private void removeActiveBeacon() {
        if (level == null || activeBeacons.isEmpty()) return;
        for (Map.Entry<BlockPos, BlockState> entry : new HashMap<>(activeBeacons).entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue() == null ? Blocks.AIR.defaultBlockState() : entry.getValue(), 3);
        }
        activeBeacons.clear();
    }

    private void clearActivePowerUps(MinecraftServer server) {
        for (Map.Entry<UUID, Set<Holder<MobEffect>>> entry : new HashMap<>(roundPowerupEffects).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) for (Holder<MobEffect> effect : entry.getValue()) player.removeEffect(effect);
        }
        roundPowerupEffects.clear();
    }

    private void playSound(Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        if (level == null) return;
        for (UUID uuid : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.connection.send(new ClientboundSoundPacket(sound, source, player.getX(), player.getY(), player.getZ(), volume, pitch, random.nextLong()));
            }
        }
    }

    private String selectWinningSong() {
        List<Track> tracks = MusicTrackManager.getTracks();
        if (tracks.isEmpty()) return null;
        Map<String, Integer> counts = new HashMap<>();
        for (String id : musicVotes.values()) counts.merge(id, 1, Integer::sum);
        if (counts.isEmpty()) return tracks.get(random.nextInt(tracks.size())).id();
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> winners = counts.entrySet().stream().filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey).toList();
        return winners.get(random.nextInt(winners.size()));
    }

    private void playSelectedMusic() {
        stopMusic();
        Track track = selectedMusicTrackId == null ? null : MusicTrackManager.get(selectedMusicTrackId);
        if (track == null) return;
        activeMusicId = track.soundId();
        playSound(track.sound(), SoundSource.MUSIC, 0.38F, 1.0F);
    }

    private void stopMusic() {
        if (activeMusicId == null || level == null) return;
        for (UUID uuid : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) player.connection.send(new ClientboundStopSoundPacket(activeMusicId, SoundSource.MUSIC));
        }
        activeMusicId = null;
    }

    public static boolean isMusicVotingBook(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.BOOK)) return false;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && "BlockParty Music Vote".equals(customName.getString());
    }

    /** The lobby play token is an ordinary nether star with a unique custom name. */
    public static boolean isJoinGameItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.NETHER_STAR)) return false;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && "BlockParty Join Game".equals(customName.getString());
    }

    /** True only while this player is physically inside this arena's lobby area. */
    public boolean isLobbyAccessibleTo(ServerPlayer player) {
        if (!lobbySet || level == null || player.serverLevel() != level) return false;
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) return false;
        return Math.abs(player.getX() - lobbyX) <= 36.0
                && Math.abs(player.getZ() - lobbyZ) <= 40.0
                && Math.abs(player.getY() - lobbyY) <= 22.0;
    }

    /** Give the non-interactive wall instructions' matching inventory items to lobby visitors. */
    public void ensureLobbyItems(ServerPlayer player) {
        if (!lobbySet || level == null || player.serverLevel() != level) return;
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) return;
        giveUniqueLobbyItem(player, createJoinGameItem(), Arena::isJoinGameItem);
        giveMusicVotingBook(player);
        if (hasLobbyItem(player, Arena::isJoinGameItem) && hasLobbyItem(player, Arena::isMusicVotingBook)) {
            fullLobbyInventoryNotices.remove(player.getUUID());
        }
    }

    private static boolean hasLobbyItem(ServerPlayer player, java.util.function.Predicate<ItemStack> matcher) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (matcher.test(player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private void giveUniqueLobbyItem(ServerPlayer player, ItemStack item,
                                     java.util.function.Predicate<ItemStack> matcher) {
        if (hasLobbyItem(player, matcher)) return;
        int slot = -1;
        // Prefer the hotbar so the join control is immediately visible, then use main inventory.
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) { slot = i; break; }
        }
        if (slot < 0) {
            for (int i = 9; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).isEmpty()) { slot = i; break; }
            }
        }
        if (slot < 0) {
            if (fullLobbyInventoryNotices.add(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Inventaret er fullt. Frigjør to plasser for BlockParty Join-stjernen og Music Vote-boken.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        player.getInventory().setItem(slot, item);
    }

    private ItemStack createJoinGameItem() {
        ItemStack star = new ItemStack(Items.NETHER_STAR);
        star.set(DataComponents.CUSTOM_NAME, Component.literal("BlockParty Join Game")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        return star;
    }

    private void giveMusicVotingBook(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isMusicVotingBook(player.getInventory().getItem(i))) return;
        }
        ItemStack book = createMusicVotingBook();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) { slot = i; break; }
        }
        if (slot < 0) {
            for (int i = 9; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).isEmpty()) { slot = i; break; }
            }
        }
        if (slot < 0) {
            if (fullLobbyInventoryNotices.add(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Inventaret er fullt. Frigjør plass til Music Vote-boken.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        player.getInventory().setItem(slot, book);
    }

    private ItemStack createMusicVotingBook() {
        ItemStack book = new ItemStack(Items.BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.literal("BlockParty Music Vote")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        return book;
    }

    private void removeLobbyItemsFromInventory(ServerPlayer player) {
        int selected = player.getInventory().selected;
        boolean removedSelected = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isMusicVotingBook(stack) || isJoinGameItem(stack)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                if (i == selected) removedSelected = true;
            }
        }
        if (removedSelected) {
            int replacement = 0;
            for (int i = 0; i < 9; i++) {
                if (!player.getInventory().getItem(i).isEmpty()) { replacement = i; break; }
            }
            player.getInventory().selected = replacement;
        }
    }

    public void openMusicVoting(ServerPlayer player) {
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) {
            player.sendSystemMessage(Component.literal("Musikkavstemningen er stengt. Stem før kampen starter.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.literal("BLOCKPARTY MUSIC VOTING").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Klikk på en sang for å stemme. Du kan endre stemmen frem til kampen starter.")
                .withStyle(ChatFormatting.GRAY));
        String currentVote = musicVotes.get(player.getUUID());
        for (Track track : MusicTrackManager.getTracks()) {
            long votes = musicVotes.values().stream().filter(track.id()::equals).count();
            Component choice = Component.literal("▶ " + track.name() + " (" + votes + " votes)")
                    .withStyle(style -> style.withColor(0x55DDE5).withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bp vote " + track.id()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Klikk for å stemme på " + track.name()))));
            if (track.id().equals(currentVote)) choice = choice.copy().append(Component.literal("  ✓ DIN STEMME")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            player.sendSystemMessage(choice);
        }
        if (currentVote == null) player.sendSystemMessage(Component.literal("Du har ikke stemt ennå.").withStyle(ChatFormatting.GRAY));
    }

    public boolean voteMusic(ServerPlayer player, String trackId) {
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) {
            player.sendSystemMessage(Component.literal("Avstemningen er stengt.").withStyle(ChatFormatting.RED));
            return false;
        }
        Track track = MusicTrackManager.get(trackId);
        if (track == null) {
            player.sendSystemMessage(Component.literal("Fant ikke sangen " + trackId + ".").withStyle(ChatFormatting.RED));
            return false;
        }
        musicVotes.put(player.getUUID(), track.id());
        player.sendSystemMessage(Component.literal("Du stemte på " + track.name() + "!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        return true;
    }

    private void captureFloor() {
        if (!isConfigured()) throw new IllegalStateException("Arenaen mangler pos1, pos2 eller lobby.");
        if (pos1.getY() != pos2.getY()) throw new IllegalStateException("Pos1 og pos2 må være på samme høyde (samme Y-koordinat).");
        if (level == null) throw new IllegalStateException("Arenaens verden er ikke tilgjengelig.");

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        long area = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (area > MAX_FLOOR_BLOCKS) {
            throw new IllegalStateException("Gulvet er for stort. Maks størrelse er " + MAX_FLOOR_BLOCKS + " blokker.");
        }

        originalFloorSnapshot.clear();
        floorSnapshot.clear();
        palette.clear();
        int floorY = pos1.getY();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, floorY, z);
                BlockState state = level.getBlockState(pos);
                DyeColor color = BlockPartyColors.getColor(state);
                if (color == null || !BlockPartyColors.isConcrete(state)) {
                    originalFloorSnapshot.clear();
                    floorSnapshot.clear();
                    palette.clear();
                    throw new IllegalStateException("Dansegulvet må bestå av farget concrete (ikke ull eller terrakotta). Fant "
                            + state.getBlock().getName().getString() + " ved " + pos.toShortString() + ".");
                }
                originalFloorSnapshot.put(pos.immutable(), state);
                floorSnapshot.put(pos.immutable(), state);
                palette.add(color);
            }
        }
        if (palette.size() < 2) throw new IllegalStateException("Gulvet må inneholde minst to forskjellige farger.");
    }

    public void tick(MinecraftServer server) {
        scoreboardTick++;
        switch (phase) {
            case WAITING -> {
                if (players.size() >= MIN_PLAYERS) {
                    phase = Phase.COUNTDOWN;
                    timerTicks = LOBBY_COUNTDOWN_TICKS;
                    broadcast(Component.literal("BlockParty starter om 60 sekunder! " + players.size() + "/" + MAX_PLAYERS + " spillere er påmeldt.").withStyle(ChatFormatting.GREEN));
                    playSound(ModSounds.ROUND_START, SoundSource.PLAYERS, 0.7F, 0.85F);
                }
            }
            case COUNTDOWN -> {
                if (players.size() < MIN_PLAYERS) {
                    phase = Phase.WAITING;
                    timerTicks = 0;
                    broadcast(Component.literal("Nedtellingen er stoppet – BlockParty trenger minst 2 spillere. Administrator kan bruke /bp forcestart " + id + ".").withStyle(ChatFormatting.YELLOW));
                    break;
                }
                timerTicks--;
                if (timerTicks % 20 == 0 && timerTicks > 0) {
                    broadcastActionBar(Component.literal("Spillet starter om " + (timerTicks / 20) + " sekunder").withStyle(ChatFormatting.YELLOW));
                }
                if (timerTicks <= 0) {
                    try {
                        start(server);
                    } catch (IllegalStateException exception) {
                        phase = Phase.WAITING;
                        timerTicks = 0;
                        broadcast(Component.literal("Kunne ikke starte BlockParty: " + exception.getMessage()).withStyle(ChatFormatting.RED));
                    }
                }
            }
            case PREPARE -> {
                timerTicks--;
                if (timerTicks > 0 && timerTicks % 2 == 0) broadcastTargetHud();
                if (timerTicks <= 0) resolveRound();
            }
            case FALL -> {
                timerTicks--;
                List<UUID> fallOrder = new ArrayList<>(pendingEliminations);
                // Lowest in the pit is processed first; the one still highest is treated as the latest fall.
                fallOrder.sort(java.util.Comparator.comparingDouble(uuid -> {
                    ServerPlayer candidate = server.getPlayerList().getPlayer(uuid);
                    return candidate == null ? Double.NEGATIVE_INFINITY : candidate.getY();
                }));
                for (UUID uuid : fallOrder) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player == null) {
                        pendingEliminations.remove(uuid);
                        alive.remove(uuid);
                    } else if (player.getY() < pos1.getY() - 2.5) {
                        eliminate(player);
                    }
                }
                if (timerTicks <= 0) {
                    List<UUID> remainingOrder = new ArrayList<>(pendingEliminations);
                    remainingOrder.sort(java.util.Comparator.comparingDouble(uuid -> {
                        ServerPlayer candidate = server.getPlayerList().getPlayer(uuid);
                        return candidate == null ? Double.NEGATIVE_INFINITY : candidate.getY();
                    }));
                    for (UUID uuid : remainingOrder) {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        if (player != null) eliminate(player);
                        else {
                            pendingEliminations.remove(uuid);
                            alive.remove(uuid);
                        }
                    }
                    finishRound();
                }
            }
            case INTERMISSION -> {
                timerTicks--;
                if (timerTicks <= 0) beginRound(server);
            }
            case FINISHING -> {
                timerTicks--;
                if (timerTicks <= 0) endGame(server);
            }
        }
        if (phase != Phase.WAITING && scoreboardTick % 5 == 0) updateScoreboard(server);
    }

    private void beginRound(MinecraftServer server) {
        if (alive.isEmpty()) {
            declareWinner();
            return;
        }
        if (round >= MAX_ROUNDS) {
            declareWinner();
            return;
        }

        clearActivePowerUps(server);
        removeActiveBeacon();
        round++;
        Set<DyeColor> roundColors = randomizeFloorForRound();
        if (roundColors.isEmpty()) {
            declareWinner();
            return;
        }
        List<DyeColor> choices = new ArrayList<>(roundColors);
        targetColor = choices.get(random.nextInt(choices.size()));
        if (choices.size() > 1 && targetColor == previousTargetColor) {
            choices.remove(targetColor);
            targetColor = choices.get(random.nextInt(choices.size()));
        }
        previousTargetColor = targetColor;

        updateTargetItems(server);
        updateColorDisplay();
        timerTicks = ROUND_DURATION_TICKS[round - 1];
        phase = Phase.PREPARE;
        broadcastTargetHud();
        playSound(ModSounds.COLOR_REVEAL, SoundSource.PLAYERS, 0.8F, 1.0F);
        playSelectedMusic();
        playSound(ModSounds.ROUND_START, SoundSource.PLAYERS, 0.55F, 1.0F);
        // Five spaced beacons are present from the first moment of each round.
        spawnPowerUpBeacons(5);
        updateScoreboard(server);
    }

    private void broadcastTargetHud() {
        if (targetColor == null || level == null) return;
        ChatFormatting color = switch (targetColor) {
            case WHITE -> ChatFormatting.WHITE;
            case ORANGE -> ChatFormatting.GOLD;
            case MAGENTA, PINK -> ChatFormatting.LIGHT_PURPLE;
            case LIGHT_BLUE, CYAN -> ChatFormatting.AQUA;
            case YELLOW -> ChatFormatting.YELLOW;
            case LIME -> ChatFormatting.GREEN;
            case GRAY -> ChatFormatting.DARK_GRAY;
            case LIGHT_GRAY -> ChatFormatting.GRAY;
            case PURPLE -> ChatFormatting.DARK_PURPLE;
            case BLUE -> ChatFormatting.BLUE;
            case BROWN -> ChatFormatting.GOLD;
            case GREEN -> ChatFormatting.DARK_GREEN;
            case RED -> ChatFormatting.RED;
            case BLACK -> ChatFormatting.DARK_GRAY;
        };
        int remainingSeconds = Math.max(1, Math.min(10, (timerTicks + 19) / 20));
        String filled = "■".repeat(remainingSeconds);
        String empty = "■".repeat(10 - remainingSeconds);
        Component hud = Component.literal("      ").append(Component.literal(BlockPartyColors.displayName(targetColor))
                .withStyle(color, ChatFormatting.BOLD))
                .append(Component.literal("  " + formatSeconds(Math.max(0, timerTicks)) + "s  ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(filled).withStyle(color, ChatFormatting.BOLD))
                .append(Component.literal(empty).withStyle(ChatFormatting.DARK_GRAY));
        for (UUID uuid : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) player.displayClientMessage(hud, true);
        }
    }

    private void showScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective current = scoreboard.getObjective("bp_match");
        if (current == null) {
            current = scoreboard.addObjective("bp_match", ObjectiveCriteria.DUMMY,
                    Component.literal("BlockParty").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        } else {
            current.setDisplayName(Component.literal("BlockParty").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }
        if (sidebarObjective == null) {
            previousSidebarObjective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            sidebarObjective = current;
        }
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, sidebarObjective);
        updateScoreboard(server);
    }

    private void updateScoreboard(MinecraftServer server) {
        if (sidebarObjective == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, sidebarObjective);

        ServerPlayer profilePlayer = null;
        for (UUID uuid : players) {
            profilePlayer = server.getPlayerList().getPlayer(uuid);
            if (profilePlayer != null) break;
        }
        int totalPoints = profilePlayer == null ? 0 : PlayerStatsManager.getPoints(profilePlayer.getUUID());
        PlayerStatsManager.Rank rank = PlayerStatsManager.getRank(totalPoints);
        int personalPowerups = profilePlayer == null ? 0 : powerupsCollected.getOrDefault(profilePlayer.getUUID(), 0);
        String timeText = (phase == Phase.PREPARE || phase == Phase.FALL || phase == Phase.INTERMISSION)
                ? formatSeconds(Math.max(0, timerTicks)) + "s" : "--";
        String nextRankText = rank.isMaxRank() ? "MAX RANK" : rank.pointsToNext(totalPoints) + " pts";

        PlayerStatsManager.PlayerRecord profile = profilePlayer == null
                ? null : PlayerStatsManager.getRecord(profilePlayer.getUUID());
        int currentStreak = profile == null ? 0 : profile.currentStreak();
        int bestStreak = profile == null ? 0 : profile.bestStreak();
        String[] ids = {
                "bp_line_0", "bp_line_1", "bp_line_2", "bp_line_3", "bp_line_4", "bp_line_5",
                "bp_line_6", "bp_line_7", "bp_line_8", "bp_line_9", "bp_line_10", "bp_line_11",
                "bp_line_12", "bp_line_13"
        };
        Component[] lines = {
                Component.literal("Dancers Left").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                Component.literal(Integer.toString(alive.size())).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Game Info").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.literal("Round: " + round + " / " + MAX_ROUNDS).withStyle(ChatFormatting.WHITE),
                Component.literal("Round Speed: " + timeText).withStyle(ChatFormatting.GRAY),
                Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Your Stats").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal("Points: " + totalPoints).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                Component.literal("Rank: " + rank.name()).withStyle(ChatFormatting.AQUA),
                Component.literal("Next Rank: " + nextRankText).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("Streak: " + currentStreak).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Best Streak: " + bestStreak).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("Powerups: " + personalPowerups).withStyle(ChatFormatting.GRAY)
        };
        for (int i = 0; i < ids.length; i++) {
            ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(ids[i]), sidebarObjective);
            score.set(ids.length - i);
            score.display(lines[i]);
            score.numberFormatOverride(BlankFormat.INSTANCE);
        }
    }

    private void hideScoreboard(MinecraftServer server) {
        if (sidebarObjective == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        if (scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) == sidebarObjective) {
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, previousSidebarObjective);
        }
        sidebarObjective = null;
        previousSidebarObjective = null;
    }

    /** Repaints the same tile positions with a newly randomized color arrangement and pattern. */
    private Set<DyeColor> randomizeFloorForRound() {
        List<DyeColor> colors = new ArrayList<>(palette);
        Collections.shuffle(colors, random);
        if (colors.isEmpty()) return Set.of();

        int minX = Math.min(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int pattern = random.nextInt(5);
        if (pattern == previousPattern) pattern = (pattern + 1 + random.nextInt(4)) % 5;
        previousPattern = pattern;
        int offset = random.nextInt(colors.size());
        int bandWidth = 1 + random.nextInt(3);
        Map<Long, DyeColor> patchColors = new HashMap<>();
        Set<DyeColor> used = EnumSet.noneOf(DyeColor.class);

        for (BlockPos pos : new ArrayList<>(originalFloorSnapshot.keySet())) {
            int x = pos.getX() - minX;
            int z = pos.getZ() - minZ;
            DyeColor color;
            switch (pattern) {
                case 1 -> color = colors.get(Math.floorMod((x / bandWidth) + offset, colors.size())); // vertical bands
                case 2 -> color = colors.get(Math.floorMod((z / bandWidth) + offset, colors.size())); // horizontal bands
                case 3 -> {
                    long patchKey = (((long) (x / 2)) << 32) | (z / 2 & 0xffffffffL);
                    color = patchColors.computeIfAbsent(patchKey, ignored -> colors.get(random.nextInt(colors.size())));
                }
                case 4 -> color = colors.get(Math.floorMod(x + z + (x / 3) * 2 + offset, colors.size())); // diagonal mosaic
                default -> color = colors.get(random.nextInt(colors.size())); // scattered mosaic
            }
            BlockState newState = BlockPartyColors.getConcrete(color).defaultBlockState();
            level.setBlock(pos, newState, 3);
            floorSnapshot.put(pos, newState);
            used.add(color);
        }
        return used;
    }

    private void updateTargetItems(MinecraftServer server) {
        if (targetColor == null) return;
        for (UUID uuid : alive) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            HeldItemSnapshot snapshot = originalHeldItems.get(uuid);
            if (player == null || snapshot == null) continue;

            player.getInventory().selected = snapshot.slot();
            ItemStack targetBlock = new ItemStack(BlockPartyColors.getConcrete(targetColor));
            targetBlock.set(DataComponents.CUSTOM_NAME, Component.literal(BlockPartyColors.displayName(targetColor))
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
            player.getInventory().setItem(snapshot.slot(), targetBlock);
        }
    }

    private void resolveRound() {
        pendingEliminations.clear();
        for (UUID uuid : alive) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                pendingEliminations.add(uuid);
                continue;
            }
            BlockPos tile = new BlockPos(player.blockPosition().getX(), pos1.getY(), player.blockPosition().getZ());
            DyeColor standingColor = BlockPartyColors.getColor(level.getBlockState(tile));
            if (standingColor != targetColor) pendingEliminations.add(uuid);
        }

        stopMusic();
        for (UUID uuid : alive) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) player.displayClientMessage(Component.empty(), true);
        }
        playSound(ModSounds.ROUND_END, SoundSource.PLAYERS, 0.65F, 1.0F);
        removeActiveBeacon();
        clearActivePowerUps(level.getServer());

        // Remove every tile except the announced color. The arena generator leaves a fall pit below.
        for (Map.Entry<BlockPos, BlockState> entry : floorSnapshot.entrySet()) {
            DyeColor color = BlockPartyColors.getColor(entry.getValue());
            if (color != targetColor) level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), 3);
        }
        phase = Phase.FALL;
        timerTicks = FALL_TICKS;
        broadcastActionBar(Component.literal("FEIL FARGE FORSVINNER!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private void eliminate(ServerPlayer player) {
        UUID uuid = player.getUUID();
        lastEliminatedPlayer = uuid;
        if (!alive.remove(uuid)) {
            pendingEliminations.remove(uuid);
            return;
        }
        pendingEliminations.remove(uuid);
        // Cosmetic lightning strikes at the hole in the floor; visual-only prevents damage/fire to others.
        if (level != null) {
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            bolt.setVisualOnly(true);
            bolt.setPos(player.getX(), pos1.getY() + 0.15, player.getZ());
            level.addFreshEntity(bolt);
        }
        broadcast(Component.literal(player.getName().getString() + " tapte konkurransen!")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        PlayerStatsManager.breakStreak(level.getServer(), uuid);
        restoreHeldItemOnly(player);
        player.setGameMode(GameType.ADVENTURE);
        teleportToSpectatorWalkway(player);
        player.sendSystemMessage(Component.literal("Du er ute! Du kan følge kampen fra spectatorgangen.")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        playSound(ModSounds.ELIMINATION, SoundSource.PLAYERS, 0.55F, 1.0F);
    }

    private void finishRound() {
        // Each player who survives this round earns one permanent world-wide point.
        for (UUID uuid : alive) {
            points.merge(uuid, 1, Integer::sum);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) PlayerStatsManager.recordRoundSurvived(level.getServer(), player, round);
        }
        clearActivePowerUps(level.getServer());
        removeActiveBeacon();
        stopMusic();

        // The remaining target-colour tiles stay visible for the full 3-second transition.
        // FALL_TICKS (35) + INTERMISSION_TICKS (25) = 60 ticks; the next pattern replaces them in-place.
        broadcastActionBar(Component.literal("RUNDE " + round + " FULLFØRT  •  " + alive.size() + " IGJEN")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        refreshLobbyLeaderboard();

        if (alive.isEmpty()) {
            declareWinner();
        } else if (soloMode) {
            if (round >= MAX_ROUNDS) declareWinner();
            else {
                phase = Phase.INTERMISSION;
                timerTicks = INTERMISSION_TICKS;
            }
        } else if (alive.size() <= 1 || round >= MAX_ROUNDS) {
            declareWinner();
        } else {
            phase = Phase.INTERMISSION;
            timerTicks = INTERMISSION_TICKS;
        }
        updateScoreboard(level.getServer());
    }

    private void declareWinner() {
        if (phase == Phase.FINISHING) return;
        stopMusic();
        playSound(ModSounds.VICTORY, SoundSource.PLAYERS, 1.0F, 1.0F);
        clearActivePowerUps(level.getServer());
        removeActiveBeacon();
        restoreOriginalFloor();

        ServerPlayer winner = null;
        if (alive.size() == 1) {
            UUID winnerId = alive.iterator().next();
            winner = level.getServer().getPlayerList().getPlayer(winnerId);
        } else if (alive.isEmpty() && lastEliminatedPlayer != null && players.size() >= MIN_PLAYERS) {
            // If the final two players fall in the same resolution window, the last processed fall wins.
            winner = level.getServer().getPlayerList().getPlayer(lastEliminatedPlayer);
        } else if (round >= MAX_ROUNDS && !alive.isEmpty()) {
            winner = alive.stream().map(uuid -> level.getServer().getPlayerList().getPlayer(uuid))
                    .filter(java.util.Objects::nonNull)
                    .max(java.util.Comparator.comparingInt(player -> points.getOrDefault(player.getUUID(), 0)))
                    .orElse(null);
        }

        if (winner != null) {
            PlayerStatsManager.recordWin(level.getServer(), winner);
            level.sendParticles(ParticleTypes.FIREWORK, winner.getX(), winner.getY() + 1.5, winner.getZ(),
                    90, 2.4, 2.0, 2.4, 0.18);
            level.sendParticles(ParticleTypes.END_ROD, winner.getX(), winner.getY() + 1.0, winner.getZ(),
                    36, 1.4, 1.2, 1.4, 0.04);
            ServerPlayer mvp = players.stream()
                    .map(uuid -> level.getServer().getPlayerList().getPlayer(uuid))
                    .filter(java.util.Objects::nonNull)
                    .max(java.util.Comparator
                            .comparingInt((ServerPlayer candidate) -> points.getOrDefault(candidate.getUUID(), 0))
                            .thenComparingInt(candidate -> powerupsCollected.getOrDefault(candidate.getUUID(), 0)))
                    .orElse(winner);
            PlayerStatsManager.recordMvp(level.getServer(), mvp);
            Component title = Component.literal(winner.getName().getString())
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            Component subtitle = Component.literal("is the winner of this game!")
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD, ChatFormatting.ITALIC);
            for (UUID uuid : players) {
                ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(uuid);
                if (viewer == null) continue;
                viewer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
                viewer.connection.send(new ClientboundSetTitleTextPacket(title));
                viewer.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
            broadcast(Component.literal("🏆 " + winner.getName().getString() + " VANT BLOCK PARTY! 🏆")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            broadcast(Component.literal("MVP: " + mvp.getName().getString() + "  •  "
                    + points.getOrDefault(mvp.getUUID(), 0) + " runder overlevd  •  "
                    + powerupsCollected.getOrDefault(mvp.getUUID(), 0) + " power-ups")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        } else if (alive.isEmpty()) {
            broadcast(Component.literal("BlockParty er ferdig – ingen overlevde denne gangen.").withStyle(ChatFormatting.GRAY));
        } else {
            broadcast(Component.literal("🏆 15 RUNDER FULLFØRT! Alle gjenværende spillere vinner! 🏆")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        phase = Phase.FINISHING;
        timerTicks = 120;
    }

    public void stop(MinecraftServer server) {
        if (phase == Phase.WAITING && players.isEmpty()) return;
        broadcast(Component.literal("BlockParty ble stoppet av en administrator.").withStyle(ChatFormatting.RED));
        endGame(server);
    }

    private void endGame(MinecraftServer server) {
        stopMusic();
        hideScoreboard(server);
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) player.displayClientMessage(Component.empty(), true);
        }
        clearActivePowerUps(server);
        removeActiveBeacon();
        restoreOriginalFloor();
        Set<UUID> returningPlayers = new HashSet<>(players);
        for (UUID uuid : returningPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                restorePlayerState(player);
                teleportToLobby(player);
                player.sendSystemMessage(Component.literal("Takk for at du spilte BlockParty!").withStyle(ChatFormatting.GREEN));
            }
        }
        players.clear();
        alive.clear();
        pendingEliminations.clear();
        originalGameModes.clear();
        originalHeldItems.clear();
        musicVotes.clear();
        selectedMusicTrackId = null;
        points.clear();
        powerupsCollected.clear();
        phase = Phase.WAITING;
        timerTicks = 0;
        for (UUID uuid : returningPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) ensureLobbyItems(player);
        }
        round = 0;
        targetColor = null;
        previousTargetColor = null;
        previousPattern = -1;
        soloMode = false;
        lastEliminatedPlayer = null;
        if (level != null && originalDayTime != Long.MIN_VALUE) {
            level.setDayTime(originalDayTime);
            if (originalDaylightCycle != null) {
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalDaylightCycle, server);
            }
            originalDayTime = Long.MIN_VALUE;
            originalDaylightCycle = null;
        }
        refreshLobbyLeaderboard();
    }

    private void restorePlayerState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Set<Holder<MobEffect>> effects = roundPowerupEffects.remove(uuid);
        if (effects != null) for (Holder<MobEffect> effect : effects) player.removeEffect(effect);
        GameType oldMode = originalGameModes.remove(uuid);
        if (oldMode != null) player.setGameMode(oldMode);
        HeldItemSnapshot held = originalHeldItems.remove(uuid);
        if (held != null) {
            player.getInventory().selected = held.slot();
            player.getInventory().setItem(held.slot(), held.stack().copy());
        }
    }

    private void restoreHeldItemOnly(ServerPlayer player) {
        HeldItemSnapshot held = originalHeldItems.get(player.getUUID());
        if (held == null) return;
        player.getInventory().selected = held.slot();
        player.getInventory().setItem(held.slot(), held.stack().copy());
    }

    private void teleportToSpectatorWalkway(ServerPlayer player) {
        if (level == null || pos1 == null || pos2 == null) return;
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int x;
        int z;
        // Preserve the side/quadrant from which the player fell, then place them on the raised ring.
        if (Math.abs(player.getX() - centerX) > Math.abs(player.getZ() - centerZ)) {
            x = player.getX() < centerX ? minX - 6 : maxX + 6;
            z = Math.max(minZ - 2, Math.min(maxZ + 2, player.blockPosition().getZ()));
        } else {
            z = player.getZ() < centerZ ? minZ - 6 : maxZ + 6;
            x = Math.max(minX - 2, Math.min(maxX + 2, player.blockPosition().getX()));
        }
        int y = pos1.getY() + ArenaGenerator.SPECTATOR_HEIGHT + 1;
        player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), 0.0F);
    }

    private void restoreOriginalFloor() {
        if (level == null) return;
        for (Map.Entry<BlockPos, BlockState> entry : originalFloorSnapshot.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        floorSnapshot.clear();
        floorSnapshot.putAll(originalFloorSnapshot);
    }

    /** Refreshes a large, full-wall leaderboard built from correctly oriented Text Display entities. */
    private void refreshLobbyLeaderboard() {
        if (level == null || !lobbySet) return;
        double boardX = lobbyX + 30.0;
        double topY = lobbyY + 13.0;
        double boardZ = lobbyZ + 8.0;
        BlockPos anchor = BlockPos.containing(boardX, topY, boardZ);
        if (!level.hasChunkAt(anchor)) return;

        String tag = "blockparty_lb_" + id;
        AABB area = new AABB(boardX - 5.0, lobbyY - 1.0, boardZ - 26.0,
                boardX + 5.0, lobbyY + 16.0, boardZ + 26.0);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                display -> display.getTags().contains(tag))) old.discard();
        for (ArmorStand old : level.getEntitiesOfClass(ArmorStand.class, area,
                stand -> stand.getTags().contains(tag))) old.discard();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("BLOCKPARTY LEADERBOARD").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        lines.add(Component.literal("TOP 10 DANCERS").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        List<PlayerStatsManager.PlayerRecord> top = PlayerStatsManager.topPlayers(10);
        if (top.isEmpty()) {
            lines.add(Component.literal("No scores yet - survive a round to score!").withStyle(ChatFormatting.GRAY));
        } else {
            for (int i = 0; i < 10; i++) {
                if (i >= top.size()) {
                    lines.add(Component.literal("#" + (i + 1) + "  ---").withStyle(ChatFormatting.DARK_GRAY));
                    continue;
                }
                PlayerStatsManager.PlayerRecord record = top.get(i);
                PlayerStatsManager.Rank rank = PlayerStatsManager.getRank(record.points());
                ChatFormatting placeColor = i == 0 ? ChatFormatting.GOLD : i == 1 ? ChatFormatting.WHITE
                        : i == 2 ? ChatFormatting.RED : ChatFormatting.GRAY;
                lines.add(Component.literal("#" + (i + 1) + "  ").withStyle(placeColor, ChatFormatting.BOLD)
                        .append(Component.literal(record.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                        .append(Component.literal("   " + record.points() + " pts   ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                        .append(Component.literal(rank.name()).withStyle(ChatFormatting.AQUA)));
            }
        }
        lines.add(Component.literal("/bp leaderboard  •  /bp rank").withStyle(ChatFormatting.LIGHT_PURPLE));
        // The panel faces west toward the lobby interior; text entities avoid mirrored block-built letters.
        for (int i = 0; i < lines.size(); i++) {
            float scale = i == 0 ? 2.8F : (i == 1 ? 2.0F : (i == lines.size() - 1 ? 1.35F : 1.55F));
            double y = topY - (i * 0.9);
            BlockPartyText.spawnTextDisplay(level, boardX, y, boardZ, 90.0F, lines.get(i), scale, tag);
        }
    }

    private void teleportToLobby(ServerPlayer player) {
        if (level != null && lobbySet) player.teleportTo(level, lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch);
    }

    private void teleportToRandomFloorTile(ServerPlayer player) {
        List<BlockPos> positions = new ArrayList<>(floorSnapshot.keySet());
        if (positions.isEmpty()) return;
        BlockPos tile = positions.get(random.nextInt(positions.size()));
        player.teleportTo(level, tile.getX() + 0.5, tile.getY() + 1.05, tile.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    private boolean isGameInProgress() {
        return phase == Phase.PREPARE || phase == Phase.FALL || phase == Phase.INTERMISSION || phase == Phase.FINISHING;
    }

    private static String formatSeconds(int ticks) {
        if (ticks % 20 == 0) return Integer.toString(ticks / 20);
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    public void broadcast(Component message) {
        if (level == null) return;
        for (UUID uuid : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) player.sendSystemMessage(message);
        }
    }

    private void broadcastActionBar(Component message) {
        if (level == null) return;
        for (UUID uuid : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) player.displayClientMessage(message, true);
        }
    }

    public String getStatusText() {
        return "Arena " + id + " | Status: " + phase.name() + " | Spillere: " + players.size() + " | Runde: " + round + "/" + MAX_ROUNDS + " | Power-ups: " + activeBeacons.size();
    }

    public void setLoadedCorners(BlockPos first, BlockPos second) {
        this.pos1 = first;
        this.pos2 = second;
    }

    public void setLoadedDisplayCenter(BlockPos displayCenter) {
        setDisplayCenter(displayCenter);
    }

    public String getDimensionId() {
        return level.dimension().location().toString();
    }

    private record HeldItemSnapshot(int slot, ItemStack stack) {
    }
}
