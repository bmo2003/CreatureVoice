package com.owlmaddie.goals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The {@code AttackPlayerGoal} class instructs a Mob Entity to show aggression towards a target Entity.
 * For passive entities like chickens (or hostile entities in creative mode), damage is simulated with particles.
 */
public class AttackPlayerGoal extends PlayerBaseGoal {
    protected final MobEntity attackerEntity;
    protected final double speed;
    protected enum EntityState { MOVING_TOWARDS_PLAYER, IDLE, CHARGING, ATTACKING, LEAPING }
    protected EntityState currentState = EntityState.IDLE;
    protected int cooldownTimer = 0;
    protected final int CHARGE_TIME = 15; // Time before leaping / attacking
    protected final double MOVE_DISTANCE = 200D; // 20 blocks away
    protected final double CHARGE_DISTANCE = 25D; // 5 blocks away
    protected final double ATTACK_DISTANCE = 4D; // 2 blocks away

    public AttackPlayerGoal(LivingEntity targetEntity, MobEntity attackerEntity, double speed) {
        super(targetEntity);
        this.attackerEntity = attackerEntity;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.TARGET));
    }

    @Override
    public boolean canStart() {
        return super.canStart() && isGoalActive();
    }

    @Override
    public boolean shouldContinue() {
        return super.canStart() && isGoalActive();
    }

    @Override
    public void stop() {
    }

    private boolean isGoalActive() {
        if (this.targetEntity == null || (this.targetEntity != null && !this.targetEntity.isAlive())) {
            return false;
        }

        // Set the attack target (if not self)
        if (!this.attackerEntity.equals(this.targetEntity)) {
            this.attackerEntity.setTarget(this.targetEntity);
        }

        // Is nearby to target
        boolean isNearby = this.attackerEntity.squaredDistanceTo(this.targetEntity) < MOVE_DISTANCE;

        // Check if the attacker is nearby and no native attacks
        boolean isNearbyAndNoNativeAttacks = isNearby && !hasNativeAttacks();

        // Check if it has native attacks but can't target (e.g., creative mode)
        LivingEntity livingAttackerEntity = this.attackerEntity;
        boolean hasNativeAttacksButCannotTarget = isNearby && hasNativeAttacks() && !livingAttackerEntity.canTarget(this.targetEntity);

        // Return true if either condition is met
        return isNearbyAndNoNativeAttacks || hasNativeAttacksButCannotTarget;
    }

    private boolean hasNativeAttacks() {
        // Does this entity have native attacks
        return this.attackerEntity instanceof HostileEntity ||
                this.attackerEntity instanceof Angerable ||
                this.attackerEntity instanceof RangedAttackMob ||
                this.attackerEntity instanceof GolemEntity;
    }

    private void performAttack() {
        // Track the attacker (needed for protect to work)
        if (!this.attackerEntity.equals(this.targetEntity)) {
            this.targetEntity.setAttacker(this.attackerEntity);
        }

        // For passive entities (or hostile in creative mode), apply minimal damage to simulate a 'leap' / 'melee' attack
        this.targetEntity.damage(this.attackerEntity.getDamageSources().generic(), 1.0F);

        // Play damage sound
        this.attackerEntity.playSound(SoundEvents.ENTITY_PLAYER_HURT, 1F, 1F);

        // Spawn red particles to simulate 'injury'
        ((ServerWorld) this.attackerEntity.getWorld()).spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                this.targetEntity.getX(),
                this.targetEntity.getBodyY(0.5D),
                this.targetEntity.getZ(),
                10, // number of particles
                0.1, 0.1, 0.1, 0.2); // speed and randomness
    }

    @Override
    public void tick() {
        double squaredDistanceToPlayer = this.attackerEntity.squaredDistanceTo(this.targetEntity);
        this.attackerEntity.getLookControl().lookAt(this.targetEntity, 30.0F, 30.0F);

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
                this.attackerEntity.getNavigation().startMovingTo(this.targetEntity, this.speed);
                if (squaredDistanceToPlayer < CHARGE_DISTANCE) {
                    currentState = EntityState.CHARGING;
                } else {
                    currentState = EntityState.IDLE;
                }
                break;

            case CHARGING:
                this.attackerEntity.getNavigation().startMovingTo(this.targetEntity, this.speed / 2.5D);
                if (cooldownTimer <= 0) {
                    currentState = EntityState.LEAPING;
                }
                break;

            case LEAPING:
                // Leap towards the player
                Vec3d leapDirection = new Vec3d(this.targetEntity.getX() - this.attackerEntity.getX(), 0.1D, this.targetEntity.getZ() - this.attackerEntity.getZ()).normalize().multiply(1.0);
                this.attackerEntity.setVelocity(leapDirection);
                this.attackerEntity.velocityModified = true;

                currentState = EntityState.ATTACKING;
                break;

            case ATTACKING:
                // Attack player
                this.attackerEntity.getNavigation().startMovingTo(this.targetEntity, this.speed / 2.5D);
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
