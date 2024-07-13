package com.owlmaddie.goals;

import com.owlmaddie.controls.LookControls;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The {@code FollowPlayerGoal} class instructs a Mob Entity to follow the current target entity.
 */
public class FollowPlayerGoal extends PlayerBaseGoal {
    private final MobEntity entity;
    private final EntityNavigation navigation;
    private final double speed;

    public FollowPlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed) {
        super(player);
        this.entity = entity;
        this.speed = speed;
        this.navigation = entity.getNavigation();
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Start only if the target player is more than 8 blocks away
        return super.canStart() && this.entity.squaredDistanceTo(this.targetEntity) > 64;
    }

    @Override
    public boolean shouldContinue() {
        // Continue unless the entity gets within 3 blocks of the player
        return super.canStart() && this.entity.squaredDistanceTo(this.targetEntity) > 9;
    }

    @Override
    public void stop() {
        // Stop the entity temporarily
        this.navigation.stop();
    }

    @Override
    public void tick() {
        if (this.entity instanceof EndermanEntity || this.entity instanceof EndermiteEntity || this.entity instanceof ShulkerEntity) {
            // Certain entities should teleport to the player if they get too far
            if (this.entity.squaredDistanceTo(this.targetEntity) > 256) {
                Vec3d targetPos = findTeleportPosition(12);
                if (targetPos != null) {
                    this.entity.teleport(targetPos.x, targetPos.y, targetPos.z);
                }
            }
        } else {
            // Look at the player and start moving towards them
            if (this.targetEntity instanceof ServerPlayerEntity) {
                LookControls.lookAtPlayer((ServerPlayerEntity)this.targetEntity, this.entity);
            }
            this.navigation.startMovingTo(this.targetEntity, this.speed);
        }
    }

    private Vec3d findTeleportPosition(int distance) {
        if (this.entity instanceof PathAwareEntity) {
            return FuzzyTargeting.findTo((PathAwareEntity) this.entity, distance, distance, this.targetEntity.getPos());
        }
        return null;
    }
}
