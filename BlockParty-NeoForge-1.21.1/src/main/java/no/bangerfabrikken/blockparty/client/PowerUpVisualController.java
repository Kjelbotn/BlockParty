package no.bangerfabrikken.blockparty.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import no.bangerfabrikken.blockparty.BlockPartyMod;
import no.bangerfabrikken.blockparty.network.PowerUpVisualPayload;

/** Client-only camera treatment for the PANIC MODE power-up. */
@EventBusSubscriber(modid = BlockPartyMod.MOD_ID, value = Dist.CLIENT)
public final class PowerUpVisualController {
    private static int cameraShakeTicks;
    private static long elapsedTicks;

    private PowerUpVisualController() { }

    public static void handle(PowerUpVisualPayload payload) {
        if (payload.action() == PowerUpVisualPayload.START_CAMERA_SHAKE) {
            cameraShakeTicks = Math.max(0, Math.min(200, payload.durationTicks()));
            elapsedTicks = 0L;
        } else if (payload.action() == PowerUpVisualPayload.CLEAR_CAMERA_SHAKE) {
            clear();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (cameraShakeTicks > 0 && --cameraShakeTicks == 0) elapsedTicks = 0L;
        else if (cameraShakeTicks > 0) elapsedTicks++;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (cameraShakeTicks <= 0 || Minecraft.getInstance().player == null) return;
        double time = elapsedTicks + event.getPartialTick();
        // Stronger layered camera shake: clearly noticeable without obscuring the floor.
        event.setYaw(event.getYaw() + (float) Math.sin(time * 1.15D) * 0.65F + (float) Math.sin(time * 2.40D) * 0.22F);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 1.43D) * 0.55F + (float) Math.sin(time * 2.05D) * 0.18F);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 0.95D) * 1.65F + (float) Math.sin(time * 1.80D) * 0.55F);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        cameraShakeTicks = 0;
        elapsedTicks = 0L;
    }
}
