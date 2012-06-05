package org.mctourney.AutoReferee;

import java.util.*;
import java.util.logging.Logger;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;

public class ZoneListener implements Listener 
{
	AutoReferee plugin = null;
	public Logger log = Logger.getLogger("Minecraft");
	
	public static final double SNEAK_DISTANCE = 0.301;
	public static final double FREEFALL_THRESHOLD = 0.350;

	// convenience for changing defaults
	enum ToolAction
	{
		TOOL_WINCOND,
	}

	private Map<Integer, ToolAction> toolMap;

	public ZoneListener(Plugin p)
	{
		plugin = (AutoReferee) p;
		toolMap = new HashMap<Integer, ToolAction>();

		// tools.win-condition: 284 (golden shovel)
		toolMap.put(plugin.getConfig().getInt(
			"config-mode.tools.win-condition", 284), 
			ToolAction.TOOL_WINCOND);
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		
		double d = plugin.distanceToClosestRegion(
			plugin.getTeam(player), event.getTo());
		double fallspeed = event.getFrom().getY() - event.getTo().getY();
		
		// only kill if they are in survival mode. otherwise, what's the point?
		if (player.getGameMode() == GameMode.SURVIVAL && d > 0)
		{
//			player.sendMessage("s: " + player.isSneaking());
//			player.sendMessage("d: " + Double.toString(d));
//			player.sendMessage("f: " + Double.toString(fallspeed));
			
			// player is close enough to not need sneaking to be supported
			if (d < 0.30);
			
			// player is sneaking off the edge and not in freefall
			else if (player.isSneaking() && d < SNEAK_DISTANCE && fallspeed < FREEFALL_THRESHOLD);
			
			else
			{
				// if any of the above clauses fail, they are not in a defensible position
				plugin.actionTaken.put(player.getName(), AutoReferee.eAction.ENTERED_VOIDLANE);
				if (!player.isDead()) player.setHealth(0);
			}
		}
		
		// if a player leaves the start region, empty their inventory
		if (player.getGameMode() == GameMode.SURVIVAL && plugin.inStartRegion(event.getFrom())
			&& !plugin.inStartRegion(event.getTo())) player.getInventory().clear();
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void blockPlace(BlockPlaceEvent event)
	{
		// if the match isn't currently in progress, a player should
		// not be allowed to place or destroy blocks anywhere
		if (plugin.getState() != AutoReferee.eMatchStatus.PLAYING)
		{ event.setCancelled(true); return; }
		
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();

		// if this block is outside the player's zone, don't place
		if (!plugin.checkPosition(player, loc))
		{ event.setCancelled(true); return; }
		
		// we are playing right now, so check win conditions
		plugin.checkWinConditions(event.getBlock().getWorld(), null);
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void blockBreak(BlockBreakEvent event)
	{
		// if the match isn't currently in progress, a player should
		// not be allowed to place or destroy blocks anywhere
		log.info("Block break, match status: " + plugin.getState().name());
		if (plugin.getState() != AutoReferee.eMatchStatus.PLAYING)
		{ event.setCancelled(true); return; }
		
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();

		// if this block is outside the player's zone, don't place
		if (!plugin.checkPosition(player, loc))
		{ event.setCancelled(true); return; }
		
		// we are playing right now, so check win conditions (with air location)
		plugin.checkWinConditions(event.getBlock().getWorld(), 
				event.getBlock().getLocation());
	}

	@EventHandler
	public void endermanPickup(EntityChangeBlockEvent event)
	{
		// don't let endermen pick up blocks, as a rule
		if (event.getEntity() instanceof Enderman)
			event.setCancelled(true);
	}

	@EventHandler
	public void toolUsage(PlayerInteractEvent event)
	{
		// this event is not an "item" event
		if (!event.hasItem()) return;

		// get type id of the event and check if its one of our tools
		int typeID = event.getItem().getTypeId();
		if (!toolMap.containsKey(typeID)) return;

		// get which action to perform
		switch (toolMap.get(typeID))
		{
			// this is the tool built for setting win conditions
			case TOOL_WINCOND:
				
				// if there is no block involved in this event, nothing
				if (!event.hasBlock()) return;
				
				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission(
					"autoreferee.configure")) return;
				
				// determine who owns the region that the clicked block is in
				Block block = event.getClickedBlock();
				Set<Team> owns = plugin.locationOwnership(block.getLocation());
				
				// if the region is owned by only one team, make it one of their
				// win conditions (otherwise, we may need to configure by hand)
				if (owns.size() == 1)
					plugin.addWinCondition(block, (Team)owns.toArray()[0]);
				
				break;
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void creatureSpawn(CreatureSpawnEvent event)
	{
		// if the match hasn't started, cancel
		if (plugin.getState() != eMatchStatus.PLAYING)
		{ event.setCancelled(true); return; }

		// if this is in the start region, cancel
		if (plugin.inStartRegion(event.getLocation()))
		{ event.setCancelled(true); return; }
	}
}

