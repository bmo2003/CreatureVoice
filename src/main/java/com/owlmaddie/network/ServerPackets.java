package com.owlmaddie.network;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.ChatDataSaverScheduler;
import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.chat.PlayerData;
import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.goals.EntityBehaviorManager;
import com.owlmaddie.goals.GoalPriority;
import com.owlmaddie.goals.TalkPlayerGoal;
import com.owlmaddie.particle.LeadParticleEffect;
import com.owlmaddie.utils.Compression;
import com.owlmaddie.utils.Randomizer;
import com.owlmaddie.utils.ServerEntityFinder;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The {@code ServerPackets} class provides methods to send packets to/from the client for generating greetings,
 * updating message details, and sending user messages.
 */
public class ServerPackets {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    public static MinecraftServer serverInstance;
    public static ChatDataSaverScheduler scheduler = null;
    public static final Identifier PACKET_C2S_GREETING = new Identifier("creaturechat", "packet_c2s_greeting");
    public static final Identifier PACKET_C2S_READ_NEXT = new Identifier("creaturechat", "packet_c2s_read_next");
    public static final Identifier PACKET_C2S_SET_STATUS = new Identifier("creaturechat", "packet_c2s_set_status");
    public static final Identifier PACKET_C2S_OPEN_CHAT = new Identifier("creaturechat", "packet_c2s_open_chat");
    public static final Identifier PACKET_C2S_CLOSE_CHAT = new Identifier("creaturechat", "packet_c2s_close_chat");
    public static final Identifier PACKET_C2S_SEND_CHAT = new Identifier("creaturechat", "packet_c2s_send_chat");
    public static final Identifier PACKET_S2C_ENTITY_MESSAGE = new Identifier("creaturechat", "packet_s2c_entity_message");
    public static final Identifier PACKET_S2C_PLAYER_MESSAGE = new Identifier("creaturechat", "packet_s2c_player_message");
    public static final Identifier PACKET_S2C_LOGIN = new Identifier("creaturechat", "packet_s2c_login");
    public static final Identifier PACKET_S2C_WHITELIST = new Identifier("creaturechat", "packet_s2c_whitelist");
    public static final Identifier PACKET_S2C_PLAYER_STATUS = new Identifier("creaturechat", "packet_s2c_player_status");
    public static final DefaultParticleType HEART_SMALL_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType HEART_BIG_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType FIRE_SMALL_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType FIRE_BIG_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType ATTACK_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType FLEE_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType FOLLOW_FRIEND_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType FOLLOW_ENEMY_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType PROTECT_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType LEAD_FRIEND_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType LEAD_ENEMY_PARTICLE = FabricParticleTypes.simple();
    public static final ParticleType<LeadParticleEffect> LEAD_PARTICLE = FabricParticleTypes.complex(LeadParticleEffect.DESERIALIZER);

