package org.mctourney.autoreferee.listeners.lobby;

import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.util.PlayerUtil;
import org.mctourney.autoreferee.util.SportBukkitUtil;

import com.google.common.collect.Sets;

public abstract class LobbyListener implements Listener
{
	protected AutoReferee plugin = null;

	protected boolean checkAdminPrivilege(Entity e)
	{
		return (e.getType() == EntityType.PLAYER &&
			((Player) e).hasPermission("autoreferee.admin"));
	}

	// set of players to be processed who have logged into a non-existent world
	Set<String> playerLimboLogin = Sets.newHashSet();

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerPreLogin(AsyncPlayerPreLoginEvent event)
	{
		OfflinePlayer opl = Bukkit.getOfflinePlayer(event.getName());
		Location oloc = SportBukkitUtil.getOfflinePlayerLocation(opl);

		// either there is no offline player utility, or check the world like normal
		if (opl.hasPlayedBefore() && (oloc == null || oloc.getWorld() == null))
		{
			if (!opl.isOnline()) playerLimboLogin.add(opl.getName());
			// TODO deferred teleportation back on the sync server thread if online
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();

		// if this player is "in limbo" (logged into an unloaded world)
		if (player.hasPlayedBefore() && playerLimboLogin.remove(player.getName()))
		{
			World newWorld = plugin.getLobbyWorld();
			if (newWorld == null) newWorld = Bukkit.getWorlds().get(0);
			player.teleport(newWorld.getSpawnLocation());
		}

		if (player.getWorld() == plugin.getLobbyWorld())
		{
			if (!player.hasPlayedBefore())
				player.teleport(player.getWorld().getSpawnLocation());

			PlayerUtil.setGameMode(player, GameMode.ADVENTURE);
			player.setAllowFlight(true);
		}
	}

	@EventHandler
	public void worldJoin(PlayerChangedWorldEvent event)
	{
		// moving to lobby world, set player to creative
		Player player = event.getPlayer();
		if (player.getWorld() == plugin.getLobbyWorld())
		{
			PlayerUtil.setGameMode(player, GameMode.ADVENTURE);
			player.setAllowFlight(true);
		}
	}

	@EventHandler(priority= EventPriority.HIGHEST)
	public void creatureSpawn(CreatureSpawnEvent event)
	{
		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
		{ event.setCancelled(true); return; }
	}
}
