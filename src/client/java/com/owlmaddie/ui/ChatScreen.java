// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.network.ClientPackets;
import com.owlmaddie.utils.TextureLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code ChatScreen} class is used to display a chat dialog UI for the player and handle keyboard
 * entry events.
 */
public class ChatScreen extends Screen {
    private TextFieldWidget textField;
    private ButtonWidget sendButton;
    private ButtonWidget cancelButton;
    private Entity screenEntity;
    private final Text labelText = Text.literal("Enter your message:");
    private static final int BG_WIDTH = 261;
    private static final int BG_HEIGHT = 88;
    protected static final TextureLoader textures = new TextureLoader();

    private int bgX;
    private int bgY;

    /**
     * Simple multi-line text field that wraps text based on the widget width.
     */
    private static class MultiLineTextField extends TextFieldWidget {
        private final int maxLines;
        private final TextRenderer renderer;

        public MultiLineTextField(TextRenderer renderer, int x, int y, int width, int height, Text text, int maxLines) {
            super(renderer, x, y, width, height, text);
            this.renderer = renderer;
            this.maxLines = maxLines;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            boolean result = super.charTyped(chr, modifiers);
            wrap();
            return result;
        }

        @Override
        public void write(String text) {
            super.write(text);
            wrap();
        }

        private void wrap() {
            String[] lines = getText().split("\n");
            List<String> wrapped = new ArrayList<>();
            for (String line : lines) {
                while (renderer.getWidth(line) > this.getInnerWidth()) {
                    int len = line.length();
                    while (len > 0 && renderer.getWidth(line.substring(0, len)) > this.getInnerWidth()) {
                        len--;
                    }
                    wrapped.add(line.substring(0, len));
                    line = line.substring(len);
                }
                wrapped.add(line);
            }
            if (wrapped.size() > maxLines) {
                wrapped = wrapped.subList(wrapped.size() - maxLines, wrapped.size());
            }
            String joined = String.join("\n", wrapped);
            if (!joined.equals(getText())) {
                setText(joined);
            }
        }
    }

    public ChatScreen(Entity entity, PlayerEntity player) {
        super(Text.literal("Simple Chat"));
        screenEntity = entity;

        // Notify server that chat screen
        ClientPackets.sendOpenChat(entity);
    }

    @Override
    protected void init() {
        super.init();
        // Calculate background location for positioning elements
        bgX = (this.width - BG_WIDTH) / 2;
        bgY = (this.height - BG_HEIGHT) / 5;

        int margin = 20;

        // Text field placed near the top of the background
        int textFieldWidth = BG_WIDTH - margin * 2;
        int textFieldHeight = 20; // allow two lines of text
        int textFieldX = bgX + margin;
        int textFieldY = bgY + margin;

        // Initialize the text field
        textField = new MultiLineTextField(textRenderer, textFieldX, textFieldY, textFieldWidth, textFieldHeight, Text.literal("Chat Input"), 1);
        textField.setMaxLength(ChatDataManager.MAX_CHAR_IN_USER_MESSAGE);
        textField.setDrawsBackground(true);
        textField.setText("");
        textField.setChangedListener(this::onTextChanged);
        this.addDrawableChild(textField);

        // Set focus to the text field
        setFocused(textField);  // Set the text field as the focused element
        textField.setFocused(true); // Request focus for the text field

        // Button dimensions and positions
        int buttonWidth = 80;
        int buttonHeight = 20;
        int buttonsY = bgY + BG_HEIGHT - buttonHeight - margin;

        // Initialize the cancel button
        cancelButton = new ButtonWidget.Builder(Text.literal("Cancel"), button -> close())
                .size(buttonWidth, buttonHeight)
                .position(bgX + margin, buttonsY)
                .build();
        this.addDrawableChild(cancelButton);

        // Initialize the send button
        sendButton = new ButtonWidget.Builder(Text.literal("Send"), button -> sendChatMessage())
                .size(buttonWidth, buttonHeight)
                .position(bgX + BG_WIDTH - buttonWidth - margin, buttonsY)
                .build();
        sendButton.active = false;
        this.addDrawableChild(sendButton);
    }

    private void sendChatMessage() {
        // Send message to server
        String message = textField.getText();
        ClientPackets.sendChat(screenEntity, message);
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (textField.isFocused() && !textField.getText().isEmpty()) {
                // Close window on ENTER key press
                sendChatMessage();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // Handle other key presses
    }

    private void onTextChanged(String text) {
        // Enable the button only if the text field is not empty
        sendButton.active = !text.isEmpty();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Let the base class draw its background and children first
        super.render(context, mouseX, mouseY, delta);

        // Draw the label text above the text field
        int labelWidth = textRenderer.getWidth(labelText);
        int labelX = (this.width - labelWidth) / 2; // Centered X position
        int labelY = textField.getY() - 15; // Positioned above the text field
        context.drawTextWithShadow(textRenderer, labelText, labelX, labelY, 0xFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw default gradient or texture from the base game
        super.renderBackground(context, mouseX, mouseY, delta);

        // Draw the custom background texture centered on screen
        Identifier texture = textures.GetUI("chat-background");
        int x = (this.width - BG_WIDTH) / 2;
        int y = (this.height - BG_HEIGHT) / 5;
        if (texture != null) {
            context.drawTexture(texture, x, y, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);
        } else {
            // Fallback: semi-transparent rectangle if texture missing
            context.fillGradient(0, 0, this.width, this.height, 0xA3000000, 0xA3000000);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Return true if you want the screen to close when the ESC key is pressed
        return true;
    }

    @Override
    public boolean shouldPause() {
        // Return false to prevent the game from pausing when the screen is open
        return false;
    }

    @Override
    public void removed() {
        super.removed();

        // Notify server that chat screen
        ClientPackets.sendCloseChat();
    }
}
