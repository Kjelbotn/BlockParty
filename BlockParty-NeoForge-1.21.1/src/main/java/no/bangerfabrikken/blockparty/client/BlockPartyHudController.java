package no.bangerfabrikken.blockparty.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import no.bangerfabrikken.blockparty.BlockPartyMod;
import no.bangerfabrikken.blockparty.network.BlockPartyHudPayload;

import java.util.Comparator;
import java.util.List;

/** Compact client-side list of timed potion effects during a BlockParty match. */
@EventBusSubscriber(modid = BlockPartyMod.MOD_ID, value = Dist.CLIENT)
public final class BlockPartyHudController {
    private static boolean active;

    private BlockPartyHudController() { }

    public static boolean isActive() {
        return active;
    }

    public static void handle(BlockPartyHudPayload payload) {
        active = payload.active();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.player == null || minecraft.screen != null) return;

        List<MobEffectInstance> effects = minecraft.player.getActiveEffects().stream()
                .filter(effect -> effect.getDuration() > 0)
                .sorted(Comparator.comparing(effect -> effect.getEffect().value().getDescriptionId()))
                .toList();
        if (effects.isEmpty()) return;

        var gui = event.getGuiGraphics();
        int x = 8;
        int y = 8;
        int width = 142;
        int visibleEffects = Math.min(effects.size(), 7);
        int height = 17 + visibleEffects * 11;
        gui.fill(x - 4, y - 4, x + width, y + height, 0x90000000);
        gui.drawString(minecraft.font, Component.literal("AKTIVE EFFEKTER"), x, y, 0x55FFFF, true);

        for (int i = 0; i < visibleEffects; i++) {
            MobEffectInstance effect = effects.get(i);
            String name = Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
            int seconds = (effect.getDuration() + 19) / 20;
            String line = name + "  " + seconds + "s";
            gui.drawString(minecraft.font, line, x, y + 12 + i * 11, 0xFFFFFF, true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
    }
}
