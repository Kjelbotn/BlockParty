package no.bangerfabrikken.blockparty.util;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps the 16 Minecraft dye colours to the concrete blocks used by the dance floor. */
public final class BlockPartyColors {
    private static final Map<Block, DyeColor> BLOCK_TO_COLOR = new HashMap<>();
    private static final Map<DyeColor, Block> COLOR_TO_CONCRETE = new EnumMap<>(DyeColor.class);
    private static final Map<DyeColor, Block> COLOR_TO_WOOL = new EnumMap<>(DyeColor.class);

    static {
        add(DyeColor.WHITE, Blocks.WHITE_CONCRETE, Blocks.WHITE_WOOL, Blocks.WHITE_TERRACOTTA);
        add(DyeColor.ORANGE, Blocks.ORANGE_CONCRETE, Blocks.ORANGE_WOOL, Blocks.ORANGE_TERRACOTTA);
        add(DyeColor.MAGENTA, Blocks.MAGENTA_CONCRETE, Blocks.MAGENTA_WOOL, Blocks.MAGENTA_TERRACOTTA);
        add(DyeColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_WOOL, Blocks.LIGHT_BLUE_TERRACOTTA);
        add(DyeColor.YELLOW, Blocks.YELLOW_CONCRETE, Blocks.YELLOW_WOOL, Blocks.YELLOW_TERRACOTTA);
        add(DyeColor.LIME, Blocks.LIME_CONCRETE, Blocks.LIME_WOOL, Blocks.LIME_TERRACOTTA);
        add(DyeColor.PINK, Blocks.PINK_CONCRETE, Blocks.PINK_WOOL, Blocks.PINK_TERRACOTTA);
        add(DyeColor.GRAY, Blocks.GRAY_CONCRETE, Blocks.GRAY_WOOL, Blocks.GRAY_TERRACOTTA);
        add(DyeColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_WOOL, Blocks.LIGHT_GRAY_TERRACOTTA);
        add(DyeColor.CYAN, Blocks.CYAN_CONCRETE, Blocks.CYAN_WOOL, Blocks.CYAN_TERRACOTTA);
        add(DyeColor.PURPLE, Blocks.PURPLE_CONCRETE, Blocks.PURPLE_WOOL, Blocks.PURPLE_TERRACOTTA);
        add(DyeColor.BLUE, Blocks.BLUE_CONCRETE, Blocks.BLUE_WOOL, Blocks.BLUE_TERRACOTTA);
        add(DyeColor.BROWN, Blocks.BROWN_CONCRETE, Blocks.BROWN_WOOL, Blocks.BROWN_TERRACOTTA);
        add(DyeColor.GREEN, Blocks.GREEN_CONCRETE, Blocks.GREEN_WOOL, Blocks.GREEN_TERRACOTTA);
        add(DyeColor.RED, Blocks.RED_CONCRETE, Blocks.RED_WOOL, Blocks.RED_TERRACOTTA);
        add(DyeColor.BLACK, Blocks.BLACK_CONCRETE, Blocks.BLACK_WOOL, Blocks.BLACK_TERRACOTTA);
    }

    private BlockPartyColors() { }

    private static void add(DyeColor color, Block concrete, Block wool, Block terracotta) {
        COLOR_TO_CONCRETE.put(color, concrete);
        COLOR_TO_WOOL.put(color, wool);
        BLOCK_TO_COLOR.put(concrete, color);
        BLOCK_TO_COLOR.put(wool, color);
        BLOCK_TO_COLOR.put(terracotta, color);
    }

    public static DyeColor getColor(BlockState state) {
        return BLOCK_TO_COLOR.get(state.getBlock());
    }

    /** True only for concrete blocks. Wool and terracotta are intentionally not accepted as arena tiles. */
    public static boolean isConcrete(BlockState state) {
        DyeColor color = getColor(state);
        return color != null && COLOR_TO_CONCRETE.get(color) == state.getBlock();
    }

    public static Block getConcrete(DyeColor color) {
        return COLOR_TO_CONCRETE.getOrDefault(color, Blocks.WHITE_CONCRETE);
    }

    public static Block getWool(DyeColor color) {
        return COLOR_TO_WOOL.getOrDefault(color, Blocks.WHITE_WOOL);
    }

    public static List<DyeColor> allColors() {
        return new ArrayList<>(COLOR_TO_CONCRETE.keySet());
    }

    public static String displayName(DyeColor color) {
        return color.getName().replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
    }
}
