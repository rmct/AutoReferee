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
	{
		boolean tmp = event.getWorld().getName().startsWith(AutoReferee.WORLD_PREFIX);
		AutoRefMatch.setupWorld(event.getWorld(), tmp);
	}
	
	@EventHandler
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null) return;
		
		AutoRefTeam team = match.getPlayerTeam(player);
		if (team != null) event.setJoinMessage(event.getJoinMessage()
			.replace(player.getName(), match.getPlayerName(player)));
	}
	
	@EventHandler
	public void worldJoin(PlayerChangedWorldEvent event)
	{
		// update team ready information for both worlds
		AutoRefMatch matchFm = plugin.getMatch(event.getFrom());
		if (matchFm != null) matchFm.checkTeamsReady();
		
		AutoRefMatch matchTo = plugin.getMatch(event.getPlayer().getWorld());
		if (matchTo != null) matchTo.checkTeamsReady();
	}
}
