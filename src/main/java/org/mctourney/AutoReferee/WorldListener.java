package org.mctourney.AutoReferee;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
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
	{ AutoRefMatch.setupWorld(event.getWorld()); }
	
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

	@EventHandler
	public void playerBowFire(EntityShootBowEvent event)
	{
		// if the entity is not a player, we don't care
		if (event.getEntityType() != EntityType.PLAYER) return;
		
		Player player = (Player) event.getEntity();
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null) return;
		
		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null) ++apl.shotsFired;
	}

	@EventHandler
	public void playerArrowHit(ProjectileHitEvent event)
	{
		if ((event.getEntityType() == EntityType.ARROW) && 
			(event.getEntity().getShooter() instanceof Player))
		{
			Player player = (Player) event.getEntity().getShooter();
			AutoRefMatch match = plugin.getMatch(player.getWorld());
			if (match == null) return;
			
			AutoRefPlayer apl = match.getPlayer(player);
			if (apl != null) ++apl.shotsHit;
		}
	}
}
