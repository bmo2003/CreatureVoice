package com.owlmaddie;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class ClickHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
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
            int entityId = buffer.readInt();
            String message = buffer.readString(32767);
            int line = buffer.readInt();
            String status_name = buffer.readString(32767);

            // Update the chat data manager on the client-side
            client.execute(() -> { // Make sure to run on the client thread
                Entity entity = client.world.getEntityById(entityId);
                if (entity != null) {
                    ChatDataManager chatDataManager = ChatDataManager.getClientInstance();
                    ChatDataManager.EntityChatData chatData = chatDataManager.getOrCreateChatData(entityId);
                    if (!message.isEmpty()) {
                        chatData.currentMessage = message;
                    }
                    chatData.currentLineNumber = line;
                    chatData.status = ChatDataManager.ChatStatus.valueOf(status_name);
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

        // Filter out living entities
        List<LivingEntity> nearbyCreatures = nearbyEntities.stream()
                .filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                .collect(Collectors.toList());

        // Get the player from the client
        ClientPlayerEntity player = client.player;

        // Get the camera position for ray start to support both first-person and third-person views
        Vec3d startRay = client.gameRenderer.getCamera().getPos();

        // Use the player's looking direction to define the ray's direction
        Vec3d lookVec = player.getRotationVec(1.0F);
        Vec3d endRay = startRay.add(lookVec.normalize().multiply(renderDistance));

        Entity closestEntity = null;
        double closestDistance = Double.MAX_VALUE; // Start with the largest possible distance

        // Iterate through the entities to check for hits
        for (Entity entity : nearbyCreatures) {
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
            ChatDataManager.EntityChatData chatData = ChatDataManager.getClientInstance().getOrCreateChatData(closestEntity.getId());

            if (chatData.currentMessage.isEmpty()) {
                // Start conversation
                ModPackets.sendGenerateGreeting(closestEntity);
            } else {
                // Update lines read
                ModPackets.sendUpdateLineNumber(closestEntity, chatData.currentLineNumber + ClientInit.DISPLAY_NUM_LINES);
            }
        }

    }
}