// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload for messages used by Minecraft 1.20.5+ compatibility. Previous versions did
 * not need a Payload.
 */
public record LegacyPayload(CustomPayload.Id<LegacyPayload> id, PacketByteBuf data)
        implements CustomPayload {

    @Override
    public CustomPayload.Id<LegacyPayload> getId() {
        return id;
    }

    /* turn a logical channel into a payload id */
    public static CustomPayload.Id<LegacyPayload> idFor(Identifier chan) {
        return new CustomPayload.Id<>(chan);
    }

    // Codec that drains the buffer when decoding, fixing the “extra bytes” kick.
    public static PacketCodec<RegistryByteBuf, LegacyPayload> codec(
            CustomPayload.Id<LegacyPayload> pid) {

        return PacketCodec.ofStatic(
                // encoder (two-arg, returns void)
                (RegistryByteBuf buf, LegacyPayload p) ->
                        buf.writeBytes(p.data()),

                // decoder (one-arg, returns value)
                (RegistryByteBuf buf) -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes); // consume all bytes
                    return new LegacyPayload(pid,
                            new PacketByteBuf(Unpooled.wrappedBuffer(bytes)));
                });
    }
}
