package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/** Each player sets a preference; the registered majority selects the next match mode. */
final class GameModeMenu extends ChestMenu {
    private final Arena arena;
    private final SimpleContainer contents;

    GameModeMenu(int containerId, Inventory inventory, Arena arena, ServerPlayer viewer) {
        this(containerId, inventory, arena, new SimpleContainer(27), viewer);
    }

    private GameModeMenu(int containerId, Inventory inventory, Arena arena,
                         SimpleContainer contents, ServerPlayer viewer) {
        super(MenuType.GENERIC_9x3, containerId, inventory, contents, 3);
        this.arena = arena;
        this.contents = contents;

        ItemStack frame = pane(Items.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 27; slot++) {
            if (slot < 9 || slot >= 18 || slot % 9 == 0 || slot % 9 == 8) {
                contents.setItem(slot, frame.copy());
            }
        }

        Arena.GameMode current = arena.getPreferredGameMode(viewer.getUUID());
        boolean hasVoted = arena.hasGameModeVote(viewer.getUUID());
        int classicVotes = arena.getGameModeVoteCount(Arena.GameMode.CLASSIC);
        int memoryVotes = arena.getGameModeVoteCount(Arena.GameMode.MEMORY);
        add(4, Items.COMPASS, "VOTE: GAME MODE",
                "Flest stemmer vinner. Stemmer nullstilles etter hver match.");
        add(11, Items.NETHER_STAR, (hasVoted && current == Arena.GameMode.CLASSIC ? "✓ " : "") + "CLASSIC",
                "Vanlig BlockParty. Stemmer: " + classicVotes + ".");
        add(15, Items.BOOK, (hasVoted && current == Arena.GameMode.MEMORY ? "✓ " : "") + "MEMORY MODE",
                "5s memorering, 7s grått gulv + 2s varsel. Stemmer: " + memoryVotes + ".");
    }

    private ItemStack pane(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(" ").withStyle(ChatFormatting.RESET));
        return stack;
    }

    private void add(int slot, net.minecraft.world.item.Item item, String name, String description) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(description).withStyle(ChatFormatting.GRAY))));
        contents.setItem(slot, stack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || clickType != ClickType.PICKUP || button != 0) return;
        Arena.GameMode selected = switch (slotId) {
            case 11 -> Arena.GameMode.CLASSIC;
            case 15 -> Arena.GameMode.MEMORY;
            default -> null;
        };
        if (selected == null) return;
        serverPlayer.closeContainer();
        arena.setPreferredGameMode(serverPlayer, selected);
    }
}
