package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import no.bangerfabrikken.blockparty.util.BlockPartyColors;
import no.bangerfabrikken.blockparty.util.BlockPartyText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Generates an open-air arena, elevated spectator promenade, and separate open lobby. */
public final class ArenaGenerator {
    public static final int MIN_SIZE = 24;
    public static final int MAX_SIZE = 50;
    public static final int DEFAULT_SIZE = 40;
    public static final int SPECTATOR_HEIGHT = 4;

    private static final int PIT_DEPTH = 14;
    private static final int WALKWAY_INNER_GAP = 3;
    private static final int WALKWAY_OUTER_MARGIN = 9;
    private static final int OUTER_WALL_MARGIN = 12;
    private static final int LOBBY_WIDTH = 64;
    private static final int LOBBY_DEPTH = 56;
    private static final int WALL_HEIGHT = 22;

    private ArenaGenerator() { }

    public record Result(BlockPos firstCorner, BlockPos secondCorner, BlockPos displayCenter,
                         double lobbyX, double lobbyY, double lobbyZ,
                         float lobbyYaw, float lobbyPitch) { }

    public static Result generate(ServerLevel level, BlockPos playerPosition, int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("Gulvstørrelsen må være mellom " + MIN_SIZE + " og " + MAX_SIZE + " blokker.");
        }

        int floorY = playerPosition.getY() - 1;
        if (floorY - PIT_DEPTH <= level.getMinBuildHeight() || floorY + WALL_HEIGHT + 4 >= level.getMaxBuildHeight()) {
            throw new IllegalArgumentException("Det er ikke nok vertikal plass her. Flytt deg noen blokker opp eller ned og prøv igjen.");
        }

        int centerX = playerPosition.getX();
        int centerZ = playerPosition.getZ();
        int minX = centerX - size / 2;
        int minZ = centerZ - size / 2;
        int maxX = minX + size - 1;
        int maxZ = minZ + size - 1;

        int outerMinX = minX - OUTER_WALL_MARGIN;
        int outerMaxX = maxX + OUTER_WALL_MARGIN;
        int outerMinZ = minZ - OUTER_WALL_MARGIN;
        int outerMaxZ = maxZ + OUTER_WALL_MARGIN;
        int lobbyMinZ = outerMaxZ + 2;
        int lobbyMaxZ = lobbyMinZ + LOBBY_DEPTH - 1;
        int lobbyMinX = centerX - LOBBY_WIDTH / 2;
        int lobbyMaxX = centerX + LOBBY_WIDTH / 2;
        int walkwayY = floorY + SPECTATOR_HEIGHT;

