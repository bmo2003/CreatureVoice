// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for messages used by Minecraft 1.20.5+ compatibility. Previous versions did
 * not need a Payload.
 */
public record LegacyPayload(CustomPacketPayload.Type<LegacyPayload> id, FriendlyByteBuf data)
        implements CustomPacketPayload {

    @Override
    public CustomPacketPayload.Type<LegacyPayload> type() {
        return id;
    }

    /* turn a logical channel into a payload id */
    public static CustomPacketPayload.Type<LegacyPayload> idFor(ResourceLocation chan) {
        return new CustomPacketPayload.Type<>(chan);
    }

    // Codec that drains the buffer when decoding, fixing the “extra bytes” kick.
    public static StreamCodec<RegistryFriendlyByteBuf, LegacyPayload> codec(
            CustomPacketPayload.Type<LegacyPayload> pid) {

        return StreamCodec.of(
                // encoder (two-arg, returns void)
                (RegistryFriendlyByteBuf buf, LegacyPayload p) ->
                        buf.writeBytes(p.data()),

                // decoder (one-arg, returns value)
                (RegistryFriendlyByteBuf buf) -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes); // consume all bytes
                    return new LegacyPayload(pid,
                            new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
                });
    }
}
