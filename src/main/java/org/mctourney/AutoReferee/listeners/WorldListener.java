package org.mctourney.AutoReferee.listeners;

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
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null)
		{
			event.setJoinMessage(match.colorMessage(event.getJoinMessage()));
			match.sendMatchInfo(event.getPlayer());
			match.setupSpectators(event.getPlayer());

			if (match.isReferee(event.getPlayer()))
				match.updateReferee(event.getPlayer());
		}
	}
	
	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null) 
			event.setQuitMessage(match.colorMessage(event.getQuitMessage()));
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
		}
	}
}
