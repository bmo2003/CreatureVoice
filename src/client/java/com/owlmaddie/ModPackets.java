package com.owlmaddie;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ModPackets {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");

    public static void sendEntityClickPacket(Entity entity) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());

        // Send C2S packet
        ClientPlayNetworking.send(ExampleMod.PACKET_CLIENT_CLICK, buf);
    }
}

