package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class CosmeticsMenu extends ChestMenu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final ServerPlayer viewer;
    private final CosmeticsManager.Category category;
    private final boolean mine;
    private final SimpleContainer contents;

    private CosmeticsMenu(int id, Inventory inventory, ServerPlayer player,
                          CosmeticsManager.Category category, boolean mine, SimpleContainer contents) {
        super(MenuType.GENERIC_9x6, id, inventory, contents, 6);
        this.viewer = player;
        this.category = category;
        this.mine = mine;
        this.contents = contents;

        ItemStack filler = item(Items.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 54; slot++) contents.setItem(slot, filler.copy());
        put(4, Items.NETHER_STAR, mine ? "MINE KOSMETIKK" : "KOSMETIKKBUTIKK",
                "Poeng: " + PlayerStatsManager.getPoints(player.getUUID()));

        if (category == null && !mine) {
            put(20, Items.FIREWORK_STAR, "TRAILS", "Partikler som følger deg");
            put(22, Items.DIAMOND_CHESTPLATE, "ARMOR", "Kosmetisk rustningsutseende");
            put(24, Items.BELL, "KILL SOUNDS", "Lyder ved eliminering");
            put(30, Items.FEATHER, "FOOTSTEPS", "Effekter når du går");
            put(32, Items.CHEST, "MY COSMETICS", "Alt du eier og har utstyrt");
        } else {
            Collection<CosmeticsManager.Cosmetic> cosmetics = mine
                    ? CosmeticsManager.owned(player.getUUID()) : CosmeticsManager.items(category);
            int index = 0;
            for (CosmeticsManager.Cosmetic cosmetic : cosmetics) {
                if (index >= SLOTS.length) break;
                boolean owned = CosmeticsManager.owns(player.getUUID(), cosmetic.id());
                boolean equipped = cosmetic.id().equals(CosmeticsManager.equipped(player.getUUID(), cosmetic.category()));
                Item icon = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(cosmetic.icon());
                contents.setItem(SLOTS[index++], item(icon, cosmetic.name(), cosmetic.description(),
                        owned ? "EIES" : "Pris: " + cosmetic.price() + " poeng",
                        equipped ? "UTSTYRT" : "Klikk for å kjøpe / utstyre"));
            }
            put(49, Items.ARROW, "TILBAKE", "Til kategoriene");
            put(45, Items.BARRIER, "FJERN UTSTYR", "Fjern kosmetikken i denne kategorien");
        }
        put(53, Items.BARRIER, "LUKK", "Lukk menyen");
    }

    static void open(ServerPlayer player, CosmeticsManager.Category category) {
        open(player, category, false);
    }

    static void open(ServerPlayer player, CosmeticsManager.Category category, boolean mine) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> new CosmeticsMenu(id, inventory, (ServerPlayer) viewer,
                        category, mine, new SimpleContainer(54)),
                Component.literal("BlockParty Kosmetikk")));
    }

    private static ItemStack item(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    private void put(int slot, Item item, String name, String lore) {
        contents.setItem(slot, item(item, name, lore));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != viewer || type != ClickType.PICKUP || button != 0 || slot < 0 || slot >= 54) return;
        if (slot == 53) {
            viewer.closeContainer();
            return;
        }
        if (category == null && !mine) {
            if (slot == 20) open(viewer, CosmeticsManager.Category.TRAILS);
            else if (slot == 22) open(viewer, CosmeticsManager.Category.ARMOR);
            else if (slot == 24) open(viewer, CosmeticsManager.Category.KILL_SOUNDS);
            else if (slot == 30) open(viewer, CosmeticsManager.Category.FOOTSTEPS);
            else if (slot == 32) open(viewer, null, true);
            return;
        }
        if (slot == 49) {
            open(viewer, null);
            return;
        }
        if (slot == 45 && category != null) {
            CosmeticsManager.unequip(viewer, category);
            open(viewer, category, mine);
            return;
        }
        Collection<CosmeticsManager.Cosmetic> cosmetics = mine
                ? CosmeticsManager.owned(viewer.getUUID()) : CosmeticsManager.items(category);
        for (int index = 0; index < Math.min(SLOTS.length, cosmetics.size()); index++) {
            if (SLOTS[index] == slot) {
                CosmeticsManager.Cosmetic selected = cosmetics.stream().skip(index).findFirst().orElse(null);
                if (selected != null) CosmeticsManager.select(viewer, selected.id());
                open(viewer, category, mine);
                return;
            }
        }
    }
}
