package org.mctourney.AutoReferee.listeners;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Redstone;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefMatch.Role;
import org.mctourney.AutoReferee.AutoRefPlayer;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.AutoRefMatch.StartMechanism;
import org.mctourney.AutoReferee.AutoRefMatch.MatchStatus;
import org.mctourney.AutoReferee.goals.BlockGoal;
import org.mctourney.AutoReferee.regions.AutoRefRegion;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.LocationUtil;

import com.google.common.collect.Maps;

public class ZoneListener implements Listener
{
	AutoReferee plugin = null;

	// distance a player may travel outside of their lane without penalty
	private static final double SAFE_TRAVEL_DISTANCE = 1.595;

	// minimum teleport distance worth reporting to streamers
	private static final double LONG_TELE_DISTANCE = 6.0;

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

		Location locUnder = event.getTo().clone().add(0.0, -0.1, 0.0);
		int blockUnder = match.getWorld().getBlockTypeIdAt(locUnder);
		boolean onGround = (blockUnder != Material.AIR.getId());

		AutoRefPlayer apl = match.getPlayer(player);
		if (apl == null)
		{
			// if the player is not on a team and has left the start area, teleport back
			if (!match.isSpectator(player) && !match.inStartRegion(event.getTo()) && onGround)
			{
				player.teleport(match.getWorldSpawn());
				player.setFallDistance(0.0f);
			}
			return;
		}

		AutoRefTeam team = apl.getTeam();
		if (team == null) return;

		double fallspeed = event.getFrom().getY() - event.getTo().getY();
		Location exit = apl.getExitLocation();

		// don't bother if the player isn't in survival mode
		if (player.getGameMode() != GameMode.SURVIVAL
			|| match.inStartRegion(event.getTo())) return;

		// if a player leaves the start region...
		if (!match.inStartRegion(event.getTo()))
		{
			if (match.getCurrentState().inProgress())
			{
				// if they are leaving the start region, clear everything
				if (match.inStartRegion(event.getFrom()) && !apl.isActive()) apl.reset();

				// one way or another, the player is now active
				apl.setActive();
			}

			else if (match.getCurrentState().isBeforeMatch())
			{ if (onGround) apl.die(null, false); return; }
		}

		// if they have left their region, mark their exit location
		if (!team.canEnter(event.getTo(), 0.3))
		{
			// player is sneaking off the edge and not in freefall
			if (player.isSneaking() && team.canEnter(event.getTo()) && fallspeed < FREEFALL_THRESHOLD);

			// if there is no exit position, set the exit position
			else if (exit == null) apl.setExitLocation(player.getLocation());

			// if there is an exit position and they aren't falling, kill them
			else if (exit != null && fallspeed < FREEFALL_THRESHOLD && onGround)
				apl.die(AutoRefPlayer.VOID_DEATH, true);
		}

