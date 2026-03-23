// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * MobPickerScreen shows a searchable, scrollable list of all Minecraft mob entity types
 * with checkboxes so the player can select which mobs belong to the whitelist or blacklist.
 * It is opened from a button in the ModMenuIntegration config screen.
 */
public class MobPickerScreen extends Screen {

    private final Screen parent;
    private final String pickerTitle;
    private final List<String> selected;           // currently selected mob IDs
    private final Consumer<List<String>> callback; // called with updated list when Done is clicked

    // All mob IDs sorted alphabetically, filtered by the search box
    private final List<String> allMobIds;
    private MobList mobList;
    private EditBox searchBox;

    public MobPickerScreen(Screen parent, String pickerTitle,
                           List<String> current, Consumer<List<String>> callback) {
        super(Component.literal(pickerTitle));
        this.parent = parent;
        this.pickerTitle = pickerTitle;
        this.selected = new ArrayList<>(current);
        this.callback = callback;
        this.allMobIds = buildMobIdList();
    }

    /** Collect all entity types that are actual mobs (not players, items, arrows, etc.). */
    private static List<String> buildMobIdList() {
        List<String> ids = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(entry -> {
            MobCategory cat = entry.getValue().getCategory();
            if (cat != MobCategory.MISC) {
                ResourceLocation key = entry.getKey().location();
                ids.add(key.toString());
            }
        });
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    @Override
    protected void init() {
        // Search box at top
        searchBox = new EditBox(font, width / 2 - 150, 30, 300, 20,
                Component.literal("Search mobs..."));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(query -> {
            if (mobList != null) mobList.filter(query);
        });
        addWidget(searchBox);

        // Scrollable mob list in the centre
        mobList = new MobList(minecraft, width, height - 80, 60, 20);
        mobList.filter("");
        addWidget(mobList);

        // Done button
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            callback.accept(new ArrayList<>(selected));
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 80, height - 30, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // renderBackground calls blur — which crashes in 1.21.7 when opened from Cloth Config.
        // renderMenuBackground is safe: it draws the dirt/paper texture without applying blur.
        renderMenuBackground(graphics);
        mobList.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, pickerTitle, width / 2, 12, 0xFFFFFF);
        searchBox.render(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) return searchBox.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (searchBox.isFocused()) return searchBox.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    // ── Scrollable list ───────────────────────────────────────────────────────

    private class MobList extends ObjectSelectionList<MobList.MobEntry> {

        MobList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        /** Re-populate the list with only entries matching the search query. */
        void filter(String query) {
            clearEntries();
            String lower = query.toLowerCase(Locale.ENGLISH);
            for (String id : allMobIds) {
                if (lower.isEmpty() || id.contains(lower)) {
                    addEntry(new MobEntry(id));
                }
            }
        }

        @Override
        public int getRowWidth() { return width - 20; }

        class MobEntry extends Entry<MobEntry> {
            private final String mobId;

            MobEntry(String mobId) { this.mobId = mobId; }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left,
                               int rowWidth, int rowHeight, int mouseX, int mouseY,
                               boolean isHovered, float delta) {
                boolean isSelected = selected.contains(mobId);
                int color = isSelected ? 0xFF55FF55 : (isHovered ? 0xFFDDDDDD : 0xFFAAAAAA);

                // Checkbox: [x] or [ ]
                graphics.drawString(font, isSelected ? "[x] " : "[ ] ", left + 4, top + 4, color);
                graphics.drawString(font, mobId, left + 28, top + 4, color);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (selected.contains(mobId)) {
                    selected.remove(mobId);
                } else {
                    selected.add(mobId);
                }
                return true;
            }

            public Component getNarration() {
                return Component.literal(mobId);
            }
        }
    }
}
