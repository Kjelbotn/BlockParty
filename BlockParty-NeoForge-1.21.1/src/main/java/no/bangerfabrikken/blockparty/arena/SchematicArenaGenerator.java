package no.bangerfabrikken.blockparty.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/** Imports the bundled Sponge v3 schematic used by the Xmas BlockParty map. */
public final class SchematicArenaGenerator {
    private static final String RESOURCE = "/assets/blockparty/schematics/xmas.schem.b64";
    private static final int FLOOR_SIZE = 39;
    private static final Set<Block> CONCRETE_BLOCKS = Set.of(
            Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.MAGENTA_CONCRETE,
            Blocks.LIGHT_BLUE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
            Blocks.PINK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE,
            Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.BLUE_CONCRETE,
            Blocks.BROWN_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.RED_CONCRETE,
            Blocks.BLACK_CONCRETE);

    private SchematicArenaGenerator() {}

    public record Result(BlockPos floorFirst, BlockPos floorSecond, BlockPos displayCenter,
                         BlockPos lobbyScreenCenter, float lobbyScreenYaw,
                         float displayYaw, double lobbyX, double lobbyY, double lobbyZ,
                         float lobbyYaw, float lobbyPitch) {}

    public static Result generate(ServerLevel level, BlockPos origin) throws IOException {
        Schematic schematic = load();
        if (origin.getY() + schematic.height >= level.getMaxBuildHeight()
                || origin.getY() <= level.getMinBuildHeight()) {
            throw new IllegalArgumentException("Det er ikke nok vertikal plass til Xmas-arenaen her.");
        }

        // Paste the full structure. Air blocks are skipped to avoid deleting nearby terrain.
        for (int y = 0; y < schematic.height; y++) {
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    int index = schematic.indices[x + z * schematic.width + y * schematic.width * schematic.length];
                    BlockState state = schematic.palette.get(index);
                    if (y >= 12 && state.getBlock() instanceof StairBlock) state = state.rotate(Rotation.CLOCKWISE_180);
                    if (!state.isAir()) level.setBlock(origin.offset(x, y, z), state, 3);
                }
            }
        }

        FloorRegion floor = findDanceFloor(schematic);
        // The dance floor must be a true fall-through platform when its concrete is
        // removed. Clear only snow directly beneath the 40x40 playable tiles; preserve
        // all natural terrain and non-snow supports below the map.
        for (int x = floor.minX; x < floor.minX + FLOOR_SIZE; x++) {
            for (int z = floor.minZ; z < floor.minZ + FLOOR_SIZE; z++) {
                BlockPos belowFloor = origin.offset(x, floor.y - 1, z);
                BlockState support = level.getBlockState(belowFloor);
                if (support.is(Blocks.SNOW) || support.is(Blocks.SNOW_BLOCK)) {
                    level.setBlock(belowFloor, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Flatten the snowy apron immediately outside the dance floor to the same
        // ground datum as the floor surface. Do not overwrite buildings or terrain:
        // only reposition existing snow blocks/layers into empty or snowy target cells.
        normalizeSnowApron(level, origin, schematic, floor);
        trimLobbySideBorder(level, origin, schematic, floor);
        placeDanceFloorLights(level, origin, floor);
        BlockPos first = origin.offset(floor.minX, floor.y, floor.minZ);
        BlockPos second = origin.offset(floor.minX + FLOOR_SIZE - 1, floor.y,
                floor.minZ + FLOOR_SIZE - 1);
        int centerX = floor.minX + FLOOR_SIZE / 2;
        // Dedicated Xmas target-screen anchor: vertical centre of the large white panel.
        // The display is placed one block toward the dance floor (+Z) and faces south,
        // so its front is visible to players standing on the arena.
        BlockPos display = origin.offset(centerX, floor.y + 16, floor.minZ - 9);
        // The black panel on the LEFT indoor wall is reserved for music voting.
        // Match the wall coordinates used by the old leaderboard panel, now removed.
        BlockPos lobbyScreen = origin.offset(10, 14, 95);
        placeHiddenLobbyLights(level, origin);
        BlockPos spawn = findLobbySpawn(level, origin);
        return new Result(first, second, display, lobbyScreen, -90.0F, 0.0F,
                spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                90.0F, 0.0F);
    }


    private static BlockPos findLobbySpawn(ServerLevel level, BlockPos origin) throws IOException {
        int preferredX = origin.getX() + 32, preferredZ = origin.getZ() + 95;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int y = origin.getY() + 1; y <= origin.getY() + 12; y++) {
            for (int z = origin.getZ() + 78; z <= origin.getZ() + 112; z++) {
                for (int x = origin.getX() + 12; x <= origin.getX() + 52; x++) {
                    BlockPos feet = new BlockPos(x, y, z), support = feet.below();
                    if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
                            || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
                    double score = Math.abs(x - preferredX) * 1.5 + Math.abs(z - preferredZ)
                            + Math.abs(y - (origin.getY() + 3)) * 2.0;
                    if (score < bestScore) { bestScore = score; best = feet; }
                }
            }
        }
        if (best == null) throw new IOException("Fant ingen trygg spawnplass inne i Xmas-lobbyen.");
        return best;
    }

    /** Removes the entire pasted Xmas map and all BlockParty displays with one command. */
    public static void demolish(ServerLevel level, BlockPos floorFirst) throws IOException {
        if (level == null || floorFirst == null) throw new IllegalArgumentException("Xmas-arenaen mangler posisjoner.");
        Schematic schematic = load();
        FloorRegion floor = findDanceFloor(schematic);
        BlockPos origin = floorFirst.offset(-floor.minX, -floor.y, -floor.minZ);
        for (int y = 0; y < schematic.height; y++) {
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    BlockPos pos = origin.offset(x, y, z);
                    int index = schematic.indices[x + z * schematic.width + y * schematic.width * schematic.length];
                    BlockState expected = schematic.palette.get(index);
                    BlockState current = level.getBlockState(pos);
                    boolean floorTile = y == floor.y && x >= floor.minX && x < floor.minX + FLOOR_SIZE
                            && z >= floor.minZ && z < floor.minZ + FLOOR_SIZE
                            && CONCRETE_BLOCKS.contains(current.getBlock());
                    if (floorTile || (!expected.isAir() && current.getBlock() == expected.getBlock())
                            || current.is(Blocks.LIGHT)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Remove the non-playable red/white lobby-edge decoration added at generation.
        int edgeZ = floor.minZ + FLOOR_SIZE;
        for (int x = floor.minX; x < floor.minX + FLOOR_SIZE; x++) {
            BlockPos edge = origin.offset(x, floor.y, edgeZ);
            BlockPos barrier = edge.above();
            BlockPos upperBarrier = edge.above(2);
            Block current = level.getBlockState(edge).getBlock();
            if (current == Blocks.RED_CONCRETE || current == Blocks.WHITE_CONCRETE) {
                level.setBlock(edge, Blocks.AIR.defaultBlockState(), 3);
            }
            if (level.getBlockState(barrier).is(Blocks.BARRIER)) {
                level.setBlock(barrier, Blocks.AIR.defaultBlockState(), 3);
            }
            if (level.getBlockState(upperBarrier).is(Blocks.BARRIER)) {
                level.setBlock(upperBarrier, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        AABB bounds = new AABB(origin.getX() - 2, origin.getY() - 2, origin.getZ() - 2,
                origin.getX() + schematic.width + 2, origin.getY() + schematic.height + 8,
                origin.getZ() + schematic.length + 2);
        for (Display.TextDisplay display : level.getEntitiesOfClass(Display.TextDisplay.class, bounds,
                entity -> entity.getTags().stream().anyMatch(tag -> tag.startsWith("bp_")
                        || tag.startsWith("blockparty_lb_")))) display.discard();
    }

    private record FloorRegion(int minX, int minZ, int y) {}

    /** Find the actual 40x40 concrete dance floor, not the indoor wooden lobby floor. */
    private static FloorRegion findDanceFloor(Schematic schematic) throws IOException {
        FloorRegion best = null;
        int bestConcrete = -1, bestColors = -1;
        for (int y = 0; y < schematic.height; y++) {
            for (int z = 0; z <= schematic.length - FLOOR_SIZE; z++) {
                for (int x = 0; x <= schematic.width - FLOOR_SIZE; x++) {
                    Set<Block> colors = new java.util.HashSet<>();
                    int concrete = 0;
                    for (int dz = 0; dz < FLOOR_SIZE; dz++) {
                        for (int dx = 0; dx < FLOOR_SIZE; dx++) {
                            int index = schematic.indices[x + dx + (z + dz) * schematic.width
                                    + y * schematic.width * schematic.length];
                            Block block = schematic.palette.get(index).getBlock();
                            if (CONCRETE_BLOCKS.contains(block)) { concrete++; colors.add(block); }
                        }
                    }
                    if (concrete > bestConcrete || (concrete == bestConcrete && colors.size() > bestColors)) {
                        bestConcrete = concrete; bestColors = colors.size(); best = new FloorRegion(x, z, y);
                    }
                }
            }
        }
        if (best == null || bestConcrete != FLOOR_SIZE * FLOOR_SIZE || bestColors < 4) {
            throw new IOException("Fant ikke et komplett 40x40 dansegulv av farget concrete i Xmas-schematicen ("
                    + bestConcrete + "/1600 blokker, " + bestColors + " farger).");
        }
        return best;
    }

    /**
     * Keep the snow immediately around the dance floor at the same ground level as
     * the playable surface. Only touch snow in a narrow apron around the floor;
     * the rest of the Xmas schematic and any non-snow terrain remain untouched.
     */
    private static void normalizeSnowApron(ServerLevel level, BlockPos origin,
                                           Schematic schematic, FloorRegion floor) {
        int minX = Math.max(0, floor.minX - 4);
        int maxX = Math.min(schematic.width - 1, floor.minX + FLOOR_SIZE + 3);
        int minZ = Math.max(0, floor.minZ - 4);
        int maxZ = Math.min(schematic.length - 1, floor.minZ + FLOOR_SIZE + 3);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean onPlayableFloor = x >= floor.minX && x < floor.minX + FLOOR_SIZE
                        && z >= floor.minZ && z < floor.minZ + FLOOR_SIZE;
                if (onPlayableFloor) continue;
                for (int y = Math.max(0, floor.y - 3); y < Math.min(schematic.height, floor.y + 3); y++) {
                    int index = schematic.indices[x + z * schematic.width + y * schematic.width * schematic.length];
                    Block block = schematic.palette.get(index).getBlock();
                    if (block != Blocks.SNOW && block != Blocks.SNOW_BLOCK) continue;
                    BlockPos oldPos = origin.offset(x, y, z);
                    BlockPos target = origin.offset(x, block == Blocks.SNOW_BLOCK ? floor.y - 1 : floor.y, z);
                    if (oldPos.equals(target)) continue;
                    BlockState targetState = level.getBlockState(target);
                    if (!targetState.isAir() && !targetState.is(Blocks.SNOW) && !targetState.is(Blocks.SNOW_BLOCK)) continue;
                    level.setBlock(oldPos, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(target, schematic.palette.get(index), 3);
                }
            }
        }
    }

    /**
     * The imported map has a two-block-wide trim on the lobby-facing (positive Z)
     * edge of the dance floor. Keep the inner row and remove only the outer row,
     * leaving the requested single-block border. The trim is detected from the
     * schematic itself so this does not depend on the arena's world coordinates.
     */
    private static void trimLobbySideBorder(ServerLevel level, BlockPos origin,
                                             Schematic schematic, FloorRegion floor) {
        int innerZ = floor.minZ + FLOOR_SIZE;
        int outerZ = innerZ + 1;
        int y = floor.y;
        int matchingColumns = 0;
        for (int x = floor.minX; x < floor.minX + FLOOR_SIZE; x++) {
            BlockState inner = schematic.palette.get(schematic.indices[
                    x + innerZ * schematic.width + y * schematic.width * schematic.length]);
            BlockState outer = schematic.palette.get(schematic.indices[
                    x + outerZ * schematic.width + y * schematic.width * schematic.length]);
            BlockPos innerPos = origin.offset(x, y, innerZ);
            BlockPos outerPos = origin.offset(x, y, outerZ);
            if (!inner.isAir() && !outer.isAir()
                    && inner.isFaceSturdy(level, innerPos, Direction.UP)
                    && outer.isFaceSturdy(level, outerPos, Direction.UP)) {
                matchingColumns++;
            }
        }
        // Avoid touching architectural walls or trim unless both rows form the
        // same continuous border material across almost the complete arena width.
        if (matchingColumns < FLOOR_SIZE * 3 / 4) return;
        for (int x = floor.minX; x < floor.minX + FLOOR_SIZE; x++) {
            BlockState inner = schematic.palette.get(schematic.indices[
                    x + innerZ * schematic.width + y * schematic.width * schematic.length]);
            BlockState outer = schematic.palette.get(schematic.indices[
                    x + outerZ * schematic.width + y * schematic.width * schematic.length]);
            BlockPos innerPos = origin.offset(x, y, innerZ);
            BlockPos outerPos = origin.offset(x, y, outerZ);
            if (!inner.isAir() && !outer.isAir()
                    && inner.isFaceSturdy(level, innerPos, Direction.UP)
                    && outer.isFaceSturdy(level, outerPos, Direction.UP)) {
                level.setBlock(outerPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Adds invisible light sources above the full dance floor and in front of its white target board. */
    private static void placeDanceFloorLights(ServerLevel level, BlockPos origin, FloorRegion floor) {
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        int maxX = floor.minX + FLOOR_SIZE - 1;
        int maxZ = floor.minZ + FLOOR_SIZE - 1;
        // Even light coverage over all 1600 playable concrete tiles.
        for (int x = floor.minX + 2; x <= maxX - 1; x += 4) {
            for (int z = floor.minZ + 2; z <= maxZ - 1; z += 4) {
                for (int yOffset : new int[]{5, 9}) {
                    BlockPos pos = origin.offset(x, floor.y + yOffset, z);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, light, 3);
                }
            }
        }
        // Extra front-facing light for the large white target board; distribute sources
        // across its width and height to eliminate the dark patches visible at night.
        int boardZ = floor.minZ - 7;
        for (int x = floor.minX - 1; x <= maxX + 1; x += 3) {
            for (int yOffset : new int[]{7, 11, 15, 19}) {
                BlockPos pos = origin.offset(x, floor.y + yOffset, boardZ);
                if (level.getBlockState(pos).isAir()) level.setBlock(pos, light, 3);
            }
        }
    }

    private static void placeHiddenLobbyLights(ServerLevel level, BlockPos origin) {
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        // Brighter, more even light coverage through the indoor lobby.
        // Hidden light blocks are placed only in air so the architecture is untouched.
        for (int yOffset : new int[]{14, 18, 22, 26}) {
            int step = yOffset >= 22 ? 7 : 6;
            for (int x = 4; x < 61; x += step) {
                for (int z = 2; z < 121; z += step) {
                    BlockPos pos = origin.offset(x, yOffset, z);
                    if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, light, 3);
                    }
                }
            }
        }
    }

    private static Schematic load() throws IOException {
        try (InputStream resource = SchematicArenaGenerator.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) throw new IOException("Mangler innebygd Xmas-schematic.");
            String encoded = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).trim();
            byte[] compressed = Base64.getDecoder().decode(encoded);
            CompoundTag root;
            // The resource is GZIP-compressed NBT. readCompressed owns the GZIP
            // stream; do not wrap it in GZIPInputStream first (that double-decompresses).
            try (InputStream compressedInput = new ByteArrayInputStream(compressed)) {
                root = NbtIo.readCompressed(compressedInput, NbtAccounter.unlimitedHeap());
            } catch (IOException | RuntimeException exception) {
                Throwable detail = exception;
                while (detail.getCause() != null && detail.getCause() != detail) detail = detail.getCause();
                String message = detail.getMessage() == null ? detail.getClass().getSimpleName() : detail.getMessage();
                throw new IOException("Kunne ikke lese Xmas-NBT (" + exception.getClass().getSimpleName()
                        + "): " + message, exception);
            }
            CompoundTag schematic = root.getCompound("Schematic");
            if (schematic.getInt("Version") != 3) throw new IOException("Xmas-schematic må være Sponge v3.");
            int width = schematic.getShort("Width");
            int height = schematic.getShort("Height");
            int length = schematic.getShort("Length");
            if (width != 64 || height != 30 || length != 122) {
                throw new IOException("Uventede dimensjoner i Xmas-schematicen.");
            }

            CompoundTag blocks = schematic.getCompound("Blocks");
            CompoundTag paletteTag = blocks.getCompound("Palette");
            int maxPalette = 0;
            for (String key : paletteTag.getAllKeys()) maxPalette = Math.max(maxPalette, paletteTag.getInt(key));
            List<BlockState> palette = new ArrayList<>(java.util.Collections.nCopies(maxPalette + 1, Blocks.AIR.defaultBlockState()));
            for (String key : paletteTag.getAllKeys()) palette.set(paletteTag.getInt(key), parseState(key));
            int[] indices = decodeVarInts(blocks.getByteArray("Data"), width * height * length);
            return new Schematic(width, height, length, palette, indices);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Ugyldig Xmas-schematic: " + exception.getMessage(), exception);
        }
    }

    private static BlockState parseState(String text) throws IOException {
        int bracket = text.indexOf('[');
        String id = bracket < 0 ? text : text.substring(0, bracket);
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        BlockState state = block.defaultBlockState();
        if (bracket < 0) return state;
        if (!text.endsWith("]")) throw new IOException("Ugyldig blockstate: " + text);
        String properties = text.substring(bracket + 1, text.length() - 1);
        if (properties.isBlank()) return state;
        for (String pair : properties.split(",")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) throw new IOException("Ugyldig block property: " + pair);
            Property<?> property = block.getStateDefinition().getProperty(parts[0]);
            if (property == null) throw new IOException("Ukjent property " + parts[0] + " på " + id);
            state = setProperty(state, property, parts[1]);
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setProperty(BlockState state, Property property, String value) throws IOException {
        Comparable parsed = (Comparable) property.getValue(value).orElse(null);
        if (parsed == null) throw new IOException("Ugyldig propertyverdi " + value);
        return state.setValue(property, parsed);
    }

    private static int[] decodeVarInts(byte[] data, int expected) throws IOException {
        int[] result = new int[expected];
        int offset = 0;
        for (int i = 0; i < expected; i++) {
            int value = 0, shift = 0;
            while (true) {
                if (offset >= data.length || shift > 28) throw new IOException("Ugyldig varint-data.");
                int b = data[offset++] & 0xff;
                value |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            result[i] = value;
        }
        if (offset != data.length) throw new IOException("Ekstra bytes i schematicens blockdata.");
        return result;
    }

    private record Schematic(int width, int height, int length, List<BlockState> palette, int[] indices) {}
}
