// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.controls;

import net.minecraft.world.phys.Vec3;

/* Interface for adding a new method to SquidEntity, to control swim direction */
public interface ISquidEntity {
    /** Force the internal swim vector to this value. */
    void forceSwimVector(Vec3 vec);
}