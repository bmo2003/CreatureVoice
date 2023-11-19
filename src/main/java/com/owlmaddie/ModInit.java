package com.owlmaddie;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ModInit implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
	public static MinecraftServer serverInstance;
	public static final Identifier PACKET_C2S_GREETING = new Identifier("mobgpt", "packet_c2s_greeting");
	public static final Identifier PACKET_C2S_READ_NEXT = new Identifier("mobgpt", "packet_c2s_read_next");
	public static final Identifier PACKET_C2S_START_CHAT = new Identifier("mobgpt", "packet_c2s_start_chat");
	public static final Identifier PACKET_C2S_SEND_CHAT = new Identifier("mobgpt", "packet_c2s_send_chat");
	public static final Identifier PACKET_S2C_MESSAGE = new Identifier("mobgpt", "packet_s2c_message");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Handle packet for Greeting
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_GREETING, (server, player, handler, buf, responseSender) -> {
			int entityId = buf.readInt();

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				Entity entity = player.getServerWorld().getEntityById(entityId);
				if (entity != null) {
					// Slow entity
					SlowEntity((LivingEntity) entity, 3.5F);

					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entityId);
					if (chatData.status == ChatDataManager.ChatStatus.NONE ||
							chatData.status == ChatDataManager.ChatStatus.END) {
						// Only generate a new greeting if not already doing so
						LOGGER.info("Generate greeting for: " + entity.getType().toString());
						chatData.generateMessage(player, "Hello!");
					}
				}
			});
		});

		// Handle packet for reading lines of message
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_READ_NEXT, (server, player, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			int lineNumber = buf.readInt();

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				Entity entity = player.getServerWorld().getEntityById(entityId);
				if (entity != null) {
					// Slow entity
					SlowEntity((LivingEntity) entity, 3.5F);

					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entityId);
					if (chatData.status == ChatDataManager.ChatStatus.DISPLAY) {
						// Only set line number if status allows
						LOGGER.info("Increment read lines to " + lineNumber + " for: " + entity.getType().toString());
						chatData.setLineNumber(lineNumber);
					}
				}
			});
		});

		// Handle packet for Start Chat
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_START_CHAT, (server, player, handler, buf, responseSender) -> {
			int entityId = buf.readInt();

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				Entity entity = player.getServerWorld().getEntityById(entityId);
				if (entity != null) {
					// Slow entity, so it does NOT walk away during player typing
					SlowEntity((LivingEntity) entity, 7F);
				}
			});
		});

		// Handle packet for new chat message
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_SEND_CHAT, (server, player, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			String message = buf.readString(32767);

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				Entity entity = player.getServerWorld().getEntityById(entityId);
				if (entity != null) {
					// Slow entity
					SlowEntity((LivingEntity) entity, 3.5F);

					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entityId);
					if (chatData.status == ChatDataManager.ChatStatus.END) {
						// Add new message
						LOGGER.info("Add new message (" + message + ") to Entity: " + entity.getType().toString());
						chatData.generateMessage(player, message);
					}
				}
			});
		});

		ServerWorldEvents.LOAD.register((server, world) -> {
			// Load chat data...
			LOGGER.info("LOAD chat data from NBT: " + world.getRegistryKey().getValue());
			serverInstance = server;
		});
		ServerWorldEvents.UNLOAD.register((server, world) -> {
			// Save chat data...
			LOGGER.info("SAVE chat data to NBT: " + world.getRegistryKey().getValue());
			serverInstance = null;
		});

		LOGGER.info("MobGPT Initialized!");
	}

	// Send new message to all connected players
	public static void BroadcastPacketMessage(ChatDataManager.EntityChatData chatData) {
		for (ServerWorld world : serverInstance.getWorlds()) {
			Entity entity = world.getEntityById(chatData.entityId);
			if (entity != null) {
				PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());

				// Write the entity's chat updated data
				buffer.writeInt(entity.getId());
				buffer.writeString(chatData.currentMessage);
				buffer.writeInt(chatData.currentLineNumber);
				buffer.writeString(chatData.status.toString());
				buffer.writeString(chatData.sender.toString());

				// Iterate over all players and send the packet
				for (ServerPlayerEntity player : serverInstance.getPlayerManager().getPlayerList()) {
					LOGGER.info("Server send message packet to player: " + player.getName().getString());
					ServerPlayNetworking.send(player, PACKET_S2C_MESSAGE, buffer);
				}
				break;
			}
		}
	}

	public void SlowEntity(LivingEntity entity, float numSeconds) {
		// Slow the entity temporarily (so they don't run away)
		// Apply a slowness effect with a high amplifier for a short duration
		// (Amplifier value must be between 0 and 127)
		LOGGER.info("Apply SLOWNESS status effect to: " + entity.getType().toString());
		float TPS = 20F; // ticks per second
		StatusEffectInstance slowness = new StatusEffectInstance(StatusEffects.SLOWNESS, Math.round(numSeconds * TPS),
				127, false, false);
		entity.addStatusEffect(slowness);
	}
}