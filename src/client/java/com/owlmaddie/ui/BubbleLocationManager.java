// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code BubbleLocationManager} class is used to keep track of the currently rendered chat bubbles,
 * to simplify click handling of nearby chat bubbles. This data includes the exact location of the chat
 * bubbles, and their rotations (pitch, yaw).
 */
public class BubbleLocationManager {
    private static final Map<UUID, BubbleData> bubbleDataMap = new ConcurrentHashMap<>();

    public static void updateBubbleData(UUID entityId, Vec3 position, double width, double height, double yaw, double pitch) {
        bubbleDataMap.put(entityId, new BubbleData(position, width, height, yaw, pitch));
    }

    public static BubbleData getBubbleData(UUID entityId) {
        return bubbleDataMap.get(entityId);
    }

    public static class BubbleData {
        public final Vec3 position;
        public final double width;
        public final double height;
        public final double yaw;
        public final double pitch;

        public BubbleData(Vec3 position, double width, double height, double yaw, double pitch) {
            this.position = position;
            this.width = width;
            this.height = height;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public static void performCleanup(List<UUID> activeEntityIds) {
        // Retain only entries for active entities
        bubbleDataMap.keySet().retainAll(activeEntityIds);
    }

    public static Map<UUID, BubbleData> getAllBubbleData() {
        return Collections.unmodifiableMap(bubbleDataMap);
    }
}

