// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC – unauthorized use prohibited
package com.owlmaddie.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is used to register and send packets between server and client, modified
 * to support Minecraft 1.20.5+ compatability. Message packets now require custom payloads,
 * and changed quite a bit.
 */
public final class PacketHelper {

    private PacketHelper() {}

    // Legacy functional interface (matches old PlayChannelHandler)
    @FunctionalInterface
    public interface TriConsumer<T, U, V> { void accept(T t, U u, V v); }

    // Id + codec cache (one per logical channel)
    private static final Map<Identifier, CustomPayload.Id<LegacyPayload>> IDS =
            new ConcurrentHashMap<>();

    // Obtain (and lazily register) the payload id for this channel
    private static CustomPayload.Id<LegacyPayload> idOf(Identifier ch) {
        return IDS.computeIfAbsent(ch, key -> {
            var pid   = LegacyPayload.idFor(key);
            var codec = LegacyPayload.codec(pid);

            /*  Register once per logical side – ignore duplicates.  */
            try { PayloadTypeRegistry.playC2S().register(pid, codec); }
            catch (IllegalArgumentException ignored) {}
            try { PayloadTypeRegistry.playS2C().register(pid, codec); }
            catch (IllegalArgumentException ignored) {}

            return pid;
        });
    }

    // Send helpers
    public static void send(ServerPlayerEntity player,
                            Identifier channel,
                            PacketByteBuf buf) {
        ServerPlayNetworking.send(player, new LegacyPayload(idOf(channel), buf));
    }

    // Receive helper
    public static void registerReceiver(Identifier channel,
                                        TriConsumer<MinecraftServer,
                                                ServerPlayerEntity,
                                                PacketByteBuf> handler) {

        ServerPlayNetworking.registerGlobalReceiver(idOf(channel),
                (LegacyPayload p, ServerPlayNetworking.Context ctx) -> handler.accept(
                        ctx.player().getServer(),
                        ctx.player(),
                        p.data()
                ));
    }
}
