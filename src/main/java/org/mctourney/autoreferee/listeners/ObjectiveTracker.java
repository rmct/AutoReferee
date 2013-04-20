package org.mctourney.autoreferee.listeners;

import org.bukkit.Material;
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

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefMatch.RespawnMode;
import org.mctourney.autoreferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.autoreferee.goals.AutoRefGoal;
import org.mctourney.autoreferee.goals.BlockGoal;
import org.mctourney.autoreferee.goals.CoreGoal;
import org.mctourney.autoreferee.util.AchievementPoints;
import org.mctourney.autoreferee.util.BlockData;

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
			if (apl.getTeam() != null)
				for (AutoRefGoal goal : apl.getTeam().getTeamGoals()) if (goal.hasItem())
			{
				BlockData b = goal.getItem();
				if (goal instanceof BlockGoal && ((BlockGoal) goal).isSatisfied(match) &&
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

	/* TRACKING OBJECTIVES/BED */

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void bedBreak(BlockBreakEvent event)
	{
		Player pl = event.getPlayer();
		Block block = event.getBlock();

		AutoRefMatch match = plugin.getMatch(block.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match != null && apl != null && match.getCurrentState().inProgress()
			&& match.getRespawnMode() == RespawnMode.BEDSONLY && block.getType() == Material.BED_BLOCK)
				match.new BedUpdateTask(apl).runTask(plugin);
	}

	// ----------------- START WINCONDITION -----------------------

	private void _checkWinConditions(BlockEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getBlock().getWorld());
		if (match != null) match.checkWinConditions();
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockBreakEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockBurnEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockFadeEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockFromToEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getBlock().getWorld());
		if (match != null) for (AutoRefTeam team : match.getTeams())
			for (CoreGoal goal : team.getTeamGoals(CoreGoal.class))
				goal.checkSatisfied(event);

		// typical win condition check as well
		_checkWinConditions(event);
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockGrowEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockIgniteEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockPhysicsEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockPistonExtendEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockPistonRetractEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(BlockRedstoneEvent event)
	{ _checkWinConditions(event); }

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void checkWinConditions(LeavesDecayEvent event)
	{ _checkWinConditions(event); }

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
			&& match.isSpectator(pl) && match.isPlayer((Player) entity))
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
