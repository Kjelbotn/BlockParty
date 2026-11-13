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
    private static final int MAX_COLUMNS = 7;
    private static final int MAX_VISIBLE_TRACKS = 21;
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

    private static String getDifficulty(int matchSeconds) {
        if (matchSeconds >= 180) return "EASY";
        if (matchSeconds >= 120) return "MEDIUM";
        if (matchSeconds >= 90) return "HARD";
        return "EXPERT";
    }

    private static ChatFormatting getDifficultyColor(String difficulty) {
        return switch (difficulty) {
            case "EASY" -> ChatFormatting.GREEN;
            case "MEDIUM" -> ChatFormatting.AQUA;
            case "HARD" -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
    }

    void refreshContents() {
        songContainer.clearContent();
        trackBySlot.clear();
        List<MusicTrackManager.Track> tracks = MusicTrackManager.getTracks();
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

            ItemStack icon = new ItemStack(switch (i % 7) {
                case 0 -> Items.RED_CONCRETE;
                case 1 -> Items.BLUE_CONCRETE;
                case 2 -> Items.LIME_CONCRETE;
                case 3 -> Items.PURPLE_CONCRETE;
                case 4 -> Items.ORANGE_CONCRETE;
                case 5 -> Items.CYAN_CONCRETE;
                default -> Items.YELLOW_CONCRETE;
            });
            boolean selected = track.id().equals(selectedTrack);
            MutableComponent displayName;
            if ("luxus_leverpostei_195".equals(track.id())) {
                // Keep the section sign and the digits in separate literal components so
                // the legacy text renderer cannot interpret "§1" as a color code.
                displayName = Component.literal(selected ? "✓ " : "")
                        .append(Component.literal("§"))
                        .append(Component.literal("195"));
            } else {
                displayName = Component.literal((selected ? "✓ " : "") + track.name());
            }
            icon.set(DataComponents.CUSTOM_NAME, displayName
                    .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.AQUA));

            long votes = arena.getTrackVoteCount(track.id());
            int matchSeconds = getDisplayedMatchSeconds(track);
            String difficulty = getDifficulty(matchSeconds);
            icon.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Artist: " + track.artist()).withStyle(ChatFormatting.GRAY),
                    Component.literal(Arena.getTrackRoundLimit(track) + " RUNDER"
                            + " | " + difficulty + " | " + formatDuration(matchSeconds))
                            .withStyle(getDifficultyColor(difficulty), ChatFormatting.BOLD),
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
