package no.bangerfabrikken.blockparty.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

/** Server-authoritative, non-transferable chest GUI with a horizontal row of song choices. */
final class MusicVotingMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int FIRST_ROW = 2;
    private static final int MAX_COLUMNS = 9;
    private static final int MAX_VISIBLE_TRACKS = 27;
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

    private static int getDisplayedMatchSeconds(MusicTrackManager.Track track) {
        return Math.max(1, (int) Math.round(track.durationSeconds()));
    }

    private static String formatDuration(int seconds) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    void refreshContents() {
        songContainer.clearContent();
        trackBySlot.clear();
        List<MusicTrackManager.Track> tracks = MusicTrackManager.getTracks(arena.getId());
        int count = Math.min(tracks.size(), MAX_VISIBLE_TRACKS);
        String selectedTrack = arena.getPlayerMusicVote(viewerId);

        for (int i = 0; i < count; i++) {
            MusicTrackManager.Track track = tracks.get(i);
            int row = i / MAX_COLUMNS;
            int rowCount = Math.min(MAX_COLUMNS, count - row * MAX_COLUMNS);
            int firstColumn = (9 - rowCount) / 2;
            int column = i % MAX_COLUMNS;
            int slot = (FIRST_ROW + row) * 9 + firstColumn + column;
            trackBySlot.put(slot, track.id());

            ItemStack icon = new ItemStack("xmas".equals(arena.getId())
                    ? switch (i % 3) {
                        case 0 -> Items.RED_CONCRETE;
                        case 1 -> Items.GREEN_CONCRETE;
                        default -> Items.WHITE_CONCRETE;
                    }
                    : (i % 2 == 0 ? Items.YELLOW_CONCRETE : Items.LIME_CONCRETE));
            boolean selected = track.id().equals(selectedTrack);
            MutableComponent trackTitle = Component.literal(selected ? "✓ " : "")
                    .append(MusicTrackManager.displayNameComponent(track.name()));
            icon.set(DataComponents.CUSTOM_NAME, trackTitle
                    .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.YELLOW));

            long votes = arena.getTrackVoteCount(track.id());
            int matchSeconds = getDisplayedMatchSeconds(track);
            boolean xmasMenu = "xmas".equals(arena.getId());
            icon.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Artist: " + track.artist())
                            .withStyle(xmasMenu ? ChatFormatting.AQUA : ChatFormatting.GRAY),
                    Component.literal(formatDuration(matchSeconds))
                            .withStyle(xmasMenu ? ChatFormatting.WHITE : ChatFormatting.YELLOW),
                    Component.literal("Stemmer: " + votes)
                            .withStyle(xmasMenu ? ChatFormatting.RED : ChatFormatting.YELLOW),
                    Component.literal(selected ? "DITT NÅVÆRENDE VALG" : "Klikk for å stemme eller bytte valg")
                            .withStyle(selected ? ChatFormatting.GREEN : (xmasMenu ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
            )));
            songContainer.setItem(slot, icon);
        }

        // Three game-mode choices share the bottom row; the heading sits above songs.
        Arena.GameMode selectedMode = arena.getPreferredGameMode(viewerId);
        boolean hasModeVote = arena.hasGameModeVote(viewerId);
        addModeVote(47, Items.NETHER_STAR, "CLASSIC", Arena.GameMode.CLASSIC, selectedMode, hasModeVote);
        addModeVote(49, Items.BOOK, "MEMORY MODE", Arena.GameMode.MEMORY, selectedMode, hasModeVote);
        addModeVote(51, Items.IRON_SWORD, "HARDCORE MODE", Arena.GameMode.HARDCORE, selectedMode, hasModeVote);
        ItemStack heading = new ItemStack(Items.COMPASS);
        heading.set(DataComponents.CUSTOM_NAME, Component.literal("STEM PÅ SPILLMODUS")
                .withStyle(ChatFormatting.YELLOW));
        heading.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Velg Classic, Memory eller Hardcore").withStyle(ChatFormatting.GRAY))));
        songContainer.setItem(4, heading);
    }


    private void addModeVote(int slot, net.minecraft.world.item.Item item, String name,
                             Arena.GameMode mode, Arena.GameMode selectedMode, boolean hasModeVote) {
        boolean selected = hasModeVote && selectedMode == mode;
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal((selected ? "✓ " : "") + name)
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Stemmer: " + arena.getGameModeVoteCount(mode)).withStyle(ChatFormatting.YELLOW),
                Component.literal("Klikk for å stemme").withStyle(ChatFormatting.GRAY))));
        songContainer.setItem(slot, stack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Never delegate to ChestMenu: voting icons must not be picked up, moved, or shift-clicked.
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (clickType != ClickType.PICKUP || button != 0) return;
        if (slotId == 47 || slotId == 49 || slotId == 51) {
            Arena.GameMode mode = switch (slotId) {
                case 47 -> Arena.GameMode.CLASSIC;
                case 49 -> Arena.GameMode.MEMORY;
                default -> Arena.GameMode.HARDCORE;
            };
            arena.setPreferredGameMode(serverPlayer, mode);
            refreshContents();
            broadcastChanges();
            return;
        }
        String trackId = trackBySlot.get(slotId);
        if (trackId == null) return;
        if (!arena.voteMusic(serverPlayer, trackId)) {
            refreshContents();
            broadcastChanges();
        }
        // A successful song vote opens the separate game-mode menu from Arena.
    }
}
