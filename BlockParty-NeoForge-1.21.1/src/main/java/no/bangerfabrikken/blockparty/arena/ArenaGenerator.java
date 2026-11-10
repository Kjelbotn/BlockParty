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

        // Remove legacy/accidental support blocks beneath the spectator ring.
        // Keep the arena's outer shell intact; the walkway itself is intentionally cantilevered.
        for (int x = minX - WALKWAY_OUTER_MARGIN; x <= maxX + WALKWAY_OUTER_MARGIN; x++) {
            for (int z = minZ - WALKWAY_OUTER_MARGIN; z <= maxZ + WALKWAY_OUTER_MARGIN; z++) {
                boolean innerVoid = x >= minX - WALKWAY_INNER_GAP && x <= maxX + WALKWAY_INNER_GAP
                        && z >= minZ - WALKWAY_INNER_GAP && z <= maxZ + WALKWAY_INNER_GAP;
                boolean outerShell = x == outerMinX || x == outerMaxX || z == outerMinZ || z == outerMaxZ;
                if (!innerVoid && !outerShell) {
                    for (int y = floorY + 1; y < walkwayY; y++) put(level, x, y, z, Blocks.AIR);
                }
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
        // The spectator ring is intentionally cantilevered; do not generate
        // visible support pillars hanging down beneath the arena perimeter.
        // Keep the full outer tinted-glass guardrail around the spectator ring.
        // Only the small south entrance section is opened by buildSpectatorEntrance.
        for (int y = walkwayY + 1; y <= walkwayY + 2; y++) {
            for (int x = minX - WALKWAY_OUTER_MARGIN; x <= maxX + WALKWAY_OUTER_MARGIN; x++) {
                put(level, x, y, minZ - WALKWAY_OUTER_MARGIN, Blocks.TINTED_GLASS);
                put(level, x, y, maxZ + WALKWAY_OUTER_MARGIN, Blocks.TINTED_GLASS);
            }
            for (int z = minZ - WALKWAY_OUTER_MARGIN; z <= maxZ + WALKWAY_OUTER_MARGIN; z++) {
                put(level, minX - WALKWAY_OUTER_MARGIN, y, z, Blocks.TINTED_GLASS);
                put(level, maxX + WALKWAY_OUTER_MARGIN, y, z, Blocks.TINTED_GLASS);
            }
        }
        // Inner purple rail remains between spectators and the playing floor.
        for (int y = walkwayY + 1; y <= walkwayY + 2; y++) {
            for (int x = minX - WALKWAY_INNER_GAP; x <= maxX + WALKWAY_INNER_GAP; x++) {
                put(level, x, y, minZ - WALKWAY_INNER_GAP, Blocks.PURPLE_STAINED_GLASS);
                put(level, x, y, maxZ + WALKWAY_INNER_GAP, Blocks.PURPLE_STAINED_GLASS);
            }
            for (int z = minZ - WALKWAY_INNER_GAP; z <= maxZ + WALKWAY_INNER_GAP; z++) {
                put(level, minX - WALKWAY_INNER_GAP, y, z, Blocks.PURPLE_STAINED_GLASS);
                put(level, maxX + WALKWAY_INNER_GAP, y, z, Blocks.PURPLE_STAINED_GLASS);
            }
        }

        // Open-air stadium shell. No roof and no spectator tiers: only the elevated ring around the floor.
        for (int x = outerMinX; x <= outerMaxX; x++) {
            for (int y = floorY + 1; y <= floorY + WALL_HEIGHT; y++) {
                put(level, x, y, outerMinZ, shellBlock(y));
                put(level, x, y, outerMaxZ, shellBlock(y));
            }
        }
        for (int z = outerMinZ; z <= outerMaxZ; z++) {
            for (int y = floorY + 1; y <= floorY + WALL_HEIGHT; y++) {
                put(level, outerMinX, y, z, shellBlock(y));
                put(level, outerMaxX, y, z, shellBlock(y));
            }
        }
        // Back-wall feature treatment: symmetrical neon ribs, framed panels and always-lit lamps.
        for (int x = outerMinX + 4; x <= outerMaxX - 4; x += 8) {
            for (int y = floorY + 3; y <= floorY + WALL_HEIGHT - 3; y++) {
                put(level, x, y, outerMaxZ, (Math.floorMod(x / 8, 2) == 0)
                        ? Blocks.PURPLE_STAINED_GLASS : Blocks.MAGENTA_STAINED_GLASS);
            }
            put(level, x, floorY + 2, outerMaxZ, Blocks.CYAN_CONCRETE);
            put(level, x, floorY + WALL_HEIGHT - 2, outerMaxZ, Blocks.MAGENTA_CONCRETE);
        }
        for (int x = outerMinX + 6; x <= outerMaxX - 6; x += 12) {
            put(level, x, floorY + WALL_HEIGHT - 5, outerMaxZ,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            put(level, x, floorY + WALL_HEIGHT - 6, outerMaxZ - 1, Blocks.SEA_LANTERN);
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

        // Walkable entrance: four actual stairs rise northward to the spectator ring.

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
        }
        for (int z = lobbyMinZ + 4; z <= lobbyMaxZ - 4; z += 8) {
            put(level, lobbyMinX, floorY + 8, z, Blocks.SEA_LANTERN);
        }

        // Lobby and arena remain separate; match transitions use teleportation, not a bridge.
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
        for (int x = centerX - 19; x <= centerX + 19; x++) {
            for (int y = floorY + 4; y <= floorY + 11; y++) put(level, x, y, lobbyMaxZ, Blocks.BLACK_CONCRETE);
            put(level, x, floorY + 3, lobbyMaxZ, Blocks.PURPLE_CONCRETE);
            put(level, x, floorY + 12, lobbyMaxZ, Blocks.MAGENTA_CONCRETE);
        }
        for (int z = lobbyMinZ + 6; z <= lobbyMaxZ - 6; z++) {
            for (int y = floorY + 3; y <= floorY + 12; y++) put(level, lobbyMaxX, y, z, Blocks.BLACK_CONCRETE);
        }

        // Music-reactive lamp bars flank the DJ screen.
        for (int x : new int[]{centerX - 22, centerX + 22}) {
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

        buildNeonLobbyInterior(level, centerX, lobbyMinX, lobbyMaxX, lobbyMinZ, lobbyMaxZ, floorY);
        buildLobbyRoofAndLighting(level, lobbyMinX, lobbyMaxX, lobbyMinZ, lobbyMaxZ, floorY);

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
    /** Repairs the lobby-to-spectator connection, including arenas built by older versions. */
    /** Restore continuous tinted-glass and purple-glass rails around the full spectator ring. */

    /**
     * Adds the club/cafe interior pass. This is deliberately block-only and safe to
     * run both during generation and when upgrading an existing lobby in place.
     */
    private static void buildNeonLobbyInterior(ServerLevel level, int centerX,
                                                int minX, int maxX, int minZ, int maxZ, int floorY) {
        // Restrained club palette: blackstone, charcoal, magenta, purple and cyan.
        // Ceiling ribs run in parallel and never hang low over player movement.
        int ceilingY = floorY + 14;
        for (int x = minX + 4; x <= maxX - 4; x += 10) {
            for (int z = minZ + 3; z <= maxZ - 3; z++) {
                put(level, x, ceilingY, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
            for (int z = minZ + 6; z <= maxZ - 6; z += 12) {
                put(level, x, ceilingY - 1, z, Blocks.SEA_LANTERN);
                put(level, x, ceilingY - 2, z, Blocks.PURPLE_STAINED_GLASS);
            }
        }

        // Matching framed wall panels on both long sides, aligned at regular intervals.
        for (int z = minZ + 8; z <= maxZ - 8; z += 14) {
            for (int y = floorY + 3; y <= floorY + 8; y++) {
                put(level, minX, y, z, Blocks.BLACK_CONCRETE);
                put(level, minX, y, z + 1, Blocks.PURPLE_STAINED_GLASS);
                put(level, maxX, y, z, Blocks.BLACK_CONCRETE);
                put(level, maxX, y, z + 1, Blocks.MAGENTA_STAINED_GLASS);
            }
            put(level, minX + 1, floorY + 2, z, Blocks.CYAN_CONCRETE);
            put(level, maxX - 1, floorY + 2, z, Blocks.CYAN_CONCRETE);
        }

        // Clear the footprint of the previous experimental cafe and lounge builds first.
        // This removes orphaned rods, floating glass and misplaced shelves from existing lobbies.
        int oldCafeX = centerX - 24;
        for (int x = oldCafeX - 7; x <= oldCafeX + 7; x++) {
            for (int z = minZ + 5; z <= minZ + 13; z++) {
                for (int y = floorY + 1; y <= floorY + 7; y++) put(level, x, y, z, Blocks.AIR);
            }
        }
        for (int side : new int[]{-1, 1}) {
            int oldLoungeX = centerX + side * 24;
            for (int x = oldLoungeX - 6; x <= oldLoungeX + 6; x++) {
                for (int z = minZ + 7; z <= maxZ - 10; z += 13) {
                    for (int zz = z - 5; zz <= z + 5; zz++) {
                        for (int y = floorY + 1; y <= floorY + 9; y++) {
                            BlockPos legacy = new BlockPos(x, y, zz);
                            Block block = level.getBlockState(legacy).getBlock();
                            if (block == Blocks.SEA_LANTERN || block == Blocks.END_ROD
                                    || block == Blocks.PURPLE_STAINED_GLASS || block == Blocks.CYAN_STAINED_GLASS
                                    || block == Blocks.MAGENTA_STAINED_GLASS || block == Blocks.BOOKSHELF) {
                                put(level, x, y, zz, Blocks.AIR);
                            }
                        }
                    }
                }
            }
        }

        // Compact cafe counter at the west/rear side, with a clear approach and no floating decor.
        int cafeX = centerX - 24;
        int cafeZ = minZ + 8;
        for (int x = cafeX - 4; x <= cafeX + 4; x++) {
            put(level, x, floorY + 1, cafeZ, Blocks.SMOOTH_QUARTZ);
            put(level, x, floorY + 2, cafeZ, Blocks.QUARTZ_SLAB);
            put(level, x, floorY + 3, cafeZ - 1, Blocks.BOOKSHELF);
        }
        for (int x : new int[]{cafeX - 3, cafeX, cafeX + 3}) {
            put(level, x, floorY + 4, cafeZ - 2, Blocks.SEA_LANTERN);
            put(level, x, floorY + 5, cafeZ - 2, Blocks.CYAN_STAINED_GLASS);
        }

        // Two mirrored lounge bays: benches face inward toward low tables.
        for (int side : new int[]{-1, 1}) {
            int loungeX = centerX + side * 24;
            for (int z = minZ + 17; z <= maxZ - 13; z += 14) {
                for (int dx = -2; dx <= 2; dx++) {
                    put(level, loungeX + dx, floorY + 1, z, Blocks.DARK_OAK_STAIRS);
                }
                put(level, loungeX, floorY + 1, z + 3, Blocks.POLISHED_BLACKSTONE_SLAB);
                put(level, loungeX, floorY + 2, z + 3, Blocks.SEA_LANTERN);
            }
        }

        // Thin neon borders define the dance area; no decorative blocks intrude onto its surface.
        for (int x = centerX - 10; x <= centerX + 10; x++) {
            put(level, x, floorY, minZ + 13, Blocks.PURPLE_CONCRETE);
            put(level, x, floorY, minZ + 29, Blocks.MAGENTA_CONCRETE);
        }
        for (int z = minZ + 13; z <= minZ + 29; z++) {
            put(level, centerX - 10, floorY, z, Blocks.CYAN_CONCRETE);
            put(level, centerX + 10, floorY, z, Blocks.CYAN_CONCRETE);
        }

        // Speaker towers sit outside the dancefloor corners and use matching mirrored shapes.
        for (int side : new int[]{-1, 1}) {
            int x = centerX + side * 13;
            int z = minZ + 20;
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                put(level, x, y, z, Blocks.BLACKSTONE);
            }
            for (int y : new int[]{floorY + 2, floorY + 4}) {
                put(level, x, y, z - 1, Blocks.SEA_LANTERN);
                put(level, x, y + 1, z - 1, Blocks.PURPLE_STAINED_GLASS);
            }
        }
    }

    private static void restoreSpectatorRails(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int floorY) {
        int walkwayY = floorY + SPECTATOR_HEIGHT;
        for (int y = walkwayY + 1; y <= walkwayY + 2; y++) {
            for (int x = minX - WALKWAY_OUTER_MARGIN; x <= maxX + WALKWAY_OUTER_MARGIN; x++) {
                put(level, x, y, minZ - WALKWAY_OUTER_MARGIN, Blocks.TINTED_GLASS);
                put(level, x, y, maxZ + WALKWAY_OUTER_MARGIN, Blocks.TINTED_GLASS);
            }
            for (int z = minZ - WALKWAY_OUTER_MARGIN; z <= maxZ + WALKWAY_OUTER_MARGIN; z++) {
                put(level, minX - WALKWAY_OUTER_MARGIN, y, z, Blocks.TINTED_GLASS);
                put(level, maxX + WALKWAY_OUTER_MARGIN, y, z, Blocks.TINTED_GLASS);
            }
        }
        for (int y = walkwayY + 1; y <= walkwayY + 2; y++) {
            for (int x = minX - WALKWAY_INNER_GAP; x <= maxX + WALKWAY_INNER_GAP; x++) {
                put(level, x, y, minZ - WALKWAY_INNER_GAP, Blocks.PURPLE_STAINED_GLASS);
                put(level, x, y, maxZ + WALKWAY_INNER_GAP, Blocks.PURPLE_STAINED_GLASS);
            }
            for (int z = minZ - WALKWAY_INNER_GAP; z <= maxZ + WALKWAY_INNER_GAP; z++) {
                put(level, minX - WALKWAY_INNER_GAP, y, z, Blocks.PURPLE_STAINED_GLASS);
                put(level, maxX + WALKWAY_INNER_GAP, y, z, Blocks.PURPLE_STAINED_GLASS);
            }
        }
    }

    /** Remove old stair blocks left by earlier versions without touching the spectator floor. */
    private static void removeLegacyEntranceStairs(ServerLevel level, int centerX, int floorY, int maxZ) {
        for (int x = centerX - 6; x <= centerX + 6; x++) {
            for (int z = maxZ + 1; z <= maxZ + 5; z++) {
                for (int y = floorY; y <= floorY + SPECTATOR_HEIGHT + 2; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.StairBlock)
                        put(level, x, y, z, Blocks.AIR);
                }
            }
        }
    }

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
        int walkwayY = floorY + SPECTATOR_HEIGHT;
        BlockPos arenaAnchor = new BlockPos(centerX, floorY + 12, minZ + (maxZ - minZ + 1) / 2);
        BlockPos lobbyAnchor = new BlockPos(centerX, floorY + 8, lobbyMaxZ);
        if (!level.hasChunkAt(arenaAnchor) || !level.hasChunkAt(lobbyAnchor)) return false;
        // Clean up legacy blocks hanging below the spectator ring on existing arenas.
        for (int x = minX - WALKWAY_OUTER_MARGIN; x <= maxX + WALKWAY_OUTER_MARGIN; x++) {
            for (int z = minZ - WALKWAY_OUTER_MARGIN; z <= maxZ + WALKWAY_OUTER_MARGIN; z++) {
                boolean innerVoid = x >= minX - WALKWAY_INNER_GAP && x <= maxX + WALKWAY_INNER_GAP
                        && z >= minZ - WALKWAY_INNER_GAP && z <= maxZ + WALKWAY_INNER_GAP;
                boolean outerShell = x == outerMinX || x == outerMaxX || z == outerMinZ || z == outerMaxZ;
                if (!innerVoid && !outerShell) {
                    for (int y = floorY + 1; y < walkwayY; y++) {
                        BlockPos under = new BlockPos(x, y, z);
                        if (level.hasChunkAt(under)) put(level, x, y, z, Blocks.AIR);
                    }
                }
            }
        }

        // Restore the outer tinted-glass guardrail around the full ring for arenas
        // touched by the previous buggy update. The entrance helper then reopens
        // only the centered doorway at the south side.
        int walkwayY = floorY + SPECTATOR_HEIGHT;
        for (int y = walkwayY + 1; y <= walkwayY + 2; y++) {
            for (int x = minX - WALKWAY_OUTER_MARGIN; x <= maxX + WALKWAY_OUTER_MARGIN; x++) {
                put(level, x, y, minZ - WALKWAY_OUTER_MARGIN, Blocks.TINTED_GLASS);
                put(level, x, y, maxZ + WALKWAY_OUTER_MARGIN, Blocks.TINTED_GLASS);
            }
            for (int z = minZ - WALKWAY_OUTER_MARGIN; z <= maxZ + WALKWAY_OUTER_MARGIN; z++) {
                put(level, minX - WALKWAY_OUTER_MARGIN, y, z, Blocks.TINTED_GLASS);
                put(level, maxX + WALKWAY_OUTER_MARGIN, y, z, Blocks.TINTED_GLASS);
            }
        }
        // Upgrade existing arenas in place; no need to regenerate the dance floor.

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
        }
        for (int z = lobbyMinZ + 4; z <= lobbyMaxZ - 4; z += 8) {
            put(level, lobbyMinX, floorY + 8, z, Blocks.SEA_LANTERN);
        }

        // Back-wall decoration on the far side of the arena, restored for existing worlds.
        for (int x = outerMinX + 4; x <= outerMaxX - 4; x += 8) {
            for (int y = floorY + 3; y <= floorY + WALL_HEIGHT - 3; y++) {
                put(level, x, y, outerMaxZ, (Math.floorMod(x / 8, 2) == 0)
                        ? Blocks.PURPLE_STAINED_GLASS : Blocks.MAGENTA_STAINED_GLASS);
            }
            put(level, x, floorY + 2, outerMaxZ, Blocks.CYAN_CONCRETE);
            put(level, x, floorY + WALL_HEIGHT - 2, outerMaxZ, Blocks.MAGENTA_CONCRETE);
        }
        for (int x = outerMinX + 6; x <= outerMaxX - 6; x += 12) {
            put(level, x, floorY + WALL_HEIGHT - 5, outerMaxZ,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            put(level, x, floorY + WALL_HEIGHT - 6, outerMaxZ - 1, Blocks.SEA_LANTERN);
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

        for (int x = centerX - 19; x <= centerX + 19; x++) {
            for (int y = floorY + 4; y <= floorY + 11; y++) {
                BlockPos screenBlock = new BlockPos(x, y, lobbyMaxZ);
                if (level.getBlockState(screenBlock).is(Blocks.SEA_LANTERN)) {
                    put(level, x, y, lobbyMaxZ, Blocks.BLACK_CONCRETE);
                }
            }
        }

        // Lobby DJ screen and decorative frame on the back wall.
        for (int x = centerX - 19; x <= centerX + 19; x++) {
            for (int y = floorY + 4; y <= floorY + 11; y++) put(level, x, y, lobbyMaxZ, Blocks.BLACK_CONCRETE);
            put(level, x, floorY + 3, lobbyMaxZ, Blocks.PURPLE_CONCRETE);
            put(level, x, floorY + 12, lobbyMaxZ, Blocks.MAGENTA_CONCRETE);
        }
        for (int side : new int[]{-1, 1}) {
            int lampX = centerX + side * 30;
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
        buildNeonLobbyInterior(level, centerX, lobbyMinX, lobbyMaxX, lobbyMinZ, lobbyMaxZ, floorY);
        buildLobbyRoofAndLighting(level, lobbyMinX, lobbyMaxX, lobbyMinZ, lobbyMaxZ, floorY);
        restoreSpectatorRails(level, minX, maxX, minZ, maxZ, floorY);
        removeLegacyEntranceStairs(level, (minX + maxX) / 2, floorY, maxZ);

        // Close the old arena-to-lobby bridge opening and any missing centre wall sections.
        // The lobby and arena are separate teleport destinations, so no physical gap is needed.
        for (int x = outerMinX; x <= outerMaxX; x++) {
            for (int y = floorY + 1; y <= floorY + WALL_HEIGHT; y++) {
                put(level, x, y, outerMaxZ, shellBlock(y));
            }
        }
        for (int x = lobbyMinX; x <= lobbyMaxX; x++) {
            for (int y = floorY + 1; y <= floorY + 14; y++) {
                put(level, x, y, lobbyMinZ, Blocks.POLISHED_BLACKSTONE_BRICKS);
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

    /** Builds a covered neon-club ceiling and a dense invisible light grid over the whole lobby floor. */
    private static void buildLobbyRoofAndLighting(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int floorY) {
        int roofY = floorY + 16;
        // Solid dark roof with a purple/magenta perimeter and a subtle central glass skylight strip.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                boolean neonBand = x == minX + 1 || x == maxX - 1 || z == minZ + 1 || z == maxZ - 1;
                Block roof = edge ? Blocks.POLISHED_BLACKSTONE_BRICKS
                        : neonBand ? Blocks.PURPLE_CONCRETE : Blocks.BLACKSTONE;
                if (Math.abs(x - (minX + maxX) / 2) <= 1 && z >= minZ + 5 && z <= maxZ - 5) {
                    roof = Blocks.TINTED_GLASS;
                }
                put(level, x, roofY, z, roof);
            }
        }
        // Decorative ceiling beams and bright fixtures, kept above the usable headroom.
        for (int x = minX + 4; x <= maxX - 4; x += 8) {
            for (int z = minZ + 3; z <= maxZ - 3; z++) {
                if (z % 4 == 0) put(level, x, roofY - 1, z, Blocks.PURPLE_CONCRETE);
            }
        }
        for (int x = minX + 5; x <= maxX - 5; x += 10) {
            for (int z : new int[]{minZ + 5, maxZ - 5}) {
                put(level, x, roofY - 1, z, Blocks.SEA_LANTERN);
            }
        }
        // Dense, invisible full-bright sources close to the floor. The roof-level grid
        // provides general room light, but light loses strength with distance; this
        // lower 3-block grid keeps the entire walkable floor evenly illuminated.
        for (int x = minX + 1; x < maxX; x += 3) {
            for (int z = minZ + 1; z < maxZ; z += 3) {
                putLightIfOpen(level, x, floorY + 3, z);
            }
        }
        // A second, slightly higher grid fills lounge corners and the central aisle.
        for (int x = minX + 2; x < maxX; x += 6) {
            for (int z = minZ + 2; z < maxZ; z += 6) {
                putLightIfOpen(level, x, floorY + 7, z);
                putLightIfOpen(level, x, floorY + 14, z);
            }
        }
        // Additional mid-height light points brighten lounge corners and the dance-floor edges.
        for (int x = minX + 3; x < maxX; x += 8) {
            for (int z = minZ + 3; z < maxZ; z += 8) {
                putLightIfOpen(level, x, floorY + 7, z);
            }
        }
        // Concealed wall-adjacent light points prevent the perimeter and lounge corners from falling dark.
        for (int z = minZ + 3; z < maxZ; z += 5) {
            putLightIfOpen(level, minX + 1, floorY + 6, z);
            putLightIfOpen(level, maxX - 1, floorY + 6, z);
        }
        for (int x = minX + 3; x < maxX; x += 5) {
            putLightIfOpen(level, x, floorY + 6, minZ + 1);
        }
        // Keep visible fixtures on the walls as architectural accents.
        for (int x = minX + 5; x <= maxX - 5; x += 10) {
            put(level, x, floorY + 8, minZ, Blocks.SEA_LANTERN);
        }
        for (int z = minZ + 5; z <= maxZ - 5; z += 10) {
            put(level, minX, floorY + 8, z, Blocks.SEA_LANTERN);
            put(level, maxX, floorY + 8, z, Blocks.SEA_LANTERN);
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
