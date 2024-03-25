package com.owlmaddie.goals;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;

import java.lang.reflect.Field;

/**
 * The {@code GoalUtils} class uses reflection to extend the MobEntity class
 * and add a public GoalSelector method.
 */
public class GoalUtils {

    private static Field goalSelectorField;

    static {
        try {
            goalSelectorField = MobEntity.class.getDeclaredField("goalSelector");
            goalSelectorField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Handle the exception, perhaps the field name has changed
        }
    }

    public static GoalSelector getGoalSelector(MobEntity mobEntity) {
        try {
            return (GoalSelector) goalSelectorField.get(mobEntity);
        } catch (IllegalAccessException e) {
            // Handle the exception
            return null;
        }
    }
}
