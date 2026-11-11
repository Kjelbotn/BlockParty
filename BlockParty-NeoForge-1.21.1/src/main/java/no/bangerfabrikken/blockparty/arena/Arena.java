package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import no.bangerfabrikken.blockparty.network.MusicControlPayload;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import no.bangerfabrikken.blockparty.BlockPartyMod;
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
        WAITING, COUNTDOWN, TELEPORT_COUNTDOWN, PREPARE, FALL, INTERMISSION, FINISHING
    }

    private enum PowerUp {
        SPEED_UPGRADE, NIGHT_VISION, SUPER_JUMP, SLOWNESS, LEAP, COLOR_TELEPORTER, COLOR_SCANNER, LUCKY_LANDING
    }

    public static final int MAX_ROUNDS = 20;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 10;
    private static final int MAX_FLOOR_BLOCKS = 50 * 50;
    private static final int START_COUNTDOWN_TICKS = 200; // 10 seconds in lobby
    private static final int TELEPORT_COUNTDOWN_TICKS = 100; // 5 seconds after teleport
    private static final long TELEPORT_SUNSET_END = 13_500L;
    // These transition phases pause the music, so they are deliberately not
    // included in the song-time budget. They remain fixed gameplay transitions.
    private static final int FALL_TICKS = 35;
    private static final int INTERMISSION_TICKS = 25;
    // Round one starts at 10 seconds; each following round gets progressively shorter.
    // Round 20 is never shorter than 2.5 seconds, regardless of the selected song length.
    private static final int FIRST_ROUND_DURATION_TICKS = 10 * 20;
    private static final int LAST_ROUND_DURATION_TICKS = 50;

    private int getRoundDurationTicks(int roundNumber) {
        int index = Math.max(0, Math.min(roundNumber - 1, MAX_ROUNDS - 1));
        double progress = index / (double) (MAX_ROUNDS - 1);
        return (int) Math.round(FIRST_ROUND_DURATION_TICKS
                + (LAST_ROUND_DURATION_TICKS - FIRST_ROUND_DURATION_TICKS) * progress);
    }

    private int getFallDurationTicks() {
        return FALL_TICKS;
    }

    private int getIntermissionDurationTicks() {
        return INTERMISSION_TICKS;
    }

    private static final int TARGET_BLOCK_SLOT = 4; // middle hotbar slot (1-based slot 5)


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
    private boolean visualUpgradeApplied;
    private long teleportSunsetStartTime = Long.MIN_VALUE;
    private long teleportSunsetEndTime = Long.MIN_VALUE;

    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> pendingEliminations = new LinkedHashSet<>();
    private final Map<UUID, GameType> originalGameModes = new HashMap<>();
    private final Map<UUID, InventorySnapshot> originalHeldItems = new HashMap<>();
    private final Map<UUID, FoodSnapshot> originalFoodStates = new HashMap<>();
    private final Map<UUID, ReturnLocation> returnLocations = new HashMap<>();
    private record ReturnLocation(ServerLevel level, double x, double y, double z, float yaw, float pitch) { }
    private final Map<UUID, Set<Holder<MobEffect>>> roundPowerupEffects = new HashMap<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Integer> powerupsCollected = new HashMap<>();
    private final Map<UUID, Integer> matchStartPersonalBest = new HashMap<>();
    private final Set<UUID> personalBestNotified = new HashSet<>();
    private final Set<UUID> leapFallProtection = new HashSet<>();
    private final Set<UUID> luckyLanding = new HashSet<>();
    private int scannerTicks;
    private long matchStartTick;
    private long lastMatchDurationTicks;
    // Last tick a player was physically standing on the announced target tile.
    // This small grace window prevents a block-removal/tick-order race at round end.
    private final Map<UUID, Long> lastTargetContactTick = new HashMap<>();
    private final Map<UUID, BlockPos> lastTargetContactPos = new HashMap<>();
    private final Map<BlockPos, BlockState> originalFloorSnapshot = new HashMap<>();
    private final Map<BlockPos, BlockState> originalArenaAccentSnapshot = new HashMap<>();
    private final List<BlockPos> musicLightPositions = new ArrayList<>();
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
    private UUID matchWinnerId;
    private final List<String> lastPodium = new ArrayList<>();
    private final Map<UUID, Integer> podiumFreezeTicks = new HashMap<>();
    private final Map<UUID, BlockPos> podiumFreezePositions = new HashMap<>();
    /** Original lobby floor blocks temporarily replaced by winner podium columns. */
    private final Map<BlockPos, BlockState> podiumFloorSnapshot = new HashMap<>();
    private BlockPos winnerPodiumSpawn;
    /** Ticks remaining before the post-match podium and its holograms are removed. */
    private int podiumDisplayTicks;
    private int lastDancePattern = -1;

    /** Active beacon blocks are placed one block above five separate floor tiles. */
    private final Map<BlockPos, BlockState> activeBeacons = new HashMap<>();
    private final Map<UUID, String> musicVotes = new HashMap<>();
    private final Set<UUID> musicVotingUnlocked = new HashSet<>();
    private final Set<UUID> fullLobbyInventoryNotices = new HashSet<>();
    private String selectedMusicTrackId;
    private Objective sidebarObjective;
    private Objective previousSidebarObjective;
    private int scoreboardTick;
    private int djBeatTicks;
    private int djPulseTicks;
    private int musicTicksRemaining;
    private boolean musicFinished;
    private boolean musicPaused;
    private int lastPowerupRound = -10;
    private ResourceLocation activeMusicId;
    private long originalDayTime = Long.MIN_VALUE;
    private Boolean originalDaylightCycle;
    private Boolean originalWeatherCycle;

    public Arena(String id, ServerLevel level) {
        this.id = id;
        this.level = level;
    }

    public String getId() { return id; }
    public ServerLevel getLevel() { return level; }
    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }
    public BlockPos getDisplayCenter() { return displayCenter; }
    public void setDisplayCenter(BlockPos displayCenter) {
        this.displayCenter = displayCenter == null ? null : displayCenter.immutable();
        refreshLobbyDisplays();
    }
    public Phase getPhase() { return phase; }
    public int getPlayerCount() { return players.size(); }
    public boolean containsPlayer(UUID playerId) { return players.contains(playerId); }
    public boolean hasReturnLocation(UUID playerId) { return returnLocations.containsKey(playerId); }

    /** Fallback for /bp leave if a session UUID was lost: only match players physically near this running arena. */
    public boolean canLocatePlayerForLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (players.contains(uuid) || returnLocations.containsKey(uuid)
                || originalHeldItems.containsKey(uuid) || originalGameModes.containsKey(uuid)) return true;
        if (!isGameInProgress() || level == null || player.serverLevel() != level || pos1 == null || pos2 == null) return false;
        double minX = Math.min(pos1.getX(), pos2.getX()) - 35.0;
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 35.0;
        double minZ = Math.min(pos1.getZ(), pos2.getZ()) - 35.0;
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 35.0;
        double minY = Math.min(pos1.getY(), pos2.getY()) - 90.0;
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 55.0;
        return player.getX() >= minX && player.getX() <= maxX
                && player.getZ() >= minZ && player.getZ() <= maxZ
                && player.getY() >= minY && player.getY() <= maxY;
    }

    public boolean isMatchParticipant(ServerPlayer player) {
        return isGameInProgress() && (players.contains(player.getUUID()) || canLocatePlayerForLeave(player));
    }

    /** True for the generated arena, spectator ring, pit and separate lobby footprint. */
    public boolean protectsLocation(ServerLevel candidateLevel, double x, double y, double z) {
        if (level == null || candidateLevel != level || !lobbySet || pos1 == null || pos2 == null) return false;
        int floorY = pos1.getY();
        int floorMinX = Math.min(pos1.getX(), pos2.getX());
        int floorMaxX = Math.max(pos1.getX(), pos2.getX());
        int floorMinZ = Math.min(pos1.getZ(), pos2.getZ());
        int floorMaxZ = Math.max(pos1.getZ(), pos2.getZ());
        boolean arenaArea = x >= floorMinX - 14 && x <= floorMaxX + 14
                && z >= floorMinZ - 14 && z <= floorMaxZ + 14
                && y >= floorY - 18 && y <= floorY + 30;
        // ArenaGenerator builds a 64x56 lobby; lobbyX is its centre and lobbyZ is 20 blocks
        // from its rear edge. Include the roofless lobby volume and a small vertical margin.
        boolean lobbyArea = Math.abs(x - lobbyX) <= 34
                && z >= lobbyZ - 22 && z <= lobbyZ + 38
                && y >= lobbyY - 3 && y <= lobbyY + 28;
        return arenaArea || lobbyArea;
    }

    /** Stops a vanilla fall/void death if the active player crossed the elimination threshold. */
    public boolean handleOutOfBoundsDeath(ServerPlayer player) {
        if (player == null || level == null || player.serverLevel() != level || !alive.contains(player.getUUID())
                || !isGameInProgress() || pos1 == null) return false;
        if (player.getY() > pos1.getY() - 3.0) return false;
        // A fall/void death event can arrive before the regular arena tick. Revive the
        // player just enough for the normal elimination teleport to complete cleanly.
        if (player.getHealth() <= 0.0F) player.setHealth(1.0F);
        player.fallDistance = 0.0F;
        eliminate(player);
        return true;
    }

    /** Fall damage is disabled for active Jump Boost and the one-use LEAP BOOST jump. */
    public boolean shouldCancelFallDamage(ServerPlayer player) {
        if (player == null || !alive.contains(player.getUUID()) || !isGameInProgress()) return false;
        return player.hasEffect(MobEffects.JUMP) || leapFallProtection.contains(player.getUUID());
    }

    /** Consume one-use Leap protection after the landing event has been handled. */
    public void consumeLeapFallProtection(ServerPlayer player) {
        if (player != null) leapFallProtection.remove(player.getUUID());
    }

    private void enforceBlockPartyNeeds(MinecraftServer server) {
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || player.serverLevel() != level) continue;
            var food = player.getFoodData();
            if (food.getFoodLevel() != 20) food.setFoodLevel(20);
            if (food.getSaturationLevel() != 20.0F) food.setSaturation(20.0F);
            if (food.getExhaustionLevel() != 0.0F) food.setExhaustion(0.0F);

            // Once the match starts, the only permitted inventory item for active
            // dancers is the named target-colour concrete in its fixed centre slot.
            if (phase == Phase.TELEPORT_COUNTDOWN || phase == Phase.PREPARE || phase == Phase.FALL
                    || phase == Phase.INTERMISSION || phase == Phase.FINISHING) {
                boolean changed = false;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    boolean activePlayer = alive.contains(uuid);
                    boolean keepTarget = activePlayer && slot == TARGET_BLOCK_SLOT && isTargetColorBlock(stack);
                    // LEAP BOOST is the only permitted extra item for active players.
                    // Eliminated players remain inventory-empty until the match ends.
                    boolean keepLeapFeather = activePlayer && isLeapFeather(stack);
                    if (!stack.isEmpty() && !keepTarget && !keepLeapFeather) {
                        player.getInventory().setItem(slot, ItemStack.EMPTY);
                        changed = true;
                    }
                }
                if (changed) player.inventoryMenu.broadcastChanges();
            }
        }
    }

    private void removeMobsFromProtectedArea() {
        if (level == null || !lobbySet || pos1 == null || pos2 == null) return;
        int minX = Math.min(pos1.getX(), pos2.getX()) - 14;
        int maxX = Math.max(pos1.getX(), pos2.getX()) + 14;
        int minZ = Math.min(pos1.getZ(), pos2.getZ()) - 14;
        int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 14;
        int floorY = pos1.getY();
        AABB arenaArea = new AABB(minX, floorY - 18, minZ, maxX + 1, floorY + 31, maxZ + 1);
        AABB lobbyArea = new AABB(lobbyX - 34, lobbyY - 3, lobbyZ - 22,
                lobbyX + 34, lobbyY + 29, lobbyZ + 39);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, arenaArea)) mob.discard();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, lobbyArea)) mob.discard();
    }

    public boolean isConfigured() {
        return level != null && pos1 != null && pos2 != null && lobbySet;
    }

    public boolean isRunning() {
        return phase != Phase.WAITING;
    }

    /** Lobby controls are usable only before participants are teleported to the floor. */
    public boolean isLobbyControlPhase() {
        return phase == Phase.WAITING || phase == Phase.COUNTDOWN;
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
        refreshLobbyDisplays();
    }

    public boolean hasLobby() { return lobbySet; }
    public double getLobbyX() { return lobbyX; }
    public double getLobbyY() { return lobbyY; }
    public double getLobbyZ() { return lobbyZ; }
    public float getLobbyYaw() { return lobbyYaw; }
    public float getLobbyPitch() { return lobbyPitch; }

    /** /bp opens a lobby session; only the Nether Star enrolls the visitor in a match. */
    public void join(ServerPlayer player) {
        if (!isConfigured()) {
            player.sendSystemMessage(Component.literal("Arenaen er ikke ferdig konfigurert.").withStyle(ChatFormatting.RED));
            return;
        }
        UUID uuid = player.getUUID();
        boolean firstLobbyEntry = !returnLocations.containsKey(uuid);
        if (firstLobbyEntry) {
            originalGameModes.putIfAbsent(uuid, player.gameMode.getGameModeForPlayer());
            returnLocations.put(uuid, new ReturnLocation(player.serverLevel(), player.getX(), player.getY(),
                    player.getZ(), player.getYRot(), player.getXRot()));
            originalHeldItems.putIfAbsent(uuid, InventorySnapshot.capture(player));
            originalFoodStates.putIfAbsent(uuid, FoodSnapshot.capture(player));
            player.stopUsingItem();
            player.getInventory().clearContent();
            player.inventoryMenu.broadcastChanges();
        }
        applyVisualUpgrade();
        teleportToLobby(player);
        ensureLobbyItems(player);
        player.sendSystemMessage(Component.literal("Velkommen til BlockParty! Trykk Nether Star når du vil melde deg på.")
                .withStyle(ChatFormatting.GREEN));
        if (firstLobbyEntry) announceLobbyJoin(player);
    }

    /** Announce a first-time lobby entry to online players who are currently in Survival mode. */
    private void announceLobbyJoin(ServerPlayer joiningPlayer) {
        if (level == null || level.getServer() == null) return;
        Component announcement = Component.literal(joiningPlayer.getName().getString())
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" ble med i BlockParty-lobbyen! Bli med du også: ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("/bp").withStyle(ChatFormatting.AQUA));
        for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
            if (online.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
                online.sendSystemMessage(announcement);
            }
        }
    }

    /** Explicit match registration, triggered only by the lobby Nether Star. */
    public void registerForGame(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!returnLocations.containsKey(uuid) || !isInsideBlockPartyArea(player)) {
            player.sendSystemMessage(Component.literal("Bruk /bp for å gå til lobbyen først.").withStyle(ChatFormatting.RED));
            return;
        }
        if (players.contains(uuid)) {
            player.sendSystemMessage(Component.literal("Du er allerede påmeldt neste BlockParty-match.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (players.size() >= MAX_PLAYERS) {
            player.sendSystemMessage(Component.literal("Denne kampen er full (maks " + MAX_PLAYERS + " spillere).")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        players.add(uuid);
        if (phase == Phase.WAITING || phase == Phase.COUNTDOWN) {
            unlockMusicVotingAfterStar(player);
        } else {
            player.sendSystemMessage(Component.literal("En kamp pågår. Du er påmeldt neste kamp og kan stemme når den er ferdig.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        refreshLobbyDisplays();
        PlayerStatsManager.sendPlayerRank(player);
        player.sendSystemMessage(Component.literal("Du er påmeldt BlockParty! (" + players.size() + "/" + MAX_PLAYERS + ")")
                .withStyle(ChatFormatting.GREEN));
        broadcast(Component.literal(player.getName().getString() + " meldte seg på! (" + players.size() + "/" + MAX_PLAYERS + ")")
                .withStyle(ChatFormatting.YELLOW));
    }

    public void leave(ServerPlayer player, boolean teleport) {
        UUID uuid = player.getUUID();
        // Treat any retained session snapshot as membership too. This protects /bp leave
        // if the live-player set ever gets out of sync during a phase transition.
        boolean wasMember = players.contains(uuid) || returnLocations.containsKey(uuid)
                || originalHeldItems.containsKey(uuid) || originalGameModes.containsKey(uuid)
                || originalFoodStates.containsKey(uuid)
                || canLocatePlayerForLeave(player);
        if (wasMember && activeMusicId != null) {
            PacketDistributor.sendToPlayer(player,
                    new MusicControlPayload(activeMusicId, MusicControlPayload.STOP));
        }
        players.remove(uuid);
        alive.remove(uuid);
        pendingEliminations.remove(uuid);
        removeLobbyItemsFromInventory(player);
        fullLobbyInventoryNotices.remove(uuid);
        musicVotes.remove(uuid);
        musicVotingUnlocked.remove(uuid);
        restorePlayerState(player);
        if (teleport && wasMember) {
            ReturnLocation origin = returnLocations.remove(uuid);
            if (origin != null) {
                player.teleportTo(origin.level(), origin.x(), origin.y(), origin.z(), origin.yaw(), origin.pitch());
            } else {
                player.sendSystemMessage(Component.literal("Returposisjonen din ble ikke funnet. Du er ikke teleportert til lobbyen.")
                        .withStyle(ChatFormatting.RED));
            }
        } else if (wasMember && !teleport) {
            returnLocations.remove(uuid);
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
        podiumFreezeTicks.remove(player.getUUID());
        podiumFreezePositions.remove(player.getUUID());
        player.setNoGravity(false);
        // Restore the pre-BlockParty snapshot before the server saves disconnecting player data.
        // This also persists the original return location instead of the arena position.
        leave(player, true);
    }

    public void start(MinecraftServer server) {
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN && phase != Phase.TELEPORT_COUNTDOWN) {
            throw new IllegalStateException("Arenaen kjører allerede.");
        }
        players.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        if (players.isEmpty()) {
            throw new IllegalStateException("Ingen tilkoblede spillere er med i arenaen.");
        }

        applyVisualUpgrade();
        clearWinnerPodiumAtDjBooth();
        captureFloor();
        // Recalculate the target screen from the stored floor corners. This repairs stale
        // display coordinates saved by earlier builds without rebuilding the whole arena.
        int screenCenterX = Math.min(pos1.getX(), pos2.getX()) + (Math.abs(pos2.getX() - pos1.getX()) + 1) / 2;
        int screenFloorY = pos1.getY();
        int screenNorthZ = Math.min(pos1.getZ(), pos2.getZ()) - 9;
        displayCenter = new BlockPos(screenCenterX, screenFloorY + 11, screenNorthZ);
        updateArenaWelcomeDisplay();
        alive.clear();
        alive.addAll(players);
        pendingEliminations.clear();
        lastEliminatedPlayer = null;
        points.clear();
        powerupsCollected.clear();
        leapFallProtection.clear();
        for (UUID uuid : players) {
            points.put(uuid, 0);
            powerupsCollected.put(uuid, 0);
        }
        round = 0;
        previousTargetColor = null;
        previousPattern = -1;
        soloMode = players.size() == 1;
        activeBeacons.clear();
        lastPowerupRound = -10;
        stopMusic();
        selectedMusicTrackId = selectWinningSong();
        Track winningTrack = selectedMusicTrackId == null ? null : MusicTrackManager.get(selectedMusicTrackId);
        if (winningTrack != null) {
            broadcast(Component.literal("Vote ended. " + winningTrack.name() + " by " + winningTrack.artist() + " won.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        for (UUID uuid : players) {
            ServerPlayer participant = server.getPlayerList().getPlayer(uuid);
            if (participant != null && participant.containerMenu instanceof MusicVotingMenu) {
                participant.closeContainer();
            }
        }
        phase = Phase.COUNTDOWN;
        timerTicks = START_COUNTDOWN_TICKS;
        hideLobbyLeaderboard();
        broadcast(Component.literal("BlockParty starter om 10 sekunder!").withStyle(ChatFormatting.GREEN));
        playSound(ModSounds.ROUND_START, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    private void beginMatch(MinecraftServer server) {
        matchStartTick = scoreboardTick;
        matchStartPersonalBest.clear();
        personalBestNotified.clear();
        for (UUID uuid : players) matchStartPersonalBest.put(uuid, PlayerStatsManager.getRecord(uuid).personalBest());
        // The lobby countdown has ended; players are teleported before the final countdown.
        for (UUID uuid : new ArrayList<>(alive)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                alive.remove(uuid);
                continue;
            }
            removeLobbyItemsFromInventory(player);
            player.getInventory().clearContent();
            player.setGameMode(GameType.ADVENTURE);
            teleportToRandomFloorTile(player);
        }
        if (alive.isEmpty()) {
            declareWinner();
            return;
        }
        // Snapshot the actual Survival-world time only after everyone has arrived.
        // The lobby countdown therefore never changes or rewinds the world clock.
        if (originalDayTime == Long.MIN_VALUE) {
            originalDayTime = level.getDayTime();
            originalDaylightCycle = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            originalWeatherCycle = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        }
        long dayStart = originalDayTime - Math.floorMod(originalDayTime, 24_000L);
        // Begin at the exact Survival-world time captured after teleportation.
        // Animate from that value to 13,500 over the five-second arena countdown.
        teleportSunsetStartTime = originalDayTime;
        teleportSunsetEndTime = dayStart + TELEPORT_SUNSET_END;
        // Freeze natural time progression, then manually animate sunset during the
        // five-second arena countdown so players can watch the sky darken.
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(teleportSunsetStartTime);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setWeatherParameters(20 * 60 * 60, 0, false, false);

        hideLobbyLeaderboard();
        showScoreboard(server);
        phase = Phase.TELEPORT_COUNTDOWN;
        timerTicks = TELEPORT_COUNTDOWN_TICKS;
        broadcast(Component.literal("Du er på banen! Spillet starter om 5 sekunder.")
                .withStyle(ChatFormatting.GREEN));
        for (UUID uuid : alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 5));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("5")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
            // Clear the pre-teleport subtitle once players arrive in the arena.
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        }
    }

    public void refreshLobbyDisplays() {
        refreshLobbyLeaderboard();
        refreshDjScreen();
        refreshWinnerPodium();
    }

    /** Applies the non-destructive visual upgrade once when an existing arena is visited. */
    public void applyVisualUpgrade() {
        if (visualUpgradeApplied || !isConfigured()) return;
        if (ArenaGenerator.refreshVisuals(level, pos1, pos2, lobbyX, lobbyY, lobbyZ)) {
            visualUpgradeApplied = true;
            captureArenaAccents();
            refreshLobbyDisplays();
        }
    }

    private void refreshWinnerPodium() {
        if (level == null || !lobbySet) return;
        // Podium is hidden during waiting and gameplay; it is built at the DJ booth after a match.
        int centerX = (int) Math.floor(lobbyX);
        int floorY = (int) Math.floor(lobbyY) - 1;
        int oldZ = (int) Math.floor(lobbyZ) + 8;
        int oldX = centerX - 28;
        String tag = "bp_podium_" + id;
        AABB oldArea = new AABB(oldX - 5, floorY, oldZ - 3, oldX + 6, floorY + 8, oldZ + 3);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, oldArea,
                entity -> entity.getTags().contains(tag))) old.discard();
        for (int x = oldX - 3; x <= oldX + 3; x++) {
            for (int z = oldZ - 1; z <= oldZ + 1; z++) {
                for (int y = floorY; y <= floorY + 2; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block == Blocks.IRON_BLOCK || block == Blocks.GOLD_BLOCK || block == Blocks.COPPER_BLOCK) {
                        // The previous podium used to occupy this side bay; replace its base with flooring.
                        level.setBlock(pos, y == floorY ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                                : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void buildWinnerPodiumAtDjBooth() {
        if (level == null || !lobbySet || lastPodium.isEmpty()) return;
        podiumDisplayTicks = 0;
        int centerX = (int) Math.floor(lobbyX);
        int floorY = (int) Math.floor(lobbyY) - 1;
        int lobbyMaxZ = (int) Math.floor(lobbyZ) + 35;
        int boothZ = lobbyMaxZ - 8;
        int podiumZ = boothZ - 5; // in front of the DJ, toward the dancefloor
        String tag = "bp_podium_" + id;
        AABB area = new AABB(centerX - 8, floorY, podiumZ - 4, centerX + 8, floorY + 8, podiumZ + 4);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                entity -> entity.getTags().contains(tag))) old.discard();
        int[] heights = {1, 2, 1};
        Block[] blocks = {Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.COPPER_BLOCK};
        for (int i = 0; i < 3; i++) {
            int x = centerX - 2 + i * 2;
            for (int h = 0; h < heights[i]; h++) {
                BlockPos podiumBlock = new BlockPos(x, floorY + h, podiumZ);
                podiumFloorSnapshot.putIfAbsent(podiumBlock.immutable(), level.getBlockState(podiumBlock));
                level.setBlock(podiumBlock, blocks[i].defaultBlockState(), 3);
            }
        }
        BlockPos goldTop = new BlockPos(centerX, floorY + 1, podiumZ);
        winnerPodiumSpawn = goldTop.above();
        BlockPartyText.spawnTextDisplay(level, centerX + 0.5, floorY + 5.4, podiumZ - 1.2,
                180.0F, Component.literal("BLOCKPARTY-VINNER")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 1.1F, tag);
        for (int i = 0; i < 3; i++) {
            String line = i < lastPodium.size() ? (i + 1) + ". " + lastPodium.get(i) : (i + 1) + ". —";
            ChatFormatting colour = i == 0 ? ChatFormatting.GOLD : i == 1 ? ChatFormatting.GRAY : ChatFormatting.RED;
            BlockPartyText.spawnTextDisplay(level, centerX + 0.5, floorY + 4.1 - i * 0.8,
                    podiumZ - 1.2, 180.0F, Component.literal(line).withStyle(colour, ChatFormatting.BOLD),
                    0.78F, tag);
        }
    }

    private void clearWinnerPodiumAtDjBooth() {
        podiumDisplayTicks = 0;
        if (level == null || !lobbySet) return;
        int centerX = (int) Math.floor(lobbyX);
        int floorY = (int) Math.floor(lobbyY) - 1;
        int podiumZ = (int) Math.floor(lobbyZ) + 22;
        String tag = "bp_podium_" + id;
        AABB area = new AABB(centerX - 8, floorY, podiumZ - 4, centerX + 8, floorY + 8, podiumZ + 4);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                entity -> entity.getTags().contains(tag))) old.discard();
        // Restore the exact blocks the podium covered instead of leaving holes in the lobby.
        if (!podiumFloorSnapshot.isEmpty()) {
            for (Map.Entry<BlockPos, BlockState> entry : podiumFloorSnapshot.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
            podiumFloorSnapshot.clear();
        } else {
            // Backwards-compatible cleanup for podiums created before floor snapshots existed.
            int[] heights = {1, 2, 1};
            for (int i = 0; i < 3; i++) {
                BlockPos base = new BlockPos(centerX - 2 + i * 2, floorY, podiumZ);
                for (int h = 0; h < heights[i]; h++) {
                    BlockPos pos = base.above(h);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block == Blocks.IRON_BLOCK || block == Blocks.GOLD_BLOCK || block == Blocks.COPPER_BLOCK) {
                        level.setBlock(pos, h == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                                : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        winnerPodiumSpawn = null;
    }

    private void teleportWinnerToPodium(ServerPlayer winner) {
        if (winner == null || level == null || winnerPodiumSpawn == null) return;
        BlockPos spawn = winnerPodiumSpawn;
        winner.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 180.0F, 0.0F);
        winner.setDeltaMovement(0.0, 0.0, 0.0);
        winner.setNoGravity(true);
        podiumFreezeTicks.put(winner.getUUID(), 100);
        podiumFreezePositions.put(winner.getUUID(), spawn);
        winner.sendSystemMessage(Component.literal("Du er vinneren! Hold posisjonen på podiet i 5 sekunder.")
                .withStyle(ChatFormatting.GOLD));
    }

    private void tickWinnerPodiumFreeze(MinecraftServer server) {
        for (UUID uuid : new HashSet<>(podiumFreezeTicks.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            Integer remaining = podiumFreezeTicks.get(uuid);
            BlockPos spawn = podiumFreezePositions.get(uuid);
            if (player == null || remaining == null || spawn == null) {
                podiumFreezeTicks.remove(uuid);
                podiumFreezePositions.remove(uuid);
                continue;
            }
            player.setNoGravity(true);
            player.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            player.setDeltaMovement(0.0, 0.0, 0.0);
            if (remaining <= 1) {
                podiumFreezeTicks.remove(uuid);
                podiumFreezePositions.remove(uuid);
                player.setNoGravity(false);
                player.setDeltaMovement(0.0, 0.0, 0.0);
                player.sendSystemMessage(Component.literal("Du kan nå bevege deg fritt i lobbyen!")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                podiumFreezeTicks.put(uuid, remaining - 1);
            }
        }
    }

    private void refreshDjScreen() {
        if (level == null || !lobbySet) return;
        int centerX = (int) Math.floor(lobbyX);
        int wallZ = (int) Math.floor(lobbyZ) + 35;
        BlockPos anchor = new BlockPos(centerX, (int) Math.floor(lobbyY) + 9, wallZ - 1);
        if (!level.hasChunkAt(anchor)) return;
        String tag = "bp_dj_" + id;
        AABB area = new AABB(centerX - 24, lobbyY + 1, wallZ - 5,
                centerX + 24, lobbyY + 13, wallZ + 2);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                entity -> entity.getTags().contains(tag) || entity.getTags().stream().anyMatch(t -> t.startsWith("bp_dj_")))) old.discard();

        AABB entranceArea = new AABB(centerX - 20, lobbyY + 4, lobbyZ - 25,
                centerX + 20, lobbyY + 16, lobbyZ - 13);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, entranceArea)) old.discard();

        BlockPartyText.spawnTextDisplay(level, centerX + 0.5, lobbyY + 11.2, wallZ - 0.72,
                180.0F, Component.literal("MUSIKKAVSTEMNING")
                        .withStyle(ChatFormatting.YELLOW), 1.8F, tag);
        BlockPartyText.spawnTextDisplay(level, centerX - 8.0, lobbyY + 9.45, wallZ - 0.72,
                180.0F, Component.literal("SPILLELISTE")
                        .withStyle(ChatFormatting.AQUA), 1.4F, tag);

        List<Track> catalogue = MusicTrackManager.getTracks();
        int visible = Math.min(catalogue.size(), 10);
        for (int i = 0; i < visible; i++) {
            Track track = catalogue.get(i);
            long votes = musicVotes.values().stream().filter(track.id()::equals).count();
            // Text displays face the lobby, so swap physical columns to keep song 01 at upper left.
            int column = 1 - (i / 5);
            int row = i % 5;
            double x = centerX - 14.0 + column * 12.0;
            double y = lobbyY + 8.1 - row * 1.1;
            Component title = Component.literal(String.format(Locale.ROOT, "%02d  ", i + 1))
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(track.name()).withStyle(ChatFormatting.WHITE));
            Component details = Component.literal(track.artist())
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("   •   " + votes + " STEMMER")
                            .withStyle(ChatFormatting.AQUA));
            BlockPartyText.spawnTextDisplay(level, x, y, wallZ - 0.72, 180.0F, title, 1.2F, tag);
            BlockPartyText.spawnTextDisplay(level, x, y - 0.45, wallZ - 0.72, 180.0F, details, 0.84F, tag);
        }
        if (catalogue.size() > visible) {
            BlockPartyText.spawnTextDisplay(level, centerX - 8.0, lobbyY + 1.6, wallZ - 0.72,
                    180.0F, Component.literal("+" + (catalogue.size() - visible) + " FLERE LÅTER I HUB")
                            .withStyle(ChatFormatting.GRAY), 0.75F, tag);
        }

        Track leader = mostVotedTrack();
        long leaderVotes = leader == null ? 0 : musicVotes.values().stream().filter(leader.id()::equals).count();
        BlockPartyText.spawnTextDisplay(level, centerX + 11.0, lobbyY + 9.0, wallZ - 0.72,
                180.0F, Component.literal("MEST STEMTE LÅT")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), 1.2F, tag);
        BlockPartyText.spawnTextDisplay(level, centerX + 11.0, lobbyY + 6.7, wallZ - 0.72,
                180.0F, Component.literal(leader == null || leaderVotes == 0 ? "INGEN STEMMER ENNÅ" : leader.name())
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), 1.0F, tag);
        BlockPartyText.spawnTextDisplay(level, centerX + 11.0, lobbyY + 5.2, wallZ - 0.72,
                180.0F, Component.literal(leader == null || leaderVotes == 0 ? "STEM PÅ EN LÅT I HUB"
                        : leader.artist() + "  •  " + leaderVotes + " STEMMER")
                        .withStyle(ChatFormatting.YELLOW), 0.72F, tag);
        BlockPartyText.spawnTextDisplay(level, centerX + 11.0, lobbyY + 3.8, wallZ - 0.72,
                180.0F, Component.literal("HØYREKLIKK KOMPASSET FOR HUB")
                        .withStyle(ChatFormatting.AQUA), 0.65F, tag);
    }

    private Track mostVotedTrack() {
        List<Track> tracks = MusicTrackManager.getTracks();
        if (tracks.isEmpty() || musicVotes.isEmpty()) return null;
        return tracks.stream().max(java.util.Comparator.comparingLong(track ->
                musicVotes.values().stream().filter(track.id()::equals).count())).orElse(null);
    }


    private void tickMusicEffects() {
        if (activeMusicId == null || selectedMusicTrackId == null || level == null || musicPaused) return;
        if (musicTicksRemaining > 0 && --musicTicksRemaining == 0) {
            // Play once only. The 20-round schedule is scaled to fit the song;
            // if tick rounding differs, continue silently rather than replaying it.
            sendMusicControl(MusicControlPayload.STOP);
            activeMusicId = null;
            musicFinished = true;
            musicPaused = false;
        }
    }

    private void setDjLights(boolean lit) {
        if (level == null) return;
        // Stadium lighting is permanent; music effects must never black out the arena.
        for (BlockPos pos : musicLightPositions) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.REDSTONE_LAMP)) {
                level.setBlock(pos, state.setValue(RedstoneLampBlock.LIT, true), 3);
            }
        }
    }

    private void captureArenaAccents() {
        originalArenaAccentSnapshot.clear();
        musicLightPositions.clear();
        if (level == null || pos1 == null || pos2 == null) return;
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        int floorY = pos1.getY();
        for (int x = minX - 32; x <= maxX + 32; x++) {
            for (int z = minZ - 12; z <= maxZ + 76; z++) {
                for (int y = floorY + 1; y <= floorY + 22; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.PURPLE_CONCRETE) || state.is(Blocks.PURPLE_STAINED_GLASS)
                            || state.is(Blocks.MAGENTA_CONCRETE) || state.is(Blocks.MAGENTA_STAINED_GLASS)) {
                        // Only arena trim is recoloured; the lobby has a separate, fixed colour palette.
                        if (x >= minX - 12 && x <= maxX + 12 && z <= maxZ + 12) {
                            originalArenaAccentSnapshot.put(pos.immutable(), state);
                        }
                    }
                    if (state.is(Blocks.REDSTONE_LAMP)) musicLightPositions.add(pos.immutable());
                }
            }
        }
    }

    private void updateArenaAccents(DyeColor color) {
        if (level == null || color == null) return;
        for (Map.Entry<BlockPos, BlockState> entry : originalArenaAccentSnapshot.entrySet()) {
            Block original = entry.getValue().getBlock();
            Block replacement = (original == Blocks.PURPLE_STAINED_GLASS || original == Blocks.MAGENTA_STAINED_GLASS)
                    ? BlockPartyColors.getStainedGlass(color) : BlockPartyColors.getConcrete(color);
            level.setBlock(entry.getKey(), replacement.defaultBlockState(), 3);
        }
    }

    private void restoreOriginalArenaAccents() {
        if (level == null) return;
        for (Map.Entry<BlockPos, BlockState> entry : originalArenaAccentSnapshot.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        originalArenaAccentSnapshot.clear();
        setDjLights(true);
    }

    /** Shows the arena title during the pre-round countdown; round one replaces it with the target color. */
    private void updateArenaWelcomeDisplay() {
        if (displayCenter == null || level == null) return;
        String tag = "bp_target_" + id;
        AABB area = new AABB(displayCenter.getX() - 64, displayCenter.getY() - 16, displayCenter.getZ() - 8,
                displayCenter.getX() + 64, displayCenter.getY() + 16, displayCenter.getZ() + 12);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                entity -> entity.getTags().contains(tag))) old.discard();
        BlockPartyText.spawnTextDisplay(level, displayCenter.getX() + 0.5, displayCenter.getY() - 0.3,
                displayCenter.getZ() + 1.15, 0.0F,
                Component.literal("BlockParty").withStyle(ChatFormatting.LIGHT_PURPLE), 12.0F, tag);
    }

    private void updateColorDisplay() {
        if (targetColor == null || level == null) return;
        if (displayCenter == null && pos1 != null && pos2 != null) {
            int centerX = Math.min(pos1.getX(), pos2.getX()) + (Math.abs(pos2.getX() - pos1.getX()) + 1) / 2;
            int floorY = pos1.getY();
            int northWallZ = Math.min(pos1.getZ(), pos2.getZ()) - 9;
            displayCenter = new BlockPos(centerX, floorY + 11, northWallZ);
        }
        if (displayCenter == null) return;
        String tag = "bp_target_" + id;
        AABB area = new AABB(displayCenter.getX() - 64, displayCenter.getY() - 16, displayCenter.getZ() - 8,
                displayCenter.getX() + 64, displayCenter.getY() + 16, displayCenter.getZ() + 12);
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
        // Keep the text well in front of the dark panel and vertically centred so it cannot be hidden by the wall.
        BlockPartyText.spawnTextDisplay(level, displayCenter.getX() + 0.5, displayCenter.getY() - 0.3,
                displayCenter.getZ() + 1.15, 0.0F, text, 12.0F, tag);
    }

    public boolean handleBeaconInteraction(ServerPlayer player, BlockPos clickedPos) {
        if (!activeBeacons.containsKey(clickedPos) || phase != Phase.PREPARE) return false;
        if (!players.contains(player.getUUID()) || !alive.contains(player.getUUID())) return false;

        BlockState restore = activeBeacons.remove(clickedPos);
        level.setBlock(clickedPos, restore == null ? Blocks.AIR.defaultBlockState() : restore, 3);
        int powerRoll = random.nextInt(100);
        PowerUp powerUp = powerRoll < 10 ? PowerUp.SLOWNESS
                : powerRoll < 24 ? PowerUp.LEAP
                : powerRoll < 38 ? PowerUp.COLOR_TELEPORTER
                : powerRoll < 52 ? PowerUp.COLOR_SCANNER
                : powerRoll < 64 ? PowerUp.LUCKY_LANDING
                : powerRoll < 76 ? PowerUp.SPEED_UPGRADE
                : powerRoll < 88 ? PowerUp.SUPER_JUMP : PowerUp.NIGHT_VISION;
        Holder<MobEffect> effect = null;
        int amplifier = 0;
        String label;
        switch (powerUp) {
            case SPEED_UPGRADE -> { effect = MobEffects.MOVEMENT_SPEED; amplifier = 2; label = "SPEED UPGRADE"; }
            case NIGHT_VISION -> { effect = MobEffects.NIGHT_VISION; label = "NIGHT VISION"; }
            case SUPER_JUMP -> { effect = MobEffects.JUMP; amplifier = 3; label = "SUPER JUMP"; }
            case SLOWNESS -> { effect = MobEffects.MOVEMENT_SLOWDOWN; amplifier = 1; label = "SLOWNESS TRAP"; }
            case LEAP -> label = "LEAP";
            case COLOR_TELEPORTER -> label = "COLOR TELEPORTER";
            case COLOR_SCANNER -> label = "COLOR SCANNER";
            case LUCKY_LANDING -> label = "LUCKY LANDING";
            default -> throw new IllegalStateException("Unknown power-up");
        }

        boolean activated = true;
        if (powerUp == PowerUp.LEAP) {
            ItemStack feather = new ItemStack(Items.FEATHER);
            feather.set(DataComponents.CUSTOM_NAME, Component.literal("LEAP BOOST")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            feather.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            int freeSlot = -1;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (slot == TARGET_BLOCK_SLOT) continue; // Never replace the target-colour block.
                if (player.getInventory().getItem(slot).isEmpty()) {
                    freeSlot = slot;
                    break;
                }
            }
            if (freeSlot < 0) {
                activated = false;
                player.sendSystemMessage(Component.literal("Ingen ledig inventoryplass til LEAP BOOST-fjæren.")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                // Set the slot directly instead of Inventory.add(), so the item cannot
                // be reported as failed after insertion or disappear during hotbar enforcement.
                player.getInventory().setItem(freeSlot, feather);
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                player.sendSystemMessage(Component.literal("LEAP BOOST-fjær lagt i inventoryet ditt.")
                        .withStyle(ChatFormatting.AQUA));
            }
        } else if (powerUp == PowerUp.COLOR_TELEPORTER) {
            List<BlockPos> safeTiles = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> entry : floorSnapshot.entrySet()) {
                if (BlockPartyColors.getColor(entry.getValue()) != targetColor) continue;
                BlockPos tile = entry.getKey();
                if (!level.isEmptyBlock(tile.above()) || !level.isEmptyBlock(tile.above(2))) continue;
                boolean occupied = false;
                for (UUID otherId : alive) {
                    if (otherId.equals(player.getUUID())) continue;
                    ServerPlayer other = level.getServer().getPlayerList().getPlayer(otherId);
                    if (other != null && other.blockPosition().closerThan(tile.above(), 1.5D)) { occupied = true; break; }
                }
                if (!occupied) safeTiles.add(tile);
            }
            if (safeTiles.isEmpty()) {
                activated = false;
                player.sendSystemMessage(Component.literal("COLOR TELEPORTER fant ingen ledig trygg blokk i målfargen.").withStyle(ChatFormatting.YELLOW));
            } else {
                BlockPos destination = safeTiles.get(random.nextInt(safeTiles.size()));
                player.teleportTo(level, destination.getX() + 0.5D, destination.getY() + 1.05D,
                        destination.getZ() + 0.5D, player.getYRot(), player.getXRot());
            }
        } else if (powerUp == PowerUp.COLOR_SCANNER) {
            scannerTicks = 60;
            activated = true;
        } else if (powerUp == PowerUp.LUCKY_LANDING) {
            luckyLanding.add(player.getUUID());
        } else if (player.hasEffect(effect)) {
            activated = false;
            player.sendSystemMessage(Component.literal("Du har allerede denne effekten. Beaconen er brukt opp.").withStyle(ChatFormatting.YELLOW));
        } else {
            // Removed at the end of this round; the long vanilla duration is only a safety net.
            player.addEffect(new MobEffectInstance(effect, powerUp == PowerUp.NIGHT_VISION ? 20 * 10 : 20 * 60, amplifier, false, true, true));
            roundPowerupEffects.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(effect);
        }
        if (activated) {
            powerupsCollected.merge(player.getUUID(), 1, Integer::sum);
            boolean penalty = powerUp == PowerUp.SLOWNESS;
            player.sendSystemMessage(Component.literal((penalty ? "PENALTY: " : "POWER-UP: ") + label + "!")
                    .withStyle(penalty ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
            broadcast(Component.literal(player.getName().getString() + (penalty ? " traff " : " plukket opp ") + label + "!")
                    .withStyle(penalty ? ChatFormatting.RED : ChatFormatting.AQUA));
        }
        playSound(ModSounds.POWERUP_PICKUP, SoundSource.PLAYERS, 0.65F, 1.0F);
        updateScoreboard(level.getServer());
        return true;
    }

    public static boolean isLeapFeather(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.FEATHER)) return false;
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name != null && "LEAP BOOST".equals(name.getString());
    }

    /** Right-click activation for the bankable LEAP BOOST item. */
    public boolean handlePowerupItemInteraction(ServerPlayer player, ItemStack stack) {
        if (phase != Phase.PREPARE || !alive.contains(player.getUUID()) || !isLeapFeather(stack)) return false;
        var look = player.getLookAngle();
        var motion = player.getDeltaMovement();
        double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
        double forwardX = horizontalLength > 0.001D ? look.x / horizontalLength : 0.0D;
        double forwardZ = horizontalLength > 0.001D ? look.z / horizontalLength : 0.0D;
        player.setDeltaMovement(motion.x + forwardX * 0.7D, Math.max(0.95D, motion.y + 0.65D),
                motion.z + forwardZ * 0.7D);
        player.hurtMarked = true;
        leapFallProtection.add(player.getUUID());
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.getInventory().setChanged();
        player.sendSystemMessage(Component.literal("LEAP BOOST aktivert!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        playSound(ModSounds.POWERUP_PICKUP, SoundSource.PLAYERS, 0.8F, 1.35F);
        return true;
    }

    private void spawnPowerUpBeacons(int requested) {
        // Power-ups are a bonus, not a guaranteed part of every round. Leave at least
        // one complete round without beacons between spawn waves.
        if (level == null || floorSnapshot.isEmpty() || !activeBeacons.isEmpty() || round <= 5) return;
        if (activeBeacons.size() >= 5 || round - lastPowerupRound < 2 || random.nextFloat() >= 0.35F) return;
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
                if (other.distSqr(candidate) < 15.0 * 15.0) { tooClose = true; break; }
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
            lastPowerupRound = round;
            broadcast(Component.literal("✦ " + chosenFloors.size() + " POWER-UPS HAR SPAWNET! Høyreklikk en beacon!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
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
            if (player != null) for (Holder<MobEffect> effect : entry.getValue()) {
                if (!effect.equals(MobEffects.NIGHT_VISION)) player.removeEffect(effect);
            }
        }
        roundPowerupEffects.clear();
        // LEAP BOOST feathers are intentionally NOT cleared here: they are inventory items
        // that players may save and use in a later round when they really need them.
    }

    /** Remove any banked LEAP BOOST items only when the match is completely over. */
    private void clearLeapFeathers(MinecraftServer server) {
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (isLeapFeather(player.getInventory().getItem(slot))) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
            player.inventoryMenu.broadcastChanges();
        }
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
        Set<String> enabledIds = new HashSet<>();
        for (Track track : tracks) enabledIds.add(track.id());
        for (String id : musicVotes.values()) {
            if (enabledIds.contains(id)) counts.merge(id, 1, Integer::sum);
        }
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
        musicTicksRemaining = track.durationTicks();
        musicFinished = false;
        musicPaused = false;
        BlockPartyMod.LOGGER.info("Starting client-controlled BlockParty track '{}' ({}) for {} player(s).",
                track.name(), track.soundId(), players.size());
        sendMusicControl(MusicControlPayload.PLAY);
    }

    /** Pause the actual client audio channel; its OpenAL playback cursor is retained. */
    private void pauseMusic() {
        if (activeMusicId == null || musicPaused) return;
        musicPaused = true;
        sendMusicControl(MusicControlPayload.PAUSE);
    }

    /** Resume the same client sound instance from the exact sample where it was paused. */
    private void resumeMusic() {
        if (activeMusicId == null || !musicPaused) return;
        musicPaused = false;
        sendMusicControl(MusicControlPayload.RESUME);
    }

    private void stopMusic() {
        if (activeMusicId != null) sendMusicControl(MusicControlPayload.STOP);
        activeMusicId = null;
        musicTicksRemaining = 0;
        musicPaused = false;
    }

    private void sendMusicControl(int action) {
        if (level == null) return;
        ResourceLocation id = activeMusicId != null ? activeMusicId
                : ResourceLocation.fromNamespaceAndPath(BlockPartyMod.MOD_ID, "music_control");
        MusicControlPayload payload = new MusicControlPayload(id, action);
        for (UUID uuid : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Lobby utility items are bound to the player and cannot be dropped or duplicated. */
    public static boolean isProtectedLobbyItem(ItemStack stack) {
        return isMusicVotingBook(stack) || isJoinGameItem(stack) || isLeaveLobbyItem(stack) || isHubItem(stack)
                || isTargetColorBlock(stack) || isLeapFeather(stack);
    }

    private static Component simpleLobbyItemName(String name, ChatFormatting color) {
        return Component.literal(name).withStyle(style -> style.withColor(color).withBold(false).withItalic(false));
    }

    public static boolean isMusicVotingBook(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.BOOK)) return false;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && ("BlockParty Music Vote".equals(customName.getString())
                || "Vote".equals(customName.getString()));
    }

    /** The lobby play token is an ordinary nether star with a unique custom name. */
    public static boolean isJoinGameItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.NETHER_STAR)) return false;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && ("BlockParty Join Game".equals(customName.getString())
                || "Join game".equals(customName.getString()));
    }

    public static boolean isHubItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.COMPASS)) return false;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && ("BlockParty HUB".equals(customName.getString())
                || "Menu".equals(customName.getString()));
    }

    private ItemStack createHubItem() {
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Menu", ChatFormatting.LIGHT_PURPLE));
        return compass;
    }

    /** Redstone dust in hotbar slot 9 returns a lobby participant to their saved survival location. */
    public static boolean isLeaveLobbyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.REDSTONE)) return false;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && ("BlockParty Leave Lobby".equals(customName.getString())
                || "Leave lobby".equals(customName.getString()));
    }

    /** True when a player is physically inside this lobby, regardless of match phase. */
    public boolean isInsideLobbyArea(ServerPlayer player) {
        if (player == null || !lobbySet || level == null || player.serverLevel() != level) return false;
        return Math.abs(player.getX() - lobbyX) <= 36.0
                && Math.abs(player.getZ() - lobbyZ) <= 40.0
                && Math.abs(player.getY() - lobbyY) <= 22.0;
    }

    /** True only while this player is physically inside this arena's lobby area and it is open. */
    public boolean isLobbyAccessibleTo(ServerPlayer player) {
        if (!lobbySet || level == null || player.serverLevel() != level) return false;
        // Lobby controls must never be refreshed after participants have been teleported.
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) return false;
        return Math.abs(player.getX() - lobbyX) <= 36.0
                && Math.abs(player.getZ() - lobbyZ) <= 40.0
                && Math.abs(player.getY() - lobbyY) <= 22.0;
    }

    /** Give the non-interactive wall instructions' matching inventory items to lobby visitors. */
    public void ensureLobbyItems(ServerPlayer player) {
        if (!lobbySet || level == null || player.serverLevel() != level) return;
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) return;
        if (!returnLocations.containsKey(player.getUUID())) return;
        giveUniqueLobbyItem(player, createJoinGameItem(), Arena::isJoinGameItem);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (i != 6 && isHubItem(player.getInventory().getItem(i))) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (!isHubItem(player.getInventory().getItem(6))) player.getInventory().setItem(6, createHubItem());
        ensureLeaveLobbyItem(player);
        normalizeLobbyItemNames(player);
        if (hasLobbyItem(player, Arena::isJoinGameItem)) {
            fullLobbyInventoryNotices.remove(player.getUUID());
        }
    }

    /** Upgrade old control labels in-place without duplicating items or breaking their matchers. */
    private void normalizeLobbyItemNames(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isJoinGameItem(stack)) {
                stack.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Join game", ChatFormatting.AQUA));
            } else if (isHubItem(stack)) {
                stack.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Menu", ChatFormatting.LIGHT_PURPLE));
            } else if (isLeaveLobbyItem(stack)) {
                stack.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Leave lobby", ChatFormatting.RED));
            } else if (isMusicVotingBook(stack)) {
                stack.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Vote", ChatFormatting.LIGHT_PURPLE));
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    private void ensureLeaveLobbyItem(ServerPlayer player) {
        // Lobby inventories are cleared on join, so slot 9 is reserved for the leave control.
        // Remove any stale copies first to prevent duplication if ensureLobbyItems runs repeatedly.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (i != 8 && isLeaveLobbyItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (!isLeaveLobbyItem(player.getInventory().getItem(8))) {
            player.getInventory().setItem(8, createLeaveLobbyItem());
        }
    }

    private ItemStack createLeaveLobbyItem() {
        ItemStack dust = new ItemStack(Items.REDSTONE);
        dust.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Leave lobby", ChatFormatting.RED));
        return dust;
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
        star.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Join game", ChatFormatting.AQUA));
        return star;
    }

    /** Music voting is unlocked only after the player explicitly uses the Nether Star. */
    public void unlockMusicVotingAfterStar(ServerPlayer player) {
        if (!players.contains(player.getUUID()) || !isInsideBlockPartyArea(player)) return;
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN && phase != Phase.TELEPORT_COUNTDOWN) {
            player.sendSystemMessage(Component.literal("Musikkavstemningen åpner etter at den pågående kampen er ferdig.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        musicVotingUnlocked.add(player.getUUID());
        giveMusicVotingBook(player);
        player.inventoryMenu.broadcastChanges();
    }

    /** True only while the player is physically inside this arena's lobby or arena footprint. */
    public boolean isInsideBlockPartyArea(ServerPlayer player) {
        return player != null && player.serverLevel() == level
                && protectsLocation(level, player.getX(), player.getY(), player.getZ());
    }

    private void removeLobbyControlsFromInventory(ServerPlayer player) {
        boolean changed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isMusicVotingBook(stack) || isJoinGameItem(stack) || isLeaveLobbyItem(stack)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) player.inventoryMenu.broadcastChanges();
    }

    private void giveMusicVotingBook(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack existing = player.getInventory().getItem(i);
            if (isMusicVotingBook(existing)) {
                existing.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Vote", ChatFormatting.LIGHT_PURPLE));
                return;
            }
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
        book.set(DataComponents.CUSTOM_NAME, simpleLobbyItemName("Vote", ChatFormatting.LIGHT_PURPLE));
        return book;
    }

    private void removeLobbyItemsFromInventory(ServerPlayer player) {
        int selected = player.getInventory().selected;
        boolean removedSelected = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isMusicVotingBook(stack) || isJoinGameItem(stack) || isLeaveLobbyItem(stack)) {
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

    public void openHub(ServerPlayer player) {
        if (!isLobbyAccessibleTo(player) || !returnLocations.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("HUB er bare tilgjengelig i den åpne BlockParty-lobbyen.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new BlockPartyHubMenu(containerId, inventory, this, (ServerPlayer) menuPlayer),
                Component.literal("BlockParty HUB")));
    }

    void handleHubMenuClick(ServerPlayer player, int slot) {
        if (!isLobbyAccessibleTo(player)) return;
        switch (slot) {
            case 10 -> PlayerStatsManager.sendProfile(player);
            case 12 -> openMusicVoting(player);
            case 14 -> PlayerStatsManager.sendLeaderboard(player);
            case 30 -> player.sendSystemMessage(Component.literal("Prestasjoner kommer i en senere oppdatering.")
                    .withStyle(ChatFormatting.YELLOW));
            case 32 -> player.sendSystemMessage(Component.literal("Kosmetikk kommer i en senere oppdatering.")
                    .withStyle(ChatFormatting.YELLOW));
            case 49 -> registerForGame(player);
            default -> { }
        }
    }

    public void openMusicVoting(ServerPlayer player) {
        if (!players.contains(player.getUUID()) || !musicVotingUnlocked.contains(player.getUUID())
                || !isInsideBlockPartyArea(player)) {
            player.sendSystemMessage(Component.literal("Trykk på Nether Star i BlockParty-lobbyen for å låse opp musikkavstemningen.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN && phase != Phase.TELEPORT_COUNTDOWN) {
            player.sendSystemMessage(Component.literal("Musikkavstemningen er stengt. Stem før kampen starter.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new MusicVotingMenu(containerId, inventory, this, (ServerPlayer) menuPlayer),
                Component.literal("BlockParty – Sangvalg")));
    }

    long getTrackVoteCount(String trackId) {
        return musicVotes.values().stream().filter(trackId::equals).count();
    }

    private void refreshOpenMusicVotingMenus() {
        if (level == null || level.getServer() == null) return;
        for (UUID uuid : players) {
            ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(uuid);
            if (viewer != null && viewer.containerMenu instanceof MusicVotingMenu menu) {
                menu.refreshContents();
            }
        }
    }

    String getPlayerMusicVote(UUID playerId) {
        return musicVotes.get(playerId);
    }

    boolean hasPlayerVoted(UUID playerId) {
        return musicVotes.containsKey(playerId);
    }

    void sendAlreadyVotedMessage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Du har allerede stemt! Du kan bare stemme én gang per match.")
                .withStyle(ChatFormatting.RED));
    }

    public boolean voteMusic(ServerPlayer player, String trackId) {
        if (!players.contains(player.getUUID()) || !musicVotingUnlocked.contains(player.getUUID())
                || !isInsideBlockPartyArea(player)) {
            player.sendSystemMessage(Component.literal("Du må være i BlockParty og trykke på Nether Star før du kan stemme.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN && phase != Phase.TELEPORT_COUNTDOWN) {
            player.sendSystemMessage(Component.literal("Avstemningen er stengt.").withStyle(ChatFormatting.RED));
            return false;
        }
        Track track = MusicTrackManager.get(trackId);
        if (track == null) {
            player.sendSystemMessage(Component.literal("Fant ikke sangen " + trackId + ".").withStyle(ChatFormatting.RED));
            return false;
        }
        String previousVote = musicVotes.put(player.getUUID(), track.id());
        if (track.id().equals(previousVote)) {
            player.sendSystemMessage(Component.literal("Du har allerede valgt denne sangen.")
                    .withStyle(ChatFormatting.YELLOW));
        } else if (previousVote != null) {
            player.sendSystemMessage(Component.literal("Stemmen din er endret til " + track.name() + " — " + track.artist() + ".")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.literal("Du stemte på " + track.name() + " — " + track.artist() + "!")
                    .withStyle(ChatFormatting.GREEN));
        }
        refreshOpenMusicVotingMenus();
        refreshDjScreen();
        int requiredVotes = Math.max(2, (players.size() * 2 + 2) / 3);
        long validVotes = musicVotes.keySet().stream().filter(players::contains).count();
        if (phase == Phase.WAITING && players.size() >= MIN_PLAYERS && validVotes >= requiredVotes) {
            try {
                start(player.serverLevel().getServer());
                broadcast(Component.literal("Flertallet har stemt! BlockParty starter om 10 sekunder.").withStyle(ChatFormatting.GREEN));
            } catch (IllegalStateException exception) {
                broadcast(Component.literal("Kunne ikke starte BlockParty: " + exception.getMessage()).withStyle(ChatFormatting.RED));
            }
        }
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
        captureArenaAccents();
    }

    private void tickLobbyDanceFloor() {
        if (level == null || !lobbySet || (phase != Phase.WAITING && phase != Phase.COUNTDOWN)) return;
        int frame = (scoreboardTick / 20) % 6;
        if (frame == lastDancePattern) return;
        lastDancePattern = frame;
        int centerX = (int) Math.floor(lobbyX);
        int minZ = (int) Math.floor(lobbyZ) - 6;
        Block[] colours = {Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE,
                Blocks.BLUE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.BLACK_CONCRETE};
        for (int x = centerX - 8; x <= centerX + 8; x++) for (int z = minZ; z <= minZ + 14; z++) {
            int dx = x - centerX, dz = z - (minZ + 7), pattern;
            switch (frame) {
                case 0 -> pattern = Math.floorMod(dx + dz, 6);
                case 1 -> pattern = Math.floorMod(dx - dz, 6);
                case 2 -> pattern = Math.floorMod(dx / 2 + dz / 2, 6);
                case 3 -> pattern = Math.floorMod(Math.abs(dx) + Math.abs(dz), 6);
                case 4 -> pattern = Math.floorMod(x + scoreboardTick / 20, 6);
                default -> pattern = Math.floorMod(z + scoreboardTick / 20, 6);
            }
            BlockPos pos = new BlockPos(x, (int) Math.floor(lobbyY) - 1, z);
            BlockState desired = colours[pattern].defaultBlockState();
            if (!level.getBlockState(pos).equals(desired)) level.setBlock(pos, desired, 3);
        }
    }

    public void tick(MinecraftServer server) {
        scoreboardTick++;
        tickWinnerPodiumFreeze(server);
        if (podiumDisplayTicks > 0 && --podiumDisplayTicks == 0) {
            clearWinnerPodiumAtDjBooth();
            refreshLobbyDisplays();
        }
        if (scoreboardTick % 20 == 0) tickLobbyDanceFloor();
        enforceBlockPartyNeeds(server);
        if (scoreboardTick % 5 == 0) {
            for (UUID uuid : new HashSet<>(players)) {
                ServerPlayer sessionPlayer = server.getPlayerList().getPlayer(uuid);
                if (sessionPlayer != null && !isInsideBlockPartyArea(sessionPlayer)) {
                    removeLobbyControlsFromInventory(sessionPlayer);
                    musicVotingUnlocked.remove(uuid);
                    musicVotes.remove(uuid);
                }
            }
        }
        if ((phase == Phase.PREPARE || phase == Phase.FALL || phase == Phase.INTERMISSION
                || phase == Phase.FINISHING) && level != null) {
            // After the sunset countdown, keep the arena at its final night setting.
            if (teleportSunsetEndTime != Long.MIN_VALUE && level.getDayTime() != teleportSunsetEndTime) {
                level.setDayTime(teleportSunsetEndTime);
            }
        }
        if (scoreboardTick % 20 == 0) removeMobsFromProtectedArea();
        if (isGameInProgress()) {
            // Leaving the playable floor is an elimination, not a vanilla fall death.
            // Check every active player independently of target-contact bookkeeping so
            // jumping deliberately over the edge cannot bypass the normal elimination flow.
            for (UUID uuid : new ArrayList<>(alive)) {
                ServerPlayer active = server.getPlayerList().getPlayer(uuid);
                if (active == null) continue;
                if (active.getY() <= pos1.getY() - 3.0) eliminate(active);
            }
        }
        if (phase == Phase.PREPARE || phase == Phase.FALL) recordTargetContacts(server);
        if (phase == Phase.WAITING && scoreboardTick % 100 == 0) refreshLobbyLeaderboard();
        tickMusicEffects();
        if (scannerTicks > 0 && --scannerTicks % 20 == 0 && targetColor != null && level != null) {
            for (Map.Entry<BlockPos, BlockState> tile : floorSnapshot.entrySet()) {
                if (BlockPartyColors.getColor(tile.getValue()) == targetColor) {
                    level.sendParticles(ParticleTypes.END_ROD, tile.getKey().getX() + 0.5, tile.getKey().getY() + 1.08,
                            tile.getKey().getZ() + 0.5, 2, 0.18, 0.02, 0.18, 0.005);
                }
            }
        }
        switch (phase) {
            case WAITING -> { /* Waiting for the 50% music-vote threshold. */ }
            case COUNTDOWN -> {
                if (players.isEmpty()) {
                    phase = Phase.WAITING;
                    timerTicks = 0;
                    stopMusic();
                    break;
                }
                timerTicks--;
                if (timerTicks > 0 && timerTicks % 20 == 0) {
                    int secondsLeft = timerTicks / 20;
                    broadcastActionBar(Component.literal("SPILLET STARTER OM  " + secondsLeft + "  SEKUNDER")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                    if (secondsLeft <= 3) {
                        for (UUID uuid : players) {
                            ServerPlayer countdownPlayer = server.getPlayerList().getPlayer(uuid);
                            if (countdownPlayer == null) continue;
                            countdownPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(0, 18, 2));
                            countdownPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.literal(Integer.toString(secondsLeft))
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                            countdownPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("GJØR DEG KLAR!")
                                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
                        }
                    }
                }
                if (timerTicks <= 0) beginMatch(server);
            }
            case TELEPORT_COUNTDOWN -> {
                if (alive.isEmpty()) {
                    declareWinner();
                    break;
                }
                timerTicks--;
                long sunsetProgress = TELEPORT_COUNTDOWN_TICKS - Math.max(0, timerTicks);
                long sunsetTime = teleportSunsetStartTime
                        + ((teleportSunsetEndTime - teleportSunsetStartTime) * sunsetProgress)
                        / TELEPORT_COUNTDOWN_TICKS;
                // Send a time-sync packet every server tick. Vanilla normally synchronizes
                // world time much less often, which makes a five-second accelerated sunset
                // appear to jump in large steps on clients even when the server clock is smooth.
                level.setDayTime(sunsetTime);
                ClientboundSetTimePacket timePacket = new ClientboundSetTimePacket(
                        level.getGameTime(), sunsetTime, false);
                for (UUID uuid : alive) {
                    ServerPlayer viewer = server.getPlayerList().getPlayer(uuid);
                    if (viewer != null) viewer.connection.send(timePacket);
                }
                if (timerTicks > 0 && timerTicks % 20 == 0) {
                    int secondsLeft = timerTicks / 20;
                    for (UUID uuid : alive) {
                        ServerPlayer countdownPlayer = server.getPlayerList().getPlayer(uuid);
                        if (countdownPlayer == null) continue;
                        countdownPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(0, 18, 2));
                        countdownPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.literal(Integer.toString(secondsLeft))
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
                    }
                    broadcastActionBar(Component.literal("MATCH STARTER OM  " + secondsLeft + "  SEKUNDER")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
                if (timerTicks <= 0) {
                    Track nowPlaying = selectedMusicTrackId == null ? null : MusicTrackManager.get(selectedMusicTrackId);
                    if (nowPlaying != null) {
                        broadcast(Component.literal("Now playing: " + nowPlaying.name() + " by " + nowPlaying.artist() + ". :)")
                                .withStyle(ChatFormatting.AQUA));
                    }
                    beginRound(server);
                }
            }
            case PREPARE -> {
                rememberTargetContacts(server);
                timerTicks--;
                if (timerTicks > 0 && timerTicks % 2 == 0) broadcastTargetHud();
                if (timerTicks <= 0) {
                    rememberTargetContacts(server);
                    resolveRound();
                }
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
                    } else if (isStandingOnTargetTile(player)) {
                        // A player may complete their landing just after the round timer
                        // reaches zero. The target tiles remain during FALL, so accept
                        // a genuine landing before they drop into the pit.
                        pendingEliminations.remove(uuid);
                        lastTargetContactPos.put(uuid, new BlockPos(player.blockPosition().getX(),
                                pos1.getY(), player.blockPosition().getZ()).immutable());
                        lastTargetContactTick.put(uuid, (long) scoreboardTick);
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
        if (phase == Phase.PREPARE || phase == Phase.FALL || phase == Phase.INTERMISSION || phase == Phase.FINISHING) {
            enforceTargetHotbar(server);
        }
        if (phase != Phase.WAITING && scoreboardTick % 5 == 0) updateScoreboard(server);
    }

    /** Keep the target block fixed in the centre slot; a LEAP feather temporarily unlocks scrolling. */
    private void enforceTargetHotbar(MinecraftServer server) {
        for (UUID uuid : alive) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            // The target concrete always belongs in slot 5. If a toss or inventory action
            // displaced it, recover the existing stack before creating a replacement.
            ItemStack centre = player.getInventory().getItem(TARGET_BLOCK_SLOT);
            if (!isTargetColorBlock(centre)) {
                ItemStack recovered = ItemStack.EMPTY;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    if (slot == TARGET_BLOCK_SLOT) continue;
                    ItemStack candidate = player.getInventory().getItem(slot);
                    if (isTargetColorBlock(candidate)) {
                        recovered = candidate.copy();
                        player.getInventory().setItem(slot, ItemStack.EMPTY);
                        break;
                    }
                }
                if (recovered.isEmpty() && targetColor != null) {
                    recovered = new ItemStack(BlockPartyColors.getConcrete(targetColor));
                    recovered.set(DataComponents.CUSTOM_NAME, Component.literal(BlockPartyColors.displayName(targetColor))
                            .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
                }
                if (!recovered.isEmpty()) player.getInventory().setItem(TARGET_BLOCK_SLOT, recovered);
            }

            if (!hasLeapFeather(player) && player.getInventory().selected != TARGET_BLOCK_SLOT) {
                player.getInventory().selected = TARGET_BLOCK_SLOT;
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(TARGET_BLOCK_SLOT));
            }
        }
    }

    private static boolean hasLeapFeather(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isLeapFeather(player.getInventory().getItem(slot))) return true;
        }
        return false;
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
        lastTargetContactTick.clear();
        lastTargetContactPos.clear();
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

        updateArenaAccents(targetColor);
        updateTargetItems(server);
        updateColorDisplay();
        timerTicks = getRoundDurationTicks(round);
        phase = Phase.PREPARE;
        if (musicPaused) resumeMusic();
        else if (activeMusicId == null && !musicFinished) playSelectedMusic();
        broadcastTargetHud();
        playSound(ModSounds.COLOR_REVEAL, SoundSource.PLAYERS, 0.8F, 1.0F);
        playSound(ModSounds.ROUND_START, SoundSource.PLAYERS, 0.55F, 1.0F);
        // Up to five widely spaced beacons may appear in a round; they are intentionally rare.
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
        sidebarObjective = current;

        // Use the server scoreboard so participants receive objective and score updates
        // reliably, then hide the sidebar from every non-participant.
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, sidebarObjective);
        hideSidebarFromNonParticipants(server);
        updateScoreboard(server);
    }

    private void hideSidebarFromNonParticipants(MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!players.contains(online.getUUID())) {
                online.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
            }
        }
    }

    private void updateScoreboard(MinecraftServer server) {
        if (sidebarObjective == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        // Also suppress the sidebar for players who join after the match has started.
        hideSidebarFromNonParticipants(server);

        ServerPlayer profilePlayer = null;
        for (UUID uuid : players) {
            profilePlayer = server.getPlayerList().getPlayer(uuid);
            if (profilePlayer != null) break;
        }
        // During a match, show points earned in this session rather than lifetime progression.
        int matchPoints = profilePlayer == null ? 0 : points.getOrDefault(profilePlayer.getUUID(), 0);
        int personalPowerups = profilePlayer == null ? 0 : powerupsCollected.getOrDefault(profilePlayer.getUUID(), 0);
        String timeText = (phase == Phase.COUNTDOWN || phase == Phase.TELEPORT_COUNTDOWN) ? "NEDTELLING" :
                (phase == Phase.PREPARE || phase == Phase.FALL || phase == Phase.INTERMISSION)
                        ? formatSeconds(getRoundDurationTicks(round)) + "s" : "--";
        PlayerStatsManager.PlayerRecord profile = profilePlayer == null
                ? null : PlayerStatsManager.getRecord(profilePlayer.getUUID());
        int currentStreak = profile == null ? 0 : profile.currentStreak();
        int bestStreak = profile == null ? 0 : profile.bestStreak();
        String[] ids = {
                "bp_line_0", "bp_line_1", "bp_line_2", "bp_line_3", "bp_line_4", "bp_line_5",
                "bp_line_6", "bp_line_7", "bp_line_8", "bp_line_9", "bp_line_10", "bp_line_11"
        };
        Component[] lines = {
                Component.literal("Dancers Left").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                Component.literal(Integer.toString(alive.size())).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Game Info").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.literal("Round: " + round + " / " + MAX_ROUNDS).withStyle(ChatFormatting.WHITE),
                Component.literal("Round Length: " + timeText).withStyle(ChatFormatting.GRAY),
                Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Your Stats").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal("Points: " + matchPoints).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                Component.literal("Win Streak: " + currentStreak).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("Best Win Streak: " + bestStreak).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("Powerups: " + personalPowerups).withStyle(ChatFormatting.GRAY)
        };
        for (int i = 0; i < ids.length; i++) {
            ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(ids[i]), sidebarObjective);
            score.set(ids.length - i);
            score.display(lines[i]);
            score.numberFormatOverride(BlankFormat.INSTANCE);
        }
        // Remove entries from the previous 14-line layout so old rank lines cannot linger.
        scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly("bp_line_12"), sidebarObjective);
        scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly("bp_line_13"), sidebarObjective);
    }

    private void hideScoreboard(MinecraftServer server) {
        if (sidebarObjective == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        if (scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) == sidebarObjective) {
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, null);
        }
        // No player should retain a sidebar after the match ends.
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            online.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
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
        int width = Math.abs(pos2.getX() - pos1.getX()) + 1;
        int depth = Math.abs(pos2.getZ() - pos1.getZ()) + 1;
        int centerX = width / 2;
        int centerZ = depth / 2;
        // Early rounds use broad readable shapes; later rounds introduce finer, more fragmented layouts.
        int minPattern = round <= 5 ? 0 : round <= 10 ? 2 : round <= 15 ? 4 : round <= 20 ? 7 : 10;
        int maxPattern = round <= 5 ? 5 : round <= 10 ? 9 : round <= 15 ? 12 : round <= 20 ? 14 : 15;
        int patternCount = maxPattern - minPattern + 1;
        int pattern = minPattern + random.nextInt(patternCount);
        if (pattern == previousPattern) pattern = minPattern + ((pattern - minPattern + 1 + random.nextInt(patternCount - 1)) % patternCount);
        previousPattern = pattern;
        int offset = random.nextInt(colors.size());
        int twist = random.nextInt(4);
        Set<DyeColor> used = EnumSet.noneOf(DyeColor.class);

        for (BlockPos pos : new ArrayList<>(originalFloorSnapshot.keySet())) {
            int x = pos.getX() - minX;
            int z = pos.getZ() - minZ;
            int dx = x - centerX;
            int dz = z - centerZ;
            int ax = Math.abs(dx);
            int az = Math.abs(dz);
            int ring = Math.max(ax, az);
            int distance = ax + az;
            int diagonal = x + z;
            int index;
            switch (pattern) {
                case 0 -> index = (ring / 2 + twist) % colors.size(); // broad concentric squares
                case 1 -> index = Math.floorMod((int) Math.floor(Math.atan2(dz, dx) * 4.0 + Math.sqrt(dx * dx + dz * dz) / 2.5 + twist), colors.size()); // spiral
                case 2 -> index = ((x / 3) + (z / 3) + twist) % colors.size(); // checkerboard
                case 3 -> index = (int) Math.floorMod((diagonal / 3) + twist, colors.size()); // diagonal bands
                case 4 -> index = ((x / 4) % 2 == 0 ? z / 2 : (depth - 1 - z) / 2) + twist; // woven lanes
                case 5 -> index = (distance / 3 + twist) % colors.size(); // diamond rings
                case 6 -> index = Math.floorMod((int) Math.floor(Math.atan2(dz, dx) * colors.size() / (Math.PI * 2.0) + twist), colors.size()); // radial color wheel
                case 7 -> index = ((ax / 3) * 3 + (az / 3) + twist) % colors.size(); // nested blocks
                case 8 -> index = (ax <= 2 || az <= 2 ? 0 : (ring / 3 + twist)) % colors.size(); // cross with outer bands
                case 9 -> index = ((int) Math.floor((Math.sin(x / 3.0 + twist) + Math.cos(z / 3.0) + 2.0) * 3.0) + twist) % colors.size(); // flowing waves
                case 10 -> index = ((dx * dz >= 0 ? 0 : colors.size() / 2) + (ring / 3) + twist) % colors.size(); // pinwheel quadrants
                case 11 -> index = ((x / 5) % 2 == 0 ? z / 3 : (depth - 1 - z) / 3) + (x / 5) + twist; // interlocking maze
                case 12 -> index = x + 2 * z + twist; // tight diagonal stripes
                case 13 -> index = (x ^ z ^ twist); // one-tile checker pattern
                case 14 -> index = x * 3 + z * 5 + (x * z) % 7 + twist; // fractured mosaic
                default -> {
                    int cell = round >= 21 ? 1 : round >= 16 ? 2 : 3;
                    index = (x / cell) + (z / cell) * 2 + ((x % cell == 0 || z % cell == 0) ? twist : 0);
                } // tight interlocking micro-grid
            }
            DyeColor color = colors.get(Math.floorMod(index + offset, colors.size()));
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
            InventorySnapshot snapshot = originalHeldItems.get(uuid);
            if (player == null || snapshot == null) continue;

            player.getInventory().selected = TARGET_BLOCK_SLOT;
            ItemStack targetBlock = new ItemStack(BlockPartyColors.getConcrete(targetColor));
            targetBlock.set(DataComponents.CUSTOM_NAME, Component.literal(BlockPartyColors.displayName(targetColor))
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
            player.getInventory().setItem(TARGET_BLOCK_SLOT, targetBlock);
            player.inventoryMenu.broadcastChanges();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(TARGET_BLOCK_SLOT));
        }
    }

    /** A named target concrete block is a protected match item, not a throwable item. */
    public static boolean isTargetColorBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !BlockPartyColors.isConcrete(net.minecraft.world.level.block.Block.byItem(stack.getItem()).defaultBlockState())) return false;
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null) return false;
        for (DyeColor color : DyeColor.values()) {
            if (BlockPartyColors.displayName(color).equals(name.getString())) return true;
        }
        return false;
    }

    /** Restore a tossed target block to its dedicated centre hotbar slot, never the first free slot. */
    public void restoreTossedTargetBlock(ServerPlayer player, ItemStack stack) {
        if (targetColor == null || !players.contains(player.getUUID())) return;
        ItemStack restored = new ItemStack(BlockPartyColors.getConcrete(targetColor));
        restored.set(DataComponents.CUSTOM_NAME, Component.literal(BlockPartyColors.displayName(targetColor))
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        player.getInventory().setItem(TARGET_BLOCK_SLOT, restored);
        player.getInventory().selected = TARGET_BLOCK_SLOT;
        player.inventoryMenu.broadcastChanges();
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(TARGET_BLOCK_SLOT));
    }

    private void rememberTargetContacts(MinecraftServer server) {
        if (targetColor == null) return;
        for (UUID uuid : alive) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            BlockPos tile = new BlockPos(player.blockPosition().getX(), pos1.getY(), player.blockPosition().getZ());
            if (BlockPartyColors.getColor(level.getBlockState(tile)) == targetColor
                    && player.getY() >= pos1.getY() + 0.75
                    && player.getY() <= pos1.getY() + 2.5) {
                lastTargetContactPos.put(uuid, tile.immutable());
                lastTargetContactTick.put(uuid, (long) scoreboardTick);
            }
        }
    }

    private void recordTargetContacts(MinecraftServer server) {
        if (targetColor == null || pos1 == null) return;
        long now = level.getGameTime();
        for (UUID uuid : alive) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            BlockPos tile = new BlockPos(player.blockPosition().getX(), pos1.getY(), player.blockPosition().getZ());
            if (BlockPartyColors.getColor(level.getBlockState(tile)) == targetColor) {
                lastTargetContactTick.put(uuid, (long) scoreboardTick);
                lastTargetContactPos.put(uuid, tile.immutable());
            }
        }
    }

    private boolean isStandingOnTargetTile(ServerPlayer player) {
        if (targetColor == null || pos1 == null || level == null) return false;
        BlockPos tile = new BlockPos(player.blockPosition().getX(), pos1.getY(), player.blockPosition().getZ());
        return BlockPartyColors.getColor(level.getBlockState(tile)) == targetColor
                && player.getY() >= pos1.getY() + 0.75
                && player.getY() <= pos1.getY() + 2.5;
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
            boolean onTargetNow = standingColor == targetColor;
            long lastContact = lastTargetContactTick.getOrDefault(uuid, Long.MIN_VALUE);
            BlockPos lastTile = lastTargetContactPos.get(uuid);
            boolean tickOrderGrace = !onTargetNow && lastTile != null
                    && scoreboardTick - lastContact <= 3
                    && Math.abs(player.getX() - (lastTile.getX() + 0.5)) <= 0.85
                    && Math.abs(player.getZ() - (lastTile.getZ() + 0.5)) <= 0.85
                    && player.getY() >= pos1.getY() + 0.55
                    && player.getY() <= pos1.getY() + 2.5;
            if (!onTargetNow && !tickOrderGrace) pendingEliminations.add(uuid);
        }

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
        pauseMusic();
        phase = Phase.FALL;
        timerTicks = getFallDurationTicks();
        broadcastActionBar(Component.literal("FEIL FARGE FORSVINNER!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private void eliminate(ServerPlayer player) {
        UUID uuid = player.getUUID();
        lastEliminatedPlayer = uuid;
        if (!alive.contains(uuid)) {
            pendingEliminations.remove(uuid);
            return;
        }
        if (luckyLanding.remove(uuid)) {
            pendingEliminations.remove(uuid);
            if (targetColor != null) {
                List<BlockPos> safe = floorSnapshot.entrySet().stream()
                        .filter(entry -> BlockPartyColors.getColor(entry.getValue()) == targetColor)
                        .map(Map.Entry::getKey).filter(pos -> level.isEmptyBlock(pos.above()) && level.isEmptyBlock(pos.above(2)))
                        .toList();
                if (!safe.isEmpty()) {
                    BlockPos rescue = safe.get(random.nextInt(safe.size()));
                    player.teleportTo(level, rescue.getX() + 0.5, rescue.getY() + 1.05, rescue.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                    player.fallDistance = 0.0F;
                    player.sendSystemMessage(Component.literal("LUCKY LANDING reddet deg! Én eliminering er blokkert.")
                            .withStyle(ChatFormatting.GREEN));
                    playSound(ModSounds.POWERUP_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
                    return;
                }
            }
        }
        if (!alive.remove(uuid)) return;
        pendingEliminations.remove(uuid);
        // Cosmetic lightning strikes at the hole in the floor; visual-only prevents damage/fire to others.
        if (level != null) {
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            bolt.setVisualOnly(true);
            bolt.setPos(player.getX(), pos1.getY() + 0.15, player.getZ());
            level.addFreshEntity(bolt);
        }
        broadcast(Component.literal(player.getName().getString() + " tapte konkurransen!")
                .withStyle(ChatFormatting.RED));
        PlayerStatsManager.breakStreak(level.getServer(), uuid);
        // Eliminated players must have an entirely empty inventory until the match
        // ends. In particular, never restore their pre-match selected item here: it
        // could be a Nether Star or Redstone Dust and expose lobby controls mid-match.
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();
        player.setGameMode(GameType.ADVENTURE);
        teleportToSpectatorWalkway(player);
        player.sendSystemMessage(Component.literal("Du er ute! Du kan følge kampen fra spectatorgangen.")
                .withStyle(ChatFormatting.RED));
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

        // The remaining target-colour tiles stay visible for the full 3-second transition.
        // Transition durations scale with the selected song so all 20 rounds fit its playback window.
        broadcastActionBar(Component.literal("RUNDE " + round + " FULLFØRT  •  " + alive.size() + " IGJEN")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        refreshLobbyDisplays();

        if (alive.isEmpty()) {
            declareWinner();
        } else if (soloMode) {
            if (round >= MAX_ROUNDS) declareWinner();
            else {
                phase = Phase.INTERMISSION;
                timerTicks = getIntermissionDurationTicks();
            }
        } else if (alive.size() <= 1 || round >= MAX_ROUNDS) {
            declareWinner();
        } else {
            phase = Phase.INTERMISSION;
            timerTicks = getIntermissionDurationTicks();
        }
        updateScoreboard(level.getServer());
    }

    private void declareWinner() {
        if (phase == Phase.FINISHING) return;
        stopMusic();
        lastMatchDurationTicks = Math.max(0L, scoreboardTick - matchStartTick);
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

        matchWinnerId = winner == null ? null : winner.getUUID();
        lastPodium.clear();
        players.stream().sorted((a, b) -> Integer.compare(points.getOrDefault(b, 0), points.getOrDefault(a, 0)))
                .limit(3).forEach(uuid -> {
                    ServerPlayer ranked = level.getServer().getPlayerList().getPlayer(uuid);
                    String name = ranked != null ? ranked.getName().getString() : PlayerStatsManager.getRecord(uuid).name();
                    lastPodium.add(name + "  •  " + points.getOrDefault(uuid, 0) + " runder");
                });
        // A win streak counts consecutive match victories, never rounds survived.
        // Every participant other than the declared winner has lost this match.
        for (UUID participantId : players) {
            if (winner == null || !participantId.equals(winner.getUUID())) {
                PlayerStatsManager.breakStreak(level.getServer(), participantId);
            }
        }
        if (winner != null) {
            PlayerStatsManager.recordWin(level.getServer(), winner);
            level.sendParticles(ParticleTypes.FIREWORK, winner.getX(), winner.getY() + 1.5, winner.getZ(),
                    90, 2.4, 2.0, 2.4, 0.18);
            level.sendParticles(ParticleTypes.END_ROD, winner.getX(), winner.getY() + 1.0, winner.getZ(),
                    36, 1.4, 1.2, 1.4, 0.04);
            level.sendParticles(ParticleTypes.FIREWORK, winner.getX(), winner.getY() + 2.0, winner.getZ(),
                    120, 3.5, 2.5, 3.5, 0.28);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, winner.getX(), winner.getY() + 1.0, winner.getZ(),
                    100, 2.8, 1.8, 2.8, 0.18);
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
                    .withStyle(ChatFormatting.GOLD));
            broadcast(Component.literal("MVP: " + mvp.getName().getString() + "  •  "
                    + points.getOrDefault(mvp.getUUID(), 0) + " runder overlevd  •  "
                    + powerupsCollected.getOrDefault(mvp.getUUID(), 0) + " power-ups")
                    .withStyle(ChatFormatting.AQUA));
        } else if (alive.isEmpty()) {
            broadcast(Component.literal("BlockParty er ferdig – ingen overlevde denne gangen.").withStyle(ChatFormatting.GRAY));
        } else {
            broadcast(Component.literal("🏆 25 RUNDER FULLFØRT! Alle gjenværende spillere vinner! 🏆")
                    .withStyle(ChatFormatting.GOLD));
        }
        broadcast(Component.literal("MATCHVARIGHET: " + formatSeconds((int) lastMatchDurationTicks) + " sekunder")
                .withStyle(ChatFormatting.YELLOW));
        for (UUID uuid : players) {
            ServerPlayer participant = level.getServer().getPlayerList().getPlayer(uuid);
            if (participant != null) participant.sendSystemMessage(Component.literal("STATISTIKK  •  "
                    + points.getOrDefault(uuid, 0) + " runder overlevd  •  "
                    + powerupsCollected.getOrDefault(uuid, 0) + " power-ups  •  "
                    + formatSeconds((int) lastMatchDurationTicks) + " sekunder")
                    .withStyle(ChatFormatting.AQUA));
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
        clearLeapFeathers(server);
        removeActiveBeacon();
        restoreOriginalFloor();
        restoreOriginalArenaAccents();

        // The match is fully over: move the podium from the lobby corner to the DJ booth.
        if (matchWinnerId != null) buildWinnerPodiumAtDjBooth();

        // The match is fully over: return every participant, including the winner and
        // eliminated spectators, to the BlockParty lobby. They remain in the session
        // until they explicitly use /bp leave or the redstone-dust leave item.
        for (UUID uuid : new HashSet<>(players)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            Set<Holder<MobEffect>> effects = roundPowerupEffects.remove(uuid);
            if (effects != null) for (Holder<MobEffect> effect : effects) player.removeEffect(effect);
            player.removeEffect(MobEffects.NIGHT_VISION);
            player.setGameMode(GameType.ADVENTURE);
            player.getInventory().clearContent();
            // Returning to the lobby does not enroll anyone in the next match.
            // The Nether Star is the explicit opt-in; only then is the vote book granted.
            player.getInventory().setItem(0, createJoinGameItem());
            player.getInventory().setItem(6, createHubItem());
            player.getInventory().setItem(8, createLeaveLobbyItem());
            player.inventoryMenu.broadcastChanges();
            teleportToLobby(player);
            if (matchWinnerId != null && uuid.equals(matchWinnerId)) teleportWinnerToPodium(player);
            player.sendSystemMessage(Component.literal("Kampen er ferdig! Alle er sendt tilbake til BlockParty-lobbyen. Bruk /bp leave eller redstone dust for å dra hjem.")
                    .withStyle(ChatFormatting.GREEN));
        }

        // Start the 20-second podium display window only after all participants have returned.
        podiumDisplayTicks = matchWinnerId != null ? 20 * 20 : 0;

        alive.clear();
        pendingEliminations.clear();
        players.clear();
        musicVotingUnlocked.clear();
        musicVotes.clear();
        selectedMusicTrackId = null;
        points.clear();
        powerupsCollected.clear();
        leapFallProtection.clear();
        luckyLanding.clear();
        matchStartPersonalBest.clear();
        personalBestNotified.clear();
        scannerTicks = 0;
        matchStartTick = 0L;
        phase = Phase.WAITING;
        timerTicks = 0;
        matchWinnerId = null;
        round = 0;
        targetColor = null;
        previousTargetColor = null;
        previousPattern = -1;
        lastPowerupRound = -10;
        soloMode = false;
        lastEliminatedPlayer = null;
        if (level != null && originalDayTime != Long.MIN_VALUE) {
            level.setDayTime(originalDayTime);
            if (originalDaylightCycle != null) {
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalDaylightCycle, server);
            }
            if (originalWeatherCycle != null) {
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(originalWeatherCycle, server);
            }
            originalDayTime = Long.MIN_VALUE;
            teleportSunsetStartTime = Long.MIN_VALUE;
            teleportSunsetEndTime = Long.MIN_VALUE;
            originalDaylightCycle = null;
            originalWeatherCycle = null;
        }
        refreshLobbyDisplays();
        updateScoreboard(server);
    }

    private void restorePlayerState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        leapFallProtection.remove(uuid);
        Set<Holder<MobEffect>> effects = roundPowerupEffects.remove(uuid);
        if (effects != null) for (Holder<MobEffect> effect : effects) player.removeEffect(effect);

        // End any in-progress item use from the lobby controls before restoring the
        // player's Survival state. This avoids a stale use/action state on the client.
        player.stopUsingItem();

        // Leaving BlockParty always returns the player to normal Survival.
        originalGameModes.remove(uuid);
        player.setGameMode(GameType.SURVIVAL);

        FoodSnapshot food = originalFoodStates.remove(uuid);
        if (food != null) food.restore(player);

        InventorySnapshot held = originalHeldItems.remove(uuid);
        if (held != null) held.restore(player);

        // Explicitly synchronize abilities, selected hotbar slot and inventory.
        // The Survival game-mode packet normally handles this, but the explicit refresh
        // prevents Adventure-mode interaction state from lingering after /bp leave.
        player.onUpdateAbilities();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(
                player.getInventory().selected));
    }


    private void teleportToSpectatorWalkway(ServerPlayer player) {
        if (level == null || pos1 == null || pos2 == null) return;
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        int centerX = minX + (maxX - minX + 1) / 2;
        int centerZ = minZ + (maxZ - minZ + 1) / 2;
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

    private void hideLobbyLeaderboard() {
        if (level == null || !lobbySet) return;
        // Search the complete arena + lobby footprint, not just the current lobby panel.
        // Older builds briefly spawned the leaderboard on the arena wall, so this also
        // removes those stale displays when a match starts.
        double minX = lobbyX - 70.0;
        double maxX = lobbyX + 70.0;
        double minZ = lobbyZ - 110.0;
        double maxZ = lobbyZ + 75.0;
        double minY = lobbyY - 30.0;
        double maxY = lobbyY + 55.0;
        if (pos1 != null && pos2 != null) {
            minX = Math.min(minX, Math.min(pos1.getX(), pos2.getX()) - 24.0);
            maxX = Math.max(maxX, Math.max(pos1.getX(), pos2.getX()) + 24.0);
            minZ = Math.min(minZ, Math.min(pos1.getZ(), pos2.getZ()) - 30.0);
            maxZ = Math.max(maxZ, Math.max(pos1.getZ(), pos2.getZ()) + 30.0);
            minY = Math.min(minY, Math.min(pos1.getY(), pos2.getY()) - 20.0);
            maxY = Math.max(maxY, Math.max(pos1.getY(), pos2.getY()) + 35.0);
        }
        AABB area = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                display -> isLeaderboardHologram(display.getTags()))) old.discard();
        for (ArmorStand old : level.getEntitiesOfClass(ArmorStand.class, area,
                stand -> isLeaderboardHologram(stand.getTags()))) old.discard();
    }

    private boolean isLeaderboardHologram(Set<String> tags) {
        for (String raw : tags) {
            String tag = raw.toLowerCase(java.util.Locale.ROOT);
            if (tag.contains("leaderboard") || tag.contains("lb_") || tag.contains("top10")) return true;
        }
        return false;
    }

    /** Refreshes the lobby-only leaderboard. Its text is hidden while players are in the arena. */
    private void refreshLobbyLeaderboard() {
        if (level == null || !lobbySet) return;
        if (phase == Phase.COUNTDOWN || phase == Phase.TELEPORT_COUNTDOWN || phase == Phase.PREPARE || phase == Phase.FALL
                || phase == Phase.INTERMISSION || phase == Phase.FINISHING) {
            hideLobbyLeaderboard();
            return;
        }
        hideLobbyLeaderboard();
        double boardX = lobbyX + 30.0;
        // The east wall is split into two balanced panels. Commands occupy the
        // lower-Z side; the top-five leaderboard occupies the upper-Z side.
        double boardZ = lobbyZ + 18.5;
        double commandsZ = lobbyZ - 6.5;
        BlockPos anchor = BlockPos.containing(boardX, lobbyY + 8.0, boardZ);
        if (!level.hasChunkAt(anchor)) return;

        String tag = "blockparty_lb_" + id;
        // Both panels share one cleanup region and tag, so periodic refreshes cannot duplicate text.
        AABB area = new AABB(boardX - 5.0, lobbyY - 1.0, lobbyZ - 24.0,
                boardX + 5.0, lobbyY + 16.0, lobbyZ + 32.0);
        for (Display.TextDisplay old : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                display -> display.getTags().contains(tag))) old.discard();
        for (ArmorStand old : level.getEntitiesOfClass(ArmorStand.class, area,
                stand -> stand.getTags().contains(tag))) old.discard();

        // LEADERBOARD PANEL — keep a constant vertical centre regardless of list length.
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("LEADERBOARD").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        lines.add(Component.literal("TOP 5 DANCERS").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        List<PlayerStatsManager.PlayerRecord> top = PlayerStatsManager.topPlayers(5);
        if (top.isEmpty()) {
            lines.add(Component.literal("No scores yet - survive a round to score!").withStyle(ChatFormatting.GRAY));
        } else {
            for (int i = 0; i < top.size(); i++) {
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
        lines.add(Component.literal("/bp leaderboard  •  /bp ranklist").withStyle(ChatFormatting.LIGHT_PURPLE));

        double panelCenterY = lobbyY + 7.0; // lowered from the previous layout
        double lineSpacing = 0.95;
        double firstLineY = panelCenterY + (lines.size() - 1) * lineSpacing / 2.0;
        for (int i = 0; i < lines.size(); i++) {
            float scale = i == 0 ? 1.9F : (i == 1 ? 1.25F : (i == lines.size() - 1 ? 0.95F : 1.0F));
            double y = firstLineY - i * lineSpacing;
            BlockPartyText.spawnTextDisplay(level, boardX, y, boardZ, 90.0F, lines.get(i), scale, tag);
        }

        // COMMANDS PANEL — the three most useful player commands and what they do.
        List<Component> commandLines = List.of(
                Component.literal("NYTTIGE KOMMANDOER").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                Component.literal("/bp leaderboard").withStyle(ChatFormatting.AQUA),
                Component.literal("Se topp 5 spillere").withStyle(ChatFormatting.GRAY),
                Component.literal("/bp ranklist").withStyle(ChatFormatting.AQUA),
                Component.literal("Se alle ranker og poengkrav").withStyle(ChatFormatting.GRAY),
                Component.literal("/bp profile").withStyle(ChatFormatting.AQUA),
                Component.literal("Se din rank, poeng og statistikk").withStyle(ChatFormatting.GRAY)
        );
        double commandSpacing = 1.05;
        double commandFirstY = panelCenterY + (commandLines.size() - 1) * commandSpacing / 2.0;
        for (int i = 0; i < commandLines.size(); i++) {
            float scale = i == 0 ? 1.65F : (i % 2 == 1 ? 1.15F : 0.78F);
            double y = commandFirstY - i * commandSpacing;
            BlockPartyText.spawnTextDisplay(level, boardX, y, commandsZ, 90.0F,
                    commandLines.get(i), scale, tag);
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
        return phase == Phase.COUNTDOWN || phase == Phase.TELEPORT_COUNTDOWN || phase == Phase.PREPARE || phase == Phase.FALL
                || phase == Phase.INTERMISSION || phase == Phase.FINISHING;
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

    private record FoodSnapshot(int foodLevel, float saturation, float exhaustion) {
        private static FoodSnapshot capture(ServerPlayer player) {
            var food = player.getFoodData();
            return new FoodSnapshot(food.getFoodLevel(), food.getSaturationLevel(), food.getExhaustionLevel());
        }

        private void restore(ServerPlayer player) {
            var food = player.getFoodData();
            food.setFoodLevel(foodLevel);
            food.setSaturation(saturation);
            food.setExhaustion(exhaustion);
        }
    }

    private record InventorySnapshot(List<ItemStack> main, List<ItemStack> armor,
                                     List<ItemStack> offhand, int selectedSlot) {
        private static InventorySnapshot capture(ServerPlayer player) {
            var inventory = player.getInventory();
            return new InventorySnapshot(
                    inventory.items.stream().map(ItemStack::copy).toList(),
                    inventory.armor.stream().map(ItemStack::copy).toList(),
                    inventory.offhand.stream().map(ItemStack::copy).toList(),
                    inventory.selected);
        }

        private void restore(ServerPlayer player) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.items.size(); i++)
                inventory.items.set(i, i < main.size() ? main.get(i).copy() : ItemStack.EMPTY);
            for (int i = 0; i < inventory.armor.size(); i++)
                inventory.armor.set(i, i < armor.size() ? armor.get(i).copy() : ItemStack.EMPTY);
            for (int i = 0; i < inventory.offhand.size(); i++)
                inventory.offhand.set(i, i < offhand.size() ? offhand.get(i).copy() : ItemStack.EMPTY);
            inventory.selected = Math.max(0, Math.min(8, selectedSlot));
            player.inventoryMenu.broadcastChanges();
        }
    }
}
