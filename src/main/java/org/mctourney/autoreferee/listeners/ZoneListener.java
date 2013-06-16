package org.mctourney.autoreferee.listeners;

import java.util.Iterator;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.Plugin;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefMatch.MatchStatus;
import org.mctourney.autoreferee.AutoRefMatch.Role;
import org.mctourney.autoreferee.goals.BlockGoal;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;

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

	public ZoneListener(Plugin p)
	{ plugin = (AutoReferee) p; }

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

		// announce region (for cosmetic regions)
		for (AutoRefRegion reg : team.getRegions())
			if (reg.getName() != null && reg.isEnterEvent(event)) reg.announceRegion(apl);

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

		// if we are in practice mode, whatever...
		if (match.isPracticeMode()) return true;

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
			if (!match.getPlayer(player).isInsideLane())
			{ event.setCancelled(true); return; }

			if (match.isStartMechanism(loc) && !match.getStartMechanism(loc).canFlip(match))
			{ event.setCancelled(true); return; }

			if (!validPlayer(player))
			{ event.setCancelled(true); return; }
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void entityInteract(PlayerInteractEntityEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getRightClicked().getLocation();

		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		if (match == null) return;

		if (!validPlayer(player) || !match.getPlayer(player).isInsideLane())
		{ event.setCancelled(true); return; }
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void creatureSpawn(CreatureSpawnEvent event)
	{
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

	public void teleportEvent(Player pl, Location fm, Location to, String reason)
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
		String bedrock = BlockGoal.blockInRange(BlockData.BEDROCK, to, 5) != null ? " (near bedrock)" : "";
		String message = apl.getDisplayName() + ChatColor.GRAY + " has teleported @ " +
			LocationUtil.toBlockCoords(to) + bedrock + (reason != null ? " " + reason : "");

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

			case UNKNOWN: // not meaningful data
				break;

			default: // otherwise, fire a teleport event (to notify)
				String reason = "by " + event.getCause().name().toLowerCase().replaceAll("_", " ");
				teleportEvent(event.getPlayer(), event.getFrom(), event.getTo(), reason);
				break;
		}
	}

	private static Map<Class<? extends Entity>, String> entityRenames = Maps.newHashMap();
	static
	{
		entityRenames.put(Minecart.class, "minecart");
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerVehicleEnter(VehicleEnterEvent event)
	{
		String vehicleType = event.getVehicle().getType().getName().toLowerCase();

		Class<? extends Entity> clazz = event.getVehicle().getType().getEntityClass();
		for (Map.Entry<Class<? extends Entity>, String> e : entityRenames.entrySet())
			if (e.getKey().isAssignableFrom(clazz)) vehicleType = e.getValue();

		if (event.getEntered() instanceof Player)
			teleportEvent((Player) event.getEntered(), event.getEntered().getLocation(),
				event.getVehicle().getLocation(), "into a " + vehicleType);
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerBedEnter(PlayerBedEnterEvent event)
	{
		teleportEvent(event.getPlayer(), event.getPlayer().getLocation(),
			event.getBed().getLocation(), "into a bed");
	}

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