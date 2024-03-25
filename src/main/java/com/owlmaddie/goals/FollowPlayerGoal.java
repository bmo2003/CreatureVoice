package com.owlmaddie.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

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
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return this.targetPlayer != null;
    }

    @Override
    public boolean shouldContinue() {
        return this.targetPlayer != null && this.targetPlayer.isAlive();
    }

    @Override
    public void stop() {
        this.targetPlayer = null;
        this.navigation.stop();
    }

    @Override
    public void tick() {
        this.entity.getLookControl().lookAt(this.targetPlayer, 10.0F, (float) this.entity.getMaxLookPitchChange());
        if (this.entity.squaredDistanceTo(this.targetPlayer) > 2.25) {
            this.navigation.startMovingTo(this.targetPlayer, this.speed);
        }
    }
}
