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
import net.minecraft.world.level.block.RedstoneLampBlock;
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
                // Leave the pit completely open below the dance floor; no polished-blackstone base.
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
                if (x < centerX - 4 || x > centerX + 4) {
                    put(level, x, y, maxZ + WALKWAY_INNER_GAP, Blocks.PURPLE_STAINED_GLASS);
                }
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
        // Open-air arena: no roof. Dense invisible level-15 lights keep every tile vivid at night.
        for (int x = outerMinX + 2; x < outerMaxX; x += 4) {
            for (int z = outerMinZ + 2; z < outerMaxZ; z += 4) {
                put(level, x, floorY + 8, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
                put(level, x, floorY + 12, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
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
        // Redstone-lamp fixtures pulse to the selected DJ track's tempo during a match.
        for (int x = outerMinX + 6; x <= outerMaxX - 6; x += 12) {
            put(level, x, floorY + WALL_HEIGHT - 5, outerMinZ,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            put(level, x, floorY + WALL_HEIGHT - 5, outerMaxZ,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        }
        for (int z = outerMinZ + 6; z <= outerMaxZ - 6; z += 12) {
            put(level, outerMinX, floorY + WALL_HEIGHT - 5, z,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            put(level, outerMaxX, floorY + WALL_HEIGHT - 5, z,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
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
            for (int x = centerX - 6; x <= centerX + 6; x++) {
                // Continuous, solid landing beside the ramp closes the side gaps.
                if (x < centerX - 4 || x > centerX + 4) {
                    put(level, x, floorY, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                } else {
                    put(level, x, floorY, z, Blocks.PURPLE_CONCRETE);
                }
            }
        }
        // Keep the entrance visually open: clear the remaining central glass rail section.
        for (int y = walkwayY + 1; y <= walkwayY + 2; y++) {
            for (int x = centerX - 4; x <= centerX + 4; x++) {
                put(level, x, y, maxZ + WALKWAY_INNER_GAP, Blocks.AIR);
            }
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
        for (int x = lobbyMinX + 4; x < lobbyMaxX; x += 6) {
            for (int z = lobbyMinZ + 4; z < lobbyMaxZ; z += 6) {
                put(level, x, floorY + 11, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
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

        // Dedicated DJ screen on the back wall. Arena refreshes the text with the live playlist and vote counts.
        for (int x = centerX - 12; x <= centerX + 12; x++) {
            for (int y = floorY + 4; y <= floorY + 11; y++) put(level, x, y, lobbyMaxZ, Blocks.BLACK_CONCRETE);
            put(level, x, floorY + 3, lobbyMaxZ, Blocks.PURPLE_CONCRETE);
            put(level, x, floorY + 12, lobbyMaxZ, Blocks.MAGENTA_CONCRETE);
        }
        for (int z = lobbyMinZ + 6; z <= lobbyMaxZ - 6; z++) {
            for (int y = floorY + 3; y <= floorY + 12; y++) put(level, lobbyMaxX, y, z, Blocks.BLACK_CONCRETE);
        }

        // Music-reactive lamp bars flank the DJ screen.
        for (int x : new int[]{centerX - 16, centerX + 16}) {
            for (int y : new int[]{floorY + 5, floorY + 8, floorY + 11}) {
                put(level, x, y, lobbyMaxZ,
                        Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            }
        }
        // Raised DJ booth and speaker stacks; all elements remain inside the open-air lobby.
        int boothZ = lobbyMaxZ - 8;
        for (int x = centerX - 12; x <= centerX + 12; x++) {
            for (int z = boothZ - 2; z <= boothZ + 2; z++) put(level, x, floorY + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int x = centerX - 5; x <= centerX + 5; x++) {
            put(level, x, floorY + 2, boothZ - 1, Blocks.BLACK_CONCRETE);
            put(level, x, floorY + 3, boothZ - 1, Blocks.POLISHED_BLACKSTONE_SLAB);
        }
        put(level, centerX, floorY + 2, boothZ - 2, Blocks.NOTE_BLOCK);
        for (int side : new int[]{-1, 1}) {
            int speakerX = centerX + side * 10;
            for (int y = floorY + 2; y <= floorY + 6; y++) {
                put(level, speakerX, y, boothZ - 1, Blocks.BLACKSTONE);
            }
            put(level, speakerX, floorY + 3, boothZ - 2, Blocks.NOTE_BLOCK);
            put(level, speakerX, floorY + 5, boothZ - 2, Blocks.NOTE_BLOCK);
            put(level, speakerX, floorY + 4, boothZ - 2, Blocks.SEA_LANTERN);
        }
        // Compact neon dance floor between spawn and the DJ booth.
        Block[] danceColours = {Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE,
                Blocks.BLUE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE};
        for (int x = centerX - 8; x <= centerX + 8; x++) {
            for (int z = lobbyMinZ + 14; z <= lobbyMinZ + 28; z++) {
                int colour = Math.floorMod((x + z), danceColours.length);
                put(level, x, floorY, z, danceColours[colour]);
            }
        }
        // Lounge seating and low tables on both sides of the lobby, leaving the centre aisle clear.
        for (int side : new int[]{-1, 1}) {
            int seatX = centerX + side * 23;
            for (int z = lobbyMinZ + 13; z <= lobbyMinZ + 37; z += 8) {
                put(level, seatX, floorY + 1, z, Blocks.DARK_OAK_STAIRS);
                put(level, seatX + side, floorY + 1, z, Blocks.DARK_OAK_STAIRS);
                put(level, seatX - side, floorY + 1, z, Blocks.DARK_OAK_SLAB);
                put(level, seatX - side, floorY + 2, z, Blocks.DARK_OAK_SLAB);
            }
        }

        BlockPos first = new BlockPos(minX, floorY, minZ);
        BlockPos second = new BlockPos(maxX, floorY, maxZ);
        double lobbyX = centerX + 0.5;
        double lobbyY = floorY + 1.0;
        double lobbyZ = lobbyMinZ + 20.0;
        // Face south toward the lobby's logo wall.
        return new Result(first, second, displayCenter, lobbyX, lobbyY, lobbyZ, 0.0F, 0.0F);
    }

    /**
     * Non-destructively upgrades the arena lighting and lobby interior for arenas
     * generated by older BlockParty releases. The main floor and player builds are
     * not cleared; only the reserved lobby surfaces and stadium wall fixtures change.
     */
    public static boolean refreshVisuals(ServerLevel level, BlockPos first, BlockPos second,
                                         double lobbyX, double lobbyY, double lobbyZ) {
        if (level == null || first == null || second == null || first.getY() != second.getY()) return false;
        int floorY = first.getY();
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int centerX = minX + (maxX - minX + 1) / 2;
        int outerMinX = minX - OUTER_WALL_MARGIN;
        int outerMaxX = maxX + OUTER_WALL_MARGIN;
        int outerMinZ = minZ - OUTER_WALL_MARGIN;
        int outerMaxZ = maxZ + OUTER_WALL_MARGIN;
        int lobbyMinX = centerX - LOBBY_WIDTH / 2;
        int lobbyMaxX = centerX + LOBBY_WIDTH / 2;
        int lobbyMinZ = (int) Math.floor(lobbyZ) - 20;
        int lobbyMaxZ = lobbyMinZ + LOBBY_DEPTH - 1;
        BlockPos arenaAnchor = new BlockPos(centerX, floorY + 12, minZ + (maxZ - minZ + 1) / 2);
        BlockPos lobbyAnchor = new BlockPos(centerX, floorY + 8, lobbyMaxZ);
        if (!level.hasChunkAt(arenaAnchor) || !level.hasChunkAt(lobbyAnchor)) return false;

        // Dense invisible full-bright lighting above the dance floor; only air/light blocks are replaced.
        for (int x = outerMinX + 2; x < outerMaxX; x += 4) {
            for (int z = outerMinZ + 2; z < outerMaxZ; z += 4) {
                putLightIfOpen(level, x, floorY + 8, z);
                putLightIfOpen(level, x, floorY + 12, z);
            }
        }
        for (int x = lobbyMinX + 4; x < lobbyMaxX; x += 6) {
            for (int z = lobbyMinZ + 4; z < lobbyMaxZ; z += 6) {
                putLightIfOpen(level, x, floorY + 11, z);
            }
        }
        for (int x = lobbyMinX + 4; x <= lobbyMaxX - 4; x += 8) {
            put(level, x, floorY + 8, lobbyMinZ, Blocks.SEA_LANTERN);
            put(level, x, floorY + 8, lobbyMaxZ, Blocks.SEA_LANTERN);
        }
        for (int z = lobbyMinZ + 4; z <= lobbyMaxZ - 4; z += 8) {
            put(level, lobbyMinX, floorY + 8, z, Blocks.SEA_LANTERN);
            put(level, lobbyMaxX, floorY + 8, z, Blocks.SEA_LANTERN);
        }

        // Visible brighter arena wall fixtures and tempo-reactive redstone lamps.
        for (int x = outerMinX + 3; x <= outerMaxX - 3; x += 6) {
            put(level, x, floorY + WALL_HEIGHT - 2, outerMinZ, Blocks.SEA_LANTERN);
            put(level, x, floorY + WALL_HEIGHT - 2, outerMaxZ, Blocks.SEA_LANTERN);
        }
        for (int z = outerMinZ + 3; z <= outerMaxZ - 3; z += 6) {
            put(level, outerMinX, floorY + WALL_HEIGHT - 2, z, Blocks.SEA_LANTERN);
            put(level, outerMaxX, floorY + WALL_HEIGHT - 2, z, Blocks.SEA_LANTERN);
        }
        for (int x = outerMinX + 6; x <= outerMaxX - 6; x += 12) {
            put(level, x, floorY + WALL_HEIGHT - 5, outerMinZ,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            put(level, x, floorY + WALL_HEIGHT - 5, outerMaxZ,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        }
        for (int z = outerMinZ + 6; z <= outerMaxZ - 6; z += 12) {
            put(level, outerMinX, floorY + WALL_HEIGHT - 5, z,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            put(level, outerMaxX, floorY + WALL_HEIGHT - 5, z,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        }

        // Lobby DJ screen and decorative frame on the back wall.
        for (int x = centerX - 12; x <= centerX + 12; x++) {
            for (int y = floorY + 4; y <= floorY + 11; y++) put(level, x, y, lobbyMaxZ, Blocks.BLACK_CONCRETE);
            put(level, x, floorY + 3, lobbyMaxZ, Blocks.PURPLE_CONCRETE);
            put(level, x, floorY + 12, lobbyMaxZ, Blocks.MAGENTA_CONCRETE);
        }
        for (int side : new int[]{-1, 1}) {
            int lampX = centerX + side * 16;
            for (int y : new int[]{floorY + 5, floorY + 8, floorY + 11}) {
                put(level, lampX, y, lobbyMaxZ,
                        Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            }
        }

        // DJ booth, note-block speakers and a neon checker dance floor.
        int boothZ = lobbyMaxZ - 8;
        for (int x = centerX - 12; x <= centerX + 12; x++) {
            for (int z = boothZ - 2; z <= boothZ + 2; z++) put(level, x, floorY + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int x = centerX - 5; x <= centerX + 5; x++) {
            put(level, x, floorY + 2, boothZ - 1, Blocks.BLACK_CONCRETE);
            put(level, x, floorY + 3, boothZ - 1, Blocks.POLISHED_BLACKSTONE_SLAB);
        }
        put(level, centerX, floorY + 2, boothZ - 2, Blocks.NOTE_BLOCK);
        for (int side : new int[]{-1, 1}) {
            int speakerX = centerX + side * 10;
            for (int y = floorY + 2; y <= floorY + 6; y++) put(level, speakerX, y, boothZ - 1, Blocks.BLACKSTONE);
            put(level, speakerX, floorY + 3, boothZ - 2, Blocks.NOTE_BLOCK);
            put(level, speakerX, floorY + 5, boothZ - 2, Blocks.NOTE_BLOCK);
            put(level, speakerX, floorY + 4, boothZ - 2, Blocks.SEA_LANTERN);
        }
        Block[] danceColours = {Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE,
                Blocks.BLUE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE};
        for (int x = centerX - 8; x <= centerX + 8; x++) {
            for (int z = (int) Math.floor(lobbyZ) - 6; z <= (int) Math.floor(lobbyZ) + 8; z++) {
                // Keep the dance tiles as a single floating layer; remove the old grey/terrain backing.
                put(level, x, floorY - 1, z, Blocks.AIR);
                put(level, x, floorY, z, danceColours[Math.floorMod(x + z, danceColours.length)]);
            }
        }

        // Lounge seats and side tables. The centre aisle remains clear.
        for (int side : new int[]{-1, 1}) {
            int seatX = centerX + side * 23;
            for (int z = (int) Math.floor(lobbyZ) - 7; z <= (int) Math.floor(lobbyZ) + 17; z += 8) {
                put(level, seatX, floorY + 1, z, Blocks.DARK_OAK_STAIRS);
                put(level, seatX + side, floorY + 1, z, Blocks.DARK_OAK_STAIRS);
                put(level, seatX - side, floorY + 1, z, Blocks.DARK_OAK_SLAB);
                put(level, seatX - side, floorY + 2, z, Blocks.DARK_OAK_SLAB);
            }
        }
        return true;
    }


    /**
     * Removes the generated BlockParty footprint. This is intentionally destructive:
     * the generator does not retain a snapshot of terrain overwritten during generation.
     */
    public static void demolish(ServerLevel level, BlockPos first, BlockPos second,
                                double lobbyX, double lobbyZ) {
        if (level == null || first == null || second == null) {
            throw new IllegalArgumentException("Arenaen mangler posisjoner.");
        }
        int floorY = first.getY();
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int centerX = minX + (maxX - minX + 1) / 2;
        int outerMinX = Math.min(minX - OUTER_WALL_MARGIN, centerX - LOBBY_WIDTH / 2);
        int outerMaxX = Math.max(maxX + OUTER_WALL_MARGIN, centerX + LOBBY_WIDTH / 2);
        int outerMinZ = minZ - OUTER_WALL_MARGIN;
        int outerMaxZ = Math.max(maxZ + OUTER_WALL_MARGIN, (int)Math.floor(lobbyZ) - 20 + LOBBY_DEPTH - 1);
        int lowY = Math.max(level.getMinBuildHeight(), floorY - PIT_DEPTH - 2);
        int highY = Math.min(level.getMaxBuildHeight() - 1, floorY + WALL_HEIGHT + 2);
        clearVolume(level, outerMinX, outerMaxX, outerMinZ, outerMaxZ, lowY, highY);
        // Also clear entities created by BlockParty in the generated area.
        AABB bounds = new AABB(outerMinX, lowY, outerMinZ, outerMaxX + 1, highY + 1, outerMaxZ + 1);
        for (Display.TextDisplay display : level.getEntitiesOfClass(Display.TextDisplay.class, bounds,
                entity -> entity.getTags().stream().anyMatch(tag -> tag.startsWith("bp_") || tag.startsWith("blockparty_lb_")))) {
            display.discard();
        }
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, bounds,
                entity -> entity.getTags().stream().anyMatch(tag -> tag.startsWith("blockparty_lb_")))) {
            stand.discard();
        }
    }

    private static void putLightIfOpen(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) return;
        if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.LIGHT)) {
            put(level, x, y, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        }
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
