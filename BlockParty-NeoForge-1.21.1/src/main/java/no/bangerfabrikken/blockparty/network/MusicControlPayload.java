package no.bangerfabrikken.blockparty.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative commands for the local BlockParty music player. */
public record MusicControlPayload(ResourceLocation soundId, int action) implements CustomPacketPayload {
    public static final int PLAY = 0;
    public static final int PAUSE = 1;
    public static final int RESUME = 2;
    public static final int STOP = 3;

    public static final Type<MusicControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("blockparty", "music_control"));

    public static final StreamCodec<ByteBuf, MusicControlPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, MusicControlPayload::soundId,
                    ByteBufCodecs.VAR_INT, MusicControlPayload::action,
                    MusicControlPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