        // Remove previous BlockParty displays/leaderboard stands inside this footprint before rebuilding.
        AABB generatedBounds = new AABB(outerMinX - 2, floorY - 2, outerMinZ - 2,
                outerMaxX + 2, floorY + WALL_HEIGHT + 5, lobbyMaxZ + 2);
        for (Display.TextDisplay display : level.getEntitiesOfClass(Display.TextDisplay.class, generatedBounds,
                entity -> entity.getTags().stream().anyMatch(tag -> tag.startsWith("bp_") || tag.startsWith("blockparty_lb_")))) {
            display.discard();
        }
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, generatedBounds,
                entity -> entity.getTags().stream().anyMatch(tag -> tag.startsWith("blockparty_lb_")))) {
            stand.discard();
        }

        // Clear the union of stadium and lobby footprints, including the wider lobby when the floor is small.
        int clearMinX = Math.min(outerMinX, lobbyMinX);
        int clearMaxX = Math.max(outerMaxX, lobbyMaxX);
        clearVolume(level, clearMinX, clearMaxX, outerMinZ, lobbyMaxZ, floorY + 1, floorY + WALL_HEIGHT + 1);

        // Open fall pit and concrete dance floor.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Keep the full pit volume empty so eliminated players actually fall through.
                // Only build a foundation at the very bottom; solid blocks directly under the
                // concrete floor would stop players after a one-block drop.
                for (int y = floorY - PIT_DEPTH; y < floorY; y++) put(level, x, y, z, Blocks.AIR);
                put(level, x, floorY - PIT_DEPTH - 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        List<DyeColor> colours = BlockPartyColors.allColors();
        List<DyeColor> colourBag = new ArrayList<>(size * size);
        for (int i = 0; i < size * size; i++) colourBag.add(colours.get(i % colours.size()));
        Collections.shuffle(colourBag, new Random(level.getRandom().nextLong()));
        int index = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                put(level, x, floorY, z, BlockPartyColors.getConcrete(colourBag.get(index++)));
            }
        }

        // Spectator path: a continuous raised ring, four blocks above the floor, without seating/stands.
        for (int x = minX - WALKWAY_OUTER_MARGIN; x <= maxX + WALKWAY_OUTER_MARGIN; x++) {
            for (int z = minZ - WALKWAY_OUTER_MARGIN; z <= maxZ + WALKWAY_OUTER_MARGIN; z++) {
                boolean innerVoid = x >= minX - WALKWAY_INNER_GAP && x <= maxX + WALKWAY_INNER_GAP
                        && z >= minZ - WALKWAY_INNER_GAP && z <= maxZ + WALKWAY_INNER_GAP;
                if (innerVoid) continue;
                boolean trim = x == minX - WALKWAY_OUTER_MARGIN || x == maxX + WALKWAY_OUTER_MARGIN
                        || z == minZ - WALKWAY_OUTER_MARGIN || z == maxZ + WALKWAY_OUTER_MARGIN;
                put(level, x, walkwayY, z, trim ? Blocks.PURPLE_CONCRETE : Blocks.DEEPSLATE_TILES);
            }
        }
        // Supports beneath the spectator ring, deliberately outside the fall pit.
        for (int x = minX - WALKWAY_OUTER_MARGIN + 2; x <= maxX + WALKWAY_OUTER_MARGIN - 2; x += 8) {
            for (int z = minZ - WALKWAY_OUTER_MARGIN + 2; z <= maxZ + WALKWAY_OUTER_MARGIN - 2; z += 8) {
                boolean inPit = x >= minX - WALKWAY_INNER_GAP && x <= maxX + WALKWAY_INNER_GAP
                        && z >= minZ - WALKWAY_INNER_GAP && z <= maxZ + WALKWAY_INNER_GAP;
                if (inPit) continue;
                for (int y = floorY + 1; y < walkwayY; y++) put(level, x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        // Inner rail stops spectators from stepping onto the live floor; outer rail prevents falls.
        for (int y = walkwayY + 1; y <= walkwayY + 2; y++) {
            for (int x = minX - WALKWAY_INNER_GAP; x <= maxX + WALKWAY_INNER_GAP; x++) {
                put(level, x, y, minZ - WALKWAY_INNER_GAP, Blocks.PURPLE_STAINED_GLASS);
                put(level, x, y, maxZ + WALKWAY_INNER_GAP, Blocks.PURPLE_STAINED_GLASS);
            }
            for (int z = minZ - WALKWAY_INNER_GAP; z <= maxZ + WALKWAY_INNER_GAP; z++) {
                put(level, minX - WALKWAY_INNER_GAP, y, z, Blocks.PURPLE_STAINED_GLASS);
                put(level, maxX + WALKWAY_INNER_GAP, y, z, Blocks.PURPLE_STAINED_GLASS);
            }
            for (int x = minX - WALKWAY_OUTER_MARGIN; x <= maxX + WALKWAY_OUTER_MARGIN; x++) {
                put(level, x, y, minZ - WALKWAY_OUTER_MARGIN, Blocks.TINTED_GLASS);
                put(level, x, y, maxZ + WALKWAY_OUTER_MARGIN, Blocks.TINTED_GLASS);
            }
            for (int z = minZ - WALKWAY_OUTER_MARGIN; z <= maxZ + WALKWAY_OUTER_MARGIN; z++) {
                put(level, minX - WALKWAY_OUTER_MARGIN, y, z, Blocks.TINTED_GLASS);
                put(level, maxX + WALKWAY_OUTER_MARGIN, y, z, Blocks.TINTED_GLASS);
            }
        }

        // Open-air stadium shell. No roof and no spectator tiers: only the elevated ring around the floor.
        for (int x = outerMinX; x <= outerMaxX; x++) {
            for (int y = floorY + 1; y <= floorY + WALL_HEIGHT; y++) {
                put(level, x, y, outerMinZ, shellBlock(y));
                if (!isSouthEntrance(x, centerX) || y > floorY + 8) put(level, x, y, outerMaxZ, shellBlock(y));
            }
        }
        for (int z = outerMinZ; z <= outerMaxZ; z++) {
            for (int y = floorY + 1; y <= floorY + WALL_HEIGHT; y++) {
                put(level, outerMinX, y, z, shellBlock(y));
                put(level, outerMaxX, y, z, shellBlock(y));
            }
        }
        // Open-air arena: no roof. Invisible level-15 light blocks keep the floor and walkway bright at night.
        for (int x = outerMinX + 4; x < outerMaxX; x += 8) {
            for (int z = outerMinZ + 4; z < outerMaxZ; z += 8) {
                put(level, x, floorY + 10, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
            }
        }
        // Visible sea-lantern accents along the upper wall band.
        for (int x = outerMinX + 3; x <= outerMaxX - 3; x += 6) {
            put(level, x, floorY + WALL_HEIGHT - 2, outerMinZ, Blocks.SEA_LANTERN);
            put(level, x, floorY + WALL_HEIGHT - 2, outerMaxZ, Blocks.SEA_LANTERN);
        }
        for (int z = outerMinZ + 3; z <= outerMaxZ - 3; z += 6) {
            put(level, outerMinX, floorY + WALL_HEIGHT - 2, z, Blocks.SEA_LANTERN);
            put(level, outerMaxX, floorY + WALL_HEIGHT - 2, z, Blocks.SEA_LANTERN);
        }
        for (int x = outerMinX + 3; x < outerMaxX - 2; x += 8) {
            for (int z : new int[]{outerMinZ, outerMaxZ}) {
                for (int y = floorY + 4; y <= floorY + WALL_HEIGHT - 3; y += 6) put(level, x, y, z, Blocks.SEA_LANTERN);
            }
        }
        for (int z = outerMinZ + 3; z < outerMaxZ - 2; z += 8) {
            for (int x : new int[]{outerMinX, outerMaxX}) {
                for (int y = floorY + 4; y <= floorY + WALL_HEIGHT - 3; y += 6) put(level, x, y, z, Blocks.SEA_LANTERN);
            }
        }

        // Wide south entrance through the shell, connected to a four-step ramp onto the raised walkway.
        for (int x = centerX - 4; x <= centerX + 4; x++) {
            for (int y = floorY + 1; y <= floorY + 8; y++) put(level, x, y, outerMaxZ, Blocks.AIR);
        }
        for (int step = 1; step <= SPECTATOR_HEIGHT; step++) {
            int z = outerMaxZ + 1 - step;
            int y = floorY + step;
            for (int x = centerX - 4; x <= centerX + 4; x++) put(level, x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int z = outerMaxZ + 1; z <= lobbyMinZ; z++) {
            for (int x = centerX - 4; x <= centerX + 4; x++) put(level, x, floorY, z, Blocks.PURPLE_CONCRETE);
        }

        // A large dark colour screen on the north wall. Dynamic text is spawned by Arena using Text Display entities.
        int signWidth = Math.min(80, size + 12);
        int signLeft = centerX - signWidth / 2;
        int signRight = centerX + signWidth / 2;
        int signZ = outerMinZ + 1;
        for (int x = signLeft - 1; x <= signRight + 1; x++) {
            for (int y = floorY + 5; y <= floorY + 18; y++) {
                put(level, x, y, signZ, Blocks.BLACK_CONCRETE);
                put(level, x, y, signZ + 1, Blocks.BLACK_CONCRETE);
                put(level, x, y, signZ + 2, Blocks.BLACK_CONCRETE);
            }
            put(level, x, floorY + 4, signZ + 2, Blocks.MAGENTA_CONCRETE);
            put(level, x, floorY + 19, signZ + 2, Blocks.MAGENTA_CONCRETE);
        }
        BlockPos displayCenter = new BlockPos(centerX, floorY + 11, signZ + 2);

        // Separate open-air lobby with neon trim, logo and a large framed leaderboard wall.
        for (int x = lobbyMinX; x <= lobbyMaxX; x++) {
            for (int z = lobbyMinZ; z <= lobbyMaxZ; z++) {
                boolean border = x == lobbyMinX || x == lobbyMaxX || z == lobbyMinZ || z == lobbyMaxZ;
                put(level, x, floorY, z, border ? Blocks.MAGENTA_CONCRETE : Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int x = lobbyMinX; x <= lobbyMaxX; x++) {
            for (int y = floorY + 1; y <= floorY + 14; y++) {
                put(level, x, y, lobbyMinZ, Blocks.POLISHED_BLACKSTONE_BRICKS);
                put(level, x, y, lobbyMaxZ, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int z = lobbyMinZ; z <= lobbyMaxZ; z++) {
            for (int y = floorY + 1; y <= floorY + 14; y++) {
                put(level, lobbyMinX, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                put(level, lobbyMaxX, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        // Open-sky lobby. A grid of invisible level-15 light blocks keeps the hall readable even at night.
        for (int x = lobbyMinX + 5; x < lobbyMaxX; x += 8) {
            for (int z = lobbyMinZ + 5; z < lobbyMaxZ; z += 8) {
                put(level, x, floorY + 9, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
            }
        }
        // Visible wall lights make the light sources part of the architecture.
        for (int x = lobbyMinX + 4; x <= lobbyMaxX - 4; x += 8) {
            put(level, x, floorY + 8, lobbyMinZ, Blocks.SEA_LANTERN);
            put(level, x, floorY + 8, lobbyMaxZ, Blocks.SEA_LANTERN);
        }
        for (int z = lobbyMinZ + 4; z <= lobbyMaxZ - 4; z += 8) {
            put(level, lobbyMinX, floorY + 8, z, Blocks.SEA_LANTERN);
            put(level, lobbyMaxX, floorY + 8, z, Blocks.SEA_LANTERN);
        }

        // Entrance opening from lobby to the arena bridge.
        for (int x = centerX - 4; x <= centerX + 4; x++) {
            for (int y = floorY + 1; y <= floorY + 8; y++) put(level, x, y, lobbyMinZ, Blocks.AIR);
        }
        for (int x = lobbyMinX + 1; x < lobbyMaxX; x++) {
            put(level, x, floorY + 12, lobbyMinZ, Blocks.PURPLE_CONCRETE);
            put(level, x, floorY + 12, lobbyMaxZ, Blocks.PURPLE_CONCRETE);
        }
        for (int z = lobbyMinZ + 1; z < lobbyMaxZ; z++) {
            put(level, lobbyMinX, floorY + 12, z, Blocks.MAGENTA_CONCRETE);
            put(level, lobbyMaxX, floorY + 12, z, Blocks.MAGENTA_CONCRETE);
        }
        for (int x : new int[]{lobbyMinX + 4, lobbyMaxX - 4}) {
            for (int z : new int[]{lobbyMinZ + 4, lobbyMaxZ - 4}) {
                for (int y = floorY + 1; y <= floorY + 14; y++) {
                    put(level, x, y, z, y % 4 == 0 ? Blocks.SEA_LANTERN : Blocks.BLACKSTONE);
                }
            }
        }

        // Large full-wall leaderboard screen on the east side of the lobby.
        for (int z = lobbyMinZ + 4; z <= lobbyMaxZ - 4; z++) {
            for (int y = floorY + 2; y <= floorY + 14; y++) put(level, lobbyMaxX, y, z, Blocks.BLACK_CONCRETE);
            put(level, lobbyMaxX, floorY + 1, z, Blocks.PURPLE_CONCRETE);
            put(level, lobbyMaxX, floorY + 15, z, Blocks.MAGENTA_CONCRETE);
        }

        // Lobby screens: title and clear item-based instructions. These are labels only, not clickable screen buttons.
        for (int x = centerX - 12; x <= centerX + 12; x++) {
            for (int y = floorY + 4; y <= floorY + 11; y++) put(level, x, y, lobbyMaxZ, Blocks.BLACK_CONCRETE);
            put(level, x, floorY + 3, lobbyMaxZ, Blocks.PURPLE_CONCRETE);
            put(level, x, floorY + 12, lobbyMaxZ, Blocks.MAGENTA_CONCRETE);
        }
        for (int z = lobbyMinZ + 6; z <= lobbyMaxZ - 6; z++) {
            for (int y = floorY + 3; y <= floorY + 12; y++) put(level, lobbyMaxX, y, z, Blocks.BLACK_CONCRETE);
        }

        String lobbyTag = "bp_lobby_logo_" + centerX + "_" + centerZ;
        // The lobby screens sit on the south/back wall and face north toward the lobby spawn.
        BlockPartyText.spawnTextDisplay(level, centerX + 0.5, floorY + 8.2, lobbyMaxZ - 1.2,
                180.0F, Component.literal("BLOCKPARTY").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), 5.0F, lobbyTag);
        BlockPartyText.spawnTextDisplay(level, centerX + 0.5, floorY + 6.6, lobbyMaxZ - 1.2,
                180.0F, Component.literal("RIGHT-CLICK THE STAR IN YOUR HOTBAR TO JOIN")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), 1.25F, lobbyTag);
        BlockPartyText.spawnTextDisplay(level, centerX + 0.5, floorY + 5.35, lobbyMaxZ - 1.2,
                180.0F, Component.literal("RIGHT-CLICK THE BOOK TO VOTE FOR MUSIC")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), 1.15F, lobbyTag);
        BlockPartyText.spawnTextDisplay(level, centerX + 0.5, floorY + 4.25, lobbyMaxZ - 1.2,
                180.0F, Component.literal("2-10 PLAYERS  •  60 SECOND LOBBY COUNTDOWN")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), 1.0F, lobbyTag);

        BlockPos first = new BlockPos(minX, floorY, minZ);
        BlockPos second = new BlockPos(maxX, floorY, maxZ);
        double lobbyX = centerX + 0.5;
        double lobbyY = floorY + 1.0;
        double lobbyZ = lobbyMinZ + 20.0;
        // Face south toward the lobby's logo wall.
        return new Result(first, second, displayCenter, lobbyX, lobbyY, lobbyZ, 0.0F, 0.0F);
    }

    private static boolean isSouthEntrance(int x, int centerX) {
        return x >= centerX - 4 && x <= centerX + 4;
    }

    private static Block shellBlock(int y) {
        return switch (Math.floorMod(y, 7)) {
            case 0 -> Blocks.MAGENTA_CONCRETE;
            case 1, 5 -> Blocks.POLISHED_BLACKSTONE_BRICKS;
            case 2 -> Blocks.BLACK_CONCRETE;
            case 3 -> Blocks.PURPLE_STAINED_GLASS;
            case 4 -> Blocks.DEEPSLATE_TILES;
            default -> Blocks.BLACKSTONE;
        };
    }

    private static void clearVolume(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) put(level, x, y, z, Blocks.AIR);
            }
        }
    }

    private static void put(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
    }

    private static void put(ServerLevel level, int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }
}
