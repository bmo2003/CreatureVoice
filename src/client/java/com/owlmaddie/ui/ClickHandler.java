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
import net.minecraft.particle.ParticleTypes;
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
import java.util.stream.Stream;

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
        Camera camera = client.gameRenderer.getCamera();
        Entity cameraEntity = camera.getFocusedEntity();
        if (cameraEntity == null) return;

        World world = cameraEntity.getEntityWorld();
        double renderDistance = 9.0;

        // Calculate radius of entities
        Vec3d cameraPos = cameraEntity.getPos();
        Box area = new Box(cameraPos.x - renderDistance, cameraPos.y - renderDistance, cameraPos.z - renderDistance,
                cameraPos.x + renderDistance, cameraPos.y + renderDistance, cameraPos.z + renderDistance);

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

        // Chat bubble size
        double bubbleHeight = 1.3D;
        double bubbleWidth = 2.6D;

        MobEntity closestEntity = null;
        double closestDistance = Double.MAX_VALUE; // Start with the largest possible distance
        Optional<Vec3d> closestHitResult = null;
        Vec3d closestCenter = null;

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
            Vec3d[] corners = getBillboardCorners(iconCenter, cameraPos, bubbleHeight, bubbleWidth);

            // DEBUG CODE
            //drawCorners(entity.getWorld(), corners);
            //drawRay(startRay, lookVec, entity.getWorld());

            // Cast ray and determine intersection with chat bubble
            Optional<Vec3d> hitResult = rayIntersectsPolygon(startRay, lookVec, corners);
            if (hitResult.isPresent()) {
                double distance = startRay.squaredDistanceTo(hitResult.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                    closestHitResult = hitResult;
                    closestCenter = iconCenter;
                }
            }
        }

        // Handle the click for the closest entity after the loop
        if (closestEntity != null) {
            // Look-up conversation
            ChatDataManager.EntityChatData chatData = ChatDataManager.getClientInstance().getOrCreateChatData(closestEntity.getUuidAsString());

            // Play click sound
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2F, 0.8F);

            // Determine area clicked inside chat bubble (top, left, right)
            String hitRegion = determineHitRegion(closestHitResult.get(), closestCenter, camera, bubbleHeight);
            LOGGER.info(hitRegion);

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

    public static Vec3d[] getBillboardCorners(Vec3d center, Vec3d cameraPos, double height, double width) {
        // Calculate the vector pointing from the center to the camera
        Vec3d toCamera = cameraPos.subtract(center).normalize();

        // Right vector is perpendicular on the 'toCamera' vector, assuming 'up' is the global Y-axis (0, 1, 0)
        Vec3d right = toCamera.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // The actual up vector for the billboard can be recalculated to ensure orthogonality
        Vec3d up = right.crossProduct(toCamera).normalize();

        // Adjust the center point to move it to the bottom center of the rectangle
        Vec3d adjustedCenter = center.add(up.multiply(height / 2));  // Move the center upwards by half the height

        // Calculate the corners using the adjusted center, right, and up vectors
        Vec3d topLeft = adjustedCenter.subtract(right.multiply(width / 2)).add(up.multiply(height / 2));
        Vec3d topRight = adjustedCenter.add(right.multiply(width / 2)).add(up.multiply(height / 2));
        Vec3d bottomRight = adjustedCenter.add(right.multiply(width / 2)).subtract(up.multiply(height / 2));
        Vec3d bottomLeft = adjustedCenter.subtract(right.multiply(width / 2)).subtract(up.multiply(height / 2));

        // Return an array of Vec3d representing each corner of the billboard
        return new Vec3d[] {topLeft, topRight, bottomRight, bottomLeft};
    }

    public static void drawCorners(World world, Vec3d[] corners) {
        // Iterate over the corners to place glow particles
        for (Vec3d corner : corners) {
            world.addParticle(
                    ParticleTypes.GLOW,  // Using glow particles
                    corner.x, corner.y, corner.z,  // Coordinates of the particle
                    0.0, 0.0, 0.0  // No motion
            );
        }
    }

    public static void drawRay(Vec3d origin, Vec3d direction, World world) {
        Vec3d point = origin;
        double step = 0.5;
        int count = 100;  // Draw the ray for 100 steps
        for (int i = 0; i < count; i++) {
            point = point.add(direction.multiply(step));
            world.addParticle(ParticleTypes.END_ROD, point.x, point.y, point.z, 0, 0, 0);
        }
    }

    public static Optional<Vec3d> rayIntersectsPolygon(Vec3d rayOrigin, Vec3d rayDirection, Vec3d[] vertices) {
        rayDirection = rayDirection.normalize();  // Ensure direction is normalized
        // Check two triangles formed by the quad
        return Stream.of(
                        rayIntersectsTriangle(rayOrigin, rayDirection, vertices[0], vertices[1], vertices[2]),
                        rayIntersectsTriangle(rayOrigin, rayDirection, vertices[0], vertices[2], vertices[3])
                ).filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    public static Optional<Vec3d> rayIntersectsTriangle(Vec3d rayOrigin, Vec3d rayDirection, Vec3d v0, Vec3d v1, Vec3d v2) {
        Vec3d edge1 = v1.subtract(v0);
        Vec3d edge2 = v2.subtract(v0);
        Vec3d h = rayDirection.crossProduct(edge2);
        double a = edge1.dotProduct(h);

        if (Math.abs(a) < 1e-6) return Optional.empty();  // Ray is parallel to the triangle

        double f = 1.0 / a;
        Vec3d s = rayOrigin.subtract(v0);
        double u = f * s.dotProduct(h);
        if (u < 0.0 || u > 1.0) return Optional.empty();

        Vec3d q = s.crossProduct(edge1);
        double v = f * rayDirection.dotProduct(q);
        if (v < 0.0 || u + v > 1.0) return Optional.empty();

        double t = f * edge2.dotProduct(q);
        if (t > 1e-6) {
            return Optional.of(rayOrigin.add(rayDirection.multiply(t)));
        }
        return Optional.empty();
    }

    public static String determineHitRegion(Vec3d hitPoint, Vec3d center, Camera camera, double height) {
        Vec3d cameraPos = camera.getPos();
        Vec3d toCamera = cameraPos.subtract(center).normalize();

        // Assuming a standard global up vector (aligned with the y-axis)
        Vec3d globalUp = new Vec3d(0, 1, 0);

        // Calculate the "RIGHT" vector as perpendicular to the 'toCamera' vector and the global up vector
        Vec3d right = globalUp.crossProduct(toCamera).normalize();

        // Handle the case where the camera is looking straight down or up, making the cross product degenerate
        if (right.lengthSquared() == 0) {
            // If directly above or below, define an arbitrary right vector (assuming world x-axis)
            right = new Vec3d(1, 0, 0);
        }

        // Recalculate "UP" vector to ensure it's orthogonal to both "RIGHT" and "toCamera"
        Vec3d up = toCamera.crossProduct(right).normalize();

        // Calculate the relative position of the hit point to the center of the billboard
        Vec3d relPosition = hitPoint.subtract(center);
        double relX = relPosition.dotProduct(right);  // Project onto "RIGHT"
        double relY = relPosition.dotProduct(up);     // Project onto "UP"

        // Determine hit region based on relative coordinates
        if (relY > 0.65 * height) {
            return "TOP";
        } else {
            return relX < 0 ? "LEFT" : "RIGHT"; // Determine if on the left or right half
        }
    }
}