package org.mctourney.autoreferee.listeners;

import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import org.mctourney.autoreferee.AutoReferee;

public class LobbyListener implements Listener
{
	AutoReferee plugin = null;

	public LobbyListener(Plugin p)
	{
		plugin = (AutoReferee) p;
	}

	@EventHandler(priority= EventPriority.HIGHEST)
	public void projectileLaunch(ProjectileLaunchEvent event)
	{if (event.getEntity().getWorld() == plugin.getLobbyWorld()) event.setCancelled(true);}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void blockBreak(BlockBreakEvent event)
	{if (event.getBlock().getWorld() == plugin.getLobbyWorld())  event.setCancelled(true);}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void itemConsume(PlayerItemConsumeEvent event)
	{if (event.getPlayer().getWorld() == plugin.getLobbyWorld()) event.setCancelled(true);}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void dropItem(PlayerDropItemEvent event)
	{if (event.getPlayer().getWorld() == plugin.getLobbyWorld()) event.setCancelled(true);}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void itemInteract(PlayerInteractEvent event)
	{if (event.getPlayer().getWorld() == plugin.getLobbyWorld() && (event.getMaterial() != Material.SIGN_POST || event.getMaterial() != Material.WALL_SIGN)) event.setCancelled(true);}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void blockPlace(BlockPlaceEvent event)
	{if (event.getPlayer().getWorld() == plugin.getLobbyWorld()) event.setCancelled(true);}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bowShoot(EntityShootBowEvent event)
	{if (event.getEntity().getWorld() == plugin.getLobbyWorld()) event.setCancelled(true);}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bucketEmtpy(PlayerBucketEmptyEvent event)
	{if (event.getPlayer().getWorld() == plugin.getLobbyWorld()) event.setCancelled(true);}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bucketFill(PlayerBucketFillEvent event)
	{if (event.getPlayer().getWorld() == plugin.getLobbyWorld()) event.setCancelled(true);}
}
