package org.mctourney.AutoReferee;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoReferee.AutoRefMatch;

public class WorldListener implements Listener
{
	AutoReferee plugin = null;
	
	public WorldListener(Plugin p)
	{
		plugin = (AutoReferee) p;
	}
	
	public void processWorld(World w)
	{
		// if this map isn't compatible with AutoReferee, quit...
		if (plugin.matches.containsKey(w.getUID()) ||
			!AutoRefMatch.isCompatible(w)) return;
		
		AutoRefMatch match = new AutoRefMatch(w);
		plugin.matches.put(w.getUID(), match);
	}
	
	@EventHandler
	public void worldLoad(WorldLoadEvent event)
	{ processWorld(event.getWorld()); }
}
