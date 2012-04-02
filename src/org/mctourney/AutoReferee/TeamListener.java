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
	AutoReferee refPlugin = null;
	public Logger log = Logger.getLogger("Minecraft");

	public TeamListener(Plugin plugin)
	{
		refPlugin = (AutoReferee) plugin;
	}

	@EventHandler
	public void chatMessage(PlayerChatEvent event)
	{
		// typical chat message format, swap out with colored version
		Player player = event.getPlayer();
		event.setFormat("<" + refPlugin.colorPlayer(player) + "> " + event.getMessage());
	}

	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event)
	{
		if (refPlugin.getState() == AutoReferee.eMatchStatus.PLAYING)
		{
			// get the location for the respawn event
			Location spawn = refPlugin.getPlayerSpawn(event.getPlayer());
			if (spawn != null) event.setRespawnLocation(spawn);
		}
	}

	@EventHandler
	public void playerLogin(PlayerLoginEvent event)
	{
		log.info(event.getPlayer().getName() + " login");
		if (refPlugin.getState() == AutoReferee.eMatchStatus.NONE) return;

		// if they should be whitelisted, let them in, otherwise, block them
		if (refPlugin.playerWhitelisted(event.getPlayer())) event.allow();
		else event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, 
			"Match in progress: " + refPlugin.matchName);
	}

	@EventHandler
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		Integer team = refPlugin.getTeam(player);

		if (team != null) event.setJoinMessage(event.getJoinMessage()
			.replace(player.getName(), refPlugin.colorPlayer(player)));
		refPlugin.checkTeamsReady();
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
		log.info(event.getPlayer().getName() + " quit");
	}
}

