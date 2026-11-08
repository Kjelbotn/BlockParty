package no.bangerfabrikken.blockparty.util;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Maps the 16 Minecraft dye colors to supported Block Party floor blocks. */
public final class BlockPartyColors {
    private static final Map<Block, DyeColor> BLOCK_TO_COLOR = new HashMap<>();

    static {
        add(DyeColor.WHITE, Blocks.WHITE_WOOL, Blocks.WHITE_CONCRETE, Blocks.WHITE_TERRACOTTA);
        add(DyeColor.ORANGE, Blocks.ORANGE_WOOL, Blocks.ORANGE_CONCRETE, Blocks.ORANGE_TERRACOTTA);
        add(DyeColor.MAGENTA, Blocks.MAGENTA_WOOL, Blocks.MAGENTA_CONCRETE, Blocks.MAGENTA_TERRACOTTA);
        add(DyeColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_WOOL, Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_TERRACOTTA);
        add(DyeColor.YELLOW, Blocks.YELLOW_WOOL, Blocks.YELLOW_CONCRETE, Blocks.YELLOW_TERRACOTTA);
        add(DyeColor.LIME, Blocks.LIME_WOOL, Blocks.LIME_CONCRETE, Blocks.LIME_TERRACOTTA);
        add(DyeColor.PINK, Blocks.PINK_WOOL, Blocks.PINK_CONCRETE, Blocks.PINK_TERRACOTTA);
        add(DyeColor.GRAY, Blocks.GRAY_WOOL, Blocks.GRAY_CONCRETE, Blocks.GRAY_TERRACOTTA);
        add(DyeColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_WOOL, Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_TERRACOTTA);
        add(DyeColor.CYAN, Blocks.CYAN_WOOL, Blocks.CYAN_CONCRETE, Blocks.CYAN_TERRACOTTA);
        add(DyeColor.PURPLE, Blocks.PURPLE_WOOL, Blocks.PURPLE_CONCRETE, Blocks.PURPLE_TERRACOTTA);
        add(DyeColor.BLUE, Blocks.BLUE_WOOL, Blocks.BLUE_CONCRETE, Blocks.BLUE_TERRACOTTA);
        add(DyeColor.BROWN, Blocks.BROWN_WOOL, Blocks.BROWN_CONCRETE, Blocks.BROWN_TERRACOTTA);
        add(DyeColor.GREEN, Blocks.GREEN_WOOL, Blocks.GREEN_CONCRETE, Blocks.GREEN_TERRACOTTA);
        add(DyeColor.RED, Blocks.RED_WOOL, Blocks.RED_CONCRETE, Blocks.RED_TERRACOTTA);
        add(DyeColor.BLACK, Blocks.BLACK_WOOL, Blocks.BLACK_CONCRETE, Blocks.BLACK_TERRACOTTA);
    }

    private BlockPartyColors() {
    }

    private static void add(DyeColor color, Block wool, Block concrete, Block terracotta) {
        BLOCK_TO_COLOR.put(wool, color);
        BLOCK_TO_COLOR.put(concrete, color);
        BLOCK_TO_COLOR.put(terracotta, color);
    }

    public static DyeColor getColor(BlockState state) {
        return BLOCK_TO_COLOR.get(state.getBlock());
    }

    public static String displayName(DyeColor color) {
        String name = color.getName().replace('_', ' ');
        return name.toUpperCase(java.util.Locale.ROOT);
    }
}
