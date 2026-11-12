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
        // Dark frame and subtle purple accents make the menu read as one intentional GUI.
        ItemStack frame = pane(Items.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(Items.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) contents.setItem(slot, frame.copy());
            else if (row == 2 || row == 4) contents.setItem(slot, accent.copy());
        }

        add(4, Items.AMETHYST_SHARD, "BLOCKPARTY HUB", "Velkommen! Velg en kategori nedenfor.");
        add(11, Items.PLAYER_HEAD, "PROFIL", "Se poeng, rank, rekorder og seire.");
        add(13, Items.MUSIC_DISC_CAT, "MUSIKKAVSTEMNING", "Velg hvilken låt som skal spilles.");
        add(15, Items.GOLDEN_HELMET, "LEADERBOARD", "Se de 10 beste BlockParty-spillerne.");
        add(29, Items.CLOCK, "DAILY CHALLENGES", "Tre daglige oppgaver med personlige poengbelønninger.");
        add(31, Items.COMPASS, "GAME MODE", "Velg Classic eller Memory Mode for neste match.");
        add(33, Items.LEATHER_CHESTPLATE, "KOSMETIKK", "Skins og effekter kommer senere.");
        add(49, Items.LIME_CONCRETE, "BLI MED I NESTE MATCH", "Registrer deg til neste BlockParty-runde.");
    }

    private ItemStack pane(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(" ").withStyle(ChatFormatting.RESET));
        return stack;
    }

    private void add(int slot, net.minecraft.world.item.Item item, String name, String lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore).withStyle(ChatFormatting.GRAY))));
        contents.setItem(slot, stack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || clickType != ClickType.PICKUP || button != 0) return;
        if (slotId == 11 || slotId == 13 || slotId == 15 || slotId == 29 || slotId == 31 || slotId == 33 || slotId == 49) {
            serverPlayer.closeContainer();
            arena.handleHubMenuClick(serverPlayer, switch (slotId) {
                case 11 -> 10;
                case 13 -> 12;
                case 15 -> 14;
                case 29 -> 30;
                case 31 -> 34;
                case 33 -> 32;
                default -> 49;
            });
        }
    }
}
