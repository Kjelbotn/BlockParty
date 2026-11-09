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
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import no.bangerfabrikken.blockparty.util.BlockPartyColors;
import no.bangerfabrikken.blockparty.util.BlockPartyText;
import no.bangerfabrikken.blockparty.sound.ModSounds;

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
    private static final int MAX_FLOOR_BLOCKS = 4096;
    private static final int LOBBY_COUNTDOWN_TICKS = 200;
    private static final int FALL_TICKS = 35;
    private static final int INTERMISSION_TICKS = 50;
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
    private final Set<UUID> pendingEliminations = new HashSet<>();
    private final Map<UUID, GameType> originalGameModes = new HashMap<>();
    private final Map<UUID, HeldItemSnapshot> originalHeldItems = new HashMap<>();
    private final Map<UUID, Set<Holder<MobEffect>>> roundPowerupEffects = new HashMap<>();
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
    private String currentPattern = "fargemosaikk";
    private boolean soloMode;

    private BlockPos activeBeaconPos;
    private boolean powerUpPlanned;
    private boolean powerUpSpawnedThisRound;
    private int powerUpSpawnAtRemainingTicks;
    private ResourceLocation activeMusicId;

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
        if (!players.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Du er allerede med i denne arenaen.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        teleportToLobby(player);
        player.sendSystemMessage(Component.literal("Du ble med i BlockParty-arenaen '" + id + "'. Spillere: " + players.size()).withStyle(ChatFormatting.GREEN));
        broadcast(Component.literal(player.getName().getString() + " ble med! (" + players.size() + ")").withStyle(ChatFormatting.YELLOW));
    }

    public void leave(ServerPlayer player, boolean teleport) {
        UUID uuid = player.getUUID();
        boolean wasMember = players.remove(uuid);
        alive.remove(uuid);
        pendingEliminations.remove(uuid);
        restorePlayerState(player);
        if (teleport && wasMember) teleportToLobby(player);

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
        alive.clear();
        alive.addAll(players);
        pendingEliminations.clear();
        originalGameModes.clear();
        originalHeldItems.clear();
        round = 0;
        previousTargetColor = null;
        previousPattern = -1;
        soloMode = players.size() == 1;
        powerUpPlanned = false;
        powerUpSpawnedThisRound = false;
        activeBeaconPos = null;
        activeMusicId = null;

        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                originalGameModes.put(uuid, player.gameMode.getGameModeForPlayer());
                int selectedSlot = player.getInventory().selected;
                originalHeldItems.put(uuid, new HeldItemSnapshot(selectedSlot, player.getInventory().getItem(selectedSlot).copy()));
                player.setGameMode(GameType.ADVENTURE);
                teleportToRandomFloorTile(player);
            }
        }

        broadcast(Component.literal("BLOCK PARTY STARTER! Finn fargen som vises i hånden din!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        beginRound(server);
    }

    private void updateColorDisplay() {
        if (displayCenter == null || targetColor == null || level == null) return;
        int width = 40;
        BlockPartyText.drawCentered(level, displayCenter, "FIND", Blocks.WHITE_CONCRETE, width, true);
        BlockPartyText.drawCentered(level, displayCenter.below(6), BlockPartyColors.displayName(targetColor),
                BlockPartyColors.getConcrete(targetColor), width, true);
    }

    public boolean handleBeaconInteraction(ServerPlayer player, BlockPos clickedPos) {
        if (activeBeaconPos == null || !activeBeaconPos.equals(clickedPos) || phase != Phase.PREPARE) return false;
        if (!players.contains(player.getUUID()) || !alive.contains(player.getUUID())) return false;

        removeActiveBeacon();
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
            // The effect is deliberately long in vanilla terms, then removed at this round's end.
            player.addEffect(new MobEffectInstance(effect, 20 * 60, amplifier, false, true, true));
            roundPowerupEffects.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(effect);
            player.sendSystemMessage(Component.literal("POWER-UP: " + label + " – varer ut denne runden!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            broadcast(Component.literal(player.getName().getString() + " plukket opp " + label + "!").withStyle(ChatFormatting.AQUA));
        }
        playSound(ModSounds.POWERUP_PICKUP, SoundSource.PLAYERS, 0.9F, 1.0F);
        return true;
    }

    private void spawnPowerUpBeacon() {
        powerUpSpawnedThisRound = true;
        if (level == null || floorSnapshot.isEmpty()) return;
        List<BlockPos> candidates = new ArrayList<>(floorSnapshot.keySet());
        Collections.shuffle(candidates, random);
        for (BlockPos candidate : candidates) {
            boolean occupied = false;
            for (UUID uuid : alive) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && player.blockPosition().getX() == candidate.getX()
                        && player.blockPosition().getZ() == candidate.getZ()) {
                    occupied = true;
                    break;
                }
            }
            if (occupied) continue;
            if (BlockPartyColors.getColor(level.getBlockState(candidate)) == null) continue;
            activeBeaconPos = candidate.immutable();
            level.setBlock(activeBeaconPos, Blocks.BEACON.defaultBlockState(), 3);
            broadcast(Component.literal("✦ EN POWER-UP HAR SPAWNET! Høyreklikk beaconen!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            playSound(ModSounds.BEACON_SPAWN, SoundSource.PLAYERS, 0.8F, 1.0F);
            return;
        }
    }

    private void removeActiveBeacon() {
        if (activeBeaconPos == null || level == null) return;
        BlockState original = floorSnapshot.get(activeBeaconPos);
        level.setBlock(activeBeaconPos, original == null ? Blocks.AIR.defaultBlockState() : original, 3);
        activeBeaconPos = null;
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

    private void playSoundRandomMusic() {
        stopMusic();
        int pick = random.nextInt(3);
        Holder<SoundEvent> sound;
        if (pick == 0) {
            sound = ModSounds.MUSIC_NEON;
            activeMusicId = ResourceLocation.fromNamespaceAndPath("blockparty", "music_neon");
        } else if (pick == 1) {
            sound = ModSounds.MUSIC_ARCADE;
            activeMusicId = ResourceLocation.fromNamespaceAndPath("blockparty", "music_arcade");
        } else {
            sound = ModSounds.MUSIC_RUSH;
            activeMusicId = ResourceLocation.fromNamespaceAndPath("blockparty", "music_rush");
        }
        playSound(sound, SoundSource.MUSIC, 0.48F, 1.0F);
    }

    private void stopMusic() {
        if (activeMusicId == null || level == null) return;
        for (UUID uuid : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) player.connection.send(new ClientboundStopSoundPacket(activeMusicId, SoundSource.MUSIC));
        }
        activeMusicId = null;
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
                if (color == null) {
                    originalFloorSnapshot.clear();
                    floorSnapshot.clear();
                    palette.clear();
                    throw new IllegalStateException("Gulvet må bestå av farget ull, betong eller terrakotta. Fant "
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
        switch (phase) {
            case WAITING -> {
                if (!players.isEmpty()) {
                    phase = Phase.COUNTDOWN;
                    timerTicks = LOBBY_COUNTDOWN_TICKS;
                    broadcast(Component.literal("BlockParty starter om 10 sekunder! Bruk /bp start " + id + " for å starte nå.").withStyle(ChatFormatting.GREEN));
                    playSound(ModSounds.ROUND_START, SoundSource.PLAYERS, 0.7F, 0.85F);
                }
            }
            case COUNTDOWN -> {
                if (players.isEmpty()) {
                    phase = Phase.WAITING;
                    timerTicks = 0;
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
                if (powerUpPlanned && !powerUpSpawnedThisRound && timerTicks <= powerUpSpawnAtRemainingTicks) {
                    spawnPowerUpBeacon();
                }
                if (timerTicks > 0 && (timerTicks % 10 == 0 || timerTicks < 10)) {
                    broadcastActionBar(Component.literal("FINN FARGEN: " + BlockPartyColors.displayName(targetColor)
                            + "  |  " + formatSeconds(timerTicks) + "s  |  Runde " + round + "/" + MAX_ROUNDS)
                            .withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
                }
                if (timerTicks <= 0) resolveRound();
            }
            case FALL -> {
                timerTicks--;
                for (UUID uuid : new HashSet<>(pendingEliminations)) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player == null) {
                        pendingEliminations.remove(uuid);
                        alive.remove(uuid);
                    } else if (player.getY() < pos1.getY() - 2.5) {
                        eliminate(player);
                    }
                }
                if (timerTicks <= 0) {
                    for (UUID uuid : new HashSet<>(pendingEliminations)) {
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
        restoreFloor();
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
        powerUpSpawnedThisRound = false;
        powerUpPlanned = timerTicks >= 60 && random.nextFloat() < 0.30F;
        powerUpSpawnAtRemainingTicks = powerUpPlanned
                ? 30 + random.nextInt(Math.max(1, timerTicks - 50)) : -1;
        broadcast(Component.literal("Runde " + round + "/" + MAX_ROUNDS + " – FINN " + BlockPartyColors.displayName(targetColor) + "!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        broadcast(Component.literal("Nytt gulvmønster: " + currentPattern + ". Se fargene skifte!")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        broadcastActionBar(Component.literal("FINN FARGEN: " + BlockPartyColors.displayName(targetColor)
                + "  |  " + formatSeconds(timerTicks) + "s").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        playSound(ModSounds.COLOR_REVEAL, SoundSource.PLAYERS, 0.8F, 1.0F);
        playSoundRandomMusic();
        playSound(ModSounds.ROUND_START, SoundSource.PLAYERS, 0.7F, 1.0F);
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
        currentPattern = switch (pattern) {
            case 1 -> "vertikale striper";
            case 2 -> "horisontale striper";
            case 3 -> "fargefelt";
            case 4 -> "diagonalt mosaikk";
            default -> "spredt mosaikk";
        };
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
            targetBlock.set(DataComponents.CUSTOM_NAME, Component.literal("FINN: " + BlockPartyColors.displayName(targetColor))
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
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
            DyeColor standingColor;
            if (activeBeaconPos != null && activeBeaconPos.equals(tile)) {
                BlockState underlying = floorSnapshot.get(activeBeaconPos);
                standingColor = underlying == null ? null : BlockPartyColors.getColor(underlying);
            } else {
                standingColor = BlockPartyColors.getColor(level.getBlockState(tile));
            }
            if (standingColor != targetColor) pendingEliminations.add(uuid);
        }

        stopMusic();
        playSound(ModSounds.ROUND_END, SoundSource.PLAYERS, 0.9F, 1.0F);
        removeActiveBeacon();
        clearActivePowerUps(level.getServer());

        // Remove every tile except the announced color. The arena generator leaves a fall pit below.
        for (Map.Entry<BlockPos, BlockState> entry : floorSnapshot.entrySet()) {
            DyeColor color = BlockPartyColors.getColor(entry.getValue());
            if (color != targetColor) level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), 3);
        }
        phase = Phase.FALL;
        timerTicks = FALL_TICKS;
        broadcast(Component.literal("Feil farge forsvinner! Hold deg på riktig blokk!").withStyle(ChatFormatting.RED));
    }

    private void eliminate(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!alive.remove(uuid)) {
            pendingEliminations.remove(uuid);
            return;
        }
        pendingEliminations.remove(uuid);
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.literal("Du er ute! Følg med som spectator.").withStyle(ChatFormatting.RED));
        playSound(ModSounds.ELIMINATION, SoundSource.PLAYERS, 0.8F, 0.95F);
    }

    private void finishRound() {
        clearActivePowerUps(level.getServer());
        removeActiveBeacon();
        stopMusic();
        restoreFloor();
        broadcast(Component.literal("Runde " + round + " ferdig! " + alive.size() + " spiller(e) igjen.").withStyle(ChatFormatting.AQUA));

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
    }

    private void declareWinner() {
        if (phase == Phase.FINISHING) return;
        stopMusic();
        playSound(ModSounds.VICTORY, SoundSource.PLAYERS, 1.0F, 1.0F);
        clearActivePowerUps(level.getServer());
        removeActiveBeacon();
        restoreOriginalFloor();
        if (alive.size() == 1) {
            UUID winnerId = alive.iterator().next();
            ServerPlayer winner = level.getServer().getPlayerList().getPlayer(winnerId);
            String winnerName = winner == null ? "En spiller" : winner.getName().getString();
            broadcast(Component.literal("🏆 " + winnerName + " VANT BLOCK PARTY! 🏆").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else if (alive.isEmpty()) {
            broadcast(Component.literal("BlockParty er ferdig – ingen overlevde denne gangen.").withStyle(ChatFormatting.GRAY));
        } else {
            broadcast(Component.literal("🏆 15 RUNDER FULLFØRT! Alle gjenværende spillere vinner! 🏆")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        phase = Phase.FINISHING;
        timerTicks = 100;
    }

    public void stop(MinecraftServer server) {
        if (phase == Phase.WAITING && players.isEmpty()) return;
        broadcast(Component.literal("BlockParty ble stoppet av en administrator.").withStyle(ChatFormatting.RED));
        endGame(server);
    }

    private void endGame(MinecraftServer server) {
        stopMusic();
        clearActivePowerUps(server);
        removeActiveBeacon();
        restoreOriginalFloor();
        for (UUID uuid : new HashSet<>(players)) {
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
        phase = Phase.WAITING;
        timerTicks = 0;
        round = 0;
        targetColor = null;
        previousTargetColor = null;
        previousPattern = -1;
        soloMode = false;
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

    private void restoreFloor() {
        if (level == null) return;
        for (Map.Entry<BlockPos, BlockState> entry : floorSnapshot.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private void restoreOriginalFloor() {
        if (level == null) return;
        for (Map.Entry<BlockPos, BlockState> entry : originalFloorSnapshot.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        floorSnapshot.clear();
        floorSnapshot.putAll(originalFloorSnapshot);
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
        return "Arena " + id + " | Status: " + phase.name() + " | Spillere: " + players.size() + " | Runde: " + round + "/" + MAX_ROUNDS;
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
