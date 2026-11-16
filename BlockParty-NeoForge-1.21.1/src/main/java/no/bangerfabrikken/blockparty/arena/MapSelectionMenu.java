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

/** Two-choice, server-authoritative map selector. The icons cannot be moved or taken. */
public final class MapSelectionMenu extends ChestMenu {
    private final SimpleContainer mapContainer;
    private final ServerPlayer viewer;

    public MapSelectionMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
        this(containerId, inventory, viewer, new SimpleContainer(9));
    }

    private MapSelectionMenu(int containerId, Inventory inventory, ServerPlayer viewer, SimpleContainer container) {
        super(MenuType.GENERIC_9x1, containerId, inventory, container, 1);
        this.mapContainer = container;
        this.viewer = viewer;
        setMapIcon(2, Items.GREEN_CONCRETE, "CLASSIC", ChatFormatting.GREEN,
                "Gå til Classic-lobbyen");
        setMapIcon(6, Items.RED_CONCRETE, "XMAS", ChatFormatting.RED,
                "Gå til Xmas-lobbyen");
    }

    private void setMapIcon(int slot, net.minecraft.world.item.Item item, String name,
                            ChatFormatting color, String description) {
        ItemStack icon = new ItemStack(item);
        icon.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
        icon.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.literal(description).withStyle(ChatFormatting.GRAY),
                Component.literal("Klikk for å velge").withStyle(ChatFormatting.GREEN))));
        mapContainer.setItem(slot, icon);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Keep the menu strictly read-only: no item pickup, shift-clicking or dragging.
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.getUUID().equals(viewer.getUUID())) return;
        if (clickType != ClickType.PICKUP || button != 0) return;
        if (slotId == 2) {
            selectMap(serverPlayer, "main");
        } else if (slotId == 6) {
            selectMap(serverPlayer, "xmas");
        }
    }

    private void selectMap(ServerPlayer player, String id) {
        ArenaManager manager = ArenaManager.get();
        Arena destination = manager.get(id);
        if (destination == null || !destination.isConfigured()) {
            player.sendSystemMessage(Component.literal("Fant ikke en ferdig konfigurert "
                    + ("xmas".equals(id) ? "Xmas-" : "Classic-")
                    + "lobby. Kontakt en administrator.").withStyle(ChatFormatting.RED));
            return;
        }

        player.closeContainer();
        Arena current = manager.findArenaForLeave(player);
        if (current != null && current != destination) {
            // Restore the player's original survival inventory/location before the
            // destination arena captures a fresh snapshot for its own lobby session.
            current.leave(player, true);
        }
        destination.join(player);
    }
}
