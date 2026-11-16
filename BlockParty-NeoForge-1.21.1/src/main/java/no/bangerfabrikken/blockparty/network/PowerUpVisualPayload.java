package no.bangerfabrikken.blockparty.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client visual power-up effects. */
public record PowerUpVisualPayload(int action, int durationTicks) implements CustomPacketPayload {
    public static final int START_CAMERA_SHAKE = 0;
    public static final int CLEAR_CAMERA_SHAKE = 1;

    public static final Type<PowerUpVisualPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("blockparty", "powerup_visual"));

    public static final StreamCodec<ByteBuf, PowerUpVisualPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PowerUpVisualPayload::action,
                    ByteBufCodecs.VAR_INT, PowerUpVisualPayload::durationTicks,
                    PowerUpVisualPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
