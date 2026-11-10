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

/** Non-transferable central lobby hub with shortcuts to existing BlockParty features. */
final class BlockPartyHubMenu extends ChestMenu {
    private final Arena arena;
    private final SimpleContainer contents;

    BlockPartyHubMenu(int containerId, Inventory inventory, Arena arena, ServerPlayer viewer) {
        this(containerId, inventory, arena, new SimpleContainer(54));
    }

    private BlockPartyHubMenu(int containerId, Inventory inventory, Arena arena, SimpleContainer contents) {
        super(MenuType.GENERIC_9x6, containerId, inventory, contents, 6);
        this.arena = arena;
        this.contents = contents;
        add(10, Items.PLAYER_HEAD, "Profil", "Se poeng, rank, rekorder og seire.");
        add(12, Items.MUSIC_DISC_CAT, "Musikk", "Åpne sangavstemningen.");
        add(14, Items.GOLDEN_HELMET, "Leaderboard", "Se topp 10 spillere.");
        add(30, Items.NETHER_STAR, "Prestasjoner", "Kommer i en senere oppdatering.");
        add(32, Items.LEATHER_CHESTPLATE, "Kosmetikk", "Kommer i en senere oppdatering.");
        add(49, Items.LIME_CONCRETE, "Bli med i neste match", "Registrer deg til neste BlockParty-runde.");
        add(4, Items.AMETHYST_SHARD, "BLOCKPARTY HUB", "Velkommen til lobbyen!");
    }

    private void add(int slot, net.minecraft.world.item.Item item, String name, String lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore).withStyle(ChatFormatting.GRAY))));
        contents.setItem(slot, stack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || clickType != ClickType.PICKUP || button != 0) return;
        if (slotId == 10 || slotId == 12 || slotId == 14 || slotId == 30 || slotId == 32 || slotId == 49) {
            serverPlayer.closeContainer();
            arena.handleHubMenuClick(serverPlayer, slotId);
        }
    }
}
