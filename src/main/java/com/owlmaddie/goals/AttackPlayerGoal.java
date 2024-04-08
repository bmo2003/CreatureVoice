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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The {@code AttackPlayerGoal} class instructs a Mob Entity to show aggression towards the current player.
 * For passive entities like chickens, damage is simulated with particles. But all MobEntity instances can damage
 * the player.
 */
public class AttackPlayerGoal extends Goal {
    private final MobEntity entity;
    private ServerPlayerEntity targetPlayer;
    private final EntityNavigation navigation;
    private final double speed;
    enum EntityState { MOVING_TOWARDS_PLAYER, IDLE, CHARGING, ATTACKING, LEAPING }
    private EntityState currentState = EntityState.IDLE;
    private int cooldownTimer = 0;
    private final int CHARGE_TIME = 15; // Time before leaping / attacking
    private final double MOVE_DISTANCE = 200D; // 20 blocks away
    private final double CHARGE_DISTANCE = 25D; // 5 blocks away
    private final double ATTACK_DISTANCE = 4D; // 2 blocks away

    public AttackPlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed) {
        this.targetPlayer = player;
        this.entity = entity;
        this.speed = speed;
        this.navigation = entity.getNavigation();
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Can start showing aggression if the player is within a certain range.
        return this.entity.squaredDistanceTo(this.targetPlayer) < MOVE_DISTANCE;
    }

    @Override
    public boolean shouldContinue() {
        // Continue showing aggression as long as the player is alive and within range.
        return this.targetPlayer.isAlive() && this.entity.squaredDistanceTo(this.targetPlayer) < MOVE_DISTANCE;
    }

    @Override
    public void stop() {
    }

    private void performAttack() {
        // Check if the entity is a type that is capable of attacking
        if (this.entity instanceof HostileEntity || this.entity instanceof Angerable || this.entity instanceof RangedAttackMob) {
            // Entity attacks the player
            this.entity.tryAttack(this.targetPlayer);
        } else {
            // For passive entities, apply minimal damage to simulate a 'leap' attack
            this.targetPlayer.damage(this.entity.getDamageSources().generic(), 1.0F);

            // Play damage sound
            this.targetPlayer.playSound(SoundEvents.ENTITY_PLAYER_HURT, 1F, 1F);

            // Spawn red particles to simulate 'injury'
            ((ServerWorld) this.entity.getWorld()).spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    this.targetPlayer.getX(),
                    this.targetPlayer.getBodyY(0.5D),
                    this.targetPlayer.getZ(),
                    10, // number of particles
                    0.1, 0.1, 0.1, 0.2); // speed and randomness
        }
    }

    @Override
    public void tick() {
        double squaredDistanceToPlayer = this.entity.squaredDistanceTo(this.targetPlayer);
        this.entity.getLookControl().lookAt(this.targetPlayer, 30.0F, 30.0F); // Entity faces the player

        // State transitions and actions
        switch (currentState) {
            case IDLE:
                cooldownTimer = CHARGE_TIME;
                if (squaredDistanceToPlayer < ATTACK_DISTANCE) {
                    currentState = EntityState.ATTACKING;
                } else if (squaredDistanceToPlayer < CHARGE_DISTANCE) {
                    currentState = EntityState.CHARGING;
                } else if (squaredDistanceToPlayer < MOVE_DISTANCE) {
                    currentState = EntityState.MOVING_TOWARDS_PLAYER;
                }
                break;

            case MOVING_TOWARDS_PLAYER:
                this.entity.getNavigation().startMovingTo(this.targetPlayer, this.speed);
                if (squaredDistanceToPlayer < CHARGE_DISTANCE) {
                    currentState = EntityState.CHARGING;
                } else {
                    currentState = EntityState.IDLE;
                }
                break;

            case CHARGING:
                this.entity.getNavigation().startMovingTo(this.targetPlayer, this.speed / 2D);
                if (cooldownTimer <= 0) {
                    currentState = EntityState.LEAPING;
                }
                break;

            case LEAPING:
                // Leap towards the player
                Vec3d leapDirection = new Vec3d(this.targetPlayer.getX() - this.entity.getX(), 0.1D, this.targetPlayer.getZ() - this.entity.getZ()).normalize().multiply(1.0);
                this.entity.setVelocity(leapDirection);
                this.entity.velocityModified = true;

                currentState = EntityState.ATTACKING;
                break;

            case ATTACKING:
                // Attack player
                this.entity.getNavigation().startMovingTo(this.targetPlayer, this.speed / 2D);
                if (squaredDistanceToPlayer < ATTACK_DISTANCE && cooldownTimer <= 0) {
                    this.performAttack();
                    currentState = EntityState.IDLE;
                } else if (cooldownTimer <= 0) {
                    currentState = EntityState.IDLE;
                }
                break;
        }

        // decrement cool down
        cooldownTimer--;
    }

}
