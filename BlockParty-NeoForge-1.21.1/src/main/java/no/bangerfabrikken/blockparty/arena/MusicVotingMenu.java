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

/** Server-authoritative, non-transferable chest GUI for the lobby music vote. */
final class MusicVotingMenu extends ChestMenu {
    private static final int[] CENTERED_FOUR_SONG_SLOTS = {12, 13, 21, 22};

    private final Arena arena;
    private final SimpleContainer songContainer;
    private final UUID viewerId;
    private final Map<Integer, String> trackBySlot = new HashMap<>();

    MusicVotingMenu(int containerId, Inventory inventory, Arena arena, ServerPlayer viewer) {
        this(containerId, inventory, arena, viewer.getUUID(), new SimpleContainer(27));
    }

    private MusicVotingMenu(int containerId, Inventory inventory, Arena arena, UUID viewerId, SimpleContainer container) {
        super(MenuType.GENERIC_9x3, containerId, inventory, container, 3);
        this.arena = arena;
        this.viewerId = viewerId;
        this.songContainer = container;
        refreshContents();
    }

    void refreshContents() {
        songContainer.clearContent();
        trackBySlot.clear();
        List<MusicTrackManager.Track> tracks = MusicTrackManager.getTracks();
        int count = Math.min(tracks.size(), CENTERED_FOUR_SONG_SLOTS.length);
        for (int i = 0; i < count; i++) {
            MusicTrackManager.Track track = tracks.get(i);
            int slot = CENTERED_FOUR_SONG_SLOTS[i];
            trackBySlot.put(slot, track.id());

            ItemStack icon = new ItemStack(switch (i) {
                case 0 -> Items.RED_CONCRETE;
                case 1 -> Items.BLUE_CONCRETE;
                case 2 -> Items.LIME_CONCRETE;
                default -> Items.PURPLE_CONCRETE;
            });
            boolean selected = track.id().equals(arena.getPlayerMusicVote(viewerId));
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(track.name())
                    .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.AQUA, ChatFormatting.BOLD));
            long votes = arena.getTrackVoteCount(track.id());
            icon.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Artist: " + track.artist()).withStyle(ChatFormatting.GRAY),
                    Component.literal("Stemmer: " + votes).withStyle(ChatFormatting.YELLOW),
                    Component.literal(selected ? "✓ DIN STEMME" : "Klikk for å stemme")
                            .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
            )));
            songContainer.setItem(slot, icon);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Never delegate to ChestMenu: voting icons must not be picked up, moved, or shift-clicked.
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        String trackId = trackBySlot.get(slotId);
        if (trackId == null || clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP
                || clickType == ClickType.THROW || clickType == ClickType.PICKUP_ALL) return;
        arena.voteMusic(serverPlayer, trackId);
    }
}
