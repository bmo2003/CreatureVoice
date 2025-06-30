// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

/**
 * Returns the per-frame tick-delta for Minecraft. This API changes in later
 * versions of Minecraft, so we split this into a helper class.
 */
public final class TickDelta {
    private TickDelta() { }          // utility class

    public static float get(WorldRenderContext ctx) {
        return ctx.tickDelta();
    }
}
