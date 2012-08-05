package org.mctourney.AutoReferee;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.SourceInventory;

public class ObjectiveTracker implements Listener 
{
	AutoReferee plugin = null;
	
	public ObjectiveTracker(Plugin p)
	{
		plugin = (AutoReferee) p;
	}
	
	/* TRACKING OBJECTIVES/WOOL */

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void blockPlace(BlockPlaceEvent event)
	{
		Player pl = event.getPlayer();
		Block block = event.getBlock();
		
		AutoRefMatch match = plugin.getMatch(block.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);
		
		if (match != null && apl != null)
		{
			for (Map.Entry<Location, BlockData> e : apl.getTeam().winConditions.entrySet())
			{
				Location loc = e.getKey(); BlockData bd = e.getValue();
				if (block.getLocation().equals(loc) && bd.matches(block))
					match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.OBJECTIVE_PLACED,
						String.format("%s has placed %s", apl.getPlayerName(), bd.getRawName()), loc, apl, bd));
			}
			match.checkWinConditions();
		}
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void blockBreak(BlockBreakEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getBlock().getWorld());
		if (match != null) match.checkWinConditions();
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void blockInteract(PlayerInteractEvent event)
	{
		Player pl = event.getPlayer();
		if (event.hasBlock())
		{
			Block block = event.getClickedBlock();
			AutoRefMatch match = plugin.getMatch(block.getWorld());
			AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);
			
			if (match != null && apl != null && block != null)
			{
				Location loc = block.getLocation();
				for (SourceInventory sinv : apl.getTeam().targetChests.values())
					if (loc.equals(sinv.target)) sinv.hasSeen(apl);
				match.checkWinConditions();
			}
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void blockDispense(BlockDispenseEvent event)
	{
		Block block = event.getBlock();
		World world = block.getWorld();
		
		AutoRefMatch match = plugin.getMatch(world);
		if (match != null)
		{
			Location loc = block.getLocation();
			for (AutoRefTeam team : match.locationOwnership(loc))
				for (SourceInventory sinv : team.targetChests.values())
					if (loc.equals(sinv.target)) sinv.hasSeen(match.getNearestPlayer(loc));
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void entityInteract(PlayerInteractEntityEvent event)
	{
		Player pl = event.getPlayer();
		Entity entity = event.getRightClicked();
		
		AutoRefMatch match = plugin.getMatch(pl.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);
		
		if (match != null && apl != null && entity != null)
		{
			Location loc = entity.getLocation();
			for (AutoRefTeam team : match.locationOwnership(loc))
				for (SourceInventory sinv : team.targetChests.values())
					if (entity.equals(sinv.target)) sinv.hasSeen(apl);
			match.checkWinConditions();
		}

		if (event.getPlayer().hasPermission("autoreferee.referee") &&
			(entity.getType() == EntityType.PLAYER) && match != null)
		{
			AutoRefPlayer a = match.getPlayer((Player) entity);
			a.showInventory(pl);
		}
	}
	
	/* TRACKING WOOL CARRYING */
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void inventoryClick(InventoryClickEvent event)
	{ inventoryChange(event.getWhoClicked()); }
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void itemDrop(PlayerDropItemEvent event)
	{ inventoryChange(event.getPlayer()); }
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void itemPickup(PlayerPickupItemEvent event)
	{ inventoryChange(event.getPlayer()); }
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void blockPlaceInventory(BlockPlaceEvent event)
	{ inventoryChange(event.getPlayer()); }
	
	class InventoryChangeTask implements Runnable
	{
		AutoRefPlayer apl = null;
		
		public InventoryChangeTask(AutoRefPlayer apl)
		{ this.apl = apl; }
		
		public void run()
		{ if (apl != null) apl.updateCarrying(); }
	}
	
	public void inventoryChange(HumanEntity entity)
	{
		AutoRefMatch match = plugin.getMatch(entity.getWorld());
		if (match == null) return;
		
		if (match.getCurrentState() == eMatchStatus.PLAYING &&
			entity.getType() == EntityType.PLAYER)
		{
			AutoRefPlayer apl = match.getPlayer((Player) entity);
			if (apl != null) plugin.getServer().getScheduler()
				.scheduleSyncDelayedTask(plugin, new InventoryChangeTask(apl));
		}
	}
	
	/* TRACKING PLAYER HEALTH AND ARMOR */
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void playerHealthDown(EntityDamageEvent event)
	{ healthArmorChange(event.getEntity()); }
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void playerHealthUp(EntityRegainHealthEvent event)
	{ healthArmorChange(event.getEntity()); }
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void playerArmorChange(InventoryClickEvent event)
	{ healthArmorChange(event.getWhoClicked()); }
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void playerArmorDrop(PlayerDropItemEvent event)
	{ healthArmorChange(event.getPlayer()); }
	
	class HealthArmorChangeTask implements Runnable
	{
		AutoRefPlayer apl = null;
		
		public HealthArmorChangeTask(AutoRefPlayer apl)
		{ this.apl = apl; }
		
		public void run()
		{ if (apl != null) apl.updateHealthArmor(); }
	}
	
	public void healthArmorChange(Entity entity)
	{
		AutoRefMatch match = plugin.getMatch(entity.getWorld());
		if (match != null && entity.getType() == EntityType.PLAYER)
		{
			AutoRefPlayer apl = match.getPlayer((Player) entity);
			if (apl != null) plugin.getServer().getScheduler()
				.scheduleSyncDelayedTask(plugin, new HealthArmorChangeTask(apl));
		}
	}
	
	/* MISC */
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void playerRespawn(PlayerRespawnEvent event)
	{
		inventoryChange(event.getPlayer());
		healthArmorChange(event.getPlayer());
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void itemCraft(CraftItemEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getWhoClicked().getWorld());
		if (match == null) return;
		
		if (!(event.getWhoClicked() instanceof Player)) return;
		AutoRefTeam team = plugin.getTeam((Player) event.getWhoClicked());
		
		BlockData recipeTarget = BlockData.fromItemStack(event.getRecipe().getResult());
		if (team != null && team.winConditions.containsValue(recipeTarget))
			event.setCancelled(true);
	}
}
