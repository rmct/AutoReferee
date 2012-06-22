package org.mctourney.AutoReferee;

import java.util.Iterator;
import org.bukkit.Location;
import org.bukkit.World;
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

	public TeamListener(Plugin p)
	{ plugin = (AutoReferee) p; }

	@EventHandler(priority=EventPriority.HIGHEST)
	public void chatMessage(PlayerChatEvent event)
	{
		// typical chat message format, swap out with colored version
		Player player = event.getPlayer();
		event.setFormat("<" + plugin.colorPlayer(player) + "> " + event.getMessage());

		// if we are currently playing and speaker on a team, restrict recipients
		AutoRefTeam t = plugin.getTeam(player);
		
		Iterator<Player> iter = event.getRecipients().iterator();
		while (iter.hasNext())
		{
			Player recipient = iter.next();
			
			// if the listener is in a different world
			if (recipient.getWorld() != player.getWorld())
			{ iter.remove(); continue; }
			
			// if listener is on a team, and its not the same team as the
			// speaker, remove them from the recipients list
			AutoRefTeam oteam = plugin.getTeam(recipient);
			if (plugin.getState(player.getWorld()) == eMatchStatus.PLAYING &&
				oteam != null && oteam != t) { iter.remove(); continue; }
		}
	}

	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event)
	{
		World world = event.getPlayer().getWorld();
		if (plugin.getState(world) == AutoReferee.eMatchStatus.PLAYING &&
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
		Player player = event.getPlayer();
		
		// if they should be whitelisted, let them in, otherwise, block them
		if (plugin.playerWhitelisted(player)) event.allow();
		else event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, 
			"You are not scheduled for a match on this server.");
		
		// if this player needs to be in a specific world, put them there
		AutoRefTeam team = plugin.getTeam(player);
		if (team != null && team.match != null && team.match.world != null)
			player.teleport(team.match.world.getSpawnLocation());
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
		// re-check world ready
		plugin.checkTeamsReady(event.getPlayer().getWorld());
	}
}