// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.goals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

/**
 * The {@code ProtectPlayerGoal} class instructs a Mob Entity to show aggression towards any attacker
 * of the current player.
 */
public class ProtectPlayerGoal extends AttackPlayerGoal {
    protected final LivingEntity protectedEntity;
    protected int lastAttackedTime;

    public ProtectPlayerGoal(LivingEntity protectEntity, MobEntity attackerEntity, double speed) {
        super(null, attackerEntity, speed);
        this.protectedEntity = protectEntity;
        this.lastAttackedTime = 0;
    }

    @Override
    public boolean canStart() {
        LivingEntity lastAttackedByEntity = this.protectedEntity.getLastAttacker();
        int i = this.protectedEntity.getLastAttackedTime();
        if (i != this.lastAttackedTime && lastAttackedByEntity != null && !this.attackerEntity.equals(lastAttackedByEntity)) {
            // Set target to attack
            this.lastAttackedTime = i;
            this.targetEntity = lastAttackedByEntity;
            this.attackerEntity.setTarget(this.targetEntity);
        }

        if (this.targetEntity != null && !this.targetEntity.isAlive()) {
            // clear dead target
            this.targetEntity = null;
        }

        return super.canStart();
    }
}
