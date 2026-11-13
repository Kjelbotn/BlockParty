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

import java.util.ArrayList;
import java.util.List;

/** Read-only overview of the three daily objectives, personal progress, checkmarks and rewards. */
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
            if (slot < 9 || slot >= 18 || slot % 9 == 0 || slot % 9 == 8) {
                contents.setItem(slot, frame.copy());
            }
        }

        contents.setItem(4, named(Items.CLOCK, "DAILY CHALLENGES",
                List.of(Component.literal("Tre personlige oppgaver – nye hver dag").withStyle(ChatFormatting.GRAY))));

        var server = viewer.serverLevel().getServer();
        List<DailyChallengeManager.Challenge> challenges = DailyChallengeManager.getChallenges(server);
        int[] slots = {10, 13, 16};
        for (int i = 0; i < Math.min(3, challenges.size()); i++) {
            DailyChallengeManager.Challenge challenge = challenges.get(i);
            int progress = DailyChallengeManager.getProgress(server, viewer.getUUID(), challenge.id());
            boolean done = DailyChallengeManager.isCompleted(server, viewer.getUUID(), challenge.id());
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(challenge.description()).withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Fremdrift: " + progress + " / " + challenge.goal())
                    .withStyle(ChatFormatting.AQUA));
            lore.add(Component.literal("Belønning: +" + challenge.reward() + " poeng")
                    .withStyle(ChatFormatting.GOLD));
            lore.add(Component.literal(done ? "☑ FULLFØRT" : "☐ IKKE FULLFØRT")
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.YELLOW, ChatFormatting.BOLD));
            contents.setItem(slots[i], named(challenge.icon(),
                    (done ? "☑ " : "☐ ") + challenge.title(), lore));
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
        if (!(player instanceof ServerPlayer)) return;
    }
}
