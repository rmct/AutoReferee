package org.mctourney.AutoReferee.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefPlayer;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.AutoRefMatch.RespawnMode;
import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.AutoReferee.goals.AutoRefGoal;
import org.mctourney.AutoReferee.goals.BlockGoal;
import org.mctourney.AutoReferee.util.AchievementPoints;
import org.mctourney.AutoReferee.util.BlockData;

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
			for (AutoRefGoal goal : apl.getTeam().getTeamGoals()) if (goal.hasItem())
			{
				BlockData b = goal.getItem();
				if (goal instanceof BlockGoal && match.blockInRange((BlockGoal) goal) != null &&
					b.matchesBlock(block) && goal.getItemStatus() != AutoRefGoal.ItemStatus.TARGET)
				{
					match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.OBJECTIVE_PLACED,
						String.format("%s has placed %s", apl.getName(), b.getDisplayName()), goal.getTarget(), apl, b));
					apl.addPoints(AchievementPoints.OBJECTIVE_PLACE);
				}
			}
			match.checkWinConditions();
		}
	}

	// ----------------- START WINCONDITION -----------------------

	private void delayCheckWinConditions(BlockEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getBlock().getWorld());
		if (match != null) match.checkWinConditions();
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockBreakEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockBurnEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockFadeEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockFromToEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockGrowEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockIgniteEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockPhysicsEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockPistonExtendEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockPistonRetractEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockRedstoneEvent event)
	{ delayCheckWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(LeavesDecayEvent event)
	{ delayCheckWinConditions(event); }

	// ------------------ END WINCONDITION ------------------------

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void blockBreak(BlockBreakEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getBlock().getWorld());
		if (match != null) match.checkWinConditions();
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void blockInteract(PlayerInteractEvent event)
	{
		if (event.hasBlock())
		{
			AutoRefMatch match = plugin.getMatch(event.getClickedBlock().getWorld());
			if (match != null) match.checkWinConditions();
		}
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void entityInteract(PlayerInteractEntityEvent event)
	{
		Player pl = event.getPlayer();
		Entity entity = event.getRightClicked();

		AutoRefMatch match = plugin.getMatch(pl.getWorld());
		if (match != null) match.checkWinConditions();

		if (entity.getType() == EntityType.PLAYER && match != null
			&& match.isReferee(pl) && match.isPlayer((Player) entity))
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

		if (match.getCurrentState().inProgress() &&
			entity.getType() == EntityType.PLAYER)
		{
			AutoRefPlayer apl = match.getPlayer((Player) entity);
			if (apl != null) plugin.getServer().getScheduler()
				.runTask(plugin, new InventoryChangeTask(apl));
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
				.runTask(plugin, new HealthArmorChangeTask(apl));
		}
	}

	/* MISC */

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerRespawn(PlayerRespawnEvent event)
	{
		Player player = event.getPlayer();
		inventoryChange(player);
		healthArmorChange(player);

		AutoRefMatch match = plugin.getMatch(event.getRespawnLocation().getWorld());
		if (match != null && match.getRespawnMode() == RespawnMode.BEDSONLY)
			if (player.getBedSpawnLocation() == null) match.eliminatePlayer(player);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void itemCraft(CraftItemEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getWhoClicked().getWorld());
		if (match == null) return;

		if (!(event.getWhoClicked() instanceof Player)) return;
		AutoRefTeam team = plugin.getTeam((Player) event.getWhoClicked());

		BlockData recipeTarget = BlockData.fromItemStack(event.getRecipe().getResult());
		if (team != null && !team.canCraft(recipeTarget)) event.setCancelled(true);

		// if this is on the blacklist, cancel
		if (!match.canCraft(recipeTarget)) event.setCancelled(true);
	}
}
