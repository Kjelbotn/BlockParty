package no.bangerfabrikken.blockparty.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client signal that controls BlockParty-only HUD and hotbar behavior. */
public record BlockPartyHudPayload(boolean active) implements CustomPacketPayload {
    public static final Type<BlockPartyHudPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("blockparty", "hud_state"));

    public static final StreamCodec<ByteBuf, BlockPartyHudPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, BlockPartyHudPayload::active,
                    BlockPartyHudPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
