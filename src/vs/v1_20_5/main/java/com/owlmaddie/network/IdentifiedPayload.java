// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A generic payload that wraps an Identifier and a byte array for packet data. This is for
 * Minecraft 1.20.5+ versions, which switched to CustomPayload. This maintains compatibility
 * with the rest of CreatureChat code.
 */
public record IdentifiedPayload(Identifier id, byte[] data) implements CustomPayload {
    public static final CustomPayload.Id<IdentifiedPayload> PACKET_ID =
            new CustomPayload.Id<>(new Identifier("creaturechat", "identified_payload"));

    public static final PacketCodec<RegistryByteBuf, IdentifiedPayload> PACKET_CODEC =
            PacketCodec.tuple(
                    Identifier.PACKET_CODEC, IdentifiedPayload::id,
                    PacketCodecs.BYTE_ARRAY, IdentifiedPayload::data,
                    IdentifiedPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}