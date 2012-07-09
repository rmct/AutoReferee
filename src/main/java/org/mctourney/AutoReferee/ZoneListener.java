package org.mctourney.AutoReferee;

import java.util.Map;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.material.Redstone;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;

import com.google.common.collect.Maps;

public class ZoneListener implements Listener 
{
	AutoReferee plugin = null;
	
	public static final double SNEAK_DISTANCE = 0.30001;
	public static final double FREEFALL_THRESHOLD = 0.350;

	// convenience for changing defaults
	enum ToolAction
	{
		TOOL_WINCOND,
		TOOL_STARTMECH,
	}

	private Map<Integer, ToolAction> toolMap;
	
	public static int parseTool(String s, int def)
	{
		// if no string was passed, return default
		if (s == null) return def;
		
		// check to see if this is a material name
		Material mat = Material.getMaterial(s);
		if (mat != null) return mat.getId();
		
		// try to parse as an integer
		try { return Integer.parseInt(s); }
		catch (Exception e) { return def; }
	}

	public ZoneListener(Plugin p)
	{
		plugin = (AutoReferee) p;
		toolMap = Maps.newHashMap();

		// tools.win-condition: golden shovel
		toolMap.put(parseTool(plugin.getConfig().getString(
			"config-mode.tools.win-condition", null),
				Material.GOLD_SPADE.getId()), 
			ToolAction.TOOL_WINCOND);

		// tools.start-mechanism: golden axe
		toolMap.put(parseTool(plugin.getConfig().getString(
			"config-mode.tools.start-mechanism", null),
				Material.GOLD_AXE.getId()), 
			ToolAction.TOOL_STARTMECH);
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null) return;
		
		AutoRefTeam team = plugin.getTeam(player);
		if (team == null) return;
		
		double d = team.distanceToClosestRegion(event.getTo());
		double fallspeed = event.getFrom().getY() - event.getTo().getY();
		
		// if a player leaves the start region... 
		if (player.getGameMode() == GameMode.SURVIVAL && match.getPlayer(player) != null && 
			match.inStartRegion(event.getFrom()) && !match.inStartRegion(event.getTo()))
		{
			// if game isn't going, teleport them back
			if (match.getCurrentState() != eMatchStatus.PLAYING)
			{
				player.teleport(match.getPlayerSpawn(player));
				player.setVelocity(new org.bukkit.util.Vector());
				player.setFallDistance(0.0f);
			}
			
			// if game is being played, empty their inventory
			else player.getInventory().clear();
		}
		
		// only kill if they are in survival mode. otherwise, what's the point?
		else if (player.getGameMode() == GameMode.SURVIVAL && d > 0.3)
		{
			// player is sneaking off the edge and not in freefall
			if (player.isSneaking() && d < SNEAK_DISTANCE && fallspeed < FREEFALL_THRESHOLD);
			
			// if any of the above clauses fail, they are not in a defensible position
			else if (!player.isDead())
			{
				player.setLastDamageCause(AutoRefPlayer.VOID_DEATH);
				player.setHealth(0);
			}
		}
	}
	
	public boolean validInteract(Player player, Location loc)
	{
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		AutoRefTeam team = plugin.getTeam(player);
		
		// no match for this world, not our business
		if (match == null) return true;
		
		// if the player or the match are not under our control, allowed
		if (match.getCurrentState() == eMatchStatus.NONE) return true;
		
		// if the match isn't currently in progress, a player should
		// not be allowed to place or destroy blocks anywhere
		if (match.getCurrentState() != eMatchStatus.PLAYING)
			return false;

		// if this block is inside the start region, not allowed
		if (match.inStartRegion(loc)) return false;

		// if this block is outside the player's zone, not allowed
		if (team == null || !team.checkPosition(loc)) return false;
		
		// seems okay!
		return true;
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockPlace(BlockPlaceEvent event)
	{
		// if this block interaction is invalid, cancel the event
		if (!validInteract(event.getPlayer(), event.getBlock().getLocation()))
		{ event.setCancelled(true); return; }
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockBreak(BlockBreakEvent event)
	{
		// if this block interaction is invalid, cancel the event
		if (!validInteract(event.getPlayer(), event.getBlock().getLocation()))
		{ event.setCancelled(true); return; }
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockInteract(PlayerInteractEvent event)
	{
		// if this block interaction is invalid, cancel the event
		if (!validInteract(event.getPlayer(), event.getClickedBlock().getLocation()))
		{ event.setCancelled(true); return; }
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void entityInteract(PlayerInteractEntityEvent event)
	{
		// if this block interaction is invalid, cancel the event
		if (!validInteract(event.getPlayer(), event.getRightClicked().getLocation()))
		{ event.setCancelled(true); return; }
	}

	@EventHandler
	public void toolUsage(PlayerInteractEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match == null) return;
		
		Block block;
		BlockState blockState;
		
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
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;
				
				// determine who owns the region that the clicked block is in
				block = event.getClickedBlock();
				Set<AutoRefTeam> owns = match.locationOwnership(block.getLocation());
				
				// if the region is owned by only one team, make it one of their
				// win conditions (otherwise, we may need to configure by hand)
				if (owns.size() == 1) for (AutoRefTeam team : owns)
				{
					if (block.getState() instanceof InventoryHolder)
						team.addSourceInventory(block);
					else team.addWinCondition(block);
				}
				
				break;
				
			// this is the tool built for setting start mechanisms
			case TOOL_STARTMECH:
				
				// if there is no block involved in this event, nothing
				if (!event.hasBlock()) return;
				
				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;
				
				// determine who owns the region that the clicked block is in
				block = event.getClickedBlock();
				blockState = block.getState();
				
				if (blockState.getData() instanceof Redstone)
					match.addStartMech(block, true);
				
				break;
				
			// this isn't one of our tools...
			default: return;
		}
		
		// cancel the event, since it was one of our tools being used properly
		event.setCancelled(true);
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void creatureSpawn(CreatureSpawnEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null) return;
		
		if (event.getEntityType() == EntityType.SLIME && 
			event.getSpawnReason() == SpawnReason.NATURAL)
		{ event.setCancelled(true); return; }
		
		// if the match hasn't started, cancel
		if (match.getCurrentState() != eMatchStatus.PLAYING)
		{ event.setCancelled(true); return; }

		// if this is in the start region, cancel
		if (match.inStartRegion(event.getLocation()))
		{ event.setCancelled(true); return; }
	}

	@EventHandler
	public void endermanPickup(EntityChangeBlockEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null) return;
		
		// don't let endermen pick up blocks, as a rule
		if (event.getEntityType() == EntityType.ENDERMAN)
			event.setCancelled(true);
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void weatherChange(WeatherChangeEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getWorld());
		
		// cancels event if weather is changing to 'storm'
		if (match != null && event.toWeatherState())
			event.setCancelled(true);
	}
}

