package no.bangerfabrikken.blockparty.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import no.bangerfabrikken.blockparty.BlockPartyMod;
import no.bangerfabrikken.blockparty.arena.Arena;

import java.util.ArrayList;
import java.util.List;

/** Cycles only through the dedicated BlockParty power-up slots and concrete slot. */
@EventBusSubscriber(modid = BlockPartyMod.MOD_ID, value = Dist.CLIENT)
public final class BlockPartyHotbarController {
    private static final int LEAP_SLOT = 0;
    private static final int CONCRETE_SLOT = 4;
    private static final int SNOWBALL_SLOT = 8;

    private BlockPartyHotbarController() { }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!BlockPartyHudController.isActive() || minecraft.player == null || minecraft.screen != null) return;

        Inventory inventory = minecraft.player.getInventory();
        List<Integer> availableSlots = new ArrayList<>(3);
        if (Arena.isLeapFeather(inventory.getItem(LEAP_SLOT))) availableSlots.add(LEAP_SLOT);
        availableSlots.add(CONCRETE_SLOT);
        if (Arena.isKnockbackSnowball(inventory.getItem(SNOWBALL_SLOT))) availableSlots.add(SNOWBALL_SLOT);

        // With concrete alone, the mouse wheel is intentionally locked to slot 5.
        event.setCanceled(true);
        if (availableSlots.size() == 1) {
            inventory.selected = CONCRETE_SLOT;
            return;
        }

        int currentIndex = availableSlots.indexOf(inventory.selected);
        if (currentIndex < 0) {
            inventory.selected = CONCRETE_SLOT;
            return;
        }

        // Vanilla scroll-up moves toward lower-numbered slots; preserve that feel
        // while jumping directly between the available BlockParty slots.
        int direction = event.getScrollDeltaY() > 0.0D ? -1 : 1;
        inventory.selected = availableSlots.get(Math.floorMod(currentIndex + direction, availableSlots.size()));
    }
}
