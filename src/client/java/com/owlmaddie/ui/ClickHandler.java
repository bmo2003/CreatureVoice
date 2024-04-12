package com.owlmaddie.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.owlmaddie.ModInit;
import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.network.ModPackets;
import com.owlmaddie.utils.ClientEntityFinder;
import com.owlmaddie.utils.Decompression;
import com.owlmaddie.utils.EntityHeights;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The {@code ClickHandler} class is used for the client to interact with the Entity chat UI. This class helps
 * to receive messages from the server, cast rays to see what the user clicked on, and communicate these events
 * back to the server.
 */
public class ClickHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
    private static boolean wasClicked = false;
    static HashMap<Integer, byte[]> receivedChunks = new HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.options.useKey.isPressed()) {
                if (!wasClicked) {
                    // The key has just been pressed down, so handle the 'click'
                    handleUseKeyClick(client);
                    wasClicked = true;
                }
            } else {
                // The key has been released, so reset the wasClicked flag
                wasClicked = false;
            }
        });

        // Client-side packet handler, message sync
        ClientPlayNetworking.registerGlobalReceiver(ModInit.PACKET_S2C_MESSAGE, (client, handler, buffer, responseSender) -> {
            // Read the data from the server packet
            UUID entityId = UUID.fromString(buffer.readString());
            String message = buffer.readString(32767);
            int line = buffer.readInt();
            String status_name = buffer.readString(32767);
            String sender_name = buffer.readString(32767);
            int friendship = buffer.readInt();

            // Update the chat data manager on the client-side
            client.execute(() -> { // Make sure to run on the client thread
                MobEntity entity = ClientEntityFinder.getEntityByUUID(client.world, entityId);
                if (entity != null) {
                    ChatDataManager chatDataManager = ChatDataManager.getClientInstance();
                    ChatDataManager.EntityChatData chatData = chatDataManager.getOrCreateChatData(entity.getUuidAsString());
                    if (!message.isEmpty()) {
                        chatData.currentMessage = message;
                    }
                    chatData.currentLineNumber = line;
                    chatData.status = ChatDataManager.ChatStatus.valueOf(status_name);
                    chatData.sender = ChatDataManager.ChatSender.valueOf(sender_name);
                    chatData.friendship = friendship;
                }
            });
        });

        // Client-side player login: get all chat data
        ClientPlayNetworking.registerGlobalReceiver(ModInit.PACKET_S2C_LOGIN, (client, handler, buffer, responseSender) -> {
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
                    if (chatDataJSON == null) {
                        LOGGER.info("Error decompressing lite JSON string from bytes");
                        return;
                    }

                    // Parse JSON and update client chat data
                    Gson GSON = new Gson();
                    Type type = new TypeToken<HashMap<String, ChatDataManager.EntityChatData>>(){}.getType();
                    ChatDataManager.getClientInstance().entityChatDataMap = GSON.fromJson(chatDataJSON, type);

                    // Clear receivedChunks for future use
                    receivedChunks.clear();
                }
            });
        });
    }

    public static void handleUseKeyClick(MinecraftClient client) {
        // Get Nearby Entities
        Camera camera = client.gameRenderer.getCamera();
        Entity cameraEntity = camera.getFocusedEntity();
        if (cameraEntity == null) return;

        World world = cameraEntity.getEntityWorld();
        double renderDistance = 9.0;

        // Calculate radius of entities
        Vec3d pos = cameraEntity.getPos();
        Box area = new Box(pos.x - renderDistance, pos.y - renderDistance, pos.z - renderDistance,
                pos.x + renderDistance, pos.y + renderDistance, pos.z + renderDistance);

        // Get all entities
        List<Entity> nearbyEntities = world.getOtherEntities(null, area);

        // Filter out MobEntity/Living entities
        List<MobEntity> nearbyCreatures = nearbyEntities.stream()
                .filter(entity -> entity instanceof MobEntity)
                .map(entity -> (MobEntity) entity)
                .collect(Collectors.toList());

        // Get the player from the client
        ClientPlayerEntity player = client.player;

        // Get the camera position for ray start to support both first-person and third-person views
        Vec3d startRay = client.gameRenderer.getCamera().getPos();

        // Use the player's looking direction to define the ray's direction
        Vec3d lookVec = player.getRotationVec(1.0F);
        Vec3d endRay = startRay.add(lookVec.normalize().multiply(renderDistance));

        MobEntity closestEntity = null;
        double closestDistance = Double.MAX_VALUE; // Start with the largest possible distance

        // Iterate through the entities to check for hits
        for (MobEntity entity : nearbyCreatures) {
            if (entity.getType() == EntityType.PLAYER || entity.hasPassengers()) {
                // Skip Player
                continue;
            }

            // Get entity height (adjust for specific classes)
            float entityHeight = EntityHeights.getAdjustedEntityHeight(entity);

            // Move hit box near front of entity
            float entityYawRadians = (float) Math.toRadians(entity.getYaw());
            Vec3d forwardOffset = new Vec3d(-Math.sin(entityYawRadians), 0.0, Math.cos(entityYawRadians)).multiply(entity.getWidth() / 2.0 * 0.8);

            double paddingAboveEntity = 0.4D;
            Vec3d iconCenter;

            // Determine the chat bubble position
            if (entity instanceof EnderDragonEntity) {
                // Ender dragons a unique, and we must use the Head for position
                EnderDragonEntity dragon = (EnderDragonEntity) entity;
                Vec3d headPos = dragon.head.getPos();

                // Just use the head's interpolated position directly
                iconCenter = headPos.add(0, entityHeight + paddingAboveEntity, 0);
            } else {
                // Calculate the position of the chat bubble: above the head and 80% towards the front
                Vec3d entityPos = entity.getPos();
                iconCenter = entityPos.add(forwardOffset).add(0, entityHeight + paddingAboveEntity, 0);
            }

            // Define a bounding box that accurately represents the text bubble
            double bubbleRadius = 1D; // Determine the radius or size of the text bubble
            Box iconBox = new Box(
                    iconCenter.add(-bubbleRadius, -bubbleRadius, -bubbleRadius),
                    iconCenter.add(bubbleRadius, bubbleRadius, bubbleRadius)
            );

            // Perform the raycast
            Optional<Vec3d> hitResult = iconBox.raycast(startRay, endRay);
            if (hitResult.isPresent()) {
                double distance = startRay.squaredDistanceTo(hitResult.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        // Handle the click for the closest entity after the loop
        if (closestEntity != null) {
            // Look-up conversation
            ChatDataManager.EntityChatData chatData = ChatDataManager.getClientInstance().getOrCreateChatData(closestEntity.getUuidAsString());

            // Play click sound
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2F, 0.8F);

            if (chatData.status == ChatDataManager.ChatStatus.NONE) {
                // Start conversation
                ModPackets.sendGenerateGreeting(closestEntity);
            } else if (chatData.status == ChatDataManager.ChatStatus.DISPLAY) {
                // Update lines read
                ModPackets.sendUpdateLineNumber(closestEntity, chatData.currentLineNumber + BubbleRenderer.DISPLAY_NUM_LINES);
            } else if (chatData.status == ChatDataManager.ChatStatus.END) {
                // End of chat (open player chat screen)
                ModPackets.sendStartChat(closestEntity);
                client.setScreen(new ChatScreen(closestEntity));
            }
        }

    }
}