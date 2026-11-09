package no.bangerfabrikken.blockparty.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import no.bangerfabrikken.blockparty.util.BlockPartyColors;
import no.bangerfabrikken.blockparty.util.BlockPartyText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Builds a larger open-top stadium with a continuous spectator promenade and attached lobby. */
public final class ArenaGenerator {
    public static final int MIN_SIZE = 40;
    public static final int MAX_SIZE = 96;
    public static final int DEFAULT_SIZE = 80;
    private static final int PIT_DEPTH = 14;
    private static final int WALKWAY_WIDTH = 9;
    private static final int STAND_WIDTH = 12;
    private static final int OUTER_MARGIN = WALKWAY_WIDTH + STAND_WIDTH + 4;

    private ArenaGenerator() { }

    public record Result(BlockPos firstCorner, BlockPos secondCorner, BlockPos displayCenter,
                         double lobbyX, double lobbyY, double lobbyZ,
                         float lobbyYaw, float lobbyPitch) { }

    public static Result generate(ServerLevel level, BlockPos playerPosition, int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("Gulvstørrelsen må være mellom " + MIN_SIZE + " og " + MAX_SIZE + " blokker.");
        }

        int floorY = playerPosition.getY() - 1;
        if (floorY - PIT_DEPTH <= level.getMinBuildHeight() || floorY + 24 >= level.getMaxBuildHeight()) {
            throw new IllegalArgumentException("Det er ikke nok vertikal plass her. Flytt deg noen blokker opp eller ned og prøv igjen.");
        }

        int centerX = playerPosition.getX();
        int centerZ = playerPosition.getZ();
        int minX = centerX - size / 2;
        int minZ = centerZ - size / 2;
        int maxX = minX + size - 1;
        int maxZ = minZ + size - 1;

        int outerMinX = minX - OUTER_MARGIN;
        int outerMaxX = maxX + OUTER_MARGIN;
        int outerMinZ = minZ - OUTER_MARGIN;
        int outerMaxZ = maxZ + OUTER_MARGIN;
        int lobbyMinZ = outerMaxZ + 2;
        int lobbyMaxZ = outerMaxZ + 16;
        int lobbyMinX = centerX - 9;
        int lobbyMaxX = centerX + 9;

        // WARNING: only clear the stadium + lobby footprint, not the entire surrounding landscape.
        clearVolume(level, outerMinX, outerMaxX, outerMinZ, lobbyMaxZ + 2, floorY + 1, floorY + 22);

