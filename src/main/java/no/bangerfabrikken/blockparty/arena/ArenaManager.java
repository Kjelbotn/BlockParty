package no.bangerfabrikken.blockparty.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import no.bangerfabrikken.blockparty.BlockPartyMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public final class ArenaManager {
    private static final ArenaManager INSTANCE = new ArenaManager();
    private static final String FILE_NAME = "blockparty-arenas.properties";

    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private int lobbyItemRefreshTicks;

    private ArenaManager() {
    }

    public static ArenaManager get() {
        return INSTANCE;
    }

    public Arena get(String id) {
        return arenas.get(normalize(id));
    }

    public Collection<Arena> getAll() {
        return new ArrayList<>(arenas.values());
    }

    public Arena create(String id, ServerPlayer creator) {
        String normalized = normalize(id);
        if (!normalized.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Arena-ID må inneholde 1–32 tegn: a-z, 0-9, _ eller -.");
        }
        if (arenas.containsKey(normalized)) {
            throw new IllegalArgumentException("En arena med dette navnet finnes allerede.");
        }
        Arena arena = new Arena(normalized, creator.serverLevel());
        arena.setLobby(creator);
        arenas.put(normalized, arena);
        return arena;
    }

    public boolean remove(String id) {
        Arena arena = get(id);
        if (arena == null) return false;
        if (arena.getPlayerCount() > 0 || arena.isRunning()) {
            throw new IllegalStateException("Arenaen har spillere eller kjører et spill. Stopp den og tøm den først.");
        }
        arenas.remove(normalize(id));
        return true;
    }

    public Arena findArena(UUID playerId) {
        for (Arena arena : arenas.values()) {
            if (arenaHasPlayer(arena, playerId)) return arena;
        }
        return null;
    }

    /** Find the closest open lobby near a player, even before they have joined the match. */
    public Arena findLobbyArenaFor(ServerPlayer player) {
        Arena closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Arena arena : arenas.values()) {
            if (!arena.isLobbyAccessibleTo(player)) continue;
            double dx = arena.getLobbyX() - player.getX();
            double dy = arena.getLobbyY() - player.getY();
            double dz = arena.getLobbyZ() - player.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                closest = arena;
                bestDistance = distance;
            }
        }
        return closest;
    }

    private boolean arenaHasPlayer(Arena arena, UUID playerId) {
        // The arena exposes its membership through the manager-safe helper below.
        return arena.containsPlayer(playerId);
    }

    public void tick(MinecraftServer server) {
        // Lobby visitors receive the Play star and Music Vote book automatically.
        // Refresh once per second to avoid scanning all player inventories every tick.
        if (++lobbyItemRefreshTicks >= 20) {
            lobbyItemRefreshTicks = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Arena lobbyArena = findLobbyArenaFor(player);
                if (lobbyArena != null) lobbyArena.ensureLobbyItems(player);
            }
        }
        for (Arena arena : arenas.values()) {
            arena.tick(server);
        }
    }

    public void handleLogout(ServerPlayer player) {
        Arena arena = findArena(player.getUUID());
        if (arena != null) arena.handleLogout(player);
    }

    public boolean handleBeaconInteraction(ServerPlayer player, BlockPos pos) {
        Arena arena = findArena(player.getUUID());
        return arena != null && arena.handleBeaconInteraction(player, pos);
    }

    public void load(MinecraftServer server) {
        arenas.clear();
        Path file = getFile(server);
        if (!Files.exists(file)) {
            BlockPartyMod.LOGGER.info("No saved BlockParty arenas found yet.");
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.error("Could not load BlockParty arena file {}", file, exception);
            return;
        }

        String ids = properties.getProperty("arena.ids", "");
        for (String rawId : ids.split(",")) {
            String id = normalize(rawId.trim());
            if (id.isBlank()) continue;
            try {
                String prefix = "arena." + id + ".";
                String dimensionText = properties.getProperty(prefix + "dimension");
                if (dimensionText == null) continue;
                ResourceLocation dimensionId = ResourceLocation.parse(dimensionText);
                ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
                ServerLevel level = server.getLevel(dimensionKey);
                if (level == null) {
                    BlockPartyMod.LOGGER.warn("Skipping BlockParty arena '{}': dimension {} is not available.", id, dimensionText);
                    continue;
                }

                Arena arena = new Arena(id, level);
                String first = properties.getProperty(prefix + "pos1", "");
                String second = properties.getProperty(prefix + "pos2", "");
                if (!first.isBlank() && !second.isBlank()) {
                    arena.setLoadedCorners(parseBlockPos(first), parseBlockPos(second));
                }
                String display = properties.getProperty(prefix + "display", "");
                if (!display.isBlank()) arena.setLoadedDisplayCenter(parseBlockPos(display));
                String lobby = properties.getProperty(prefix + "lobby", "");
                if (!lobby.isBlank()) {
                    String[] parts = lobby.split(",");
                    if (parts.length == 5) {
                        arena.setLobby(level,
                                Double.parseDouble(parts[0]),
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Float.parseFloat(parts[3]),
                                Float.parseFloat(parts[4]));
                    }
                }
                arenas.put(id, arena);
            } catch (Exception exception) {
                BlockPartyMod.LOGGER.warn("Skipping invalid saved BlockParty arena '{}'.", id, exception);
            }
        }
        BlockPartyMod.LOGGER.info("Loaded {} BlockParty arena(s).", arenas.size());
    }

    public void save(MinecraftServer server) {
        Properties properties = new Properties();
        StringBuilder ids = new StringBuilder();
        for (Arena arena : arenas.values()) {
            if (!arena.hasLobby()) continue;
            if (ids.length() > 0) ids.append(',');
            ids.append(arena.getId());
            String prefix = "arena." + arena.getId() + ".";
            properties.setProperty(prefix + "dimension", arena.getDimensionId());
            if (arena.getPos1() != null) properties.setProperty(prefix + "pos1", serialize(arena.getPos1()));
            if (arena.getPos2() != null) properties.setProperty(prefix + "pos2", serialize(arena.getPos2()));
            if (arena.getDisplayCenter() != null) properties.setProperty(prefix + "display", serialize(arena.getDisplayCenter()));
            properties.setProperty(prefix + "lobby", String.join(",",
                    Double.toString(arena.getLobbyX()),
                    Double.toString(arena.getLobbyY()),
                    Double.toString(arena.getLobbyZ()),
                    Float.toString(arena.getLobbyYaw()),
                    Float.toString(arena.getLobbyPitch())));
        }
        properties.setProperty("arena.ids", ids.toString());

        Path file = getFile(server);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "BlockParty arena configuration - generated automatically");
            }
        } catch (IOException exception) {
            BlockPartyMod.LOGGER.error("Could not save BlockParty arenas to {}", file, exception);
        }
    }

    public void shutdown(MinecraftServer server) {
        for (Arena arena : arenas.values()) {
            if (arena.isRunning()) arena.stop(server);
        }
    }

    private static Path getFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    private static String normalize(String id) {
        return id.toLowerCase(java.util.Locale.ROOT);
    }

    private static String serialize(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos parseBlockPos(String value) {
        String[] parts = value.split(",");
        if (parts.length != 3) throw new IllegalArgumentException("Expected x,y,z");
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
