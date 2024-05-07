package com.owlmaddie.network;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.ChatDataSaverScheduler;
import com.owlmaddie.goals.EntityBehaviorManager;
import com.owlmaddie.goals.GoalPriority;
import com.owlmaddie.goals.TalkPlayerGoal;
import com.owlmaddie.utils.Compression;
import com.owlmaddie.utils.LivingEntityInterface;
import com.owlmaddie.utils.Randomizer;
import com.owlmaddie.utils.ServerEntityFinder;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The {@code ServerPackets} class provides methods to send packets to/from the client for generating greetings,
 * updating message details, and sending user messages.
 */
public class ServerPackets {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    public static MinecraftServer serverInstance;
    private static ChatDataSaverScheduler scheduler = null;
    public static final Identifier PACKET_C2S_GREETING = new Identifier("creaturechat", "packet_c2s_greeting");
    public static final Identifier PACKET_C2S_READ_NEXT = new Identifier("creaturechat", "packet_c2s_read_next");
    public static final Identifier PACKET_C2S_SET_STATUS = new Identifier("creaturechat", "packet_c2s_set_status");
    public static final Identifier PACKET_C2S_OPEN_CHAT = new Identifier("creaturechat", "packet_c2s_open_chat");
    public static final Identifier PACKET_C2S_CLOSE_CHAT = new Identifier("creaturechat", "packet_c2s_close_chat");
    public static final Identifier PACKET_C2S_SEND_CHAT = new Identifier("creaturechat", "packet_c2s_send_chat");
    public static final Identifier PACKET_S2C_MESSAGE = new Identifier("creaturechat", "packet_s2c_message");
    public static final Identifier PACKET_S2C_LOGIN = new Identifier("creaturechat", "packet_s2c_login");
    public static final Identifier PACKET_S2C_PLAYER_STATUS = new Identifier("creaturechat", "packet_s2c_player_status");

    public static void register() {
        // Handle packet for Greeting
        ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_GREETING, (server, player, handler, buf, responseSender) -> {
            UUID entityId = UUID.fromString(buf.readString());
            String userLanguage = buf.readString(32767);

            // Ensure that the task is synced with the server thread
            server.execute(() -> {
                MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
                if (entity != null) {
                    ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
                    if (chatData.characterSheet.isEmpty()) {
                        generate_character(userLanguage, chatData, player, entity);
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
                    LOGGER.debug("Update read lines to " + lineNumber + " for: " + entity.getType().toString());
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
                    LOGGER.debug("Hiding chat bubble for: " + entity.getType().toString());
                    chatData.setStatus(ChatDataManager.ChatStatus.valueOf(status_name));
                }
            });
        });

        // Handle packet for Open Chat
        ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_OPEN_CHAT, (server, player, handler, buf, responseSender) -> {
            UUID entityId = UUID.fromString(buf.readString());

            // Ensure that the task is synced with the server thread
            server.execute(() -> {
                MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
                if (entity != null) {
                    // Set talk to player goal (prevent entity from walking off)
                    TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 7F);
                    EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);
                }

                // Sync player UI status to all clients
                BroadcastPlayerStatus(player, true);
            });
        });

        // Handle packet for Close Chat
        ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_CLOSE_CHAT, (server, player, handler, buf, responseSender) -> {

            server.execute(() -> {
                // Sync player UI status to all clients
                BroadcastPlayerStatus(player, false);
            });
        });

        // Handle packet for new chat message
        ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_SEND_CHAT, (server, player, handler, buf, responseSender) -> {
            UUID entityId = UUID.fromString(buf.readString());
            String message = buf.readString(32767);
            String userLanguage = buf.readString(32767);

            // Ensure that the task is synced with the server thread
            server.execute(() -> {
                MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
                if (entity != null) {
                    ChatDataManager.EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
                    if (chatData.characterSheet.isEmpty()) {
                        generate_character(userLanguage, chatData, player, entity);
                    } else {
                        generate_chat(userLanguage, chatData, player, entity, message, false);
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

    }

    public static void generate_character(String userLanguage, ChatDataManager.EntityChatData chatData, ServerPlayerEntity player, MobEntity entity) {
        // Set talk to player goal (prevent entity from walking off)
        TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
        EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

        // Grab random adjective
        String randomAdjective = Randomizer.getRandomMessage(Randomizer.RandomType.ADJECTIVE);
        String randomFrequency = Randomizer.getRandomMessage(Randomizer.RandomType.FREQUENCY);

        StringBuilder userMessageBuilder = new StringBuilder();
        userMessageBuilder.append("Please generate a " + randomFrequency + " " + randomAdjective);
        userMessageBuilder.append(" character ");
        if (entity.getCustomName() != null && !entity.getCustomName().getString().equals("N/A")) {
            userMessageBuilder.append("named '").append(entity.getCustomName().getString()).append("' ");
        } else {
            userMessageBuilder.append("whose name starts with the letter '").append(Randomizer.RandomLetter()).append("' ");
            userMessageBuilder.append("and uses ").append(Randomizer.RandomNumber(4) + 1).append(" syllables ");
        }
        userMessageBuilder.append("and speaks in '" + userLanguage + "'" );
        LOGGER.info(userMessageBuilder.toString());

        chatData.generateMessage(userLanguage, player, "system-character", userMessageBuilder.toString(), false);
    }

    public static void generate_chat(String userLanguage, ChatDataManager.EntityChatData chatData, ServerPlayerEntity player, MobEntity entity, String message, boolean is_auto_message) {
        // Set talk to player goal (prevent entity from walking off)
        TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
        EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

        // Add new message
        LOGGER.info("Player message received: " + message + " | Entity: " + entity.getType().toString());
        chatData.generateMessage(userLanguage, player, "system-chat", message, is_auto_message);
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
                    LOGGER.debug("Setting entity name to " + characterName + " for " + chatData.entityId);
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
                    LOGGER.debug("Server broadcast message to client: " + player.getName().getString() + " | Message: " + chatData.currentMessage);
                    ServerPlayNetworking.send(player, PACKET_S2C_MESSAGE, buffer);
                }
                break;
            }
        }
    }

    // Send new message to all connected players
    public static void BroadcastPlayerStatus(PlayerEntity player, boolean isChatOpen) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());

        // Write the entity's chat updated data
        buffer.writeString(player.getUuidAsString());
        buffer.writeBoolean(isChatOpen);

        // Iterate over all players and send the packet
        for (ServerPlayerEntity serverPlayer : serverInstance.getPlayerManager().getPlayerList()) {
            LOGGER.debug("Server broadcast " + player.getName().getString() + " player status to client: " + serverPlayer.getName().getString() + " | isChatOpen: " + isChatOpen);
            ServerPlayNetworking.send(serverPlayer, PACKET_S2C_PLAYER_STATUS, buffer);
        }
    }

    // Send a chat message to a player which is clickable (for error messages with a link for help)
    public static void SendClickableError(PlayerEntity player, String message, String url) {
        MutableText text = Text.literal(message)
                .formatted(Formatting.BLUE)
                .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withUnderline(true));
        player.sendMessage(text, false);
    }
}
