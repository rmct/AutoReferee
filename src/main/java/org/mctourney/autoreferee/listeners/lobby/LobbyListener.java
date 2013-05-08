package org.mctourney.autoreferee.listeners.lobby;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.util.PlayerUtil;

public abstract class LobbyListener implements Listener
{
	protected AutoReferee plugin = null;

	@EventHandler
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
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
