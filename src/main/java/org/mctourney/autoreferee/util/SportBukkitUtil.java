package org.mctourney.autoreferee.util;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

public class SportBukkitUtil
{
	private static boolean isSportBukkit = true;

	// custom methods, retrieved by reflection
	private static Method mAffectsSpawning = null;
	private static Method mCollidesWithEntities = null;
	private static Method mSetOverheadName = null;
	private static Method mOfflinePlayerLocation = null;

	static
	{
		// check server name
		isSportBukkit = "SportBukkit".equals(Bukkit.getName());

		// reflect to get methods if this is SportBukkit
		if (isSportBukkit) try
		{
			// last_username's affects-spawning API from SportBukkit
			mAffectsSpawning = HumanEntity.class.getDeclaredMethod("setAffectsSpawning", boolean.class);

			// last_username's collides-with-entities API from SportBukkit
			mCollidesWithEntities = Player.class.getDeclaredMethod("setCollidesWithEntities", boolean.class);

			// anxuiz's set-overhead-name API from SportBukkit
			mSetOverheadName = Player.class.getDeclaredMethod("setOverheadName", String.class);

			// md_5's offlineUtil API from SportBukkit
			mOfflinePlayerLocation = OfflinePlayer.class.getDeclaredMethod("getLocation");
		}
		catch (Exception e) { isSportBukkit = false; }
	}

	/**
	 * Checks if AutoReferee is installed on a system supporting the SportBukkit API
	 *
	 * @return true if SportBukkit is installed, false otherwise
	 * @see <a href="http://www.github.com/OvercastNetwork/SportBukkit">SportBukkit</a>
	 */
	public static boolean hasSportBukkitApi()
	{ return isSportBukkit; }

	/**
	 * Sets whether player affects spawning via natural spawn and mob spawners.
	 * Uses last_username's affects-spawning API from SportBukkit
	 *
	 * @param affectsSpawning Set whether player affects spawning
	 * @see <a href="http://www.github.com/OvercastNetwork/SportBukkit">SportBukkit</a>
	 */
	public static void setAffectsSpawning(Player player, boolean affectsSpawning)
	{
		if (mAffectsSpawning != null) try
		{ mAffectsSpawning.invoke(player, affectsSpawning); }
		catch (Throwable ignored) {  }
	}

	/**
	 * Sets whether player collides with entities, including items and arrows.
	 * Uses last_username's collides-with-entities API from SportBukkit
	 *
	 * @param collidesWithEntities Set whether player collides with entities
	 * @see <a href="http://www.github.com/OvercastNetwork/SportBukkit">SportBukkit</a>
	 */
	public static void setCollidesWithEntities(Player player, boolean collidesWithEntities)
	{
		if (mCollidesWithEntities != null) try
		{ mCollidesWithEntities.invoke(player, collidesWithEntities); }
		catch (Throwable ignored) {  }
	}

	/**
	 * Sets the overhead name, allowing for ChatColor characters.
	 * Uses anxuiz's set-overhead-name API from SportBukkit
	 *
	 * @param overheadName New string to display above player's head
	 * @see <a href="http://www.github.com/OvercastNetwork/SportBukkit">SportBukkit</a>
	 * @deprecated use built-in scoreboards to change name colors
	 */
	@Deprecated
	public static void setOverheadName(Player player, String overheadName)
	{
		if (mSetOverheadName != null) try
		{ mSetOverheadName.invoke(player, overheadName.trim()
			.replaceAll(ChatColor.RESET.toString(), "")); }
		catch (Throwable ignored) {  }
	}

	/**
	 * Gets the logged-out location of an OfflinePlayer
	 * Uses md_5's offlineUtil API from SportBukkit
	 *
	 * @see <a href="http://www.github.com/OvercastNetwork/SportBukkit">SportBukkit</a>
	 */
	public static Location getOfflinePlayerLocation(OfflinePlayer player)
	{
		if (mOfflinePlayerLocation != null) try
		{ return (Location) mOfflinePlayerLocation.invoke(player); }
		catch (Exception e) {  } return null;
	}
}
