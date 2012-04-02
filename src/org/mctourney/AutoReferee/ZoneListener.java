package org.mctourney.AutoReferee;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.GameMode;
import org.bukkit.Location;
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
		toolMap.put(refPlugin.getConfig().getInt("tools.win-condition", 284), 
			ToolAction.TOOL_WINCOND);
	}
	
	@EventHandler
	public void playerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		
		// only kill if they are in survival mode. otherwise, what's the point?
		if (player.getGameMode() == GameMode.SURVIVAL &&
			refPlugin.checkPosition(player, event.getTo(), true))
		{
			// if they were in none of their team's regions, kill them
			refPlugin.actionTaken.put(player, AutoReferee.eAction.ENTERED_VOIDLANE);
			player.setHealth(0);
		}
	}
	
	@EventHandler
	public void blockPlace(BlockPlaceEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();
		
		// if this block is outside the player's zone, don't place
		if (refPlugin.checkPosition(player, loc, true))
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
			
			break;
		}
	}
}
