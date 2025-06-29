// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC – unauthorized use prohibited
package com.owlmaddie.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 1.20.4-compatible wrapper around Fabric’s networking helpers.
 * Only the methods used by CreatureChat today are exposed;
 * add or adjust when you upgrade to 1.20.5+.
 */
public final class ClientPacketHelper {

    private ClientPacketHelper() {}  // no instances

    /* ---------- CLIENT → SERVER ---------- */

    /**
     * Send a packet from the client to the server.
     */
    public static void send(Identifier channelId, PacketByteBuf buf) {
        ClientPlayNetworking.send(channelId, buf);
    }

    /**
     * Register a global receiver on the client (S2C).
     */
    public static void registerReceiver(
            Identifier channelId,
            ClientPlayNetworking.PlayChannelHandler handler) {
        ClientPlayNetworking.registerGlobalReceiver(channelId, handler);
    }

    /* ---------- SERVER → CLIENT ---------- */

    /**
     * Send a packet from the server to a specific client.
     */
    public static void send(ServerPlayerEntity player, Identifier channelId, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, channelId, buf);
    }

    /**
     * Register a global receiver on the server (C2S).
     */
    public static void registerReceiver(
            Identifier channelId,
            ServerPlayNetworking.PlayChannelHandler handler) {
        ServerPlayNetworking.registerGlobalReceiver(channelId, handler);
    }
}
