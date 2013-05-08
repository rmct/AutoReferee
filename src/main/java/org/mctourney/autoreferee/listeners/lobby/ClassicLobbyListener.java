package org.mctourney.autoreferee.listeners.lobby;

import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;

import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoReferee;

public class ClassicLobbyListener extends LobbyListener
{
	public ClassicLobbyListener(Plugin p)
	{
		plugin = (AutoReferee) p;
	}

	@EventHandler(priority= EventPriority.HIGHEST)
	public void projectileLaunch(ProjectileLaunchEvent event)
	{
		LivingEntity shooter = event.getEntity().getShooter();
		if (shooter != null && shooter.getType() == EntityType.PLAYER &&
			((Player) shooter).hasPermission("autoreferee.admin")) return;

		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bowShoot(EntityShootBowEvent event)
	{
		if (event.getEntityType() == EntityType.PLAYER &&
			((Player) event.getEntity()).hasPermission("autoreferee.admin")) return;

		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void itemConsume(PlayerItemConsumeEvent event)
	{
		if (event.getPlayer() != null &&
			event.getPlayer().hasPermission("autoreferee.admin")) return;

		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void entityDamage(EntityDamageEvent event)
	{
		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void dropItem(PlayerDropItemEvent event)
	{
		if (event.getPlayer() != null &&
			event.getPlayer().hasPermission("autoreferee.admin")) return;

		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void blockPlace(BlockPlaceEvent event)
	{
		if (event.getPlayer() != null &&
			event.getPlayer().hasPermission("autoreferee.admin")) return;

		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void blockBreak(BlockBreakEvent event)
	{
		if (event.getPlayer() != null &&
			event.getPlayer().hasPermission("autoreferee.admin")) return;

		if (event.getBlock().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bucketEmpty(PlayerBucketEmptyEvent event)
	{
		if (event.getPlayer() != null &&
			event.getPlayer().hasPermission("autoreferee.admin")) return;

		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bucketFill(PlayerBucketFillEvent event)
	{
		if (event.getPlayer() != null &&
			event.getPlayer().hasPermission("autoreferee.admin")) return;

		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void signCommand(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		if (event.hasBlock() && event.getClickedBlock().getState() instanceof Sign)
		{
			String[] lines = ((Sign) event.getClickedBlock().getState()).getLines();
			if (lines[0] == null || !"[AutoReferee]".equals(lines[0])) return;

			if (player.getWorld() == plugin.getLobbyWorld())
			{
				// load the world named on the sign
				if (event.getAction() == Action.RIGHT_CLICK_BLOCK)
				{
					player.sendMessage(ChatColor.GREEN + "Please wait...");
					String mapName = lines[1] + " " + lines[2] + " " + lines[3];
					AutoRefMap.loadMap(player, mapName.trim(), null);
				}
			}
		}
	}
}