		// player inside region
		else
		{
			// if there is an exit location
			if (exit != null)
			{
				// if the player traveled too far through the void, kill them
				if (player.getLocation().distance(exit) > SAFE_TRAVEL_DISTANCE)
					apl.die(AutoRefPlayer.VOID_DEATH, true);

				// reset exit location since player in region
				apl.setExitLocation(null);
			}
		}
	}

	public boolean validPlayer(Player player)
	{
		// if the match is not under our control, allowed
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null || match.getCurrentState() == MatchStatus.NONE) return true;

		Role role = match.getRole(player);

		// if the player is a referee or is flying, nothing is off-limits
		if (role == Role.REFEREE || (match.getCurrentState().inProgress()
			&& player.isFlying() && role == Role.PLAYER)) return true;

		// if the match isn't currently in progress, a player should
		// not be allowed to place or destroy blocks anywhere
		if (!match.getCurrentState().inProgress()) return false;

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
		locationEvent(event, player, loc);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockBreak(BlockBreakEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getBlock().getLocation();
		locationEvent(event, player, loc);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void bucketFill(PlayerBucketFillEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
		locationEvent(event, player, loc);
	}


	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void bucketEmpty(PlayerBucketEmptyEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
		locationEvent(event, player, loc);
	}

	public void locationEvent(Cancellable event, Player player, Location loc)
	{
		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		if (match == null) return;

		if (!validPlayer(player))
		{ event.setCancelled(true); return; }

		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null && apl.getTeam().hasFlag(loc, AutoRefRegion.Flag.NO_BUILD))
		{ event.setCancelled(true); return; }
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockInteract(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getClickedBlock().getLocation();

		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		if (match == null) return;

		if (match.isPlayer(player))
		{
			if (match.isStartMechanism(loc) && !match.getStartMechanism(loc).canFlip(match))
			{ event.setCancelled(true); return; }

			if (!validPlayer(player))
			{ event.setCancelled(true); return; }
		}
		else // is spectator
		{
			if (!match.isReferee(player) && match.getCurrentState().inProgress())
				event.setCancelled(true);

			Material type = event.getClickedBlock().getType();
			if ((type == Material.WOOD_PLATE || type == Material.STONE_PLATE)
				&& match.getCurrentState().inProgress()) { event.setCancelled(true); return; }

			if (event.getClickedBlock().getState() instanceof InventoryHolder
				&& event.getAction() == Action.RIGHT_CLICK_BLOCK && match.getCurrentState().inProgress())
			{
				InventoryHolder invh = (InventoryHolder) event.getClickedBlock().getState();
				Inventory inv = invh.getInventory();

				ItemStack[] contents = inv.getContents();
				for (int i = 0; i < contents.length; ++i)
					if (contents[i] != null) contents[i] = contents[i].clone();

				Inventory newinv;
				if (inv instanceof DoubleChestInventory)
					newinv = Bukkit.getServer().createInventory(null, 54, "Large Chest");
				else newinv = Bukkit.getServer().createInventory(null, inv.getType());
				newinv.setContents(contents);

				player.openInventory(newinv);
				event.setCancelled(true); return;
			}
		}
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
	}

	// restrict item pickup by referees
	@EventHandler(priority=EventPriority.HIGHEST)
	public void refereePickup(PlayerPickupItemEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null && match.getCurrentState().inProgress()
			&& !match.isPlayer(event.getPlayer())) event.setCancelled(true);
	}

	// restrict item pickup by referees
	@EventHandler(priority=EventPriority.HIGHEST)
	public void refereeDrop(PlayerDropItemEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null && match.getCurrentState().inProgress()
			&& !match.isPlayer(event.getPlayer())) event.setCancelled(true);

		if (event.getPlayer().getListeningPluginChannels().contains(
			AutoReferee.REFEREE_PLUGIN_CHANNEL)) event.setCancelled(true);
	}

	@EventHandler
	public void toolUsage(PlayerInteractEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match == null || match.getCurrentState().inProgress()) return;

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

				for (AutoRefTeam team : match.getTeams())
					if (!team.hasFlag(block.getLocation(), AutoRefRegion.Flag.NO_BUILD))
						team.addGoal(new BlockGoal(team, block));

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
		if (match == null || match.getCurrentState().inProgress()) return;

		// this event is not an "item" event
		if (event.getPlayer().getItemInHand() == null) return;

		// get type id of the event and check if its one of our tools
		int typeID = event.getPlayer().getItemInHand().getTypeId();
		if (!toolMap.containsKey(typeID)) return;

		// get which action to perform
		switch (toolMap.get(typeID))
		{
			// this is the tool built for protecting entities
			case TOOL_PROTECT:

				// if there is no entity involved in this event, nothing
				if (event.getRightClicked() == null) return;

				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;

				// entity name
				String ename = String.format("%s @ %s", event.getRightClicked().getType().getName(),
					LocationUtil.toBlockCoords(event.getRightClicked().getLocation()));

				// save the entity's unique id
				UUID uid; match.toggleProtection(uid = event.getRightClicked().getUniqueId());
				match.broadcast(ChatColor.RED + ename + ChatColor.RESET + " is " +
					(match.isProtected(uid) ? "" : "not ") + "a protected entity");


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
		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
		{ event.setCancelled(true); return; }

		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null || match.getCurrentState() == MatchStatus.NONE) return;

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
		if (!match.getCurrentState().inProgress())
		{ event.setCancelled(true); return; }

		// if this is a safe zone, cancel
		if (match.hasFlag(event.getLocation(), AutoRefRegion.Flag.SAFE))
		{ event.setCancelled(true); return; }
	}

	public void teleportEvent(Player pl, Location fm, Location to)
	{
		// cannot compare locations in different worlds
		if (fm.getWorld() != to.getWorld()) return;

		// if distance is too small to matter, forget about it
		double dsq = fm.distanceSquared(to);
		if (dsq <= SAFE_TRAVEL_DISTANCE * SAFE_TRAVEL_DISTANCE) return;

		AutoRefMatch match = plugin.getMatch(to.getWorld());
		if (match == null || match.getCurrentState() == MatchStatus.NONE) return;

		// get the player that teleported
		AutoRefPlayer apl = match.getPlayer(pl);
		if (apl == null) return;
		apl.setLastTeleportLocation(to);

		// generate message regarding the teleport event
		String bedrock = match.blockInRange(BlockData.BEDROCK, to, 5) != null ? " (near bedrock)" : "";
		String message = apl.getDisplayName() + ChatColor.GRAY + " has teleported @ " +
			LocationUtil.toBlockCoords(to) + bedrock;

		boolean excludeStreamers = dsq <= LONG_TELE_DISTANCE * LONG_TELE_DISTANCE;
		for (Player ref : match.getReferees(excludeStreamers)) ref.sendMessage(message);
		plugin.getLogger().info(ChatColor.stripColor(message));
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerTeleport(PlayerTeleportEvent event)
	{
		playerMove(event);
		switch (event.getCause())
		{
			case PLUGIN: // if this teleport is caused by a plugin
			case COMMAND: // or a vanilla command of some sort, do nothing
				break;

			default: // otherwise, fire a teleport event (to notify)
				teleportEvent(event.getPlayer(), event.getFrom(), event.getTo());
				return;
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerVehicleEnter(VehicleEnterEvent event)
	{ teleportEvent((Player) event.getEntered(), event.getEntered().getLocation(), event.getVehicle().getLocation()); }

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerBedEnter(PlayerBedEnterEvent event)
	{ teleportEvent(event.getPlayer(), event.getPlayer().getLocation(), event.getBed().getLocation()); }

	@EventHandler
	public void creatureTarget(EntityTargetEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null || event.getTarget() == null) return;

		// if the target is a player that isn't on a team, get rid of the target
		if (event.getTarget().getType() == EntityType.PLAYER &&
			!match.isPlayer((Player) event.getTarget()))
		{ event.setTarget(null); return; }

		if (!match.getCurrentState().inProgress() ||
			match.hasFlag(event.getTarget().getLocation(), AutoRefRegion.Flag.SAFE))
		{ event.setTarget(null); return; }
	}

	@EventHandler
	public void explosion(EntityExplodeEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null) return;

		Iterator<Block> iter = event.blockList().iterator();
		blockloop: while (iter.hasNext())
		{
			Block b = iter.next();
			if (match.hasFlag(b.getLocation(), AutoRefRegion.Flag.NO_EXPLOSIONS))
			{ iter.remove(); continue blockloop; }
		}
	}

	@EventHandler
	public void endermanPickup(EntityChangeBlockEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getBlock().getWorld());
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

