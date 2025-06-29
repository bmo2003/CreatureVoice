// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * This class is used to register and send packets between server and client
 */
public class PacketHelper {

    // Custom functional interface for three parameters
    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    // Method to send a packet
    public static void send(ServerPlayerEntity player, Identifier identifier, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, identifier, buf);
    }

    // Updated registerReceiver method using TriConsumer
    public static void registerReceiver(Identifier identifier, TriConsumer<MinecraftServer, ServerPlayerEntity, PacketByteBuf> handler) {
        ServerPlayNetworking.registerGlobalReceiver(identifier, (server, player, netHandler, buf, responseSender) -> {
            handler.accept(server, player, buf);
        });
    }
}