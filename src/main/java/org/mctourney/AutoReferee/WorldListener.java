package org.mctourney.AutoReferee;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
	public void worldJoin(PlayerChangedWorldEvent event)
	{
		
	}
}
