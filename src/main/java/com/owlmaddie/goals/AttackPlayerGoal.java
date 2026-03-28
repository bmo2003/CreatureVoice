// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.goals;

import com.owlmaddie.controls.DamageHelper;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;

import static com.owlmaddie.network.ServerPackets.ATTACK_PARTICLE;

/**
 * The {@code AttackPlayerGoal} class instructs a Mob Entity to show aggression towards a target Entity.
 * For passive entities like chickens (or hostile entities in creative mode), damage is simulated with particles.
 */
public class AttackPlayerGoal extends PlayerBaseGoal {
    /**
     * Populated in start(), cleared in stop(). Read by NpcLifeManager every server tick
     * (END_SERVER_TICK) to re-issue moveTo AFTER Brain.tick() has run for villagers —
     * same Brain-override fix used by GoToPositionGoal and FleePlayerGoal.
     */
    public static final ConcurrentHashMap<UUID, AttackPlayerGoal> ATTACK_MOBS = new ConcurrentHashMap<>();

    protected final Mob attackerEntity;
    protected final double speed;
    protected final boolean forceAttack; // when true, skip native-attack checks (used for ATTACK_NPC)
    protected enum EntityState { MOVING_TOWARDS_PLAYER, IDLE, CHARGING, ATTACKING, LEAPING }
    protected EntityState currentState = EntityState.IDLE;
    protected int cooldownTimer = 0;
    protected final int CHARGE_TIME = 12; // Time before leaping / attacking
    protected final double MOVE_DISTANCE = 200D; // 20 blocks away
    protected final double CHARGE_DISTANCE = 25D; // 5 blocks away
    protected final double ATTACK_DISTANCE = 4D; // 2 blocks away

    public AttackPlayerGoal(LivingEntity targetEntity, Mob attackerEntity, double speed) {
        this(targetEntity, attackerEntity, speed, false);
    }

    public AttackPlayerGoal(LivingEntity targetEntity, Mob attackerEntity, double speed, boolean forceAttack) {
        super(targetEntity);
        this.attackerEntity = attackerEntity;
        this.speed = speed;
        this.forceAttack = forceAttack;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return super.canUse() && isGoalActive();
    }

    @Override
    public boolean canContinueToUse() {
        return super.canUse() && isGoalActive();
    }

    @Override
    public void start() {
        // Register so NpcLifeManager can re-issue navigation after Brain.tick()
        ATTACK_MOBS.put(attackerEntity.getUUID(), this);
    }

    @Override
    public void stop() {
        ATTACK_MOBS.remove(attackerEntity.getUUID());
        attackerEntity.getNavigation().stop();
        // Clear the mob's target so native goals don't resume attacking a dead entity
        attackerEntity.setTarget(null);
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
        boolean isNearby = this.attackerEntity.distanceToSqr(this.targetEntity) < MOVE_DISTANCE;

        // ATTACK_NPC (forceAttack=true): always pursue regardless of native-attack capability.
        // Without this, monsters like zombies or skeletons would fail the goal because
        // canAttack(target)=true makes hasNativeAttacksButCannotTarget=false, so isGoalActive
        // returns false and the goal never runs its tick loop.
        // Uses 200-block range (squared) so NPCs track distant targets the same way SPEAK_TO does.
        if (forceAttack) {
            return this.attackerEntity.distanceToSqr(this.targetEntity) < 200.0 * 200.0;
        }

        // Check if the attacker is nearby and no native attacks
        boolean isNearbyAndNoNativeAttacks = isNearby && !hasNativeAttacks();

        // Check if it has native attacks but can't target (e.g., creative mode)
        LivingEntity livingAttackerEntity = this.attackerEntity;
        boolean hasNativeAttacksButCannotTarget = isNearby && hasNativeAttacks() && !livingAttackerEntity.canAttack(this.targetEntity);

        // Return true if either condition is met
        return isNearbyAndNoNativeAttacks || hasNativeAttacksButCannotTarget;
    }

    private boolean hasNativeAttacks() {
        // Does this entity have native attacks
        return this.attackerEntity instanceof Monster ||
                this.attackerEntity instanceof NeutralMob ||
                this.attackerEntity instanceof RangedAttackMob ||
                this.attackerEntity instanceof AbstractGolem;
    }

    private void performAttack() {
        // Track the attacker (needed for protect to work)
        if (!this.attackerEntity.equals(this.targetEntity)) {
            this.targetEntity.setLastHurtByMob(this.attackerEntity);
        }

        // For passive entities (or hostile in creative mode), apply damage to simulate a melee attack.
        // 4HP makes the hit clearly visible (knockback + red flash) and kills in a reasonable time.
        DamageHelper.applyLeapDamage(attackerEntity, targetEntity, 4.0F);

        // Play damage sound
        this.attackerEntity.playSound(SoundEvents.PLAYER_HURT, 1F, 1F);

        // Spawn red particles to simulate 'injury'
        int numParticles = ThreadLocalRandom.current().nextInt(2, 7);  // Random number between 2 (inclusive) and 7 (exclusive)
        ((ServerLevel) this.attackerEntity.level()).sendParticles((ParticleOptions) ATTACK_PARTICLE,
                this.targetEntity.getX(), this.targetEntity.getY(0.5D), this.targetEntity.getZ(),
                numParticles, 0.5, 0.5, 0.1, 0.4);
    }

    @Override
    public void tick() {
        double squaredDistanceToPlayer = this.attackerEntity.distanceToSqr(this.targetEntity);
        this.attackerEntity.getLookControl().setLookAt(this.targetEntity, 30.0F, 30.0F);

        // Re-issue navigation every tick so Brain-based mobs (villagers, etc.) keep pursuing
        // their target even though the Brain AI tries to override the path each tick.
        if (squaredDistanceToPlayer >= ATTACK_DISTANCE) {
            this.attackerEntity.getNavigation().moveTo(this.targetEntity, this.speed);
        }

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
                if (squaredDistanceToPlayer < CHARGE_DISTANCE) {
                    currentState = EntityState.CHARGING;
                } else {
                    currentState = EntityState.IDLE;
                }
                break;

            case CHARGING:
                this.attackerEntity.getNavigation().moveTo(this.targetEntity, this.speed / 2.5D);
                if (cooldownTimer <= 0) {
                    currentState = EntityState.LEAPING;
                }
                break;

            case LEAPING:
                // Leap towards the target
                Vec3 leapDirection = new Vec3(this.targetEntity.getX() - this.attackerEntity.getX(), 0.1D, this.targetEntity.getZ() - this.attackerEntity.getZ()).normalize().scale(1.0);
                this.attackerEntity.setDeltaMovement(leapDirection);
                this.attackerEntity.hurtMarked = true;
                currentState = EntityState.ATTACKING;
                break;

            case ATTACKING:
                this.attackerEntity.getNavigation().moveTo(this.targetEntity, this.speed / 2.5D);
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

    public Mob getAttacker()        { return attackerEntity; }
    public LivingEntity getTarget() { return targetEntity; }
    public double getSpeed()        { return speed; }

}
