package org.mctourney.AutoReferee;

import java.util.Iterator;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;

public class TeamListener implements Listener 
{
	AutoReferee plugin = null;
	public Logger log = Logger.getLogger("Minecraft");

	public TeamListener(Plugin p)
	{ plugin = (AutoReferee) p; }

	@EventHandler(priority=EventPriority.HIGHEST)
	public void chatMessage(PlayerChatEvent event)
	{
		// typical chat message format, swap out with colored version
		Player player = event.getPlayer();
		event.setFormat("<" + plugin.colorPlayer(player) + "> " + event.getMessage());

		// if we are currently playing and speaker on a team, restrict recipients
		Team t = plugin.getTeam(player);
		if (plugin.getState() == eMatchStatus.PLAYING && t != null)
		{
			Iterator<Player> iter = event.getRecipients().iterator();
			while (iter.hasNext())
			{
				// if listener is on a team, and its not the same team as the
				// speaker, remove them from the recipients list
				Team ot = plugin.getTeam(iter.next());
				if (ot != null && ot != t) iter.remove();
			}
		}
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
		
		// color the name appropriately
		player.setPlayerListName(plugin.colorPlayer(player));
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
	}
}

