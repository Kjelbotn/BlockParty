package no.bangerfabrikken.blockparty.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

/** Tiny 3x5 concrete-block font used for the arena's in-world colour display. */
public final class BlockPartyText {
    private static final Map<Character, String[]> FONT = Map.ofEntries(
            Map.entry('A', rows("101", "111", "101", "101", "101")),
            Map.entry('B', rows("110", "101", "110", "101", "110")),
            Map.entry('C', rows("111", "100", "100", "100", "111")),
            Map.entry('D', rows("110", "101", "101", "101", "110")),
            Map.entry('E', rows("111", "100", "110", "100", "111")),
            Map.entry('F', rows("111", "100", "110", "100", "100")),
            Map.entry('G', rows("111", "100", "101", "101", "111")),
            Map.entry('H', rows("101", "101", "111", "101", "101")),
            Map.entry('I', rows("111", "010", "010", "010", "111")),
            Map.entry('J', rows("001", "001", "001", "101", "111")),
            Map.entry('K', rows("101", "101", "110", "101", "101")),
            Map.entry('L', rows("100", "100", "100", "100", "111")),
            Map.entry('M', rows("101", "111", "111", "101", "101")),
            Map.entry('N', rows("101", "111", "111", "111", "101")),
            Map.entry('O', rows("111", "101", "101", "101", "111")),
            Map.entry('P', rows("110", "101", "110", "100", "100")),
            Map.entry('Q', rows("111", "101", "101", "111", "001")),
            Map.entry('R', rows("110", "101", "110", "101", "101")),
            Map.entry('S', rows("111", "100", "111", "001", "111")),
            Map.entry('T', rows("111", "010", "010", "010", "010")),
            Map.entry('U', rows("101", "101", "101", "101", "111")),
            Map.entry('V', rows("101", "101", "101", "101", "010")),
            Map.entry('W', rows("101", "101", "111", "111", "101")),
            Map.entry('X', rows("101", "101", "010", "101", "101")),
            Map.entry('Y', rows("101", "101", "010", "010", "010")),
            Map.entry('Z', rows("111", "001", "010", "100", "111")),
            Map.entry('0', rows("111", "101", "101", "101", "111")),
            Map.entry('1', rows("010", "110", "010", "010", "111")),
            Map.entry('2', rows("110", "001", "010", "100", "111")),
            Map.entry('3', rows("110", "001", "010", "001", "110")),
            Map.entry(' ', rows("000", "000", "000", "000", "000"))
    );

    private BlockPartyText() { }

    private static String[] rows(String... rows) { return rows; }

    /**
     * Draws a centred word. The origin's Y is the top pixel row. The callers must pass the correct X direction for their viewing side; the north-wall arena sign is not reversed.
     */
    public static void drawCentered(ServerLevel level, BlockPos origin, String text, Block foreground, int maxWidth) {
        drawCentered(level, origin, text, foreground, maxWidth, false);
    }

    public static void drawCentered(ServerLevel level, BlockPos origin, String text, Block foreground, int maxWidth, boolean reverseX) {
        String normalized = text.toUpperCase(java.util.Locale.ROOT);
        int width = Math.max(1, normalized.length() * 4 - 1);
        if (width > maxWidth) normalized = normalized.substring(0, Math.max(1, (maxWidth + 1) / 4));
        width = Math.max(1, normalized.length() * 4 - 1);
        int left = origin.getX() - width / 2;

        // Clear the full panel row first so a longer word cannot leave pixels from the previous word.
        for (int y = 0; y < 5; y++) {
            for (int x = -maxWidth / 2; x <= maxWidth / 2; x++) {
                level.setBlock(new BlockPos(origin.getX() + x, origin.getY() - y, origin.getZ()),
                        Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
            }
        }

        for (int i = 0; i < normalized.length(); i++) {
            String[] glyph = FONT.getOrDefault(normalized.charAt(i), FONT.get(' '));
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    if (glyph[row].charAt(col) == '1') {
                        int screenColumn = i * 4 + col;
                        int x = reverseX ? left + width - 1 - screenColumn : left + screenColumn;
                        level.setBlock(new BlockPos(x, origin.getY() - row, origin.getZ()), foreground.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