    public static void register() {
        // Register custom particles
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "heart_small"), HEART_SMALL_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "heart_big"), HEART_BIG_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "fire_small"), FIRE_SMALL_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "fire_big"), FIRE_BIG_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "attack"), ATTACK_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "flee"), FLEE_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "follow_enemy"), FOLLOW_ENEMY_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "follow_friend"), FOLLOW_FRIEND_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "protect"), PROTECT_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "lead_enemy"), LEAD_ENEMY_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "lead_friend"), LEAD_FRIEND_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("creaturechat", "lead"), LEAD_PARTICLE);

        // Handle packet for Greeting
        ServerPlayNetworking.registerGlobalReceiver(PACKET_C2S_GREETING, (server, player, handler, buf, responseSender) -> {
            UUID entityId = UUID.fromString(buf.readString());
            String userLanguage = buf.readString(32767);

            // Ensure that the task is synced with the server thread
            server.execute(() -> {
                MobEntity entity = (MobEntity)ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
                if (entity != null) {
                    EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
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
                MobEntity entity = (MobEntity)ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
                if (entity != null) {
                    // Set talk to player goal (prevent entity from walking off)
                    TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
                    EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

                    EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
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
                MobEntity entity = (MobEntity)ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
                if (entity != null) {
                    // Set talk to player goal (prevent entity from walking off)
                    TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
                    EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

                    EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
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
                MobEntity entity = (MobEntity)ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
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
                MobEntity entity = (MobEntity)ServerEntityFinder.getEntityByUUID(player.getServerWorld(), entityId);
                if (entity != null) {
                    EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(entity.getUuidAsString());
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

            // Send entire whitelist / blacklist to logged in player
            send_whitelist_blacklist(player);

            LOGGER.info("Server send compressed, chunked login message packets to player: " + player.getName().getString());
            // Get lite JSON data & compress to byte array
            String chatDataJSON = ChatDataManager.getServerInstance().GetLightChatData(player.getDisplayName().getString());
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
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            String entityUUID = entity.getUuidAsString();
            if (entity.getRemovalReason() == Entity.RemovalReason.KILLED && ChatDataManager.getServerInstance().entityChatDataMap.containsKey(entityUUID)) {
                LOGGER.debug("Entity killed (" + entityUUID + "), updating death time stamp.");
                ChatDataManager.getServerInstance().entityChatDataMap.get(entityUUID).death = System.currentTimeMillis();
            }
        });

    }

    public static void send_whitelist_blacklist(ServerPlayerEntity player) {
        ConfigurationHandler.Config config = new ConfigurationHandler(ServerPackets.serverInstance).loadConfig();
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());

        // Write the whitelist data to the buffer
        List<String> whitelist = config.getWhitelist();
        buffer.writeInt(whitelist.size());
        for (String entry : whitelist) {
            buffer.writeString(entry);
        }

        // Write the blacklist data to the buffer
        List<String> blacklist = config.getBlacklist();
        buffer.writeInt(blacklist.size());
        for (String entry : blacklist) {
            buffer.writeString(entry);
        }

        if (player != null) {
            // Send packet to specific player
            LOGGER.info("Sending whitelist / blacklist packet to player: " + player.getName().getString());
            ServerPlayNetworking.send(player, PACKET_S2C_WHITELIST, buffer);
        } else {
            // Iterate over all players and send the packet
            for (ServerPlayerEntity serverPlayer : serverInstance.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(serverPlayer, PACKET_S2C_WHITELIST, buffer);
            }
        }
    }

    public static void generate_character(String userLanguage, EntityChatData chatData, ServerPlayerEntity player, MobEntity entity) {
        // Set talk to player goal (prevent entity from walking off)
        TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
        EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

        // Grab random adjective
        String randomAdjective = Randomizer.getRandomMessage(Randomizer.RandomType.ADJECTIVE);
        String randomClass = Randomizer.getRandomMessage(Randomizer.RandomType.CLASS);
        String randomAlignment = Randomizer.getRandomMessage(Randomizer.RandomType.ALIGNMENT);
        String randomSpeakingStyle = Randomizer.getRandomMessage(Randomizer.RandomType.SPEAKING_STYLE);

        // Generate random name parameters
        String randomLetter = Randomizer.RandomLetter();
        int randomSyllables = Randomizer.RandomNumber(5) + 1;

        // Build the message
        StringBuilder userMessageBuilder = new StringBuilder();
        userMessageBuilder.append("Please generate a ").append(randomAdjective).append(" character. ");
        userMessageBuilder.append("This character is a ").append(randomClass).append(" class, who is ").append(randomAlignment).append(". ");
        if (entity.getCustomName() != null && !entity.getCustomName().getString().equals("N/A")) {
            userMessageBuilder.append("Their name is '").append(entity.getCustomName().getString()).append("'. ");
        } else {
            userMessageBuilder.append("Their name starts with the letter '").append(randomLetter)
                    .append("' and is ").append(randomSyllables).append(" syllables long. ");
        }
        userMessageBuilder.append("They speak in '").append(userLanguage).append("' with a ").append(randomSpeakingStyle).append(" style.");

        // Generate new character
        chatData.generateCharacter(userLanguage, player, userMessageBuilder.toString(), false);
    }

    public static void generate_chat(String userLanguage, EntityChatData chatData, ServerPlayerEntity player, MobEntity entity, String message, boolean is_auto_message) {
        // Set talk to player goal (prevent entity from walking off)
        TalkPlayerGoal talkGoal = new TalkPlayerGoal(player, entity, 3.5F);
        EntityBehaviorManager.addGoal(entity, talkGoal, GoalPriority.TALK_PLAYER);

        // Add new message
        chatData.generateMessage(userLanguage, player, message, is_auto_message);
    }

    // Writing a Map<String, PlayerData> to the buffer
    public static void writePlayerDataMap(PacketByteBuf buffer, Map<String, PlayerData> map) {
        buffer.writeInt(map.size()); // Write the size of the map
        for (Map.Entry<String, PlayerData> entry : map.entrySet()) {
            buffer.writeString(entry.getKey()); // Write the key (playerName)
            PlayerData data = entry.getValue();
            buffer.writeInt(data.friendship); // Write PlayerData field(s)
        }
    }

    // Send new message to all connected players
    public static void BroadcastEntityMessage(EntityChatData chatData) {
        // Log useful information before looping through all players
        LOGGER.info("Broadcasting entity message: entityId={}, status={}, currentMessage={}, currentLineNumber={}, senderType={}",
                chatData.entityId, chatData.status,
                chatData.currentMessage.length() > 24 ? chatData.currentMessage.substring(0, 24) + "..." : chatData.currentMessage,
                chatData.currentLineNumber, chatData.sender);

        for (ServerWorld world : serverInstance.getWorlds()) {
            // Find Entity by UUID and update custom name
            UUID entityId = UUID.fromString(chatData.entityId);
            MobEntity entity = (MobEntity)ServerEntityFinder.getEntityByUUID(world, entityId);
            if (entity != null) {
                String characterName = chatData.getCharacterProp("name");
                if (!characterName.isEmpty() && !characterName.equals("N/A") && entity.getCustomName() == null) {
                    LOGGER.debug("Setting entity name to " + characterName + " for " + chatData.entityId);
                    entity.setCustomName(Text.literal(characterName));
                    entity.setCustomNameVisible(true);
                    entity.setPersistent();
                }
            }

            // Make auto-generated message appear as a pending icon (attack, show/give, arrival)
            if (chatData.sender == ChatDataManager.ChatSender.USER && chatData.auto_generated > 0) {
                chatData.status = ChatDataManager.ChatStatus.PENDING;
            }

            // Iterate over all players and send the packet
            for (ServerPlayerEntity player : serverInstance.getPlayerManager().getPlayerList()) {
                PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
                buffer.writeString(chatData.entityId);
                buffer.writeString(chatData.currentMessage);
                buffer.writeInt(chatData.currentLineNumber);
                buffer.writeString(chatData.status.toString());
                buffer.writeString(chatData.sender.toString());
                writePlayerDataMap(buffer, chatData.players);

                // Send message to player
                ServerPlayNetworking.send(player, PACKET_S2C_ENTITY_MESSAGE, buffer);
            }
            break;
        }
    }

    // Send new message to all connected players
    public static void BroadcastPlayerMessage(EntityChatData chatData, ServerPlayerEntity sender) {
        // Log the specific data being sent
        LOGGER.info("Broadcasting player message: senderUUID={}, message={}", sender.getUuidAsString(),
                chatData.currentMessage);

        // Create the buffer for the packet
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());

        // Write the sender's UUID and the chat message to the buffer
        buffer.writeString(sender.getUuidAsString());
        buffer.writeString(sender.getDisplayName().getString());
        buffer.writeString(chatData.currentMessage);

        // Iterate over all connected players and send the packet
        for (ServerPlayerEntity serverPlayer : serverInstance.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(serverPlayer, PACKET_S2C_PLAYER_MESSAGE, buffer);
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

    // Send a chat message to all players (i.e. death message)
    public static void BroadcastMessage(Text message) {
        for (ServerPlayerEntity serverPlayer : serverInstance.getPlayerManager().getPlayerList()) {
            serverPlayer.sendMessage(message, false);
        };
    }

    // Send a chat message to a player which is clickable (for error messages with a link for help)
    public static void SendClickableError(PlayerEntity player, String message, String url) {
        MutableText text = Text.literal(message)
                .formatted(Formatting.RED)
                .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withUnderline(true));
        player.sendMessage(text, false);
    }

    // Send a clickable message to ALL Ops
    public static void sendErrorToAllOps(MinecraftServer server, String message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Check if the player is an operator
            if (server.getPlayerManager().isOperator(player.getGameProfile())) {
                ServerPackets.SendClickableError(player, message, "http://discord.creaturechat.com");
            }
        }
    }
}
