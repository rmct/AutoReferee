package org.mctourney.autoreferee.listeners;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefMatch.TranscriptEvent;
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
	public void tracePlace(BlockPlaceEvent event)
	{
		Player pl = event.getPlayer();
		Block block = event.getBlock();

		AutoRefMatch match = plugin.getMatch(block.getWorld());
		AutoRefPlayer apl = match == null ? null : match.getPlayer(pl);

		if (match != null && apl != null)
		{
			if (apl.getTeam() != null)
				for (BlockData b : apl.getTeam().getObjectives())
				{
					if (b.matchesBlock(block))
					{
						// TranscriptEvent.ObjectiveDetailType.PLACE
						match.addEvent(new TranscriptEvent(match,
								TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
										"%s has placed a %s (@ %s)", apl.getDisplayName(),
										b.getDisplayName(),
										LocationUtil.toBlockCoords(block.getLocation())), block
										.getLocation(), apl, b));
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

		if (match != null && apl != null)
		{
			if (apl.getTeam() != null)
			{
				checkContainerBreak(block, apl.getTeam().getObjectives(), match,
						apl.getDisplayName());
				for (BlockData b : apl.getTeam().getObjectives())
				{
					if (b.matchesBlock(block))
					{
						// TranscriptEvent.ObjectiveDetailType.BREAK_PLAYER
						match.addEvent(new TranscriptEvent(match,
								TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
										"%s has broken a %s (@ %s)", apl.getDisplayName(),
										b.getDisplayName(),
										LocationUtil.toBlockCoords(block.getLocation())), block
										.getLocation(), apl, b));
					}
				}
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

		if (match != null)
		{
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
						String.format("%s has exploded %s in %s (@ %s)", causeStr, snap,
								getLocationDescription(loc, match), LocationUtil.toBlockCoords(loc)),
						loc, unpack(snap, currentResponsibleTntPlayer)));
			}
			else
			{
				// TranscriptEvent.ObjectiveDetailType.BREAK_NONPLAYER
				match.addEvent(new TranscriptEvent(
						match,
						TranscriptEvent.EventType.OBJECTIVE_DETAIL,
						String.format("%s has exploded %s in %s (@ %s)", causeStr, snap,
								getLocationDescription(loc, match), LocationUtil.toBlockCoords(loc)),
						loc, unpack(snap)));
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void traceChange(EntityChangeBlockEvent event)
	{
		Block block = event.getBlock();
		AutoRefMatch match = plugin.getMatch(block.getWorld());
		if (match != null)
		{
			Entity entity = event.getEntity();
			Location loc = block.getLocation();

			Set<AutoRefTeam> teams = teamsWithAccess(block.getLocation(), match);
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
									"%s has broken a %s in %s (@ %s)", causeStr, goal,
									getLocationDescription(loc, match),
									LocationUtil.toBlockCoords(loc)), entity.getLocation(), goal));
				}
				else if (goal.equals(after))
				{
					// process place
					// TranscriptEvent.ObjectiveDetailType.PLACE
					match.addEvent(new TranscriptEvent(match,
							TranscriptEvent.EventType.OBJECTIVE_DETAIL, String.format(
									"%s has placed a %s in %s (@ %s)", causeStr, goal,
									getLocationDescription(loc, match),
									LocationUtil.toBlockCoords(loc)), entity.getLocation(), goal));

				}
			}

			if (block.getType() != Material.AIR)
			{
				// process potential break
				checkContainerBreak(block, goals, match, causeStr);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent ev)
	{

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
			GoalsInventorySnapshot snap = new GoalsInventorySnapshot(
					((InventoryHolder) state).getInventory(), goals);
			if (snap.isEmpty()) return;
			Location loc = block.getLocation();

			// TranscriptEvent.ObjectiveDetailType.CONTAINER_DEATH
			match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.OBJECTIVE_DETAIL,
					String.format(
							"%s has %s a %s, spilling %s in %s (@ %s)", causeStr, actionStr,
							ChatColor.GOLD
									+ StringUtils.capitalize(block.getType().toString()
											.toLowerCase()) + ChatColor.RESET, snap, ChatColor.BLUE
									+ getLocationDescription(loc, match),
							LocationUtil.toBlockCoords(loc)), loc, unpack(snap)));
		}
	}

	// Utility functions

	private Object[] unpack(GoalsInventorySnapshot snap, Object... others)
	{
		List<Object> arr = Lists.newArrayList(others);
		for (Map.Entry<BlockData, Integer> entry : snap.entrySet())
		{
			final int count = entry.getValue().intValue();
			for (int i = 0; i < count; i++)
				arr.add(entry.getKey());
		}
		return arr.toArray();
	}

	private Object[] unpack(GoalsInventorySnapshot snap)
	{
		List<Object> arr = Lists.newArrayList();
		for (Map.Entry<BlockData, Integer> entry : snap.entrySet())
		{
			final int count = entry.getValue().intValue();
			for (int i = 0; i < count; i++)
				arr.add(entry.getKey());
		}
		return arr.toArray();
	}

	private Set<AutoRefTeam> teamsWithAccess(Location loc, AutoRefMatch match)
	{
		Set<AutoRefTeam> teams = Sets.newHashSet();
		for (AutoRefTeam team : match.getTeams())
		{
			if (team.canEnter(loc, 1.3D)) teams.add(team);
		}
		return teams;
	}

	private String getLocationDescription(Location loc, AutoRefMatch match)
	{
		Validate.isTrue(match.getWorld().equals(loc.getWorld()),
				"The provided location must be within the provided match!");
		if (match.inStartRegion(loc)) return "the starting area";
		Set<AutoRefTeam> teamsDirect = Sets.newHashSet();
		Set<AutoRefTeam> teamsNearby = Sets.newHashSet();
		for (AutoRefTeam t : match.getTeams())
		{
			if (t.canEnter(loc, 0D)) teamsDirect.add(t);
			if (t.canEnter(loc, 3.0D)) teamsNearby.add(t);
		}
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
			return "the shared area"; // TODO improve for modes where both teams
										// share the whole map
		}
	}
}
