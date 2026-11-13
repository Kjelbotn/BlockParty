package no.bangerfabrikken.blockparty.arena;

import net.minecraft.core.BlockPos;
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
import java.util.zip.GZIPInputStream;

/** Imports the bundled Sponge v3 schematic used by the Xmas BlockParty map. */
public final class SchematicArenaGenerator {
    private static final String RESOURCE = "/assets/blockparty/schematics/xmas.schem.b64";
    private static final int FLOOR_MIN_X = 12, FLOOR_MAX_X = 51;
    private static final int FLOOR_MIN_Z = 12, FLOOR_MAX_Z = 51, FLOOR_Y = 2;

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

        BlockPos first = origin.offset(FLOOR_MIN_X, FLOOR_Y, FLOOR_MIN_Z);
        BlockPos second = origin.offset(FLOOR_MAX_X, FLOOR_Y, FLOOR_MAX_Z);

        // Screen panel is the tall white-backed display on the side of the arena.
        // TextDisplay yaw 90 faces west, toward the 40x40 dance floor.
        BlockPos display = origin.offset(10, 16, 95);
        BlockPos lobbyScreen = origin.offset(32, 15, 2);
        placeHiddenLobbyLights(level, origin);
        return new Result(first, second, display, lobbyScreen, 0.0F, 90.0F,
                origin.getX() + 32.5, origin.getY() + 3.0, origin.getZ() + 7.5,
                0.0F, 0.0F);
    }


    private static void placeHiddenLobbyLights(ServerLevel level, BlockPos origin) {
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (int yOffset : new int[]{18, 24}) {
            int step = yOffset == 18 ? 8 : 6;
            for (int x = 5; x < 60; x += step) {
                for (int z = 1; z < 122; z += step) {
                    BlockPos pos = origin.offset(x, yOffset, z);
                    if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir()) level.setBlock(pos, light, 3);
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
            try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                root = NbtIo.readCompressed(gzip, NbtAccounter.unlimitedHeap());
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
