package com.owlmaddie.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.chat.PlayerData;
import com.owlmaddie.ui.BubbleRenderer;
import com.owlmaddie.ui.PlayerMessageManager;
import com.owlmaddie.utils.ClientEntityFinder;
import com.owlmaddie.utils.Decompression;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code ClientPackets} class provides methods to send packets to/from the server for generating greetings,
 * updating message details, and sending user messages.
 */
public class ClientPackets {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    static HashMap<Integer, byte[]> receivedChunks = new HashMap<>();

    public static void sendGenerateGreeting(Entity entity) {
        // Get user language
        String userLanguageCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        String userLanguageName = MinecraftClient.getInstance().getLanguageManager().getLanguage(userLanguageCode).getDisplayText().getString();

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());
        buf.writeString(userLanguageName);

        // Send C2S packet
        ClientPlayNetworking.send(ServerPackets.PACKET_C2S_GREETING, buf);
    }

    public static void sendUpdateLineNumber(Entity entity, Integer lineNumber) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());
        buf.writeInt(lineNumber);

        // Send C2S packet
        ClientPlayNetworking.send(ServerPackets.PACKET_C2S_READ_NEXT, buf);
    }

    public static void sendOpenChat(Entity entity) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());

        // Send C2S packet
        ClientPlayNetworking.send(ServerPackets.PACKET_C2S_OPEN_CHAT, buf);
    }

    public static void sendCloseChat() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        // Send C2S packet
        ClientPlayNetworking.send(ServerPackets.PACKET_C2S_CLOSE_CHAT, buf);
    }

    public static void setChatStatus(Entity entity, ChatDataManager.ChatStatus new_status) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());
        buf.writeString(new_status.toString());

        // Send C2S packet
        ClientPlayNetworking.send(ServerPackets.PACKET_C2S_SET_STATUS, buf);
    }

    public static void sendChat(Entity entity, String message) {
        // Get user language
        String userLanguageCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        String userLanguageName = MinecraftClient.getInstance().getLanguageManager().getLanguage(userLanguageCode).getDisplayText().getString();

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(entity.getUuidAsString());
        buf.writeString(message);
        buf.writeString(userLanguageName);

        // Send C2S packet
        ClientPlayNetworking.send(ServerPackets.PACKET_C2S_SEND_CHAT, buf);
    }

    // Reading a Map<String, PlayerData> from the buffer
    public static Map<String, PlayerData> readPlayerDataMap(PacketByteBuf buffer) {
        int size = buffer.readInt(); // Read the size of the map
        Map<String, PlayerData> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buffer.readString(); // Read the key (playerName)
            PlayerData data = new PlayerData();
            data.friendship = buffer.readInt(); // Read PlayerData field(s)
            map.put(key, data); // Add to the map
        }
        return map;
    }

    public static void register() {
        // Client-side packet handler, message sync
        ClientPlayNetworking.registerGlobalReceiver(ServerPackets.PACKET_S2C_MESSAGE, (client, handler, buffer, responseSender) -> {
            // Read the data from the server packet
            UUID entityId = UUID.fromString(buffer.readString());
            String sendingPlayerIdStr = buffer.readString(32767);
            String senderPlayerName = buffer.readString(32767);
            UUID senderPlayerId;
            if (!sendingPlayerIdStr.isEmpty()) {
                senderPlayerId = UUID.fromString(sendingPlayerIdStr);
            } else {
                senderPlayerId = null;
            }
            String message = buffer.readString(32767);
            int line = buffer.readInt();
            String status_name = buffer.readString(32767);
            ChatDataManager.ChatStatus status = ChatDataManager.ChatStatus.valueOf(status_name);
            String sender_name = buffer.readString(32767);
            ChatDataManager.ChatSender sender = ChatDataManager.ChatSender.valueOf(sender_name);
            Map<String, PlayerData> players = readPlayerDataMap(buffer);

            // Update the chat data manager on the client-side
            client.execute(() -> { // Make sure to run on the client thread
                // Ensure client.player is initialized
                if (client.player == null || client.world == null) {
                    LOGGER.warn("Client not fully initialized. Dropping message for entity '{}'.", entityId);
                    return;
                }

                // Update the chat data manager on the client-side
                MobEntity entity = ClientEntityFinder.getEntityByUUID(client.world, entityId);
                if (entity == null) {
                    LOGGER.warn("Entity with ID '{}' not found. Skipping message processing.", entityId);
                    return;
                }

                // Get entity chat data for current entity & player
                String currentPlayerName = client.player.getDisplayName().getString();
                ChatDataManager chatDataManager = ChatDataManager.getClientInstance();
                EntityChatData chatData = chatDataManager.getOrCreateChatData(entity.getUuidAsString(), currentPlayerName);

                if (senderPlayerId != null && sender == ChatDataManager.ChatSender.USER && status == ChatDataManager.ChatStatus.DISPLAY) {
                    // Add player message to queue for rendering
                    PlayerMessageManager.addMessage(senderPlayerId, message, senderPlayerName, ChatDataManager.TICKS_TO_DISPLAY_USER_MESSAGE);
                    chatData.status = ChatDataManager.ChatStatus.PENDING;

                } else {
                    // Add entity message
                    if (!message.isEmpty()) {
                        chatData.currentMessage = message;
                    }
                    chatData.currentLineNumber = line;
                    chatData.status = status;
                    chatData.sender = sender;
                    chatData.players = players; // friendships
                }

                // Play sound with volume based on distance (from player or entity)
                playNearbyUISound(client, entity, 0.2f);
            });
        });

        // Client-side player login: get all chat data
        ClientPlayNetworking.registerGlobalReceiver(ServerPackets.PACKET_S2C_LOGIN, (client, handler, buffer, responseSender) -> {
            int sequenceNumber = buffer.readInt(); // Sequence number of the current packet
            int totalPackets = buffer.readInt(); // Total number of packets for this data
            byte[] chunk = buffer.readByteArray(); // Read the byte array chunk from the current packet

            client.execute(() -> { // Make sure to run on the client thread
                // Store the received chunk
                receivedChunks.put(sequenceNumber, chunk);

                // Check if all chunks have been received
                if (receivedChunks.size() == totalPackets) {
                    LOGGER.info("Reassemble chunks on client and decompress lite JSON data string");

                    // Combine all byte array chunks
                    ByteArrayOutputStream combined = new ByteArrayOutputStream();
                    for (int i = 0; i < totalPackets; i++) {
                        combined.write(receivedChunks.get(i), 0, receivedChunks.get(i).length);
                    }

                    // Decompress the combined byte array to get the original JSON string
                    String chatDataJSON = Decompression.decompressString(combined.toByteArray());
                    if (chatDataJSON == null || chatDataJSON.isEmpty()) {
                        LOGGER.warn("Received invalid or empty chat data JSON. Skipping processing.");
                        return;
                    }

                    // Parse JSON and update client chat data
                    Gson GSON = new Gson();
                    Type type = new TypeToken<ConcurrentHashMap<String, EntityChatData>>(){}.getType();
                    ChatDataManager.getClientInstance().entityChatDataMap = GSON.fromJson(chatDataJSON, type);

                    // Clear receivedChunks for future use
                    receivedChunks.clear();
                }
            });
        });

        // Client-side packet handler, receive entire whitelist / blacklist, and update BubbleRenderer
        ClientPlayNetworking.registerGlobalReceiver(ServerPackets.PACKET_S2C_WHITELIST, (client, handler, buffer, responseSender) -> {
            // Read the whitelist data from the buffer
            int whitelistSize = buffer.readInt();
            List<String> whitelist = new ArrayList<>(whitelistSize);
            for (int i = 0; i < whitelistSize; i++) {
                whitelist.add(buffer.readString(32767));
            }

            // Read the blacklist data from the buffer
            int blacklistSize = buffer.readInt();
            List<String> blacklist = new ArrayList<>(blacklistSize);
            for (int i = 0; i < blacklistSize; i++) {
                blacklist.add(buffer.readString(32767));
            }

            client.execute(() -> {
                BubbleRenderer.whitelist = whitelist;
                BubbleRenderer.blacklist = blacklist;
            });
        });

        // Client-side packet handler, player status sync
        ClientPlayNetworking.registerGlobalReceiver(ServerPackets.PACKET_S2C_PLAYER_STATUS, (client, handler, buffer, responseSender) -> {
            // Read the data from the server packet
            UUID playerId = UUID.fromString(buffer.readString());
            boolean isChatOpen = buffer.readBoolean();

            // Get player instance
            PlayerEntity player = ClientEntityFinder.getPlayerEntityFromUUID(playerId);

            // Update the player status data manager on the client-side
            client.execute(() -> {
                if (player == null) {
                    LOGGER.warn("Player entity is null. Skipping status update.");
                    return;
                }

                if (isChatOpen) {
                    PlayerMessageManager.openChatUI(playerId);
                    playNearbyUISound(client, player, 0.2f);
                } else {
                    PlayerMessageManager.closeChatUI(playerId);
                }
            });
        });
    }

    private static void playNearbyUISound(MinecraftClient client, Entity player, float maxVolume) {
        // Play sound with volume based on distance
        int distance_squared = 144;
        if (client.player != null) {
            double distance = client.player.squaredDistanceTo(player.getX(), player.getY(), player.getZ());
            if (distance <= distance_squared) {
                // Decrease volume based on distance
                float volume = maxVolume - (float)distance / distance_squared * maxVolume;
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), volume, 0.8F);
            }
        }
    }
}

