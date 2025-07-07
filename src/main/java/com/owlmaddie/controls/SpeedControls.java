// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.controls;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

/**
 * The {@code SpeedControls} class has methods to return adjusted MaxSpeed values for different MobEntity instances.
 * Unfortunately, some entities need to be hard-coded here, for a comfortable max speed.
 */
public class SpeedControls {
    public static float getMaxSpeed(Mob entity) {
        float speed = 1.0F;

        // Adjust speeds for certain Entities
        if (entity instanceof Axolotl) {
            speed = 0.5F;
        } else if (entity instanceof Villager) {
            speed = 0.5F;
        } else if (entity instanceof AbstractIllager) {
            speed = 0.5F;
        } else if (entity instanceof Witch) {
            speed = 0.5F;
        } else if (entity instanceof WanderingTrader) {
            speed = 0.5F;
        } else if (entity instanceof Allay) {
            speed = 1.5F;
        } else if (entity instanceof Camel) {
            speed = 3F;
        } else if (entity instanceof AbstractChestedHorse) {
            speed = 1.5F;
        } else if (entity instanceof Frog) {
            speed = 2F;
        } else if (entity instanceof Panda) {
            speed = 2F;
        } else if (entity instanceof Rabbit) {
            speed = 1.5F;
        } else if (entity instanceof Phantom) {
            speed = 0.2F;
        }

        return speed;
    }
}

