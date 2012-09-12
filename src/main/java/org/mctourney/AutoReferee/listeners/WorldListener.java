package org.mctourney.AutoReferee.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoReferee;

public class WorldListener implements Listener
{
	AutoReferee plugin = null;
	
	public WorldListener(Plugin p)
	{
		plugin = (AutoReferee) p;
	}
	
	@EventHandler
	public void worldLoad(WorldLoadEvent event)
	{
		AutoRefMatch.setupWorld(event.getWorld(), false);
	}
	
	@EventHandler
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		
		// get the match for the world the player is logging into
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		
		// if there is no match here, or they aren't meant to play in this world,
		// check if there is a world they are expected in
		if (match == null || !match.isPlayer(player))
			for (AutoRefMatch m : plugin.getMatches())
				if (m.isPlayerExpected(player)) match = m;
		
		if (match != null)
		{
			// if we are logging in to the wrong world, teleport to the correct world
			if (player.getWorld() != match.getWorld()) match.acceptInvitation(player);
			
			event.setJoinMessage(match.colorMessage(event.getJoinMessage()));
			match.sendMatchInfo(player);
			match.setupSpectators(player);

			if (match.isReferee(player))
				match.updateReferee(player);
		}
	}
	
	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null)
		{
			event.setQuitMessage(match.colorMessage(event.getQuitMessage()));
			match.checkTeamsReady();
		}
	}
	
	@EventHandler
	public void worldJoin(PlayerChangedWorldEvent event)
	{
		// update team ready information for both worlds
		AutoRefMatch matchFm = plugin.getMatch(event.getFrom());
		if (matchFm != null) matchFm.checkTeamsReady();
		
		AutoRefMatch matchTo = plugin.getMatch(event.getPlayer().getWorld());
		if (matchTo != null)
		{
			matchTo.checkTeamsReady();
			matchTo.sendMatchInfo(event.getPlayer());
			matchTo.setupSpectators(event.getPlayer());
			
			if (matchTo.isReferee(event.getPlayer()))
				matchTo.updateReferee(event.getPlayer());
		}
	}
}
