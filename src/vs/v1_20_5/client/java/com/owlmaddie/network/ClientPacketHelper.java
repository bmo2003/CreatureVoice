// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.20.5+ wrapper to receive and send messagess between client and server.
 */
public final class ClientPacketHelper {

    private ClientPacketHelper() {}   // no instantiation

    // Id-and-codec cache
    private static final Map<Identifier, CustomPayload.Id<LegacyPayload>> IDS =
            new ConcurrentHashMap<>();

    // obtain (and lazily register) the payload id for this channel
    private static CustomPayload.Id<LegacyPayload> idOf(Identifier ch) {
        return IDS.computeIfAbsent(ch, key -> {
            var pid   = LegacyPayload.idFor(key);
            var codec = LegacyPayload.codec(pid);

            /* register C2S – ignore if it’s been done already */
            try { PayloadTypeRegistry.playC2S().register(pid, codec);
            } catch (IllegalArgumentException ignored) { /* duplicate, fine */ }

            /* register S2C – same guard */
            try { PayloadTypeRegistry.playS2C().register(pid, codec);
            } catch (IllegalArgumentException ignored) { /* duplicate, fine */ }

            return pid;
        });
    }

    // Functional interfaces matching the old PlayChannelHandler
    @FunctionalInterface
    public interface ClientHandler {
        void receive(MinecraftClient client,
                     ClientPlayNetworkHandler handler,
                     PacketByteBuf buffer,
                     PacketSender responseSender);
    }

    // Send helpers
    public static void send(Identifier channel, PacketByteBuf buf) {
        ClientPlayNetworking.send(new LegacyPayload(idOf(channel), buf));
    }

    // Receive helpers
    public static void registerReceiver(Identifier channel, ClientHandler h) {
        ClientPlayNetworking.registerGlobalReceiver(idOf(channel),
                (LegacyPayload p, ClientPlayNetworking.Context ctx) -> {
                    ctx.client().execute(() -> h.receive(
                            ctx.client(),
                            ctx.client().getNetworkHandler(),
                            p.data(),
                            ctx.responseSender()));
                });
    }
}