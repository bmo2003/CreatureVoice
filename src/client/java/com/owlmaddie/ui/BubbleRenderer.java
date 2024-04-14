package com.owlmaddie.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.utils.EntityHeights;
import com.owlmaddie.utils.EntityRendererAccessor;
import com.owlmaddie.utils.TextureLoader;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The {@code BubbleRenderer} class provides static methods to render the chat UI bubble, entity icons,
 * text, friendship status, and other UI-related rendering code.
 */
public class BubbleRenderer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
    protected static TextureLoader textures = new TextureLoader();
    public static int DISPLAY_NUM_LINES = 3;
    public static int DISPLAY_PADDING = 2;
    public static int animationFrame = 0;
    public static long lastTick = 0;

    public static void drawTextBubbleBackground(MatrixStack matrices, float x, float y, float width, float height, int friendship) {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        float z = 0.01F;

        // Draw UI text background (based on friendship)
        if (friendship == -3) {
            RenderSystem.setShaderTexture(0, textures.GetUI("text-top-enemy"));
        } else if (friendship == 3) {
            RenderSystem.setShaderTexture(0, textures.GetUI("text-top-friend"));
        } else {
            RenderSystem.setShaderTexture(0, textures.GetUI("text-top"));
        }
        drawTexturePart(matrices, buffer, x - 50, y, z, 228, 40);

        RenderSystem.setShaderTexture(0, textures.GetUI("text-middle"));
        drawTexturePart(matrices, buffer, x, y + 40, z, width, height);

        RenderSystem.setShaderTexture(0, textures.GetUI("text-bottom"));
        drawTexturePart(matrices, buffer, x, y + 40 + height, z, width, 5);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    private static void drawTexturePart(MatrixStack matrices, BufferBuilder buffer, float x, float y, float z, float width, float height) {
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(matrices.peek().getPositionMatrix(), x, y + height, z).texture(0, 1).next();  // bottom left
        buffer.vertex(matrices.peek().getPositionMatrix(), x + width, y + height, z).texture(1, 1).next();   // bottom right
        buffer.vertex(matrices.peek().getPositionMatrix(), x + width, y, z).texture(1, 0).next();  // top right
        buffer.vertex(matrices.peek().getPositionMatrix(), x, y, z).texture(0, 0).next(); // top left
        Tessellator.getInstance().draw();
    }

    private static void drawIcon(String ui_icon_name, MatrixStack matrices, float x, float y, float width, float height) {
        // Draw button icon
        Identifier button_texture = textures.GetUI(ui_icon_name);
        RenderSystem.setShaderTexture(0, button_texture);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        float z = -0.01F;
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x, y + height, z).texture(0, 1).next();  // bottom left
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x + width, y + height, z).texture(1, 1).next();   // bottom right
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x + width, y, z).texture(1, 0).next();  // top right
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x, y, z).texture(0, 0).next(); // top left
        tessellator.draw();

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    private static void drawFriendshipStatus(MatrixStack matrices, float x, float y, float width, float height, int friendship) {
        // dynamically calculate friendship ui image name
        String ui_icon_name = "friendship" + friendship;

        // Draw texture
        Identifier button_texture = textures.GetUI(ui_icon_name);
        RenderSystem.setShaderTexture(0, button_texture);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        float z = -0.01F;
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x, y + height, z).texture(0, 1).next();  // bottom left
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x + width, y + height, z).texture(1, 1).next();   // bottom right
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x + width, y, z).texture(1, 0).next();  // top right
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x, y, z).texture(0, 0).next(); // top left
        tessellator.draw();

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    private static void drawEntityIcon(MatrixStack matrices, MobEntity entity, float x, float y, float width, float height) {
        // Get entity renderer
        EntityRenderer renderer = EntityRendererAccessor.getEntityRenderer(entity);
        String entity_icon_path = renderer.getTexture(entity).getPath();

        // Draw face icon
        Identifier entity_id = textures.GetEntity(entity_icon_path);
        if (entity_id == null) {
            return;
        }

        RenderSystem.setShaderTexture(0, entity_id);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        float z = -0.01F;
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x, y + height, z).texture(0, 1).next();  // bottom left
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x + width, y + height, z).texture(1, 1).next();   // bottom right
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x + width, y, z).texture(1, 0).next();  // top right
        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), x, y, z).texture(0, 0).next(); // top left
        tessellator.draw();

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    private static void drawMessageText(Matrix4f matrix, List<String> lines, int starting_line, int ending_line,
                                 VertexConsumerProvider immediate, float lineSpacing, int fullBright, float yOffset) {
        TextRenderer fontRenderer = MinecraftClient.getInstance().textRenderer;
        int currentLineIndex = 0; // We'll use this to track which line we're on

        for (String lineText : lines) {
            // Only draw lines that are within the specified range
            if (currentLineIndex >= starting_line && currentLineIndex < ending_line) {
                fontRenderer.draw(lineText, -fontRenderer.getWidth(lineText) / 2f, yOffset, 0xffffff,
                        false, matrix, immediate, TextLayerType.NORMAL, 0, fullBright);
                yOffset += fontRenderer.fontHeight + lineSpacing;
            }
            currentLineIndex++;

            if (currentLineIndex > ending_line) {
                break;
            }
        }
    }

    private static void drawEndOfMessageText(Matrix4f matrix, VertexConsumerProvider immediate,
                                      int fullBright, float yOffset) {
        TextRenderer fontRenderer = MinecraftClient.getInstance().textRenderer;
        String lineText = "<end of message>";
        fontRenderer.draw(lineText, -fontRenderer.getWidth(lineText) / 2f, yOffset + 10F, 0xffffff,
                false, matrix, immediate, TextLayerType.NORMAL, 0, fullBright);
    }

    private static void drawEntityName(MobEntity entity, Matrix4f matrix, VertexConsumerProvider immediate,
                                int fullBright, float yOffset) {
        if (entity.getCustomName() != null) {
            TextRenderer fontRenderer = MinecraftClient.getInstance().textRenderer;
            String lineText = entity.getCustomName().getLiteralString();

            // Ensure the string is no longer than X characters
            if (lineText.length() > 14) {
                lineText = lineText.substring(0, 14) + "...";
            }

            fontRenderer.draw(lineText, -fontRenderer.getWidth(lineText) / 2f, yOffset, 0xffffff,
                    false, matrix, immediate, TextLayerType.NORMAL, 0, fullBright);
        }
    }

    public static void drawTextAboveEntities(WorldRenderContext context, long tick, float partialTicks) {
        Camera camera = context.camera();
        Entity cameraEntity = camera.getFocusedEntity();
        if (cameraEntity == null) return;

        World world = cameraEntity.getEntityWorld();
        double renderDistance = 9.0;

        // Calculate radius of entities
        Vec3d pos = cameraEntity.getPos();
        Box area = new Box(pos.x - renderDistance, pos.y - renderDistance, pos.z - renderDistance,
                pos.x + renderDistance, pos.y + renderDistance, pos.z + renderDistance);

        // Init font render, matrix, and vertex producer
        TextRenderer fontRenderer = MinecraftClient.getInstance().textRenderer;
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider immediate = context.consumers();

        // Get camera position
        Vec3d interpolatedCameraPos = new Vec3d(camera.getPos().x, camera.getPos().y, camera.getPos().z);

        // Get all entities
        List<Entity> nearbyEntities = world.getOtherEntities(null, area);

        // Filter MobEntity/Living entities
        List<MobEntity> nearbyCreatures = nearbyEntities.stream()
                .filter(entity -> entity instanceof MobEntity)
                .map(entity -> (MobEntity) entity)
                .collect(Collectors.toList());

        for (MobEntity entity : nearbyCreatures) {
            if (entity.getType() == EntityType.PLAYER || entity.hasPassengers()) {
                // Skip
                continue;
            }

            // Look-up greeting (if any)
            ChatDataManager.EntityChatData chatData = ChatDataManager.getClientInstance().getOrCreateChatData(entity.getUuidAsString());
            List<String> lines = chatData.getWrappedLines();

            // Set the range of lines to display
            int starting_line = chatData.currentLineNumber;
            int ending_line = Math.min(chatData.currentLineNumber + DISPLAY_NUM_LINES, lines.size());

            // Push a new matrix onto the stack.
            matrices.push();

            // Get entity height (adjust for specific classes)
            float entityHeight = EntityHeights.getAdjustedEntityHeight(entity);

            // Interpolate entity position (smooth motion)
            double paddingAboveEntity = 0.4D;
            Vec3d interpolatedEntityPos = new Vec3d(
                    MathHelper.lerp(partialTicks, entity.prevX, entity.getPos().x),
                    MathHelper.lerp(partialTicks, entity.prevY, entity.getPos().y),
                    MathHelper.lerp(partialTicks, entity.prevZ, entity.getPos().z)
            );

            // Determine the chat bubble position
            Vec3d bubblePosition;
            if (entity instanceof EnderDragonEntity) {
                // Ender dragons a unique, and we must use the Head for position
                EnderDragonEntity dragon = (EnderDragonEntity) entity;
                EnderDragonPart head = dragon.head;

                // Interpolate the head position
                Vec3d headPos = new Vec3d(
                        MathHelper.lerp(partialTicks, head.prevX, head.getX()),
                        MathHelper.lerp(partialTicks, head.prevY, head.getY()),
                        MathHelper.lerp(partialTicks, head.prevZ, head.getZ())
                );

                // Just use the head's interpolated position directly
                bubblePosition = headPos.add(0, entityHeight + paddingAboveEntity, 0);
            } else {
                // Calculate the forward offset based on the entity's yaw
                float entityYawRadians = (float) Math.toRadians(entity.getYaw(partialTicks));
                Vec3d forwardOffset = new Vec3d(-Math.sin(entityYawRadians), 0.0, Math.cos(entityYawRadians));

                // Calculate the forward offset based on the entity's yaw, scaled to 80% towards the front edge
                Vec3d scaledForwardOffset = forwardOffset.multiply(entity.getWidth() / 2.0 * 0.8);

                // Calculate the position of the chat bubble: above the head and 80% towards the front
                bubblePosition = interpolatedEntityPos.add(scaledForwardOffset)
                        .add(0, entityHeight + paddingAboveEntity, 0);
            }

            // Translate to the chat bubble's position
            matrices.translate(bubblePosition.x - interpolatedCameraPos.x,
                    bubblePosition.y - interpolatedCameraPos.y,
                    bubblePosition.z - interpolatedCameraPos.z);

            // Calculate the difference vector (from entity + padding above to camera)
            Vec3d difference = interpolatedCameraPos.subtract(new Vec3d(interpolatedEntityPos.x, interpolatedEntityPos.y + entityHeight + paddingAboveEntity, interpolatedEntityPos.z));

            // Calculate the yaw angle
            double yaw = -(Math.atan2(difference.z, difference.x) + Math.PI / 2D);

            // Convert yaw to Quaternion
            float halfYaw = (float) yaw * 0.5f;
            double sinHalfYaw = MathHelper.sin(halfYaw);
            double cosHalfYaw = MathHelper.cos(halfYaw);
            Quaternionf yawRotation = new Quaternionf(0, sinHalfYaw, 0, cosHalfYaw);

            // Apply the yaw rotation to the matrix stack
            matrices.multiply(yawRotation);

            // Obtain the horizontal distance to the entity
            double horizontalDistance = Math.sqrt(difference.x * difference.x + difference.z * difference.z);
            // Calculate the pitch angle based on the horizontal distance and the y difference
            double pitch = Math.atan2(difference.y, horizontalDistance);

            // Convert pitch to Quaternion
            float halfPitch = (float) pitch * 0.5f;
            double sinHalfPitch = MathHelper.sin(halfPitch);
            double cosHalfPitch = MathHelper.cos(halfPitch);
            Quaternionf pitchRotation = new Quaternionf(sinHalfPitch, 0, 0, cosHalfPitch);

            // Apply the pitch rotation to the matrix stack
            matrices.multiply(pitchRotation);

            // Determine max line length
            float linesDisplayed = ending_line - starting_line;
            float lineSpacing = 1F;
            float textHeaderHeight = 40F;
            float textFooterHeight = 5F;
            int fullBright = 0xF000F0;
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            // Calculate size of text scaled to world
            float scaledTextHeight = linesDisplayed * (fontRenderer.fontHeight + lineSpacing);
            float minTextHeight = (DISPLAY_NUM_LINES * (fontRenderer.fontHeight + lineSpacing)) + (DISPLAY_PADDING * 2);
            scaledTextHeight = Math.max(scaledTextHeight, minTextHeight);

            // Update Bubble Data for Click Handling using UUID (account for scaling)
            BubbleLocationManager.updateBubbleData(entity.getUuid(), bubblePosition,
                    128F / (1 / 0.02F), (scaledTextHeight + 25F) / (1 / 0.02F), yaw, pitch);

            // Scale down before rendering textures (otherwise font is huge)
            matrices.scale(-0.02F, -0.02F, 0.02F);

            // Translate above the entity
            matrices.translate(0F, -scaledTextHeight - textHeaderHeight - textFooterHeight, 0F);

            // Draw Entity (Custom Name)
            drawEntityName(entity, matrix, immediate, fullBright, 24F + DISPLAY_PADDING);

            // Check if conversation has started
            if (chatData.status == ChatDataManager.ChatStatus.NONE) {
                // Draw 'start chat' button
                drawIcon("button-chat", matrices, -16, textHeaderHeight, 32, 17);

            } else if (chatData.status == ChatDataManager.ChatStatus.PENDING) {
                // Draw 'pending' button
                drawIcon("button-dot-" + animationFrame, matrices, -16, textHeaderHeight, 32, 17);

                // Calculate animation frames (0-8) every X ticks
                if (lastTick != tick && tick % 5 == 0) {
                    lastTick = tick;
                    animationFrame++;
                }
                if (animationFrame > 8) {
                    animationFrame = 0;
                }

            } else if (chatData.sender == ChatDataManager.ChatSender.ASSISTANT) {
                // Draw text background (no smaller than 50F tall)
                drawTextBubbleBackground(matrices, -64, 0, 128, scaledTextHeight, chatData.friendship);

                // Draw face icon of entity
                drawEntityIcon(matrices, entity, -82, 7, 32, 32);

                // Draw Friendship status
                drawFriendshipStatus(matrices, 51, 18, 31, 21, chatData.friendship);

                // Render each line of the text
                drawMessageText(matrix, lines, starting_line, ending_line, immediate, lineSpacing, fullBright, 40.0F + DISPLAY_PADDING);

                if (starting_line > 0 && starting_line == ending_line) {
                    // Add <End Of Message> text
                    drawEndOfMessageText(matrix, immediate, fullBright, 40.0F + DISPLAY_PADDING);
                }
            }

            // Pop the matrix to return to the original state.
            matrices.pop();
        }

        // Get list of Entity UUIDs with chat bubbles rendered
        List<UUID> activeEntityUUIDs = nearbyCreatures.stream()
                .map(Entity::getUuid)
                .collect(Collectors.toList());

        // Purge entities that were not rendered
        BubbleLocationManager.performCleanup(activeEntityUUIDs);
    }
}
