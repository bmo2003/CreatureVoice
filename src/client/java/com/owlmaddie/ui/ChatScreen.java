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
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/**
 * The ChatScreen class displays a chat dialog UI for the player
 * and handles keyboard entry events.
 */
public class ChatScreen extends Screen {
    // background texture size
    private static final int BG_WIDTH  = 261;
    private static final int BG_HEIGHT = 88;

    // text input margins and size
    private static final int TEXT_INPUT_MARGIN_X   = 21;
    private static final int TEXT_INPUT_MARGIN_TOP = 25;
    private static final int TEXT_INPUT_HEIGHT     = 20;

    // button dimensions and margins
    private static final int BUTTON_WIDTH    = 101;
    private static final int BUTTON_HEIGHT   = 21;
    private static final int BUTTON_MARGIN_X = 10;
    private static final int BUTTON_MARGIN_Y = 9;

    // computed positions
    private int bgX, bgY;

    private TextFieldWidget textField;
    private ButtonWidget sendButton;
    private ButtonWidget cancelButton;
    private Entity screenEntity;
    private final Text labelText = Text.literal("Enter your message:");
    private static final TextureLoader textures = new TextureLoader();

    public ChatScreen(Entity entity, PlayerEntity player) {
        super(Text.literal("Simple Chat"));
        this.screenEntity = entity;
        // tell server that chat opened
        ClientPackets.sendOpenChat(entity);
    }

    @Override
    protected void init() {
        super.init();

        // center background horizontally, 1/5 down vertically
        bgX = (this.width  - BG_WIDTH)  / 2;
        bgY = (this.height - BG_HEIGHT) / 5;

        // 1) text input
        int inputX = bgX + TEXT_INPUT_MARGIN_X;
        int inputY = bgY + TEXT_INPUT_MARGIN_TOP;
        int inputW = BG_WIDTH - TEXT_INPUT_MARGIN_X * 2;
        textField = new MultiLineTextField(
                textRenderer,
                inputX, inputY,
                inputW, TEXT_INPUT_HEIGHT,
                Text.literal(""), 1
        );
        textField.setMaxLength(ChatDataManager.MAX_CHAR_IN_USER_MESSAGE);
        textField.setChangedListener(this::onTextChanged);
        setFocused(textField);
        addDrawableChild(textField);

        // 2) image buttons anchored to bottom corners
        int btnY = bgY + BG_HEIGHT - BUTTON_HEIGHT - BUTTON_MARGIN_Y;

        cancelButton = new ButtonWidget(
                bgX + BUTTON_MARGIN_X, btnY,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Text.empty(),
                widget -> close(),
                widget -> Text.empty()
        ) {
            @Override
            protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
                Identifier tex = isHovered()
                        ? textures.GetUI("chat-button-exit-hover")
                        : textures.GetUI("chat-button-exit");
                ctx.drawTexture(tex, getX(), getY(), 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                        BUTTON_WIDTH, BUTTON_HEIGHT);
            }
        };
        addDrawableChild(cancelButton);

        sendButton = new ButtonWidget(
                bgX + BG_WIDTH - BUTTON_WIDTH - BUTTON_MARGIN_X, btnY,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Text.empty(),
                widget -> sendChatMessage(),
                widget -> Text.empty()
        ) {
            @Override
            protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
                Identifier tex = isHovered()
                        ? textures.GetUI("chat-button-done-hover")
                        : textures.GetUI("chat-button-done");
                ctx.drawTexture(tex, getX(), getY(), 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                        BUTTON_WIDTH, BUTTON_HEIGHT);
            }
        };
        sendButton.active = false;
        addDrawableChild(sendButton);
    }

    private void sendChatMessage() {
        // Send message to server
        String message = textField.getText();
        ClientPackets.sendChat(screenEntity, message);
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && textField.isFocused()
                && !textField.getText().isEmpty()) {
            sendChatMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onTextChanged(String text) {
        // Enable the button only if the text field is not empty
        sendButton.active = !text.isEmpty();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Let the base class draw its background and children first
        super.render(context, mouseX, mouseY, delta);
        // draw label above text field
        int labelW = textRenderer.getWidth(labelText);
        int labelX = (this.width - labelW) / 2;
        int labelY = textField.getY() - 15;
        context.drawTextWithShadow(textRenderer, labelText, labelX, labelY, 0xFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw default gradient or texture from the base game
        super.renderBackground(context, mouseX, mouseY, delta);

        // Draw the custom background texture centered on screen
        Identifier bgTex = textures.GetUI("chat-background");
        if (bgTex != null) {
            context.drawTexture(bgTex, bgX, bgY, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);
        } else {
            context.fillGradient(0, 0, this.width, this.height, 0xA3000000, 0xA3000000);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        ClientPackets.sendCloseChat();
    }

    /**
     * A simple multi-line text field that wraps text to maxLines.
     */
    private static class MultiLineTextField extends TextFieldWidget {
        private final int maxLines;
        private final TextRenderer renderer;

        public MultiLineTextField(TextRenderer renderer,
                                  int x, int y, int width, int height,
                                  Text text, int maxLines) {
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
        public void write(String str) {
            super.write(str);
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
}
