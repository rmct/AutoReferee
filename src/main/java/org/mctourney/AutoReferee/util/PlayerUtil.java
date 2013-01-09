package org.mctourney.AutoReferee.util;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;

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

	private static Map<PotionEffectType, String> statusNames = Maps.newHashMap();
	static
	{
		statusNames.put(PotionEffectType.DAMAGE_RESISTANCE, "Resistance");
		statusNames.put(PotionEffectType.INCREASE_DAMAGE, "Strength");
		statusNames.put(PotionEffectType.JUMP, "Jump Boost");
		statusNames.put(PotionEffectType.SLOW, "Slowness");
		statusNames.put(PotionEffectType.FAST_DIGGING, "Haste");
		statusNames.put(PotionEffectType.SLOW_DIGGING, "Mining Fatigue");
	}

	private static String[] roman = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	/**
	 * Generates a human-readable string for the status effect.
	 */
	public static String getStatusEffectName(PotionEffect effect)
	{
		int time = effect.getDuration() / 20;
		PotionEffectType type = effect.getType();
		int amp = effect.getAmplifier();

		String name = statusNames.containsKey(type) ? statusNames.get(type) :
			StringUtils.capitalize(type.getName().toLowerCase().replace('_', ' '));
		if (amp >= 0) name += " " + (amp < roman.length ? roman[amp] : (1 + amp));

		return String.format("%s (%d:%02d)", name, time/60, time%60);
	}
}
