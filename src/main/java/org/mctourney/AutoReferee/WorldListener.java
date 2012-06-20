package org.mctourney.AutoReferee;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

public class WorldListener implements Listener
{
	AutoReferee plugin = null;
	
	public WorldListener(Plugin p)
	{
		plugin = (AutoReferee) p;
	}
	
	@EventHandler
	public void worldLoad(WorldLoadEvent event)
	{ plugin.processWorld(event.getWorld()); }
	
	@EventHandler
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		AutoRefTeam team = plugin.getTeam(player);
		
		if (team != null) event.setJoinMessage(event.getJoinMessage()
			.replace(player.getName(), plugin.colorPlayer(player)));
		plugin.checkTeamsReady(player.getWorld());
	}
	
	@EventHandler
	public void worldJoin(PlayerChangedWorldEvent event)
	{
		// update team ready information for both worlds
		plugin.checkTeamsReady(event.getFrom());
		plugin.checkTeamsReady(event.getPlayer().getWorld());
	}
}
