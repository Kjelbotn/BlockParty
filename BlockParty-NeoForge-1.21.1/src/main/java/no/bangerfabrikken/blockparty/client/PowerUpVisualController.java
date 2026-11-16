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
    private static boolean intenseShake;

    private PowerUpVisualController() { }

    public static void handle(PowerUpVisualPayload payload) {
        if (payload.action() == PowerUpVisualPayload.START_CAMERA_SHAKE
                || payload.action() == PowerUpVisualPayload.START_INTENSE_CAMERA_SHAKE) {
            cameraShakeTicks = Math.max(0, Math.min(240, payload.durationTicks()));
            intenseShake = payload.action() == PowerUpVisualPayload.START_INTENSE_CAMERA_SHAKE;
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
        // FULL SOM FAEN is a deliberately strong nausea-like sway. Keep the movement
        // continuous and asymmetric so it reads as vanilla nausea rather than vibration.
        float multiplier = intenseShake ? 1.0F : 0.18F;
        event.setYaw(event.getYaw() + (float) Math.sin(time * 0.82D) * 0.18F * multiplier);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 1.07D) * 0.14F * multiplier);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 0.69D) * (intenseShake ? 2.8F : 0.42F));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        cameraShakeTicks = 0;
        elapsedTicks = 0L;
        intenseShake = false;
    }
}