        // Leave a deep, open fall pit under the dance floor.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = floorY - PIT_DEPTH; y < floorY; y++) put(level, x, y, z, Blocks.AIR);
            }
        }

        // Solid spectator deck, continuous around all four sides of the dance floor.
        for (int x = outerMinX; x <= outerMaxX; x++) {
            for (int z = outerMinZ; z <= outerMaxZ; z++) {
                if (inside(x, z, minX, maxX, minZ, maxZ)) continue;
                foundation(level, x, floorY, z);
                boolean innerRim = x == minX - 1 || x == maxX + 1 || z == minZ - 1 || z == maxZ + 1;
                boolean outerTrim = x == outerMinX || x == outerMaxX || z == outerMinZ || z == outerMaxZ;
                Block deck = innerRim ? Blocks.MAGENTA_CONCRETE
                        : outerTrim ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.DEEPSLATE_TILES;
                put(level, x, floorY, z, deck);
            }
        }

        // Concrete dance floor: a balanced shuffled mix of all 16 colours.
        List<DyeColor> colors = BlockPartyColors.allColors();
        List<DyeColor> colorBag = new ArrayList<>(size * size);
        for (int i = 0; i < size * size; i++) colorBag.add(colors.get(i % colors.size()));
        Collections.shuffle(colorBag, new Random(level.getRandom().nextLong()));
        int colorIndex = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                put(level, x, floorY, z, BlockPartyColors.getConcrete(colorBag.get(colorIndex++)));
            }
        }

        // Transparent low rail separates the play floor from the spectator promenade.
        for (int x = minX - 1; x <= maxX + 1; x++) {
            put(level, x, floorY + 1, minZ - 1, Blocks.PURPLE_STAINED_GLASS_PANE);
            put(level, x, floorY + 1, maxZ + 1, Blocks.PURPLE_STAINED_GLASS_PANE);
        }
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            put(level, minX - 1, floorY + 1, z, Blocks.PURPLE_STAINED_GLASS_PANE);
            put(level, maxX + 1, floorY + 1, z, Blocks.PURPLE_STAINED_GLASS_PANE);
        }

        // Raised seating tiers live at the outside edge of the spectator route.
        buildSpectatorStands(level, minX, maxX, minZ, maxZ, floorY, centerX);

        // Tall outer shell: dark lower wall, neon belt, upper glass windows and roof rim.
        for (int x = outerMinX; x <= outerMaxX; x++) {
            for (int y = floorY + 1; y <= floorY + 19; y++) {
                // The far wall remains closed behind the target display; only the lobby-side wall has an entrance.
                buildWallBlock(level, x, y, outerMinZ);
                if (!isSouthEntrance(x, centerX) || y > floorY + 7) {
                    buildWallBlock(level, x, y, outerMaxZ);
                }
            }
        }
        for (int z = outerMinZ; z <= outerMaxZ; z++) {
            for (int y = floorY + 1; y <= floorY + 19; y++) {
                put(level, outerMinX, y, z, wallBlock(y));
                put(level, outerMaxX, y, z, wallBlock(y));
            }
        }
        for (int x = outerMinX; x <= outerMaxX; x++) {
            put(level, x, floorY + 20, outerMinZ, Blocks.POLISHED_BLACKSTONE_BRICKS);
            put(level, x, floorY + 20, outerMaxZ, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int z = outerMinZ; z <= outerMaxZ; z++) {
            put(level, outerMinX, floorY + 20, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            put(level, outerMaxX, floorY + 20, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }

        // Regular sea-lantern lighting around the perimeter and at the four corners.
        for (int x = outerMinX + 3; x < outerMaxX - 2; x += 5) {
            put(level, x, floorY + 5, outerMinZ, Blocks.SEA_LANTERN);
            if (!(x >= centerX - 4 && x <= centerX + 4)) put(level, x, floorY + 5, outerMaxZ, Blocks.SEA_LANTERN);
        }
        for (int z = outerMinZ + 3; z < outerMaxZ - 2; z += 5) {
            put(level, outerMinX, floorY + 5, z, Blocks.SEA_LANTERN);
            put(level, outerMaxX, floorY + 5, z, Blocks.SEA_LANTERN);
        }
        buildPylon(level, outerMinX, floorY, outerMinZ);
        buildPylon(level, outerMaxX, floorY, outerMinZ);
        buildPylon(level, outerMinX, floorY, outerMaxZ);
        buildPylon(level, outerMaxX, floorY, outerMaxZ);

        // Large block-letter colour display on the far wall, facing the arena (+Z).
        int signWidth = Math.max(44, Math.min(60, size - 4));
        int signLeft = centerX - signWidth / 2;
        int signRight = centerX + signWidth / 2;
        int signZ = outerMinZ + 1;
        for (int x = signLeft; x <= signRight; x++) {
            for (int y = floorY + 3; y <= floorY + 18; y++) {
                // Three-block deep panel: one text face for the arena and one for the outside.
                put(level, x, y, signZ, Blocks.BLACK_CONCRETE);
                put(level, x, y, signZ + 1, Blocks.BLACK_CONCRETE);
                put(level, x, y, signZ + 2, Blocks.BLACK_CONCRETE);
            }
        }
        for (int x = signLeft - 1; x <= signRight + 1; x++) {
            put(level, x, floorY + 2, signZ, Blocks.MAGENTA_CONCRETE);
            put(level, x, floorY + 19, signZ, Blocks.MAGENTA_CONCRETE);
            put(level, x, floorY + 2, signZ + 2, Blocks.MAGENTA_CONCRETE);
            put(level, x, floorY + 19, signZ + 2, Blocks.MAGENTA_CONCRETE);
        }
        BlockPos displayCenter = new BlockPos(centerX, floorY + 17, signZ + 2);
        // Paint both sides of the board. The inside face uses the orientation verified against
        // the in-game screenshot; the outside face is mirrored so both approaches read normally.
        BlockPartyText.drawCentered(level, displayCenter, "FIND", Blocks.WHITE_CONCRETE, signWidth, true);
        BlockPartyText.drawCentered(level, displayCenter.below(6), "READY", Blocks.LIME_CONCRETE, signWidth, true);
        BlockPartyText.drawCentered(level, displayCenter.offset(0, 0, -2), "FIND", Blocks.WHITE_CONCRETE, signWidth, false);
        BlockPartyText.drawCentered(level, displayCenter.offset(0, -6, -2), "READY", Blocks.LIME_CONCRETE, signWidth, false);

        // Stadium entrance arch, centered on the south wall, with a lit bridge to the lobby.
        for (int x : new int[]{centerX - 4, centerX + 4}) {
            for (int y = floorY + 1; y <= floorY + 7; y++) {
                put(level, x, y, outerMaxZ, y == floorY + 4 ? Blocks.SEA_LANTERN : Blocks.BLACK_CONCRETE);
            }
        }
        for (int x = centerX - 4; x <= centerX + 4; x++) put(level, x, floorY + 7, outerMaxZ, Blocks.MAGENTA_CONCRETE);
        for (int z = outerMaxZ + 1; z < lobbyMinZ; z++) {
            for (int x = centerX - 3; x <= centerX + 3; x++) {
                foundation(level, x, floorY, z);
                put(level, x, floorY, z, Blocks.PURPLE_CONCRETE);
            }
        }

        // Lobby pad with framing, lights, and a block-built BP logo.
        for (int x = lobbyMinX; x <= lobbyMaxX; x++) {
            for (int z = lobbyMinZ; z <= lobbyMaxZ; z++) {
                foundation(level, x, floorY, z);
                boolean border = x == lobbyMinX || x == lobbyMaxX || z == lobbyMinZ || z == lobbyMaxZ;
                put(level, x, floorY, z, border ? Blocks.MAGENTA_CONCRETE : Blocks.DEEPSLATE_TILES);
            }
        }
        for (int x : new int[]{lobbyMinX + 1, lobbyMaxX - 1}) {
            for (int z : new int[]{lobbyMinZ + 1, lobbyMaxZ - 1}) {
                for (int y = floorY + 1; y <= floorY + 7; y++) {
                    put(level, x, y, z, (y == floorY + 5 || y == floorY + 7) ? Blocks.SEA_LANTERN : Blocks.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        int lobbyBackZ = lobbyMaxZ;
        for (int x = centerX - 8; x <= centerX + 8; x++) {
            for (int y = floorY + 2; y <= floorY + 9; y++) put(level, x, y, lobbyBackZ, Blocks.BLACK_CONCRETE);
        }
        for (int x = centerX - 8; x <= centerX + 8; x++) {
            put(level, x, floorY + 2, lobbyBackZ, Blocks.PURPLE_CONCRETE);
            put(level, x, floorY + 9, lobbyBackZ, Blocks.MAGENTA_CONCRETE);
        }
        // Two readable faces: one for players inside the lobby and one for anyone viewing from behind.
        drawLogoLetters(level, centerX, floorY, lobbyBackZ - 1, true);
        drawLogoLetters(level, centerX, floorY, lobbyBackZ + 1, false);
        for (int x = centerX - 6; x <= centerX + 6; x++) {
            put(level, x, floorY, lobbyMaxZ - 2, Math.floorMod(x, 2) == 0 ? Blocks.CYAN_CONCRETE : Blocks.PURPLE_CONCRETE);
        }

        BlockPos first = new BlockPos(minX, floorY, minZ);
        BlockPos second = new BlockPos(maxX, floorY, maxZ);
        double lobbyX = centerX + 0.5;
        double lobbyY = floorY + 1.0;
        double lobbyZ = lobbyMinZ + 7.0;
        // Face the BP logo at the back wall of the lobby.
        return new Result(first, second, displayCenter, lobbyX, lobbyY, lobbyZ, 0.0F, 0.0F);
    }

    private static boolean isSouthEntrance(int x, int centerX) {
        return x >= centerX - 4 && x <= centerX + 4;
    }

    private static void buildSpectatorStands(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int floorY, int centerX) {
        // Ten raised rows sit outside a continuous seven-block promenade. Stair faces point toward the dance floor.
        for (int tier = 0; tier < STAND_WIDTH; tier++) {
            int y = floorY + 1 + tier / 2;
            int leftX = minX - WALKWAY_WIDTH - 1 - tier;
            int rightX = maxX + WALKWAY_WIDTH + 1 + tier;
            for (int z = minZ - 7; z <= maxZ + 7; z++) {
                BlockState leftSeat = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST);
                BlockState rightSeat = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST);
                putState(level, leftX, y, z, leftSeat);
                putState(level, rightX, y, z, rightSeat);
            }

            int northZ = minZ - WALKWAY_WIDTH - 1 - tier;
            int southZ = maxZ + WALKWAY_WIDTH + 1 + tier;
            for (int x = minX - 7; x <= maxX + 7; x++) {
                BlockState northSeat = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH);
                BlockState southSeat = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH);
                putState(level, x, y, northZ, northSeat);
                // Keep the south-side entrance lane clear for the bridge to the lobby.
                if (x < centerX - 5 || x > centerX + 5) putState(level, x, y, southZ, southSeat);
            }
        }

        // Neon aisle markers at regular intervals make the promenade readable from above.
        for (int z = minZ - WALKWAY_WIDTH; z <= maxZ + WALKWAY_WIDTH; z += 8) {
            put(level, minX - 2, floorY + 1, z, Blocks.SEA_LANTERN);
            put(level, maxX + 2, floorY + 1, z, Blocks.SEA_LANTERN);
        }
        for (int x = minX - WALKWAY_WIDTH; x <= maxX + WALKWAY_WIDTH; x += 8) {
            put(level, x, floorY + 1, minZ - 2, Blocks.SEA_LANTERN);
            if (x < centerX - 5 || x > centerX + 5) put(level, x, floorY + 1, maxZ + 2, Blocks.SEA_LANTERN);
        }
    }

    private static void foundation(ServerLevel level, int x, int floorY, int z) {
        for (int y = floorY - 3; y < floorY; y++) put(level, x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
    }

    private static Block wallBlock(int y) {
        return switch (Math.floorMod(y, 6)) {
            case 0 -> Blocks.MAGENTA_CONCRETE;
            case 1, 4 -> Blocks.POLISHED_BLACKSTONE_BRICKS;
            case 2 -> Blocks.BLACK_CONCRETE;
            case 3 -> Blocks.PURPLE_STAINED_GLASS;
            default -> Blocks.DEEPSLATE_TILES;
        };
    }

    private static void buildWallBlock(ServerLevel level, int x, int y, int z) {
        put(level, x, y, z, wallBlock(y));
    }

    private static void drawLogoLetters(ServerLevel level, int centerX, int floorY, int z, boolean reverseX) {
        String[] b = {"11110", "10001", "11110", "10001", "11110"};
        String[] p = {"11110", "10001", "11110", "10000", "10000"};
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                int y = floorY + 7 - row;
                int bX = reverseX ? centerX + 6 - col : centerX - 7 + col;
                int pX = reverseX ? centerX - 2 - col : centerX + 2 + col;
                if (b[row].charAt(col) == '1') put(level, bX, y, z, Blocks.LIME_CONCRETE);
                if (p[row].charAt(col) == '1') put(level, pX, y, z, Blocks.WHITE_CONCRETE);
            }
        }
    }

    private static void buildPylon(ServerLevel level, int x, int floorY, int z) {
        for (int y = floorY + 1; y <= floorY + 20; y++) {
            put(level, x, y, z, (y == floorY + 6 || y == floorY + 12 || y == floorY + 18)
                    ? Blocks.SEA_LANTERN : Blocks.BLACK_CONCRETE);
        }
    }

    private static void putState(ServerLevel level, int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }

    private static void clearVolume(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) put(level, x, y, z, Blocks.AIR);
            }
        }
    }

    private static boolean inside(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private static void put(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
    }
}
