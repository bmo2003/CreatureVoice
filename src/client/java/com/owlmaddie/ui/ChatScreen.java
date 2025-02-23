package com.owlmaddie.ui;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.network.ClientPackets;
import com.owlmaddie.utils.VersionUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

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

    public ChatScreen(Entity entity, PlayerEntity player) {
        super(Text.literal("Simple Chat"));
        screenEntity = entity;

        // Notify server that chat screen
        ClientPackets.sendOpenChat(entity);
    }

    @Override
    protected void init() {
        super.init();
        // Centered text field dimensions
        int textFieldWidth = 220;
        int textFieldHeight = 20;
        int textFieldX = (this.width - textFieldWidth) / 2; // Centered X position
        int textFieldY = 100; // Y position

        // Initialize the text field
        textField = new TextFieldWidget(textRenderer, textFieldX, textFieldY, textFieldWidth, textFieldHeight, Text.literal("Chat Input"));
        textField.setMaxLength(ChatDataManager.MAX_CHAR_IN_USER_MESSAGE);
        textField.setDrawsBackground(true);
        textField.setText("");
        textField.setChangedListener(this::onTextChanged);
        this.addDrawableChild(textField);

        // Set focus to the text field
        setFocused(textField);  // Set the text field as the focused element
        textField.setFocused(true); // Request focus for the text field

        // Button dimensions and positions
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonSpacing = 20; // Space between buttons
        int buttonsY = textFieldY + textFieldHeight + 15; // Y position under the text field

        // Initialize the cancel button
        cancelButton = new ButtonWidget.Builder(Text.literal("Cancel"), button -> close())
                .size(buttonWidth, buttonHeight)
                .position(textFieldX, buttonsY)
                .build();
        this.addDrawableChild(cancelButton);

        // Initialize the send button
        sendButton = new ButtonWidget.Builder(Text.literal("Send"), button -> sendChatMessage())
                .size(buttonWidth, buttonHeight)
                .position(textFieldX + buttonWidth + buttonSpacing, buttonsY)
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
        // Render custom background only for older versions
        if (VersionUtils.isOlderThan("1.20.2")) {
            renderBackground(context);
        }

        // Render the label text above the text field
        int labelWidth = textRenderer.getWidth(labelText);
        int labelX = (this.width - labelWidth) / 2; // Centered X position
        int labelY = textField.getY() - 15; // Positioned above the text field
        context.drawTextWithShadow(textRenderer, labelText, labelX, labelY, 0xFFFFFF);

        // Render the text field
        textField.render(context, mouseX, mouseY, delta);

        // Render the buttons
        sendButton.render(context, mouseX, mouseY, delta);
        cancelButton.render(context, mouseX, mouseY, delta);

        // Call super.render if necessary
        super.render(context, mouseX, mouseY, delta);
    }

    public void renderBackground(DrawContext context) {
        // Draw a slightly lighter semi-transparent rectangle as the background
        context.fillGradient(0, 0, this.width, this.height, 0xA3000000, 0xA3000000);
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
