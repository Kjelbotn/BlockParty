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

/** Read-only overview of today's personal objectives and their progress. */
final class DailyChallengeMenu extends ChestMenu {
    private final SimpleContainer contents;

    DailyChallengeMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
        this(containerId, inventory, new SimpleContainer(27), viewer);
    }

    private DailyChallengeMenu(int containerId, Inventory inventory, SimpleContainer contents, ServerPlayer viewer) {
        super(MenuType.GENERIC_9x3, containerId, inventory, contents, 3);
        this.contents = contents;
        ItemStack frame = named(Items.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 27; slot++) {
            if (slot < 9 || slot >= 18 || slot % 9 == 0 || slot % 9 == 8) contents.setItem(slot, frame.copy());
        }
        List<Component> lines = DailyChallengeManager.getLines(viewer);
        contents.setItem(4, named(Items.CLOCK, "DAILY CHALLENGES",
                lines.isEmpty() ? List.of() : List.of(lines.get(0))));
        int[] slots = {10, 12, 14, 16};
        for (int i = 1; i < lines.size() && i <= slots.length; i++) {
            contents.setItem(slots[i - 1], named(Items.PAPER, lines.get(i).getString(),
                    List.of(lines.get(i))));
        }
        if (lines.size() > 6) {
            contents.setItem(22, named(Items.EMERALD, lines.get(lines.size() - 1).getString(),
                    List.of(Component.literal("Fremdriften lagres automatisk.")
                            .withStyle(ChatFormatting.GRAY))));
        }
    }

    private ItemStack named(net.minecraft.world.item.Item item, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Informational screen: challenge icons cannot be picked up or moved.
    }
}
