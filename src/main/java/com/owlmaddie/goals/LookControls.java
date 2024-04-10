package com.owlmaddie.goals;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * The {@code LookControls} class allows an entity to look at the player, with some custom fixes
 * for certain mobs that refuse to use the normal LookControls (i.e. Slime).
 */
public class LookControls {
    public static void LookAtEntity(ServerPlayerEntity player, MobEntity entity) {

        // Fix Slimes (who handle looking differently)
        if (entity instanceof SlimeEntity) {
            // Calculate direction which entity needs to face
            double deltaX = player.getX() - entity.getX();
            double deltaZ = player.getZ() - entity.getZ();
            float currentYaw = entity.getYaw();

            float targetYaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
            float yawDifference = MathHelper.wrapDegrees(targetYaw - currentYaw); // Ensures the difference is within -180 to 180
            float yawChange = MathHelper.clamp(yawDifference, -10.0F, 10.0F); // Limits the change to 10 degrees

            ((SlimeEntity.SlimeMoveControl) entity.getMoveControl()).look(currentYaw + yawChange, false);

        } else {
            // Make the entity look at the player
            entity.getLookControl().lookAt(player, 10.0F, (float)entity.getMaxLookPitchChange());
        }

    }
}
