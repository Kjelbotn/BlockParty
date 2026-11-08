package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import no.bangerfabrikken.blockparty.util.BlockPartyColors;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class Arena {
    public enum Phase {
        WAITING, COUNTDOWN, PREPARE, FALL, INTERMISSION, FINISHING
    }

    private static final int MAX_FLOOR_BLOCKS = 4096;
    private static final int LOBBY_COUNTDOWN_TICKS = 200;
    private static final int FALL_TICKS = 35;
    private static final int INTERMISSION_TICKS = 45;

    private final String id;
    private ServerLevel level;
    private BlockPos pos1;
    private BlockPos pos2;
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
    private final Map<BlockPos, BlockState> floorSnapshot = new HashMap<>();
    private final Set<DyeColor> palette = EnumSet.noneOf(DyeColor.class);
    private final Random random = new Random();

    private Phase phase = Phase.WAITING;
    private int timerTicks;
    private int round;
    private DyeColor targetColor;

    public Arena(String id, ServerLevel level) {
        this.id = id;
        this.level = level;
    }

    public String getId() {
        return id;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean containsPlayer(UUID playerId) {
        return players.contains(playerId);
    }

    public boolean isConfigured() {
        return level != null && pos1 != null && pos2 != null && lobbySet;
    }

    public boolean isRunning() {
        return phase == Phase.PREPARE || phase == Phase.FALL || phase == Phase.INTERMISSION || phase == Phase.FINISHING;
    }

    public void setCorner(int corner, BlockPos pos) {
        if (corner == 1) {
            pos1 = pos.immutable();
        } else {
            pos2 = pos.immutable();
        }
    }

    public void setLobby(ServerPlayer player) {
        this.level = player.serverLevel();
        this.lobbyX = player.getX();
        this.lobbyY = player.getY();
        this.lobbyZ = player.getZ();
        this.lobbyYaw = player.getYRot();
        this.lobbyPitch = player.getXRot();
        this.lobbySet = true;
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

    public boolean hasLobby() {
        return lobbySet;
    }

    public double getLobbyX() { return lobbyX; }
    public double getLobbyY() { return lobbyY; }
    public double getLobbyZ() { return lobbyZ; }
    public float getLobbyYaw() { return lobbyYaw; }
    public float getLobbyPitch() { return lobbyPitch; }

    public void join(ServerPlayer player) {
        if (!isConfigured()) {
            player.sendSystemMessage(Component.literal("Arenaen er ikke ferdig konfigurert. Sett pos1 og pos2 først.").withStyle(ChatFormatting.RED));
            return;
        }
        if (isRunning()) {
            player.sendSystemMessage(Component.literal("Denne runden har allerede startet. Vent til neste spill.").withStyle(ChatFormatting.RED));
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
        GameType oldMode = originalGameModes.remove(uuid);
        if (oldMode != null) {
            player.setGameMode(oldMode);
        }
        if (teleport && wasMember) {
            teleportToLobby(player);
        }
        if (wasMember) {
            player.sendSystemMessage(Component.literal("Du forlot BlockParty-arenaen '" + id + "'.").withStyle(ChatFormatting.GRAY));
            if (isRunning() && phase != Phase.FINISHING && alive.size() <= 1) {
                declareWinner();
            }
        }
    }

    public void handleLogout(ServerPlayer player) {
        leave(player, false);
    }

    public void start(MinecraftServer server) {
        if (isRunning()) {
            throw new IllegalStateException("Arenaen kjører allerede.");
        }
        if (players.isEmpty()) {
            throw new IllegalStateException("Ingen spillere er med i arenaen.");
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
        round = 0;
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                originalGameModes.put(uuid, player.gameMode.getGameModeForPlayer());
                player.setGameMode(GameType.ADVENTURE);
                teleportToRandomFloorTile(player);
            }
        }
        broadcast(Component.literal("BLOCK PARTY STARTER! Finn riktig farge!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        beginRound();
    }

    private void captureFloor() {
        if (!isConfigured()) {
            throw new IllegalStateException("Arenaen mangler pos1, pos2 eller lobby.");
        }
        if (pos1.getY() != pos2.getY()) {
            throw new IllegalStateException("Pos1 og pos2 må være på samme høyde (samme Y-koordinat).");
        }
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        long area = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (area > MAX_FLOOR_BLOCKS) {
            throw new IllegalStateException("Gulvet er for stort. Maks størrelse er " + MAX_FLOOR_BLOCKS + " blokker.");
        }

        floorSnapshot.clear();
        palette.clear();
        int floorY = pos1.getY();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, floorY, z);
                BlockState state = level.getBlockState(pos);
                DyeColor color = BlockPartyColors.getColor(state);
                if (color == null) {
                    floorSnapshot.clear();
                    palette.clear();
                    throw new IllegalStateException("Gulvet må bestå utelukkende av farget ull, betong eller terrakotta. Fant " + state.getBlock().getName().getString() + " ved " + pos.toShortString() + ".");
                }
                floorSnapshot.put(pos.immutable(), state);
                palette.add(color);
            }
        }
        if (palette.size() < 2) {
            throw new IllegalStateException("Gulvet må inneholde minst to forskjellige farger.");
        }
    }

    public void tick(MinecraftServer server) {
        switch (phase) {
            case WAITING -> {
                if (players.size() >= 2) {
                    phase = Phase.COUNTDOWN;
                    timerTicks = LOBBY_COUNTDOWN_TICKS;
                    broadcast(Component.literal("BlockParty starter om 10 sekunder!").withStyle(ChatFormatting.GREEN));
                }
            }
            case COUNTDOWN -> {
                if (players.size() < 2) {
                    phase = Phase.WAITING;
                    broadcast(Component.literal("Nedtelling avbrutt – minst 2 spillere kreves.").withStyle(ChatFormatting.RED));
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
                        broadcast(Component.literal("Kunne ikke starte BlockParty: " + exception.getMessage()).withStyle(ChatFormatting.RED));
                    }
                }
            }
            case PREPARE -> {
                timerTicks--;
                if (timerTicks % 20 == 0 || timerTicks < 10) {
                    broadcastActionBar(Component.literal("FINN FARGEN: " + BlockPartyColors.displayName(targetColor) + "  |  " + Math.max(0, (timerTicks + 19) / 20) + "s").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
                }
                if (timerTicks <= 0) {
                    resolveRound();
                }
            }
            case FALL -> {
                timerTicks--;
                for (UUID uuid : new HashSet<>(pendingEliminations)) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player == null) {
                        pendingEliminations.remove(uuid);
                        alive.remove(uuid);
                        continue;
                    }
                    if (player.getY() < pos1.getY() - 2.5 || timerTicks <= 0) {
                        eliminate(player);
                    }
                }
                if (timerTicks <= 0 || pendingEliminations.isEmpty()) {
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
                if (timerTicks <= 0) {
                    beginRound();
                }
            }
            case FINISHING -> {
                timerTicks--;
                if (timerTicks <= 0) {
                    endGame(server);
                }
            }
        }
    }

    private void beginRound() {
        restoreFloor();
        if (alive.isEmpty()) {
            declareWinner();
            return;
        }
        round++;
        List<DyeColor> colors = new ArrayList<>(palette);
        targetColor = colors.get(random.nextInt(colors.size()));
        int prepSeconds = Math.max(2, 5 - ((round - 1) / 3));
        timerTicks = prepSeconds * 20;
        phase = Phase.PREPARE;
        broadcast(Component.literal("Runde " + round + " – finn " + BlockPartyColors.displayName(targetColor) + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
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
            if (standingColor != targetColor) {
                pendingEliminations.add(uuid);
            }
        }

        for (Map.Entry<BlockPos, BlockState> entry : floorSnapshot.entrySet()) {
            DyeColor color = BlockPartyColors.getColor(entry.getValue());
            if (color != targetColor) {
                level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        phase = Phase.FALL;
        timerTicks = FALL_TICKS;
        broadcast(Component.literal("Feil farge! Gulvet forsvinner ...").withStyle(ChatFormatting.RED));
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
    }

    private void finishRound() {
        restoreFloor();
        broadcast(Component.literal("Runde " + round + " ferdig! " + alive.size() + " spiller(e) igjen.").withStyle(ChatFormatting.AQUA));
        if (alive.size() <= 1) {
            declareWinner();
        } else {
            phase = Phase.INTERMISSION;
            timerTicks = INTERMISSION_TICKS;
        }
    }

    private void declareWinner() {
        restoreFloor();
        if (alive.size() == 1) {
            UUID winnerId = alive.iterator().next();
            ServerPlayer winner = level.getServer().getPlayerList().getPlayer(winnerId);
            String winnerName = winner == null ? "En spiller" : winner.getName().getString();
            broadcast(Component.literal("🏆 " + winnerName + " VANT BLOCK PARTY! 🏆").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            broadcast(Component.literal("BlockParty er ferdig – ingen vinner denne gangen.").withStyle(ChatFormatting.GRAY));
        }
        phase = Phase.FINISHING;
        timerTicks = 100;
    }

    public void stop(MinecraftServer server) {
        if (phase == Phase.WAITING) {
            return;
        }
        broadcast(Component.literal("BlockParty ble stoppet av en administrator.").withStyle(ChatFormatting.RED));
        endGame(server);
    }

    private void endGame(MinecraftServer server) {
        restoreFloor();
        for (UUID uuid : new HashSet<>(players)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                GameType oldMode = originalGameModes.get(uuid);
                if (oldMode != null) player.setGameMode(oldMode);
                teleportToLobby(player);
                player.sendSystemMessage(Component.literal("Takk for at du spilte BlockParty!").withStyle(ChatFormatting.GREEN));
            }
        }
        players.clear();
        alive.clear();
        pendingEliminations.clear();
        originalGameModes.clear();
        phase = Phase.WAITING;
        timerTicks = 0;
        round = 0;
        targetColor = null;
    }

    private void restoreFloor() {
        if (level == null) return;
        for (Map.Entry<BlockPos, BlockState> entry : floorSnapshot.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private void teleportToLobby(ServerPlayer player) {
        if (level != null && lobbySet) {
            player.teleportTo(level, lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch);
        }
    }

    private void teleportToRandomFloorTile(ServerPlayer player) {
        List<BlockPos> positions = new ArrayList<>(floorSnapshot.keySet());
        BlockPos tile = positions.get(random.nextInt(positions.size()));
        player.teleportTo(level, tile.getX() + 0.5, tile.getY() + 1.05, tile.getZ() + 0.5, player.getYRot(), player.getXRot());
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
        return "Arena " + id + " | Status: " + phase.name() + " | Spillere: " + players.size() + " | Runde: " + round;
    }

    public void setLoadedCorners(BlockPos first, BlockPos second) {
        this.pos1 = first;
        this.pos2 = second;
    }

    public String getDimensionId() {
        return level.dimension().location().toString();
    }
}
