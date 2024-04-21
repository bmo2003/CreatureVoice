package com.owlmaddie;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.ChatDataSaverScheduler;
import com.owlmaddie.commands.CreatureChatCommands;
import com.owlmaddie.goals.EntityBehaviorManager;
import com.owlmaddie.goals.GoalPriority;
import com.owlmaddie.goals.TalkPlayerGoal;
import com.owlmaddie.utils.Compression;
import com.owlmaddie.utils.LivingEntityInterface;
import com.owlmaddie.utils.RandomUtils;
import com.owlmaddie.utils.ServerEntityFinder;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The {@code ModInit} class initializes this mod on the server and defines all the server message
 * identifiers. It also listens for messages from the client, and has code to send
 * messages to the client.
 */
public class ModInit implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
	public static MinecraftServer serverInstance;
	private static ChatDataSaverScheduler scheduler = null;
	public static final Identifier PACKET_C2S_GREETING = new Identifier("creaturechat", "packet_c2s_greeting");
	public static final Identifier PACKET_C2S_READ_NEXT = new Identifier("creaturechat", "packet_c2s_read_next");
	public static final Identifier PACKET_C2S_SET_STATUS = new Identifier("creaturechat", "packet_c2s_set_status");
	public static final Identifier PACKET_C2S_START_CHAT = new Identifier("creaturechat", "packet_c2s_start_chat");
	public static final Identifier PACKET_C2S_SEND_CHAT = new Identifier("creaturechat", "packet_c2s_send_chat");
	public static final Identifier PACKET_S2C_MESSAGE = new Identifier("creaturechat", "packet_s2c_message");
	public static final Identifier PACKET_S2C_LOGIN = new Identifier("creaturechat", "packet_s2c_login");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Register server commands
		CreatureChatCommands.register();

		// Handle packet for Greeting
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_GREETING, (server, player, handler, buf, responseSender) -> {
			UUID entityId = UUID.fromString(buf.readString());

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
				if (entity != null) {
					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
					if (chatData.characterSheet.isEmpty()) {
						generate_character(chatData, player, entity);
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
					LOGGER.info("Update read lines to " + lineNumber + " for: " + entity.getType().toString());
					chatData.setLineNumber(lineNumber);
				}
			});
		});

		// Handle packet for setting status of chat bubbles
		ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_SET_STATUS, (server, player, handler, buf, responseSender) -> {
			UUID entityId = UUID.fromString(buf.readString());
			String status_name = buf.readString(32767);

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
				if (entity != null) {
					// Set talk to player goal (prevent entity from walking off)
					TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
					EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
					LOGGER.info("Hiding chat bubble for: " + entity.getType().toString());
					chatData.setStatus(ChatDataManager.ChatStatus.valueOf(status_name));
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
					ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
					if (chatData.characterSheet.isEmpty()) {
						generate_character(chatData, player, entity);
					} else {
						generate_chat(chatData, player, entity, message);
					}
				}
			});
		});

		// Send lite chat data JSON to new player (to populate client data)
		// Data is sent in chunks, to prevent exceeding the 32767 limit per String.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			LOGGER.info("Server send compressed, chunked login message packets to player: " + player.getName().getString());

			// Get lite JSON data & compress to byte array
			String chatDataJSON = ChatDataManager.getServerInstance().GetLightChatData();
			byte[] compressedData = Compression.compressString(chatDataJSON);
			if (compressedData == null) {
				LOGGER.error("Failed to compress chat data.");
				return;
			}

			final int chunkSize = 32000; // Define chunk size
			int totalPackets = (int) Math.ceil((double) compressedData.length / chunkSize);

			// Loop through each chunk of bytes, and send bytes to player
			for (int i = 0; i < totalPackets; i++) {
				int start = i * chunkSize;
				int end = Math.min(compressedData.length, start + chunkSize);

				PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
				buffer.writeInt(i); // Packet sequence number
				buffer.writeInt(totalPackets); // Total number of packets

				// Write chunk as byte array
				byte[] chunk = Arrays.copyOfRange(compressedData, start, end);
				buffer.writeByteArray(chunk);

				ServerPlayNetworking.send(player, PACKET_S2C_LOGIN, buffer);
			}
		});

		ServerWorldEvents.LOAD.register((server, world) -> {
			String world_name = world.getRegistryKey().getValue().getPath();
			if (world_name.equals("overworld")) {
				serverInstance = server;
				ChatDataManager.getServerInstance().loadChatData(server);

				// Start the auto-save task to save every X minutes
				scheduler = new ChatDataSaverScheduler();
				scheduler.startAutoSaveTask(server, 15, TimeUnit.MINUTES);
			}
		});
		ServerWorldEvents.UNLOAD.register((server, world) -> {
			String world_name = world.getRegistryKey().getValue().getPath();
			if (world_name == "overworld") {
				ChatDataManager.getServerInstance().saveChatData(server);
				serverInstance = null;

				// Shutdown auto scheduler
				scheduler.stopAutoSaveTask();
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			String entityUUID = entity.getUuidAsString();
			if (ChatDataManager.getServerInstance().entityChatDataMap.containsKey(entityUUID)) {
				int friendship = ChatDataManager.getServerInstance().entityChatDataMap.get(entityUUID).friendship;
				if (friendship > 0) {
					LOGGER.info("Entity loaded (" + entityUUID + "), setting friendship to " + friendship);
					((LivingEntityInterface)entity).setCanTargetPlayers(false);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			String entityUUID = entity.getUuidAsString();
			if (entity.getRemovalReason() == Entity.RemovalReason.KILLED && ChatDataManager.getServerInstance().entityChatDataMap.containsKey(entityUUID)) {
				LOGGER.info("Entity killed (" + entityUUID + "), removing chat data.");
 				ChatDataManager.getServerInstance().entityChatDataMap.remove(entityUUID);
			}
		});

		LOGGER.info("CreatureChat MOD Initialized!");
	}

	public static void generate_character(ChatDataManager.EntityChatData chatData, ServerPlayerEntity player, MobEntity entity) {
		// Set talk to player goal (prevent entity from walking off)
		TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
		EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

		// Only generate a new greeting if not already doing so
		String player_biome = player.getWorld().getBiome(player.getBlockPos()).getKey().get().getValue().getPath();

		StringBuilder userMessageBuilder = new StringBuilder();
		userMessageBuilder.append("Please generate a new character ");
		if (entity.getCustomName() != null && !entity.getCustomName().getLiteralString().equals("N/A")) {
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

	public static void generate_chat(ChatDataManager.EntityChatData chatData, ServerPlayerEntity player, MobEntity entity, String message) {
		// Set talk to player goal (prevent entity from walking off)
		TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
		EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

		// Add new message
		LOGGER.info("Add new message (" + message + ") to Entity: " + entity.getType().toString());
		chatData.generateMessage(player, "system-chat", message);
	}

	// Send new message to all connected players
	public static void BroadcastPacketMessage(ChatDataManager.EntityChatData chatData) {
		for (ServerWorld world : serverInstance.getWorlds()) {
			UUID entityId = UUID.fromString(chatData.entityId);
			MobEntity entity = ServerEntityFinder.getEntityByUUID(world, entityId);
			if (entity != null) {
				// Set custom name (if null)
				String characterName = chatData.getCharacterProp("name");
				if (!characterName.isEmpty() && !characterName.equals("N/A") && entity.getCustomName() == null) {
					LOGGER.info("Setting entity name to " + characterName + " for " + chatData.entityId);
					entity.setCustomName(Text.literal(characterName));
					entity.setCustomNameVisible(true);
				}

				PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());

				// Write the entity's chat updated data
				buffer.writeString(chatData.entityId);
				buffer.writeString(chatData.playerId);
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