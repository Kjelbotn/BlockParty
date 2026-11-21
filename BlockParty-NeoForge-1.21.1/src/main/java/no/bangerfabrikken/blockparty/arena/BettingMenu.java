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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spectator-only winner predictions using BlockParty rank points, never real money. */
final class BettingMenu extends ChestMenu {
    private static final int[] PLAYER_SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};
    private final Arena arena;
    private final SimpleContainer contents;
    private final UUID viewerId;
    private final Map<Integer, UUID> candidateBySlot = new HashMap<>();

    BettingMenu(int containerId, Inventory inventory, Arena arena, ServerPlayer viewer) {
        this(containerId, inventory, arena, viewer.getUUID(), new SimpleContainer(27));
    }

    private BettingMenu(int containerId, Inventory inventory, Arena arena, UUID viewerId, SimpleContainer contents) {
        super(MenuType.GENERIC_9x3, containerId, inventory, contents, 3);
        this.arena = arena;
        this.contents = contents;
        this.viewerId = viewerId;
        refreshContents();
    }

    void refreshContents() {
        contents.clearContent();
        candidateBySlot.clear();
        ItemStack frame = pane(Items.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 27; slot++) {
            if (slot < 9 || slot == 9 || slot == 17 || slot >= 24) contents.setItem(slot, frame.copy());
        }
        add(4, Items.COMPASS, "BETTING – VINNEREN",
                "Velg hvem du tror vinner. Innsats: " + Arena.getBetStakePoints() + " rank-poeng.");
        add(16, Items.GOLD_INGOT, "DINE POENG",
                "Tilgjengelig: " + PlayerStatsManager.getPoints(viewerId) + " rank-poeng.");

        if (arena.hasPlacedBet(viewerId)) {
            add(13, Items.ENCHANTED_BOOK, "DU HAR ALLEREDE BETTET",
                    "Du valgte " + arena.getPlacedBetCandidateName(viewerId) + ". Vent på resultatet.");
            return;
        }
        if (!arena.isBettingOpen()) {
            add(13, Items.BARRIER, "BETTING ER STENGT",
                    "Du kan tippe frem til den første spilleren blir eliminert.");
            return;
        }
        List<UUID> candidates = arena.getBettingCandidates();
        if (candidates.isEmpty()) {
            add(13, Items.BARRIER, "INGEN SPILLERE",
                    "Det finnes ingen aktive spillere å tippe på akkurat nå.");
            return;
        }
        for (int i = 0; i < Math.min(candidates.size(), PLAYER_SLOTS.length); i++) {
            UUID candidateId = candidates.get(i);
            int slot = PLAYER_SLOTS[i];
            candidateBySlot.put(slot, candidateId);
            String name = arena.getBettingCandidateName(candidateId);
            String rank = PlayerStatsManager.getRank(PlayerStatsManager.getPoints(candidateId)).name();
            ItemStack choice = new ItemStack(i % 2 == 0 ? Items.LIME_CONCRETE : Items.EMERALD_BLOCK);
            choice.set(DataComponents.CUSTOM_NAME, Component.literal(name)
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            choice.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Rank: " + rank).withStyle(ChatFormatting.GRAY),
                    Component.literal("Klikk for å bette " + Arena.getBetStakePoints() + " poeng")
                            .withStyle(ChatFormatting.YELLOW))));
            contents.setItem(slot, choice);
        }
        add(25, Items.PAPER, "REGLER",
                "Ett tips per match. Riktig tips gir 20 poeng. Feil tips taper innsatsen.");
    }

    private ItemStack pane(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(" ").withStyle(ChatFormatting.RESET));
        return stack;
    }

    private void add(int slot, net.minecraft.world.item.Item item, String name, String lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)
                .withStyle(name.startsWith("DU HAR") ? ChatFormatting.GREEN : ChatFormatting.YELLOW, ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore).withStyle(ChatFormatting.GRAY))));
        contents.setItem(slot, stack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || clickType != ClickType.PICKUP || button != 0) return;
        UUID candidate = candidateBySlot.get(slotId);
        if (candidate == null) return;
        if (arena.placeBet(serverPlayer, candidate)) serverPlayer.closeContainer();
        else {
            refreshContents();
            broadcastChanges();
        }
    }
}
