package com.owlmaddie;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;


public class ChatScreen extends Screen {
    private TextFieldWidget textField;
    private ButtonWidget sendButton;
    private ButtonWidget cancelButton;
    private Entity screenEntity;
    private final Text labelText = Text.literal("Enter your message:");

    public ChatScreen(Entity entity) {
        super(Text.literal("Simple Chat"));
        screenEntity = entity;
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
        textField.setMaxLength(512);
        textField.setDrawsBackground(true);
        textField.setText("");
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
        this.addDrawableChild(sendButton);
    }

    private void sendChatMessage() {
        // Send message to server
        String message = textField.getText();
        ModPackets.sendChat(screenEntity, message);
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (textField.isFocused()) {
                // Close window on ENTER key press
                sendChatMessage();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // Handle other key presses
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render the background
        this.renderBackground(context, mouseX, mouseY, delta);

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
}
