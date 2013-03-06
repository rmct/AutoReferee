package org.mctourney.autoreferee.util;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import com.google.common.collect.Maps;

public enum AchievementPoints
{
	KILL_PLAYER(100),
	DEATH(0),
	REVENGE(100),
	ARROW_HIT(3),

	OBJECTIVE_FOUND(300),
	OBJECTIVE_PLACE(100),

	PICKUP_BLOCK(1),
	MINE_COAL(2),
	SMELT_IRON(8),
	SMELT_GOLD(12),
	MINE_DIAMOND(25),

	CRAFT_ARMOR(25),
	CRAFT_TOOL(12),
	CRAFT_WEAPON(15),
	BREW_POTION(30),

	KILL_ZOMBIE(8),
	KILL_SKELETON(12),
	KILL_CREEPER(25),
	KILL_SPIDER(12),
	KILL_CAVE_SPIDER(12),
	KILL_ENDERMAN(20),
	KILL_SLIME(4),
	KILL_SILVERFISH(5),
	KILL_BLAZE(20),
	KILL_GHAST(20),
	KILL_PIGMAN(12),
	KILL_MAGMA_CUBE(5);

	public static final int POINTS = 100;

	private int value;

	private AchievementPoints(int v)
	{ this.value = v; }

	public int getValue()
	{ return this.value; }

	public static int ticksToPoints(int v)
	{ return v / POINTS; }

	private static Map<EntityType, AchievementPoints> monsterKill = Maps.newEnumMap(EntityType.class);
	static
	{
		// the most dangerous game...
		monsterKill.put(EntityType.PLAYER, KILL_PLAYER);

		// all mobs for which we award points
		monsterKill.put(EntityType.ZOMBIE, KILL_ZOMBIE);
		monsterKill.put(EntityType.SKELETON, KILL_SKELETON);
		monsterKill.put(EntityType.CREEPER, KILL_CREEPER);
		monsterKill.put(EntityType.SPIDER, KILL_SPIDER);
		monsterKill.put(EntityType.CAVE_SPIDER, KILL_CAVE_SPIDER);
		monsterKill.put(EntityType.ENDERMAN, KILL_ENDERMAN);
		monsterKill.put(EntityType.SLIME, KILL_SLIME);
		monsterKill.put(EntityType.SILVERFISH, KILL_SILVERFISH);
		monsterKill.put(EntityType.BLAZE, KILL_BLAZE);
		monsterKill.put(EntityType.GHAST, KILL_GHAST);
		monsterKill.put(EntityType.PIG_ZOMBIE, KILL_PIGMAN);
		monsterKill.put(EntityType.MAGMA_CUBE, KILL_MAGMA_CUBE);
	}

	public static AchievementPoints getMonsterKill(EntityType entityType)
	{ return monsterKill.get(entityType); }

	private static Map<Material, AchievementPoints> equipmentCraft = Maps.newEnumMap(Material.class);
	static
	{
		equipmentCraft.put(Material.STONE_SWORD, CRAFT_WEAPON);
		equipmentCraft.put(Material.IRON_SWORD, CRAFT_WEAPON);
		equipmentCraft.put(Material.GOLD_SWORD, CRAFT_WEAPON);
		equipmentCraft.put(Material.DIAMOND_SWORD, CRAFT_WEAPON);
		equipmentCraft.put(Material.BOW, CRAFT_WEAPON);

		equipmentCraft.put(Material.STONE_PICKAXE, CRAFT_TOOL);
		equipmentCraft.put(Material.STONE_AXE, CRAFT_TOOL);
		equipmentCraft.put(Material.STONE_SPADE, CRAFT_TOOL);
		equipmentCraft.put(Material.IRON_PICKAXE, CRAFT_TOOL);
		equipmentCraft.put(Material.IRON_AXE, CRAFT_TOOL);
		equipmentCraft.put(Material.IRON_SPADE, CRAFT_TOOL);
		equipmentCraft.put(Material.GOLD_PICKAXE, CRAFT_TOOL);
		equipmentCraft.put(Material.GOLD_AXE, CRAFT_TOOL);
		equipmentCraft.put(Material.GOLD_SPADE, CRAFT_TOOL);
		equipmentCraft.put(Material.DIAMOND_PICKAXE, CRAFT_TOOL);
		equipmentCraft.put(Material.DIAMOND_AXE, CRAFT_TOOL);
		equipmentCraft.put(Material.DIAMOND_SPADE, CRAFT_TOOL);

		equipmentCraft.put(Material.IRON_HELMET, CRAFT_ARMOR);
		equipmentCraft.put(Material.IRON_CHESTPLATE, CRAFT_ARMOR);
		equipmentCraft.put(Material.IRON_LEGGINGS, CRAFT_ARMOR);
		equipmentCraft.put(Material.IRON_BOOTS, CRAFT_ARMOR);
		equipmentCraft.put(Material.GOLD_HELMET, CRAFT_ARMOR);
		equipmentCraft.put(Material.GOLD_CHESTPLATE, CRAFT_ARMOR);
		equipmentCraft.put(Material.GOLD_LEGGINGS, CRAFT_ARMOR);
		equipmentCraft.put(Material.GOLD_BOOTS, CRAFT_ARMOR);
		equipmentCraft.put(Material.DIAMOND_HELMET, CRAFT_ARMOR);
		equipmentCraft.put(Material.DIAMOND_CHESTPLATE, CRAFT_ARMOR);
		equipmentCraft.put(Material.DIAMOND_LEGGINGS, CRAFT_ARMOR);
		equipmentCraft.put(Material.DIAMOND_BOOTS, CRAFT_ARMOR);
	}

	public static AchievementPoints getEquipmentCraft(Material material)
	{ return equipmentCraft.get(material); }
}
