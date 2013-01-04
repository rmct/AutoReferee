package org.mctourney.AutoReferee.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

public class PlayerUtil
{
	/**
	 * Heals the player and resets hunger, saturation, and exhaustion.
	 */
	public static void heal(Player player)
	{
		player.setHealth    ( 20 ); // 10 hearts
		player.setFoodLevel ( 20 ); // full food
		player.setSaturation(  5 ); // saturation depletes hunger
		player.setExhaustion(  0 ); // exhaustion depletes saturation
	}

	/**
	 * Clears the player's inventory, including armor slots.
	 */
	public static void clearInventory(Player player)
	{
		// clear the player's inventory
		PlayerInventory inv = player.getInventory();
		inv.clear();

		// clear the armor slots seperately
		inv.setHelmet(null);
		inv.setChestplate(null);
		inv.setLeggings(null);
		inv.setBoots(null);
	}

	/**
	 * Resets player's experience and levels.
	 */
	public static void resetXP(Player player)
	{
		// reset the player's level
		player.setLevel(0);
		player.setExp(0.0f);
	}

	/**
	 * Removes potion and beacon effects from the player.
	 */
	public static void removeStatusEffects(Player player)
	{
		// remove all potion effects
		for (PotionEffect effect : player.getActivePotionEffects())
			player.removePotionEffect(effect.getType());
	}

	/**
	 * Performs all PlayerUtil actions on this player.
	 */
	public static void reset(Player player)
	{
		// heal the player to full health, hunger, etc
		heal(player);

		// clear their entire inventory
		clearInventory(player);

		// reset levels and experience
		resetXP(player);

		// remove potion and beacon effects
		removeStatusEffects(player);
	}
}
