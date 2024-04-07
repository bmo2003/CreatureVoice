package com.owlmaddie.goals;

import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The {@code AttackPlayerGoal} class instructs a Mob Entity to show aggression towards the current player.
 * For passive entities like chickens, this could manifest as humorous behaviors rather than actual damage.
 */
public class AttackPlayerGoal extends Goal {
    private final MobEntity entity;
    private ServerPlayerEntity targetPlayer;
    private final EntityNavigation navigation;
    private final double speed;
    private int aggressionTimer;

    public AttackPlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed) {
        this.targetPlayer = player;
        this.entity = entity;
        this.speed = speed;
        this.navigation = entity.getNavigation();
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        this.aggressionTimer = 0; // Initialize the timer used to control aggression intervals.
    }

    @Override
    public boolean canStart() {
        // Can start showing aggression if the player is within a certain range.
        double squaredDistanceToPlayer = this.entity.squaredDistanceTo(this.targetPlayer);
        return squaredDistanceToPlayer < 25; // Example range: within 5 blocks.
    }

    @Override
    public boolean shouldContinue() {
        // Continue showing aggression as long as the player is alive and within range.
        return this.targetPlayer.isAlive() && this.entity.squaredDistanceTo(this.targetPlayer) < 25;
    }

    @Override
    public void stop() {
        this.navigation.stop();
        this.aggressionTimer = 0; // Reset the aggression timer.
    }

    @Override
    public void tick() {
        this.entity.getLookControl().lookAt(this.targetPlayer, 30.0F, 30.0F); // Make the entity face the player
        double squaredDistanceToPlayer = this.entity.squaredDistanceTo(this.targetPlayer);

        // Check if the entity is close enough to 'attack'
        if (squaredDistanceToPlayer < 3.0D) { // Using a smaller range for direct attacks
            if (--this.aggressionTimer <= 0) {
                this.aggressionTimer = 20; // Reset the timer; 'attack' every second

                if (this.entity instanceof HostileEntity ||
                        this.entity instanceof Angerable ||
                        this.entity instanceof RangedAttackMob) {
                    // Entity is capable of attacking
                    this.entity.tryAttack(this.targetPlayer);
                } else {
                    // For passive entities, apply minimal damage and simulate a 'leap'
                    this.targetPlayer.damage(this.entity.getDamageSources().generic(), 1.0F);

                    // Leap towards the player to simulate the 'attack'
                    Vec3d leapDirection = new Vec3d(this.targetPlayer.getX() - this.entity.getX(), 0.0D, this.targetPlayer.getZ() - this.entity.getZ()).normalize().multiply(0.5);
                    this.entity.setVelocity(leapDirection);
                    this.entity.velocityModified = true;

                    // Spawn red particles to simulate 'injury'
                    ((ServerWorld)this.entity.getWorld()).spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                            this.targetPlayer.getX(),
                            this.targetPlayer.getBodyY(0.5D),
                            this.targetPlayer.getZ(),
                            10, // number of particles
                            0.1, 0.1, 0.1, 0.2); // speed and randomness
                }
            }
        } else {
            // Move towards the player if not in attack range
            this.navigation.startMovingTo(this.targetPlayer, this.speed);
        }
    }

}
