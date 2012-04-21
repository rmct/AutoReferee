package org.mctourney.AutoReferee;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

public class ZoneListener implements Listener 
{
	AutoReferee refPlugin = null;
	public Logger log = Logger.getLogger("Minecraft");

	// convenience for changing defaults
	enum ToolAction
	{
		TOOL_WINCOND,
	}

	private Map<Integer, ToolAction> toolMap;

	public ZoneListener(Plugin plugin)
	{
		refPlugin = (AutoReferee) plugin;
		toolMap = new HashMap<Integer, ToolAction>();

		// tools.win-condition: 284 (golden shovel)
		toolMap.put(refPlugin.getConfig().getInt(
			"config-mode.tools.win-condition", 284), 
			ToolAction.TOOL_WINCOND);
	}

	@EventHandler
	public void playerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();

		// only kill if they are in survival mode. otherwise, what's the point?
		if (player.getGameMode() == GameMode.SURVIVAL && 
				!refPlugin.checkPosition(player, event.getTo()))
		{
			// if they were in none of their team's regions, kill them
			refPlugin.actionTaken.put(player, AutoReferee.eAction.ENTERED_VOIDLANE);
			player.setHealth(0);
		}
	}

	@EventHandler
	public void blockPlace(BlockPlaceEvent event)
	{
		// if the match isn't currently in progress, a player should
		// not be allowed to place or destroy blocks anywhere
		if (refPlugin.getState() != AutoReferee.eMatchStatus.PLAYING)
		{ event.setCancelled(true); return; }
		
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();

		// if this block is outside the player's zone, don't place
		if (!refPlugin.checkPosition(player, loc))
		{ event.setCancelled(true); return; }
	}

	@EventHandler
	public void blockBreak(BlockBreakEvent event)
	{
		// if the match isn't currently in progress, a player should
		// not be allowed to place or destroy blocks anywhere
		if (refPlugin.getState() != AutoReferee.eMatchStatus.PLAYING)
		{ event.setCancelled(true); return; }
		
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();

		// if this block is outside the player's zone, don't place
		if (!refPlugin.checkPosition(player, loc))
		{ event.setCancelled(true); return; }
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
				
				// add the clicked block as a win condition
				Block block = event.getClickedBlock();
				List<Team> owns = refPlugin.locationOwnership(block.getLocation());
				if (owns.size() == 1) refPlugin.addWinCondition(block, owns.get(0));
				
				break;
		}
	}
}

