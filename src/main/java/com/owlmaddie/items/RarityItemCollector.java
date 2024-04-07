package com.owlmaddie.items;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Rarity;

import java.util.*;

/**
 * The {@code RarityItemCollector} class is used to find items & entities by rarity
 */
public class RarityItemCollector {

    public static List<String> getItemsByRarity(Rarity rarity, int quantity) {
        List<String> itemsOfSpecificRarity = new ArrayList<>();

        for (Item item : Registries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.getRarity().equals(rarity) &&
                    !item.getName().toString().contains("spawn_egg") &&
                    !item.getName().toString().contains("jukebox") &&
                    !item.getName().toString().contains("slab")) {
                itemsOfSpecificRarity.add(item.getTranslationKey());
            }
        }

        // Shuffle the list to randomize it
        Collections.shuffle(itemsOfSpecificRarity);

        // If the quantity requested is more than the number of available items, return them all
        if (quantity >= itemsOfSpecificRarity.size()) {
            return itemsOfSpecificRarity;
        }

        // Otherwise, return a sublist containing only the number of items requested
        return itemsOfSpecificRarity.subList(0, quantity);
    }

    /*
    Categorize all entities and return a random list filtered by rarity. Rarity is calculated mostly with
    Spawn Group, with a few manual exclusions.
     */
    public static List<String> getEntitiesByRarity(Rarity rarity, int quantity) {
        List<String> categoryCommonEntities = new ArrayList<>();
        List<String> categoryUncommonEntities = new ArrayList<>();
        List<String> categoryRareEntities = new ArrayList<>();
        List<String> entitiesOfSpecificRarity = new ArrayList<>();

        // Categorize spawn groups & entity types into rarity
        Set<String> commonEntities = new HashSet<>(Arrays.asList("creature"));
        Set<String> unCommonEntities = new HashSet<>(Arrays.asList("ambient", "water_ambient", "water_creature",
                "axolotl", "underground_water_creature", "zombie", "spider", "skeleton", "enderman", "drowned",
                "creeper", "slime", "silverfish", "cave_spider", "piglin", "witch", "zombie_villager"));
        Set<String> rareEntities = new HashSet<>(Arrays.asList("monster"));

        // Always exclude these
        Set<String> excludedMonsters = new HashSet<>(Arrays.asList("ender_dragon", "phantom", "bat"));

        // Iterate through entities
        for (EntityType entityType : Registries.ENTITY_TYPE) {
            String entityName = entityType.getUntranslatedName();
            String spawnGroup = entityType.getSpawnGroup().asString();

            if (!excludedMonsters.contains(entityName)) {
                if (commonEntities.contains(spawnGroup) || commonEntities.contains(entityName)) {
                    categoryCommonEntities.add(entityName);
                } else if (unCommonEntities.contains(spawnGroup) || unCommonEntities.contains(entityName)) {
                    categoryUncommonEntities.add(entityName);
                } else if (rareEntities.contains(spawnGroup) || rareEntities.contains(entityName)) {
                    categoryRareEntities.add(entityName);
                }
            }
        }

        // Determine which list to use
        if (rarity == Rarity.COMMON) {
            entitiesOfSpecificRarity = categoryCommonEntities;
        } else if (rarity == Rarity.UNCOMMON) {
            entitiesOfSpecificRarity = categoryUncommonEntities;
        } else if (rarity == Rarity.RARE) {
            entitiesOfSpecificRarity = categoryRareEntities;
        }

        // Shuffle the list to randomize it
        Collections.shuffle(entitiesOfSpecificRarity);

        // If the quantity requested is more than the number of available items, return them all
        if (quantity >= entitiesOfSpecificRarity.size()) {
            return entitiesOfSpecificRarity;
        }

        // Otherwise, return a sublist containing only the number of items requested
        return entitiesOfSpecificRarity.subList(0, quantity);
    }
}

