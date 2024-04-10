package com.owlmaddie.goals;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.passive.*;

/**
 * The {@code SpeedControls} class has methods to return adjusted MaxSpeed values for different MobEntity instances.
 * Unfortunately, some entities need to be hard-coded here, for a comfortable max speed.
 */
public class SpeedControls {
    public static float getMaxSpeed(MobEntity entity) {
        float speed = 1.0F;

        // Adjust speeds for certain Entities
        if (entity instanceof AxolotlEntity) {
            speed = 0.5F;
        } else if (entity instanceof VillagerEntity) {
            speed = 0.5F;
        } else if (entity instanceof AllayEntity) {
            speed = 1.5F;
        } else if (entity instanceof CamelEntity) {
            speed = 3F;
        } else if (entity instanceof AbstractDonkeyEntity) {
            speed = 1.5F;
        } else if (entity instanceof FrogEntity) {
            speed = 2F;
        } else if (entity instanceof PandaEntity) {
            speed = 2F;
        } else if (entity instanceof VindicatorEntity) {
            speed = 0.75F;
        } else if (entity instanceof WitchEntity) {
            speed = 0.75F;
        } else if (entity instanceof RabbitEntity) {
            speed = 1.5F;
        }

        return speed;
    }
}

