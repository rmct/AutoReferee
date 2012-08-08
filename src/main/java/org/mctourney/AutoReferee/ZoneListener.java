package org.mctourney.AutoReferee;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
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
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.material.Redstone;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoRefMatch.StartMechanism;
import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;
import org.mctourney.AutoReferee.util.SourceInventory;

import com.google.common.collect.Maps;

public class ZoneListener implements Listener 
{
	AutoReferee plugin = null;
	
	// distance a player may travel outside of their lane without penalty
	private static final double VOID_SAFE_TRAVEL_DISTANCE = 1.595;

	public static final double SNEAK_DISTANCE = 0.301;
	public static final double FREEFALL_THRESHOLD = 0.350;

	// convenience for changing defaults
	enum ToolAction
	{
		TOOL_WINCOND,
		TOOL_STARTMECH,
	}

	private Map<Integer, ToolAction> toolMap;
	
	public static int parseTool(String s, Material def)
	{
		// if no string was passed, return default
		if (s == null) return def.getId();
		
		// check to see if this is a material name
		Material mat = Material.getMaterial(s);
		if (mat != null) return mat.getId();
		
		// try to parse as an integer
		try { return Integer.parseInt(s); }
		catch (Exception e) { return def.getId(); }
	}

	public ZoneListener(Plugin p)
	{
		plugin = (AutoReferee) p;
		toolMap = Maps.newHashMap();

		// tools.win-condition: golden shovel
		toolMap.put(parseTool(plugin.getConfig().getString(
			"config-mode.tools.win-condition", null), Material.GOLD_SPADE), 
			ToolAction.TOOL_WINCOND);

		// tools.start-mechanism: golden axe
		toolMap.put(parseTool(plugin.getConfig().getString(
			"config-mode.tools.start-mechanism", null), Material.GOLD_AXE), 
			ToolAction.TOOL_STARTMECH);
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null) return;

		AutoRefPlayer apl = match.getPlayer(player);
		if (apl == null) return;
		
		AutoRefTeam team = apl.getTeam();
		if (team == null) return;
		
		double fallspeed = event.getFrom().getY() - event.getTo().getY();
		Location exit = apl.getExitLocation();
		
		// don't bother if the player isn't in survival mode
		if (player.getGameMode() != GameMode.SURVIVAL 
			|| match.inStartRegion(event.getTo())) return;
		
		// if a player leaves the start region... 
		if (match.inStartRegion(event.getFrom()) && 
			!match.inStartRegion(event.getTo()))
		{
			// if game isn't going, teleport them back
			if (match.getCurrentState() != eMatchStatus.PLAYING)
			{
				player.teleport(team.getSpawnLocation());
				player.setVelocity(new org.bukkit.util.Vector());
				player.setFallDistance(0.0f);
			}
			
			// if game is being played, empty their inventory
			else player.getInventory().clear();
		}
		
		// if they have left their region, mark their exit location
		if (!team.canEnter(event.getTo(), 0.3))
		{
			// player is sneaking off the edge and not in freefall
			if (player.isSneaking() && team.canEnter(event.getTo()) && fallspeed < FREEFALL_THRESHOLD);
			
			// if there is no exit position, set the exit position
			else if (exit == null) apl.setExitLocation(player.getLocation());
			
			// if there is an exit position and they aren't falling, kill them
			else if (exit != null && fallspeed == 0.0)
				apl.die(AutoRefPlayer.VOID_DEATH, true);
		}
		
