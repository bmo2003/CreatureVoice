package com.owlmaddie;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.goals.EntityBehaviorManager;
import com.owlmaddie.goals.GoalPriority;
import com.owlmaddie.goals.TalkPlayerGoal;
import com.owlmaddie.utils.RandomUtils;
import com.owlmaddie.utils.ServerEntityFinder;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.UUID;

/**
 * The {@code ModInit} class initializes this mod on the server and defines all the server message
 * identifiers. It also listens for messages from the client, and has code to send
 * messages to the client.
 */
public class ModInit implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
	public static MinecraftServer serverInstance;
	public static final Identifier PACKET_C2S_GREETING = new Identifier("mobgpt", "packet_c2s_greeting");
	public static final Identifier PACKET_C2S_READ_NEXT = new Identifier("mobgpt", "packet_c2s_read_next");
	public static final Identifier PACKET_C2S_START_CHAT = new Identifier("mobgpt", "packet_c2s_start_chat");
	public static final Identifier PACKET_C2S_SEND_CHAT = new Identifier("mobgpt", "packet_c2s_send_chat");
	public static final Identifier PACKET_S2C_MESSAGE = new Identifier("mobgpt", "packet_s2c_message");
	public static final Identifier PACKET_S2C_LOGIN = new Identifier("mobgpt", "packet_s2c_login");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Handle packet for Greeting
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_GREETING, (server, player, handler, buf, responseSender) -> {
			UUID entityId = UUID.fromString(buf.readString());

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
				if (entity != null) {
					// Set talk to player goal (prevent entity from walking off)
					TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
					EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
					if (chatData.status == ChatDataManager.ChatStatus.NONE ||
							chatData.status == ChatDataManager.ChatStatus.END) {
						// Only generate a new greeting if not already doing so
						String player_biome = player.getWorld().getBiome(player.getBlockPos()).getKey().get().getValue().getPath();

						StringBuilder userMessageBuilder = new StringBuilder();
						userMessageBuilder.append("Please generate a new character ");
						if (entity.getCustomName() != null) {
							userMessageBuilder.append("named '").append(entity.getCustomName().getLiteralString()).append("' ");
						} else {
							userMessageBuilder.append("whose name starts with the letter '").append(RandomUtils.RandomLetter()).append("' ");
							userMessageBuilder.append("and which uses ").append(RandomUtils.RandomNumber(4) + 1).append(" syllables ");
						}
						userMessageBuilder.append("of type '").append(entity.getType().getUntranslatedName().toLowerCase(Locale.ROOT)).append("' ");
						userMessageBuilder.append("who lives near the ").append(player_biome).append(".");
						LOGGER.info(userMessageBuilder.toString());

						chatData.generateMessage(player, "system-character", userMessageBuilder.toString());
					}
				}
			});
		});

		// Handle packet for reading lines of message
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_READ_NEXT, (server, player, handler, buf, responseSender) -> {
			UUID entityId = UUID.fromString(buf.readString());
			int lineNumber = buf.readInt();

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
				if (entity != null) {
					// Set talk to player goal (prevent entity from walking off)
					TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
					EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
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
			UUID entityId = UUID.fromString(buf.readString());

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
				if (entity != null) {
					// Set talk to player goal (prevent entity from walking off)
					TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 7F);
					EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);
				}
			});
		});

		// Handle packet for new chat message
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_SEND_CHAT, (server, player, handler, buf, responseSender) -> {
			UUID entityId = UUID.fromString(buf.readString());
			String message = buf.readString(32767);

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
				if (entity != null) {
					// Set talk to player goal (prevent entity from walking off)
					TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
					EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
					if (chatData.status == ChatDataManager.ChatStatus.END) {
						// Add new message
						LOGGER.info("Add new message (" + message + ") to Entity: " + entity.getType().toString());
						chatData.generateMessage(player, "system-chat", message);
					}
				}
			});
		});

		// Send lite chat data JSON to new player (to populate client data)
		// Data is sent in chunks, to prevent exceeding the 32767 limit per String.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			LOGGER.info("Server send login message packet to player: " + player.getName().getString());

			String chatDataJSON = ChatDataManager.getServerInstance().GetLightChatData();
			int chunkSize = 32000; // Slightly below the limit to account for any additional data

			// Calculate the number of required packets to send the entire JSON string
			int totalPackets = (int) Math.ceil(chatDataJSON.length() / (double) chunkSize);

			for (int i = 0; i < totalPackets; i++) {
				int start = i * chunkSize;
				int end = Math.min(start + chunkSize, chatDataJSON.length());
				String chunk = chatDataJSON.substring(start, end);

				PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
				buffer.writeInt(i); // Packet sequence number
				buffer.writeInt(totalPackets); // Total number of packets
				buffer.writeString(chunk);

				ServerPlayNetworking.send(player, PACKET_S2C_LOGIN, buffer);
			}
		});

		ServerWorldEvents.LOAD.register((server, world) -> {
			String world_name = world.getRegistryKey().getValue().getPath();
			if (world_name == "overworld") {
				serverInstance = server;
				ChatDataManager.getServerInstance().loadChatData(server);
			}
		});
		ServerWorldEvents.UNLOAD.register((server, world) -> {
			String world_name = world.getRegistryKey().getValue().getPath();
			if (world_name == "overworld") {
				ChatDataManager.getServerInstance().saveChatData(server);
				serverInstance = null;
			}
		});

		LOGGER.info("MobGPT Initialized!");
	}

	// Send new message to all connected players
	public static void BroadcastPacketMessage(ChatDataManager.EntityChatData chatData) {
		for (ServerWorld world : serverInstance.getWorlds()) {
			UUID entityId = UUID.fromString(chatData.entityId);
			MobEntity entity = ServerEntityFinder.getEntityByUUID(world, entityId);
			if (entity != null) {
				// Set custom name (if none)
				if (entity.getCustomName() == null && chatData.status != ChatDataManager.ChatStatus.PENDING) {
					String characterName = chatData.getCharacterProp("name");
					LOGGER.info("Setting entity name to " + characterName + " for " + chatData.entityId);
					entity.setCustomName(Text.literal(characterName));
					entity.setCustomNameVisible(true);
				}

				PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());

				// Write the entity's chat updated data
				buffer.writeString(entity.getUuidAsString());
				buffer.writeString(chatData.currentMessage);
				buffer.writeInt(chatData.currentLineNumber);
				buffer.writeString(chatData.status.toString());
				buffer.writeString(chatData.sender.toString());
				buffer.writeInt(chatData.friendship);

				// Iterate over all players and send the packet
				for (ServerPlayerEntity player : serverInstance.getPlayerManager().getPlayerList()) {
					LOGGER.info("Server send message packet to player: " + player.getName().getString());
					ServerPlayNetworking.send(player, PACKET_S2C_MESSAGE, buffer);
				}
				break;
			}
		}
	}
}