package org.mctourney.autoreferee.util;

import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.google.common.collect.Maps;

import org.apache.commons.lang.WordUtils;
import org.bukkit.scheduler.BukkitRunnable;
import org.mctourney.autoreferee.AutoReferee;

public class PlayerUtil
{
	/**
	 * Heals the player and resets hunger, saturation, and exhaustion.
	 */
	public static void restore(Player player)
	{
		PlayerUtil.heal(player);
		PlayerUtil.feed(player);
	}

	/**
	 * Heals the player and resets hunger, saturation, and exhaustion.
	 */
	public static void heal(Player player)
	{
		player.setHealth( 20 ); // 10 hearts
	}

	/**
	 * Heals the player and resets hunger, saturation, and exhaustion.
	 */
	public static void feed(Player player)
	{
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
	 * Clears player's ender chest
	 */
	public static void clearEnderChest(Player player)
	{
	    // clear the ender chest
	    Inventory enderChest = player.getEnderChest();
	    enderChest.clear();
	}

	/**
	 * Performs all PlayerUtil actions on this player.
	 */
	public static void reset(Player player)
	{
		// restore the player to full health, hunger, etc
		restore(player);

		// clear their entire inventory
		clearInventory(player);

		// reset levels and experience
		resetXP(player);

		// remove potion and beacon effects
		removeStatusEffects(player);
	}

	private static class BufferedGameModeChangeTask extends BukkitRunnable
	{
		private static Map<String, BufferedGameModeChangeTask> players = Maps.newHashMap();

		private Player player;
		private GameMode gamemode = null;
		private Boolean flight = null;

		private BufferedGameModeChangeTask(Player player)
		{ this.player = player; }

		public static BufferedGameModeChangeTask change(Player player, GameMode gm)
		{ return BufferedGameModeChangeTask.change(player, gm, null); }

		public static BufferedGameModeChangeTask change(Player player, GameMode gm, Boolean flight)
		{
			BufferedGameModeChangeTask task = players.get(player.getName());
			if (task == null)
			{
				players.put(player.getName(),
					task = new BufferedGameModeChangeTask(player));
				task.runTaskLater(AutoReferee.getInstance(), 2L);
			}

			task.gamemode = gm;
			task.flight = flight;
			return task;
		}

		@Override
		public void run()
		{
			if (this.gamemode != null)
				player.setGameMode(this.gamemode);
			if (this.flight != null)
				player.setAllowFlight(this.flight);
			players.remove(player.getName());
		}
	}

	/**
	 * Changes the players gamemode on the next server tick. Buffers gamemode changes.
	 * @param player player
	 * @param gamemode new gamemode
	 */
	public static void setGameMode(Player player, GameMode gamemode)
	{ BufferedGameModeChangeTask.change(player, gamemode); }

	/**
	 * Changes the players gamemode on the next server tick. Buffers gamemode changes.
	 * @param player player
	 * @param gamemode new gamemode
	 * @param flight allow flight
	 */
	public static void setGameMode(Player player, GameMode gamemode, boolean flight)
	{ BufferedGameModeChangeTask.change(player, gamemode, flight); }

	public static void setSpectatorSettings(Player player, boolean spec, GameMode pgm)
	{
		// gamemode is the obvious issue
		PlayerUtil.setGameMode(player, spec ? GameMode.SPECTATOR : pgm);

		// basic player settings depending on role
		player.setAllowFlight(spec);
		player.setCanPickupItems(!spec);
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
			WordUtils.capitalizeFully(type.getName().toLowerCase().replace('_', ' '));
		if (amp >= 0) name += " " + (amp < roman.length ? roman[amp] : (1 + amp));

		return String.format("%s (%d:%02d)", name, time/60, time%60);
	}

	public static boolean hasClientMod(Player player)
	{
		if (player == null) return false;
		return player.getListeningPluginChannels().contains(AutoReferee.REFEREE_PLUGIN_CHANNEL);
	}
}
