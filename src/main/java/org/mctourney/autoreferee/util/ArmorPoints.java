package org.mctourney.autoreferee.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public abstract class ArmorPoints
{
	public static int fromMaterial(Material mat)
	{
		switch (mat)
		{
		// leather armor
		case LEATHER_HELMET: return 1;
		case LEATHER_CHESTPLATE: return 3;
		case LEATHER_LEGGINGS: return 2;
		case LEATHER_BOOTS: return 1;

		// gold armor
		case GOLD_HELMET: return 2;
		case GOLD_CHESTPLATE: return 5;
		case GOLD_LEGGINGS: return 3;
		case GOLD_BOOTS: return 1;

		// chainmail armor
		case CHAINMAIL_HELMET: return 2;
		case CHAINMAIL_CHESTPLATE: return 5;
		case CHAINMAIL_LEGGINGS: return 4;
		case CHAINMAIL_BOOTS: return 1;

		// iron armor
		case IRON_HELMET: return 2;
		case IRON_CHESTPLATE: return 6;
		case IRON_LEGGINGS: return 5;
		case IRON_BOOTS: return 2;

		// diamond armor
		case DIAMOND_HELMET: return 3;
		case DIAMOND_CHESTPLATE: return 8;
		case DIAMOND_LEGGINGS: return 6;
		case DIAMOND_BOOTS: return 3;

		// non-armor (or not listed above)
		default: return 0;
		}
	}

	public static int fromItemStack(ItemStack item)
	{ return fromMaterial(item.getType()); }

	public static int fromPlayerInventory(PlayerInventory inv)
	{
		int armorPoints = 0;
		for (ItemStack item : inv.getArmorContents())
			if (item != null){
			armorPoints += ArmorPoints.fromItemStack(item);
			}
		return armorPoints;
	}

	public static int fromPlayer(Player pl)
	{ return fromPlayerInventory(pl.getInventory()); }
}
