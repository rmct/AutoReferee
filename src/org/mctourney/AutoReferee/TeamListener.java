package org.mctourney.AutoReferee;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

public class TeamListener implements Listener 
{
	AutoReferee plugin = null;
	public Logger log = Logger.getLogger("Minecraft");

	public TeamListener(Plugin p)
	{ plugin = (AutoReferee) p; }

	@EventHandler
	public void chatMessage(PlayerChatEvent event)
	{
		// typical chat message format, swap out with colored version
		Player player = event.getPlayer();
		event.setFormat("<" + plugin.colorPlayer(player) + "> " + event.getMessage());
	}

	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event)
	{
		if (plugin.getState() == AutoReferee.eMatchStatus.PLAYING &&
			event.getPlayer().getBedSpawnLocation() == null)
		{
			// get the location for the respawn event
			Location spawn = plugin.getPlayerSpawn(event.getPlayer());
			if (spawn != null) event.setRespawnLocation(spawn);
		}
	}

	@EventHandler
	public void playerLogin(PlayerLoginEvent event)
	{
		log.info(event.getPlayer().getName() + " login");
		if (plugin.getState() == AutoReferee.eMatchStatus.NONE) return;

		// if they should be whitelisted, let them in, otherwise, block them
		if (plugin.playerWhitelisted(event.getPlayer())) event.allow();
		else event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, 
			"Match in progress: " + plugin.matchName);
	}

	@EventHandler
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		Team team = plugin.getTeam(player);

		if (team != null) event.setJoinMessage(event.getJoinMessage()
			.replace(player.getName(), plugin.colorPlayer(player)));
		plugin.checkTeamsReady();
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
		log.info(event.getPlayer().getName() + " quit");
	}
}