		// player inside region
		else
		{
			// if there is an exit location
			if (exit != null)
			{
				// if the player traveled too far through the void, kill them
				if (player.getLocation().distance(exit) > VOID_SAFE_TRAVEL_DISTANCE)
					apl.die(AutoRefPlayer.VOID_DEATH, true);
				
				// reset exit location since player in region
				apl.setExitLocation(null);
			}
		}
	}
	
	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event)
	{
		Player player = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null) return;

		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null) apl.respawn();
	}
	
	public boolean validPlayer(Player player)
	{
		// if the match is not under our control, allowed
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null || match.getCurrentState() == eMatchStatus.NONE) return true;
		
		// if the player is a referee, nothing is off-limits
		if (match.getReferees().contains(player)) return true;
		
		// if the match isn't currently in progress, a player should
		// not be allowed to place or destroy blocks anywhere
		if (match.getCurrentState() != eMatchStatus.PLAYING) return false;
		
		// if the player is not in their lane, they shouldn't be allowed to interact
		AutoRefPlayer apl = match.getPlayer(player);
		if (apl == null || apl.getExitLocation() != null) return false;
		
		// seems okay!
		return true;
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockPlace(BlockPlaceEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();
		
		if (!validPlayer(player))
		{ event.setCancelled(true); return; }
		
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		AutoRefPlayer apl = match.getPlayer(player);
		
		if (!apl.getTeam().canBuild(loc))
		{ event.setCancelled(true); return; }
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockBreak(BlockBreakEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();

		if (!validPlayer(player))
		{ event.setCancelled(true); return; }
		
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		AutoRefPlayer apl = match.getPlayer(player);
		
		if (!apl.getTeam().canBuild(loc))
		{ event.setCancelled(true); return; }
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockInteract(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getClickedBlock().getLocation();
		
		if (!validPlayer(player))
		{ event.setCancelled(true); return; }
		
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		AutoRefPlayer apl = match.getPlayer(player);
		
		if (!plugin.isAutoMode() && match.isStartMechanism(loc)) return;
		
		if (!apl.getTeam().canEnter(loc, 0.0))
		{ event.setCancelled(true); return; }
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void entityInteract(PlayerInteractEntityEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getRightClicked().getLocation();
		
		if (!validPlayer(player))
		{ event.setCancelled(true); return; }
		
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		AutoRefPlayer apl = match.getPlayer(player);
		
		if (!apl.getTeam().canEnter(loc, 0.0))
		{ event.setCancelled(true); return; }
	}
	
	// restrict block pickup by referees
	@EventHandler(priority=EventPriority.HIGHEST)
	public void refereePickup(PlayerPickupItemEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null && match.getCurrentState() == eMatchStatus.PLAYING 
			&& match.getPlayer(event.getPlayer()) == null) event.setCancelled(true);
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
				block = event.getClickedBlock();
				
				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;
				
				// if the region is owned by only one team, make it one of their
				// win conditions (otherwise, we may need to configure by hand)
				for (AutoRefTeam team : match.getTeams())
					if (team.checkPosition(block.getLocation()))
				{
					Iterator<SourceInventory> iter; boolean found = false;
					for (iter = team.targetChests.values().iterator(); iter.hasNext(); )
					{
						SourceInventory sinv = iter.next();
						if (block.getLocation().equals(sinv.target))
						{
							iter.remove(); found = true;
							match.broadcast(String.format("%s is no longer a source for %s", 
								sinv.getName(), sinv.blockdata.getName()));
						}
					}
					if (!found)
					{
						if (block.getState() instanceof InventoryHolder)
							team.addSourceInventory(block);
						else team.addWinCondition(block);
					}
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
				{
					// get the start mechanism
					StartMechanism sm = match.addStartMech(block, 
						((Redstone) blockState.getData()).isPowered());
					
					if (sm != null)
					{
						// announce it...
						String m = ChatColor.RED + sm.toString() + 
							ChatColor.RESET + " is a start mechanism.";
						event.getPlayer().sendMessage(m);
					}
				}
				
				break;
				
			// this isn't one of our tools...
			default: return;
		}
		
		// cancel the event, since it was one of our tools being used properly
		event.setCancelled(true);
	}

	@EventHandler
	public void toolUsage(PlayerInteractEntityEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match == null) return;
		
		// this event is not an "item" event
		if (event.getPlayer().getItemInHand() == null) return;

		// get type id of the event and check if its one of our tools
		int typeID = event.getPlayer().getItemInHand().getTypeId();
		if (!toolMap.containsKey(typeID)) return;

		// get which action to perform
		switch (toolMap.get(typeID))
		{
			// this is the tool built for setting win conditions
			case TOOL_WINCOND:
				
				// if there is no block involved in this event, nothing
				if (event.getRightClicked() == null) return;
				
				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;
				
				// if the region is owned by only one team, make it one of their
				// win conditions (otherwise, we may need to configure by hand)
				for (AutoRefTeam team : match.getTeams())
					if (team.checkPosition(event.getRightClicked().getLocation()))
				{
					Iterator<SourceInventory> iter; boolean found = false;
					for (iter = team.targetChests.values().iterator(); iter.hasNext(); )
					{
						SourceInventory sinv = iter.next();
						if (event.getRightClicked().equals(sinv.target))
						{
							iter.remove(); found = true;
							match.broadcast(String.format("%s is no longer a source for %s", 
								sinv.getName(), sinv.blockdata.getName()));
						}
					}
					if (!found)
					{
						if (event.getRightClicked() instanceof InventoryHolder)
							team.addSourceInventory((InventoryHolder) event.getRightClicked());
					}
				}
				
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

