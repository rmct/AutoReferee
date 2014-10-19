package org.mctourney.autoreferee.listeners;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.MapDifference;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.autoreferee.goals.AutoRefGoal;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ObjectiveTracer implements Listener
{
	AutoReferee plugin = null;

	public ObjectiveTracer(Plugin p)
	{
		plugin = (AutoReferee) p;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void saveOnOpen(InventoryOpenEvent event)
	{
		HumanEntity he = event.getPlayer();
		if (!(he instanceof Player)) return;
		Player pl = (Player) he;

		AutoRefMatch match = plugin.getMatch(pl.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match == null || apl == null)
		{ return; }
		if (apl.getTeam() == null)
		{ return; }

		InventoryHolder holder = event.getInventory().getHolder();

		if (holder == pl)
		{ apl.clearActiveInventoryInfo(); return; }

		String holderDescription;
		Location location;
		if (holder instanceof BlockState)
		{
			BlockState h = (BlockState) holder;
			location = h.getLocation();
			holderDescription = h.getType().toString();
		}
		else if (holder instanceof DoubleChest)
		{
			DoubleChest c = (DoubleChest) holder;
			location = c.getLocation();
			holderDescription = "double chest";
		}
		else if (holder instanceof Entity)
		{
			Entity e = (Entity) holder;
			location = e.getLocation();
			holderDescription = e.getType().toString();
		}
		else
		{
			holderDescription = "unknown container";
			location = pl.getLocation();
		}

		apl.setActiveInventoryInfo(apl.getCarrying(), location, holderDescription);
	}


	@EventHandler(priority = EventPriority.MONITOR)
	public void onClose(InventoryCloseEvent event)
	{
		HumanEntity he = event.getPlayer();
		if (!(he instanceof Player)) return;
		Player pl = (Player) he;

		AutoRefMatch match = plugin.getMatch(pl.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match == null || apl == null)
		{ return; }
		if (apl.getTeam() == null)
		{ return; }

		GoalsInventorySnapshot before = apl.getBeforeOpeningInventorySnapshot();
		GoalsInventorySnapshot snap = apl.getCarrying();

		if (before == null)
		{ return; }

		MapDifference<BlockData, Integer> diff = before.getDiff(snap);
		if (diff.areEqual())
		{ return; }

		GoalsInventorySnapshot droppedOff = GoalsInventorySnapshot.fromDiff(diff, true);
		GoalsInventorySnapshot pickedUp = GoalsInventorySnapshot.fromDiff(diff, false);

		if (!droppedOff.isEmpty() && !pickedUp.isEmpty())
		{
			match.addEvent(new TranscriptEvent(match,
					TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
					// {player} has dropped off {snap} and picked up {snap} from a {container} (@ {loc})
					"%s has dropped off %s and picked up %s from a %s (@ %s)", apl.getDisplayName(),
					droppedOff, pickedUp, apl.getInventoryDescription(),
					LocationUtil.toBlockCoords(apl.getInventoryLocation())),
					apl.getInventoryLocation(), unpack2(droppedOff, pickedUp, apl)
			));
		}
		else if (!droppedOff.isEmpty())
		{
			match.addEvent(new TranscriptEvent(match,
					TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
					// {player} has dropped off {snap} in a {container} (@ {loc})
					"%s has dropped off %s in a %s (@ %s)", apl.getDisplayName(),
					droppedOff, apl.getInventoryDescription(),
					LocationUtil.toBlockCoords(apl.getInventoryLocation())),
					apl.getInventoryLocation(), unpack(droppedOff, apl)
			));
		}
		else if (!pickedUp.isEmpty())
		{
			match.addEvent(new TranscriptEvent(match,
					TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
					// {player} has picked up {snap} from a {container} (@ {loc})
					"%s has picked up %s from a %s (@ %s)", apl.getDisplayName(),
					pickedUp, apl.getInventoryDescription(),
					LocationUtil.toBlockCoords(apl.getInventoryLocation())),
					apl.getInventoryLocation(), unpack(pickedUp, apl)
			));
		}
		apl.clearActiveInventoryInfo();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void tracePlace(BlockPlaceEvent event)
	{
		Player pl = event.getPlayer();
		Block block = event.getBlock();

		AutoRefMatch match = plugin.getMatch(block.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match == null || apl == null)
		{ return; }
		if (apl.getTeam() == null)
		{ return; }

		for (Map.Entry<BlockData, AutoRefGoal> entry :
				apl.getTeam().getGoalsByObjective().entrySet())
		{
			BlockData b = entry.getKey();
			AutoRefGoal g = entry.getValue();
			if (b.matchesBlock(block))
			{
				if (g.getItemStatus() != AutoRefGoal.ItemStatus.TARGET
						&& g.isSatisfied(match)) {
					match.addEvent(new TranscriptEvent(match,
							TranscriptEvent.EventType.OBJECTIVE_PLACED, String.format(
							"%s has placed the %s on the Victory Monument!",
							apl.getDisplayName(), b.getDisplayName()), block.getLocation(), apl, b
					));
				} else {
					// TranscriptEvent.ObjectiveDetailType.PLACE
					match.addEvent(new TranscriptEvent(match,
							TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
							// {player} has placed a {goal} block (@ {loc})
							"%s has placed a %s block (@ %s)", apl.getDisplayName(),
							b.getDisplayName(),
							LocationUtil.toBlockCoords(block.getLocation())),
							block.getLocation(), apl, b
					));
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void traceBreak(BlockBreakEvent event)
	{
		Player pl = event.getPlayer();
		Block block = event.getBlock();

		AutoRefMatch match = plugin.getMatch(block.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match == null || apl == null)
		{ return; }
		if (apl.getTeam() == null)
		{ return; }

		checkContainerBreak(block, apl.getTeam().getObjectives(), match,
				apl.getDisplayName());
		for (BlockData b : apl.getTeam().getObjectives())
		{
			if (b.matchesBlock(block))
			{
				// TranscriptEvent.ObjectiveDetailType.BREAK_PLAYER
				match.addEvent(new TranscriptEvent(match,
						TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
						// {player} has broken a {goal} block (@ {loc})
						"%s has broken a %s block (@ %s)", apl.getDisplayName(),
						b.getDisplayName(),
						LocationUtil.toBlockCoords(block.getLocation())), block
						.getLocation(), apl, b
				));
			}
		}
	}

	/**
	 * A set of players who have recently died, and therefore to ignore
	 * PlayerDropEvents from.
	 * <p/>
	 * Players should only ever remain in this Set for less than 1 tick.
	 */
	private final HashSet<Player> dropSkipPlayers = new HashSet<Player>();

	@EventHandler(priority = EventPriority.MONITOR)
	public void traceThrow(PlayerDropItemEvent event)
	{
		Player pl = event.getPlayer();

		if (dropSkipPlayers.contains(pl))
		{ return; }

		BlockData item = new BlockData(event.getItemDrop().getItemStack());

		AutoRefMatch match = plugin.getMatch(pl.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match == null || apl == null)
		{ return; }
		if (apl.getTeam() == null)
		{ return; }

		for (BlockData b : apl.getTeam().getObjectives())
		{
			if (b.equals(item))
			{
				GoalsInventorySnapshot droppedItems = new GoalsInventorySnapshot(event.getItemDrop().getItemStack(), b);
				match.addEvent(new TranscriptEvent(match,
						TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
							// {player} has tossed {snap} (@ {loc})
							"%s has tossed %s (@ %s)", apl.getDisplayName(),
							droppedItems, LocationUtil.toBlockCoords(pl.getLocation())),
						pl.getLocation(), apl, b
				));

				if (apl.hasActiveInventoryInfo())
				{
					apl.getBeforeOpeningInventorySnapshot().subtractInPlace(droppedItems);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent ev)
	{
		final Player pl = ev.getEntity();

		// Ignore PlayerDropItemEvent for 1 tick
		dropSkipPlayers.add(pl);
		plugin.getServer().getScheduler().runTask(plugin, new Runnable()
		{
			public void run()
			{
				dropSkipPlayers.remove(pl);
			}
		});

		AutoRefMatch match = plugin.getMatch(pl.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match == null || apl == null)
		{ return; }
		if (apl.getTeam() == null)
		{ return; }

		GoalsInventorySnapshot snapshot = GoalsInventorySnapshot
				.fromItemsAndGoals(ev.getDrops(), apl.getTeam().getObjectives());

		if (snapshot.isEmpty())
		{ return; }

		match.addEvent(new TranscriptEvent(
						match,
						TranscriptEvent.EventType.OBJECTIVE_DETAIL,
						// {player} has dropped {snap} when dying (@ {loc})
						String.format("%s has dropped %s when dying (@ %s)",
								apl.getDisplayName(), snapshot, LocationUtil.toBlockCoords(
										apl.getLocation())
						),
						apl.getLocation(), unpack(snapshot, apl, apl.getKiller())
				)
		);
		apl.clearActiveInventoryInfo();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void tracePickup(PlayerPickupItemEvent event)
	{
		Player pl = event.getPlayer();

		if (event.getItem() == null)
		{ return; }
		if (event.getItem().getItemStack() == null)
		{ return; }

		BlockData item = new BlockData(event.getItem().getItemStack());

		AutoRefMatch match = plugin.getMatch(pl.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match == null || apl == null)
		{ return; }
		if (apl.getTeam() == null)
		{ return; }

		for (BlockData b : apl.getTeam().getObjectives())
		{
			if (b.equals(item))
			{
				GoalsInventorySnapshot pickupItems = new GoalsInventorySnapshot(event.getItem().getItemStack(), b);
				match.addEvent(new TranscriptEvent(match,
						TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
							// {player} has picked up {snap} (@ {loc})
							"%s has picked up %s (@ %s)", apl.getDisplayName(),
							pickupItems, LocationUtil.toBlockCoords(pl.getLocation())),
						pl.getLocation(), apl, b
				));

				if (apl.hasActiveInventoryInfo())
				{
					apl.getBeforeOpeningInventorySnapshot().addInPlace(pickupItems);
				}

				untrackItem(event.getItem());
			}
		}
	}

	// This is done due to ordering of event handler calls - the monitor
	// listener that removes the entry might be called before us, so we fetch it
	// first
	private AutoRefPlayer currentResponsibleTntPlayer;

	@EventHandler(priority = EventPriority.HIGHEST)
	public void keepExplodeInfo(EntityExplodeEvent event)
	{
		currentResponsibleTntPlayer = (event.getEntity() == null) ? null : plugin.getTNTOwner(event
				.getEntity());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void explosionTraces(EntityExplodeEvent event)
	{
		List<Block> blocks = event.blockList();
		if (blocks.isEmpty()) return;
		// remember, event entity can be null
		Entity cause = event.getEntity();
		AutoRefMatch match = plugin.getMatch(blocks.get(0).getWorld());
		Location loc = event.getLocation();

		if (match == null)
		{ return; }

		Set<AutoRefTeam> teams = match.getTeams();
		Set<BlockData> goals = Sets.newHashSet();
		for (AutoRefTeam te : teams)
			goals.addAll(te.getObjectives());

		if (goals.isEmpty()) return; // not an objective match

		String causeStr;

		if (currentResponsibleTntPlayer != null)
		{
			causeStr = currentResponsibleTntPlayer.getDisplayName();
		}
		else if (cause != null)
		{
			causeStr = "A " + StringUtils.capitalize(cause.getType().toString().toLowerCase());
		}
		else
		{
			causeStr = "An explosion";
		}

		for (Block b : blocks)
			checkContainerBreak(b, goals, match, causeStr, "exploded");

		GoalsInventorySnapshot snap = new GoalsInventorySnapshot(blocks, goals);
		if (snap.isEmpty()) return;

		if (currentResponsibleTntPlayer != null)
		{
			// TranscriptEvent.ObjectiveDetailType.BREAK_PLAYER
			match.addEvent(new TranscriptEvent(
					match,
					TranscriptEvent.EventType.OBJECTIVE_DETAIL,
					// {entity} has exploded {snap} block(s) in {area} (@ {loc})
					String.format("%s has exploded %s block(s) in %s (@ %s)", causeStr, snap,
							getLocationDescription(loc, match), LocationUtil.toBlockCoords(loc)),
					loc, unpack(snap, currentResponsibleTntPlayer)
			));
		}
		else
		{
			// TranscriptEvent.ObjectiveDetailType.BREAK_NONPLAYER
			match.addEvent(new TranscriptEvent(
					match,
					TranscriptEvent.EventType.OBJECTIVE_DETAIL,
					// {entity} has exploded {snap} block(s) in {area} (@ {loc})
					String.format("%s has exploded %s block(s) in %s (@ %s)", causeStr, snap,
							getLocationDescription(loc, match), LocationUtil.toBlockCoords(loc)),
					loc, unpack(snap)
			));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void traceChange(EntityChangeBlockEvent event)
	{
		Block block = event.getBlock();
		AutoRefMatch match = plugin.getMatch(block.getWorld());
		if (match == null)
		{ return; }

		Entity entity = event.getEntity();
		Location loc = block.getLocation();

		Set<AutoRefTeam> teams = teamsWithAccess(block.getLocation(), match, 0.1);
		Set<BlockData> goals = Sets.newHashSet();
		for (AutoRefTeam te : teams)
			goals.addAll(te.getObjectives());
		if (goals.isEmpty()) return; // not a block objectives match

		BlockData former = BlockData.fromBlock(block);
		BlockData after = new BlockData(event.getTo(), event.getData());

		String causeStr = "A " + StringUtils.capitalize(entity.getType().toString().toLowerCase());

		for (BlockData goal : goals)
		{
			if (goal.equals(former))
			{
				// process break
				// TranscriptEvent.ObjectiveDetailType.BREAK_NONPLAYER
				match.addEvent(new TranscriptEvent(match,
						TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
						// {entity} has broken a {goal} in {area} (@ {loc})
						"%s has broken a %s in %s (@ %s)", causeStr, goal,
						getLocationDescription(loc, match),
						LocationUtil.toBlockCoords(loc)), entity.getLocation(), goal
				));
			}
			else if (goal.equals(after))
			{
				// process place
				// TranscriptEvent.ObjectiveDetailType.PLACE
				match.addEvent(new TranscriptEvent(match,
						TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
						// {entity} has placed a {goal} in {area} (@ {loc})
						"%s has placed a %s in %s (@ %s)", causeStr, goal,
						getLocationDescription(loc, match),
						LocationUtil.toBlockCoords(loc)), entity.getLocation(), goal
				));

			}
		}

		if (block.getType() != Material.AIR)
		{
			// process potential break
			checkContainerBreak(block, goals, match, causeStr);
		}
	}

	@EventHandler
	public void traceDespawn(ItemDespawnEvent event)
	{
		if (event.getEntity() == null)
		{ return; }
		if (event.getEntity().getItemStack() == null)
		{ return; }

		Location loc = event.getEntity().getLocation();

		AutoRefMatch match = plugin.getMatch(loc.getWorld());

		if (match == null)
		{ return; }

		Set<AutoRefTeam> teams = match.getTeams();
		Set<BlockData> goals = Sets.newHashSet();
		for (AutoRefTeam te : teams)
			goals.addAll(te.getObjectives());

		if (goals.isEmpty())
		{ return; }

		GoalsInventorySnapshot snap = new GoalsInventorySnapshot(event.getEntity().getItemStack(), goals);

		match.addEvent(new TranscriptEvent(match,
				TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
					// "A {snap} item entity has EXPIRED in {area} (@ {loc})"
					"A %s item entity has EXPIRED in %s (@ %s)",
					snap, getLocationDescription(loc, match), LocationUtil.toBlockCoords(loc)
				), loc, unpack(snap)
		));
	}

	// Not an @EventHandler
	public void checkContainerBreak(Block block, Set<BlockData> goals, AutoRefMatch match,
			String causeStr)
	{
		checkContainerBreak(block, goals, match, causeStr, "broken");
	}

	public void checkContainerBreak(Block block, Set<BlockData> goals, AutoRefMatch match,
			String causeStr, String actionStr)
	{
		BlockState state = block.getState();
		if (state instanceof InventoryHolder)
		{
			GoalsInventorySnapshot snap = new GoalsInventorySnapshot(((InventoryHolder) state).getInventory(), goals);
			if (snap.isEmpty()) return;
			Location loc = block.getLocation();

			// TranscriptEvent.ObjectiveDetailType.CONTAINER_DEATH
			match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.OBJECTIVE_DETAIL,
					// {entity} has {action}ed a {container}, spilling {snap} in {area} (@ {loc})
					String.format(
							"%s has %s a %s, spilling %s in %s (@ %s)", causeStr, actionStr,
							ChatColor.GOLD
									+ StringUtils.capitalize(block.getType().toString()
									.toLowerCase()) + ChatColor.RESET, snap, ChatColor.BLUE
									+ getLocationDescription(loc, match),
							LocationUtil.toBlockCoords(loc)
					), loc, unpack(snap)
			));
		}
	}

	// Item entity tracker

	private void trackItem(Item item)
	{
		// todo
	}

	private void untrackItem(Item item)
	{
		// todo
	}

	// Utility functions

	private Object[] unpack(GoalsInventorySnapshot snap, Object... others)
	{
		List<Object> arr = Lists.newArrayList(others);
		while (arr.contains(null))
			arr.remove(null);

		arr.addAll(snap.keySet());
		return arr.toArray();
	}

	private Object[] unpack(GoalsInventorySnapshot snap)
	{
		return Lists.newArrayList(snap.keySet()).toArray();
	}

	private Object[] unpack2(GoalsInventorySnapshot snap1, GoalsInventorySnapshot snap2, Object... others)
	{
		List<Object> arr = Lists.newArrayList(others);
		while (arr.contains(null))
			arr.remove(null);

		arr.addAll(Sets.union(snap1.keySet(), snap2.keySet()));
		return arr.toArray();
	}

	private Set<AutoRefTeam> teamsWithAccess(Location loc, AutoRefMatch match, double distance)
	{
		Set<AutoRefTeam> teams = Sets.newHashSet();
		for (AutoRefTeam team : match.getTeams())
		{
			if (team.canEnter(loc, distance))
				teams.add(team);
		}
		return teams;
	}

	private String getLocationDescription(Location loc, AutoRefMatch match)
	{
		// TODO map-defined points of interest
		Validate.isTrue(match.getWorld().equals(loc.getWorld()),
				"The provided location must be within the provided match!");
		if (match.inStartRegion(loc))
			return "the starting area";

		Set<AutoRefTeam> teamsDirect = teamsWithAccess(loc, match, 0);
		Set<AutoRefTeam> teamsNearby = teamsWithAccess(loc, match, 3);

		if (teamsDirect.isEmpty())
		{
			if (teamsNearby.isEmpty())
				return "the void lane";
			else if (teamsNearby.size() == 1)
				return "the void lane nearby " + teamsNearby.iterator().next().getDisplayName()
						+ ChatColor.RESET;
			else
				return "the void lane nearby the shared area";
		}
		else if (teamsDirect.size() == 1)
			// XXX is this the coloring we want?
			// note that this is what will happen most of the time
			return teamsDirect.iterator().next().getDisplayName() + "'s lane" + ChatColor.RESET;
		else
		{
			return "the shared area"; // TODO improve for modes where both teams share the whole map
		}
	}
}
