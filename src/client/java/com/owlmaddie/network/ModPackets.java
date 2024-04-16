package com.owlmaddie.network;

import com.owlmaddie.ModInit;
import com.owlmaddie.chat.ChatDataManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;

/**
 * The {@code ModPackets} class provides methods to send packets to the server for generating greetings,
 * updating message details, and sending user messages.
 */
public class ModPackets {

    public static void sendGenerateGreeting(Entity entity) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_GREETING, buf);
    }

    public static void sendUpdateLineNumber(Entity entity, Integer lineNumber) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());
        buf.writeInt(lineNumber);

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_READ_NEXT, buf);
    }

    public static void sendStartChat(Entity entity) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_START_CHAT, buf);
    }

    public static void setChatStatus(Entity entity, ChatDataManager.ChatStatus new_status) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());
        buf.writeString(new_status.toString());

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_SET_STATUS, buf);
    }

    public static void sendChat(Entity entity, String message) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());
        buf.writeString(message);

        // Send C2S packet
        ClientPlayNetworking.send(ModInit.PACKET_C2S_SEND_CHAT, buf);
    }
}

