package org.mctourney.AutoReferee;

import java.util.Iterator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		
		if (match == null) return;
		event.setFormat("<" + match.getPlayerName(player) + "> " + event.getMessage());

		// if we are currently playing and speaker on a team, restrict recipients
		AutoRefTeam t = match.getPlayerTeam(player);
		
		Iterator<Player> iter = event.getRecipients().iterator();
		while (iter.hasNext())
		{
			Player recipient = iter.next();
			
			// if the listener is in a different world
			if (recipient.getWorld() != player.getWorld())
			{ iter.remove(); continue; }
			
			// if listener is on a team, and its not the same team as the
			// speaker, remove them from the recipients list
			AutoRefTeam oteam = match.getPlayerTeam(recipient);
			if (match.getCurrentState() == eMatchStatus.PLAYING &&
				oteam != null && oteam != t) { iter.remove(); continue; }
		}
	}

	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event)
	{
		World world = event.getPlayer().getWorld();
		AutoRefMatch match = plugin.getMatch(world);
		
		if (match != null && event.getPlayer().getBedSpawnLocation() == null)
		{
			// get the location for the respawn event
			Location spawn = match.getPlayerSpawn(event.getPlayer());
			event.setRespawnLocation(spawn == null ? world.getSpawnLocation() : spawn);
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void playerLogin(PlayerLoginEvent event)
	{
		Player player = event.getPlayer();
		if (plugin.isAutoMode())
		{
			// if they should be whitelisted, let them in, otherwise, block them
			if (plugin.playerWhitelisted(player)) event.allow();
			else event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, 
				AutoReferee.NO_LOGIN_MESSAGE);
		}	
		
		// if this player needs to be in a specific world, put them there
		AutoRefTeam team = plugin.getExpectedTeam(player);
		if (team != null) team.join(player);
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
		Player player = event.getPlayer();
		
		// leave the team, if necessary
		AutoRefTeam team = plugin.getTeam(player);
		if (team != null) team.leave(player);
		
		// re-check world ready
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match != null) match.checkTeamsReady();
	}
}
