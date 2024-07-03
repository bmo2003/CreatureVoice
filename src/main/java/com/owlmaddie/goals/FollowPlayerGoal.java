package com.owlmaddie.goals;

import com.owlmaddie.controls.LookControls;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The {@code FollowPlayerGoal} class instructs a Mob Entity to follow the current player.
 */
public class FollowPlayerGoal extends Goal {
    private final MobEntity entity;
    private ServerPlayerEntity targetPlayer;
    private final EntityNavigation navigation;
    private final double speed;

    public FollowPlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed) {
        this.targetPlayer = player;
        this.entity = entity;
        this.speed = speed;
        this.navigation = entity.getNavigation();
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Start only if the target player is more than 8 blocks away
        return this.targetPlayer != null && this.entity.squaredDistanceTo(this.targetPlayer) > 64;
    }

    @Override
    public boolean shouldContinue() {
        // Continue unless the entity gets within 3 blocks of the player
        return this.targetPlayer != null && this.targetPlayer.isAlive() && this.entity.squaredDistanceTo(this.targetPlayer) > 9;
    }

    @Override
    public void stop() {
        // Stop the entity temporarily
        this.navigation.stop();
    }

    @Override
    public void tick() {
        if (this.entity.squaredDistanceTo(this.targetPlayer) > 256) {
            // If the entity is too far away (more than 16 blocks), teleport it within 8 blocks of the player
            if (this.entity instanceof EndermanEntity || this.entity instanceof EndermiteEntity || this.entity instanceof ShulkerEntity) {
                Vec3d targetPos = findTeleportPosition(8);
                if (targetPos != null) {
                    this.entity.teleport(targetPos.x, targetPos.y, targetPos.z);
                }
            }
        } else {
            // Look at the player and start moving towards them
            LookControls.lookAtPlayer(this.targetPlayer, this.entity);
            this.navigation.startMovingTo(this.targetPlayer, this.speed);
        }
    }

    private Vec3d findTeleportPosition(int distance) {
        return FuzzyTargeting.findTo((PathAwareEntity)this.entity, distance, distance, this.targetPlayer.getPos());
    }
}
