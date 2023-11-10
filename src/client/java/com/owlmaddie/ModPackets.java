package com.owlmaddie;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ModPackets {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");

    public static void sendGenerateGreeting(Entity entity) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_GREETING, buf);
    }

    public static void sendUpdateLineNumber(Entity entity, Integer lineNumber) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        buf.writeInt(lineNumber);

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_READ_NEXT, buf);
    }

    public static void sendStartChat(Entity entity) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_START_CHAT, buf);
    }

    public static void sendChat(Entity entity, String message) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        buf.writeString(message);

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_SEND_CHAT, buf);
    }
}

