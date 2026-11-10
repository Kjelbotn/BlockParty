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

/** Server-authoritative, non-transferable chest GUI with a centered vertical song list. */
final class MusicVotingMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int CENTER_COLUMN = 4;
    private static final int MAX_VISIBLE_TRACKS = ROWS;

    private final Arena arena;
    private final SimpleContainer songContainer;
    private final UUID viewerId;
    private final Map<Integer, String> trackBySlot = new HashMap<>();

    MusicVotingMenu(int containerId, Inventory inventory, Arena arena, ServerPlayer viewer) {
        this(containerId, inventory, arena, viewer.getUUID(), new SimpleContainer(ROWS * 9));
    }

    private MusicVotingMenu(int containerId, Inventory inventory, Arena arena, UUID viewerId, SimpleContainer container) {
        super(MenuType.GENERIC_9x6, containerId, inventory, container, ROWS);
        this.arena = arena;
        this.viewerId = viewerId;
        this.songContainer = container;
        refreshContents();
    }

    void refreshContents() {
        songContainer.clearContent();
        trackBySlot.clear();
        List<MusicTrackManager.Track> tracks = MusicTrackManager.getTracks();
        int count = Math.min(tracks.size(), MAX_VISIBLE_TRACKS);
        int firstRow = Math.max(0, (ROWS - count) / 2);
        String selectedTrack = arena.getPlayerMusicVote(viewerId);

        for (int i = 0; i < count; i++) {
            MusicTrackManager.Track track = tracks.get(i);
            int slot = (firstRow + i) * 9 + CENTER_COLUMN;
            trackBySlot.put(slot, track.id());

            ItemStack icon = new ItemStack(switch (i % 4) {
                case 0 -> Items.RED_CONCRETE;
                case 1 -> Items.BLUE_CONCRETE;
                case 2 -> Items.LIME_CONCRETE;
                default -> Items.PURPLE_CONCRETE;
            });
            boolean selected = track.id().equals(selectedTrack);
            icon.set(DataComponents.CUSTOM_NAME, Component.literal((selected ? "✓ " : "") + track.name())
                    .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.AQUA, ChatFormatting.BOLD));
            long votes = arena.getTrackVoteCount(track.id());
            icon.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Artist: " + track.artist()).withStyle(ChatFormatting.GRAY),
                    Component.literal("Stemmer: " + votes).withStyle(ChatFormatting.YELLOW),
                    Component.literal(selected ? "DITT NÅVÆRENDE VALG" : "Klikk for å stemme eller bytte valg")
                            .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
            )));
            songContainer.setItem(slot, icon);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Never delegate to ChestMenu: voting icons must not be picked up, moved, or shift-clicked.
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        String trackId = trackBySlot.get(slotId);
        if (trackId == null || clickType != ClickType.PICKUP || button != 0) return;
        arena.voteMusic(serverPlayer, trackId);
        refreshContents();
        broadcastChanges();
    }
}
