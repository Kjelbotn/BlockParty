package no.bangerfabrikken.blockparty.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;

/** Helpers for one-sided, correctly oriented in-world Text Display holograms. */
public final class BlockPartyText {
    private BlockPartyText() { }

    /**
     * Spawns a fixed-facing Minecraft Text Display. Unlike block-built pixel text, the
     * display is rendered by Minecraft's text renderer and cannot be mirrored by block order.
     * Yaw 0 faces south (+Z), yaw 180 faces north (-Z), yaw 90 west (-X), yaw -90 east (+X).
     */
    public static Display.TextDisplay spawnTextDisplay(ServerLevel level, double x, double y, double z,
                                                        float yaw, Component text, float scale, String tag) {
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) return null;

        CompoundTag data = new CompoundTag();
        data.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        data.putString("billboard", "fixed");
        data.putString("alignment", "center");
        data.putBoolean("shadow", true);
        data.putBoolean("see_through", false);
        data.putBoolean("default_background", false);
        data.putInt("background", 0);
        data.putInt("line_width", 1600);
        data.putByte("text_opacity", (byte) 0xFF);

        CompoundTag transform = new CompoundTag();
        transform.put("translation", floats(0, 0, 0));
        transform.put("left_rotation", floats(0, 0, 0, 1));
        transform.put("right_rotation", floats(0, 0, 0, 1));
        transform.put("scale", floats(scale, scale, scale));
        data.put("transformation", transform);

        display.load(data);
        display.moveTo(x, y, z, yaw, 0.0F);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        if (tag != null && !tag.isBlank()) display.addTag(tag);
        level.addFreshEntity(display);
        return display;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }
}
