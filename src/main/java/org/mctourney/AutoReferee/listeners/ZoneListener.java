package org.mctourney.AutoReferee.listeners;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.conversations.BooleanPrompt;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
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
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.material.Redstone;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefPlayer;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.AutoRefMatch.StartMechanism;
import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;
import org.mctourney.AutoReferee.source.SourceInventory;
import org.mctourney.AutoReferee.source.SourceInventoryBlock;
import org.mctourney.AutoReferee.source.SourceInventoryEntity;
import org.mctourney.AutoReferee.util.BlockVector3;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

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
		TOOL_PROTECT,
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

		// tools.protect-entities: golden sword
		toolMap.put(parseTool(plugin.getConfig().getString(
			"config-mode.tools.protect-entities", null), Material.GOLD_SWORD), 
			ToolAction.TOOL_PROTECT);
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
			// if game isn't going yet, they are leaving the start region
			if (match.getCurrentState() == eMatchStatus.PLAYING)
				player.getInventory().clear();
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
		
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		if (match == null) return;
		
		if (!validPlayer(player))
		{ event.setCancelled(true); return; }
		
		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null && !apl.getTeam().canBuild(loc))
		{ event.setCancelled(true); return; }
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockBreak(BlockBreakEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();
		
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		if (match == null) return;

		if (!validPlayer(player))
		{ event.setCancelled(true); return; }
		
		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null && !apl.getTeam().canBuild(loc))
		{ event.setCancelled(true); return; }
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockInteract(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getClickedBlock().getLocation();
		
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		if (match == null) return;
		
		if (!validPlayer(player))
		{ event.setCancelled(true); return; }
		
		AutoRefPlayer apl = match.getPlayer(player);
		if (!plugin.isAutoMode() && match.isStartMechanism(loc)) return;
		
		if (apl != null && !apl.getTeam().canEnter(loc, 0.0))
		{ event.setCancelled(true); return; }
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void entityInteract(PlayerInteractEntityEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getRightClicked().getLocation();
		
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		if (match == null) return;
		
		if (!validPlayer(player))
		{ event.setCancelled(true); return; }
		
		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null && !apl.getTeam().canEnter(loc, 0.0))
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
				
				if (block.getState() instanceof InventoryHolder)
				{
					// setup source inventory
					SourceInventory src = SourceInventoryBlock.fromBlock(block);
					Set<AutoRefTeam> cfgTeams = Sets.newLinkedHashSet();
					
					boolean removed = false, r;
					for (AutoRefTeam team : match.getTeams())
						if (team.checkPosition(block.getLocation()))
					{
						Set<BlockData> prevObj = getObjectives();
						removed |= (r = team.targetChests.values().remove(src));
						Set<BlockData> newObj = getObjectives();

						prevObj.removeAll(newObj);
						for (BlockData bd : prevObj) match.messageReferees(
							"team", team.getRawName(), "obj", "-" + bd.toString());

						if (r) match.broadcast(String.format("%s is no longer a source for %s for %s", 
							src.getName(), src.blockdata.getName(), team.getName()));
						else cfgTeams.add(team);
					}
					
					if (!removed && !cfgTeams.isEmpty())
					{
						if (cfgTeams.size() == 1) for (AutoRefTeam team : cfgTeams)
							team.addSourceInventory(src);
						else new Conversation(plugin, event.getPlayer(), 
							new SourceInventoryConfirmation(src, cfgTeams)).begin();
					}
				}
				else for (AutoRefTeam team : match.getTeams())
					if (team.checkPosition(block.getLocation()))
						team.addWinCondition(block);
				
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
				
				// if there is no entity involved in this event, nothing
				if (event.getRightClicked() == null) return;
				
				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;
				
				// setup source inventory
				SourceInventory src = SourceInventoryEntity.fromEntity(event.getRightClicked());
				Set<AutoRefTeam> cfgTeams = Sets.newLinkedHashSet();
				
				boolean removed = false, r;
				for (AutoRefTeam team : match.getTeams())
					if (team.checkPosition(event.getRightClicked().getLocation()))
				{
					Set<BlockData> prevObj = getObjectives();
					removed |= (r = team.targetChests.values().remove(src));
					Set<BlockData> newObj = getObjectives();

					prevObj.removeAll(newObj);
					for (BlockData bd : prevObj) match.messageReferees(
						"team", team.getRawName(), "obj", "-" + bd.toString());
					
					if (r) match.broadcast(String.format("%s is no longer a source for %s for %s", 
						src.getName(), src.blockdata.getName(), team.getName()));
					else cfgTeams.add(team);
				}
				
				if (!removed && !cfgTeams.isEmpty())
				{
					if (cfgTeams.size() == 1) for (AutoRefTeam team : cfgTeams)
						team.addSourceInventory(src);
					else new Conversation(plugin, event.getPlayer(), 
						new SourceInventoryConfirmation(src, cfgTeams)).begin();
				}
				
				break;
				
			// this is the tool built for protecting entities
			case TOOL_PROTECT:
				
				// if there is no entity involved in this event, nothing
				if (event.getRightClicked() == null) return;
				
				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;

				// entity name
				String ename = String.format("%s @ %s", event.getRightClicked().getType().getName(),
					BlockVector3.fromLocation(event.getRightClicked().getLocation()).toCoords());
				
				// save the entity's unique id
				UUID uid = event.getRightClicked().getUniqueId();
				if (match.protectedEntities.contains(uid))
				{
					match.protectedEntities.remove(uid);
					match.broadcast(ChatColor.RED + ename + ChatColor.RESET + 
						" is no longer a protected entity");
				}
				else
				{
					match.protectedEntities.add(uid);
					match.broadcast(ChatColor.RED + ename + ChatColor.RESET + 
						" is a protected entity");
				}
				
				
				break;
				
			// this isn't one of our tools...
			default: return;
		}
		
		// cancel the event, since it was one of our tools being used properly
		event.setCancelled(true);
	}
	
	private class SourceInventoryConfirmation extends BooleanPrompt
	{
		// inventory that is being set up
		private SourceInventory src = null;
		
		// teams to add this source inventory to
		private Set<AutoRefTeam> teams = null;
		private Iterator<AutoRefTeam> teamIterator = null;
		
		public SourceInventoryConfirmation(SourceInventory s, Set<AutoRefTeam> t)
		{
			this.teams = t;
			this.teamIterator = this.teams.iterator();
			this.src = s;
		}

		public String getPromptText(ConversationContext context)
		{
			if (!teamIterator.hasNext()) return null;
			
			// get the team for this specific prompt
			AutoRefTeam team = teamIterator.next();
			
			return String.format("Set %s as source for %s for %s?", 
				src.getName(), src.blockdata.getName(), team.getName());
		}

		@Override
		protected Prompt acceptValidatedInput(ConversationContext context, boolean res)
		{
			// if the configuration was rejected, remove the team
			if (!res) teamIterator.remove();
			
			// if there are more teams, return this prompt again
			if (teamIterator.hasNext()) return this;
			
			// add the source inventory to the remaining teams
			for (AutoRefTeam team : this.teams)
				team.addSourceInventory(this.src);
			
			// done with conversation
			return Prompt.END_OF_CONVERSATION;
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void creatureSpawn(CreatureSpawnEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null || match.getCurrentState() == eMatchStatus.NONE) return;
		
		if (event.getSpawnReason() == SpawnReason.SPAWNER_EGG)
		{
			Player spawner = null;
			double distance = Double.POSITIVE_INFINITY;
			
			// get the player who spawned this entity
			Location loc = event.getEntity().getLocation();
			for (Player pl : event.getEntity().getWorld().getPlayers())
			{
				double d = loc.distanceSquared(pl.getLocation());
				if (d < distance && pl.getItemInHand() != null && 
					pl.getItemInHand().getType() == Material.MONSTER_EGG)
				{ spawner = pl; distance = d; }
			}
			
			// if the player who spawned this creature can configure...
			if (spawner != null && spawner.hasPermission("autoreferee.configure")
				&& spawner.getGameMode() == GameMode.CREATIVE) return;
		}
		
		if (event.getEntityType() == EntityType.SLIME && 
			event.getSpawnReason() == SpawnReason.NATURAL)
		{ event.setCancelled(true); return; }
		
		// if the match hasn't started, cancel
		if (match.getCurrentState() != eMatchStatus.PLAYING)
		{ event.setCancelled(true); return; }

		// if this is a safe zone, cancel
		if (match.isSafeZone(event.getLocation()))
		{ event.setCancelled(true); return; }
	}
	
	@EventHandler
	public void creatureTarget(EntityTargetEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null || event.getTarget() == null) return;
		
		if (match.getCurrentState() != eMatchStatus.PLAYING || 
			match.isSafeZone(event.getTarget().getLocation()))
		{ event.setTarget(null); return; }
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

