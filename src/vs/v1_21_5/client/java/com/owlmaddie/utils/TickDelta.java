// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

public final class TickDelta {
    private TickDelta(){}

    /**
     * 1.21.5+: RenderTickCounter.getTickProgress(false) replaces getTickDelta(false)
     */
    public static float get(WorldRenderContext ctx) {
        return ctx.tickCounter().getGameTimeDeltaPartialTick(false);
    }
}
