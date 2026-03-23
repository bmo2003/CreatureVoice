// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single-row Cloth Config entry that shows "N mob(s) selected" and a button
 * to open the MobPickerScreen. Changes are saved back immediately via the
 * Consumer passed at construction time.
 */
public class MobListButtonEntry extends AbstractConfigListEntry<List<String>> {

    private List<String> value;
    // The human-readable field name (e.g. "Mob Whitelist") shown inside the button
    private final String fieldDisplayName;
    private final Button openButton;

    public MobListButtonEntry(Component label,
                              List<String> initial,
                              Consumer<List<String>> saveConsumer,
                              Supplier<Screen> parentScreenSupplier,
                              String pickerTitle) {
        super(label, false);
        this.value = new ArrayList<>(initial);
        this.fieldDisplayName = label.getString();

        this.openButton = Button.builder(buildButtonLabel(this.value, this.fieldDisplayName), btn -> {
            Screen picker = new MobPickerScreen(
                    parentScreenSupplier.get(),
                    pickerTitle,
                    this.value,
                    newList -> {
                        this.value = new ArrayList<>(newList);
                        btn.setMessage(buildButtonLabel(this.value, this.fieldDisplayName));
                        saveConsumer.accept(this.value);
                    });
            Minecraft.getInstance().setScreen(picker);
        }).build();
    }

    // Embed the field name into the button text so it reads e.g.
    // "Mob Whitelist: None — click to add" or "Mob Whitelist: 3 mob(s) — click to edit"
    private static Component buildButtonLabel(List<String> list, String fieldName) {
        if (list.isEmpty()) return Component.literal(fieldName + ": None — click to add");
        return Component.literal(fieldName + ": " + list.size() + " mob(s) — click to edit");
    }

    @Override
    public List<String> getValue() { return value; }

    @Override
    public Optional<List<String>> getDefaultValue() { return Optional.of(new ArrayList<>()); }

    @Override
    public void save() { /* saved eagerly via callback */ }

    @Override
    public boolean isEdited() { return false; }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x,
                       int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        // The field name is embedded in the button text itself, so the button
        // spans the full entry width — no separate label draw needed.
        openButton.setX(x);
        openButton.setY(y);
        openButton.setWidth(entryWidth);
        openButton.setHeight(entryHeight);
        openButton.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(openButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(openButton);
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {}
}
