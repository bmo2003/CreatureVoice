package com.owlmaddie.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.owlmaddie.ModInit;
import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.network.ModPackets;
import com.owlmaddie.utils.ClientEntityFinder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
    private static boolean wasClicked = false;

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
            // Read the data from the server packet
            int length = buffer.readInt();
            String chatDataJSON = buffer.readString(length);

            // Update the chat data manager on the client-side
            Gson GSON = new Gson();
            client.execute(() -> { // Make sure to run on the client thread
                // Parse JSON and override client chat data
                Type type = new TypeToken<HashMap<String, ChatDataManager.EntityChatData>>(){}.getType();
                ChatDataManager.getClientInstance().entityChatDataMap = GSON.fromJson(chatDataJSON, type);
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
            if (entity.getType() == EntityType.PLAYER) {
                // Skip Player
                continue;
            }

            Vec3d entityPos = entity.getPos();
            double extraHeight = 0.5D; // Calculate how much higher the text bubble is above the entity
            Vec3d iconCenter = entityPos.add(0, entity.getHeight() + extraHeight, 0);

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