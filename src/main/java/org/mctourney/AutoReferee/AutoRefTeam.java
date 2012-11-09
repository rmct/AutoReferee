package org.mctourney.AutoReferee;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import org.mctourney.AutoReferee.listeners.ZoneListener;
import org.mctourney.AutoReferee.util.*;

import org.apache.commons.collections.CollectionUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Maps;

/**
 * Represents a collection of players in a match.
 *
 * @author authorblues
 */
public class AutoRefTeam implements Comparable<AutoRefTeam>
{
	// reference to the match
	private AutoRefMatch match = null;

	/**
	 * Gets this team's match.
	 *
	 * @return match object
	 */
	public AutoRefMatch getMatch()
	{ return match; }

	// player information
	private Set<AutoRefPlayer> players = Sets.newHashSet();

	/**
	 * Gets the members of this team.
	 *
	 * @return collection of players
	 */
	public Set<AutoRefPlayer> getPlayers()
	{
		if (players != null) return players;
		return Sets.newHashSet();
	}

	protected String getPlayerList()
	{
		Set<String> plist = Sets.newHashSet();
		for (AutoRefPlayer apl : getPlayers())
			plist.add(apl.getName());
		if (plist.size() == 0) return "{empty}";
		return StringUtils.join(plist, ", ");
	}

	private Set<OfflinePlayer> expectedPlayers = Sets.newHashSet();

	/**
	 * Adds a player to the list of expected players for this team.
	 */
	public void addExpectedPlayer(OfflinePlayer opl)
	{ expectedPlayers.add(opl); }

	/**
	 * Adds a player to the list of expected players for this team by name.
	 */
	public void addExpectedPlayer(String name)
	{ addExpectedPlayer(AutoReferee.getInstance().getServer().getOfflinePlayer(name)); }

	/**
	 * Gets the players expected to join this team.
	 *
	 * @return collection of players
	 */
	public Set<OfflinePlayer> getExpectedPlayers()
	{ return expectedPlayers; }

	// team's name, may or may not be color-related
	private String name = null;
	private String customName = null;

	/**
	 * Gets the name of the team.
	 */
	public String getName()
	{
		if (customName != null) return customName;
		return name;
	}

	/**
	 * Sets the name of the team.
	 *
	 * @param name new team name
	 */
	public void setName(String name)
	{
		// send name change event before we actually change the name
		match.messageReferees("team", getName(), "name", name);

		String oldName = getDisplayName();
		customName = name;

		match.broadcast(oldName + " is now known as " + getDisplayName());
	}

	/**
	 * Gets the colored name of the team.
	 */
	public String getDisplayName()
	{ return color + getName() + ChatColor.RESET; }

	// color to use for members of this team
	private ChatColor color = ChatColor.WHITE;

	/**
	 * Gets the color associated with this team.
	 */
	public ChatColor getColor()
	{ return color; }

	/**
	 * Sets the color associated with this team.
	 */
	public void setColor(ChatColor color)
	{ this.color = color; }

	// maximum size of a team (for manual mode only)
	private int maxSize = 0;

	// is this team ready to play?
	private boolean ready = false;

	/**
	 * Checks if this team is ready for the match to begin.
	 *
	 * @return true if team is ready, otherwise false
	 */
	public boolean isReady()
	{ return ready; }

	/**
	 * Sets whether this team is ready for the match to begin.
	 */
	public void setReady(boolean ready)
	{
		if (ready == this.ready) return;
		this.ready = ready;

		for (Player pl : getMatch().getWorld().getPlayers())
			pl.sendMessage(getDisplayName() + " is now marked as " +
				ChatColor.DARK_GRAY + (this.ready ? "READY" : "NOT READY"));
		if (!this.ready) getMatch().cancelCountdown();
	}

	/**
	 * Checks whether this team is empty. Takes expected players into account.
	 *
	 * @return true if team is empty, otherwise false
	 */
	public boolean isEmptyTeam()
	{ return getPlayers().size() == 0 && getExpectedPlayers().size() == 0; }

	private Location lastObjectiveLocation = null;

	/**
	 * Gets location of this team's last objective event.
	 */
	public Location getLastObjectiveLocation()
	{ return lastObjectiveLocation; }

	public void setLastObjectiveLocation(Location loc)
	{
		lastObjectiveLocation = loc;
		getMatch().setLastObjectiveLocation(loc);
	}

	private static final Vector HALF_BLOCK_VECTOR = new Vector(0.5, 0.5, 0.5);

	/**
	 * Gets location of this team's victory monument. Victory monument location
	 * is synthesized based on objective target locations.
	 */
	public Location getVictoryMonumentLocation()
	{
		Vector vmin = null, vmax = null;
		for (WinCondition wc : winConditions)
		{
			Vector v = wc.getLocation().toVector().add(HALF_BLOCK_VECTOR);
			vmin = vmin == null ? v : Vector.getMinimum(vmin, v);
			vmax = vmax == null ? v : Vector.getMaximum(vmax, v);
		}

		World w = getMatch().getWorld();
		return vmin.getMidpoint(vmax).toLocation(w);
	}

	// list of regions
	private Set<AutoRefRegion> regions = null;

	/**
	 * Gets all regions owned by this team.
	 *
	 * @return collection of regions
	 */
	public Set<AutoRefRegion> getRegions()
	{ return regions; }

	// location of custom spawn
	private Location spawn;

	/**
	 * Sets this team's spawn location.
	 */
	public void setSpawnLocation(Location loc)
	{
		getMatch().broadcast("Set " + getDisplayName() + "'s spawn to " +
			BlockVector3.fromLocation(loc).toCoords());
		this.spawn = loc;
	}

	/**
	 * Gets this team's spawn location.
	 */
	public Location getSpawnLocation()
	{ return spawn == null ? match.getWorldSpawn() : spawn; }

	/**
	 * Represents a condition for victory.
	 *
	 * @author authorblues
	 */
	public static class WinCondition
	{
		/**
		 * Represents the status of a win condition.
		 *
		 * @author authorblues
		 */
		public static enum GoalStatus
		{
			NONE("none"),
			SEEN("found"),
			CARRYING("carry"),
			PLACED("vm");

			private String messageText;

			private GoalStatus(String mtext)
			{ messageText = mtext; }

			@Override
			public String toString()
			{ return messageText; }
		}

		private Location loc;
		private BlockData blockdata;
		private int range;
		private GoalStatus status = GoalStatus.NONE;

		/**
		 * Constructs a team's win condition.
		 *
		 * @param loc target location for objective
		 * @param blockdata objective block type
		 * @param range maximum allowed distance from target
		 */
		public WinCondition(Location loc, BlockData blockdata, int range)
		{ this.loc = loc; this.blockdata = blockdata; this.range = range; }

		/**
		 * Gets the target location for this objective.
		 */
		public Location getLocation()
		{ return loc; }

		/**
		 * Gets the block type for this objective.
		 */
		public BlockData getBlockData()
		{ return blockdata; }

		/**
		 * Gets the maximum range this objective may be placed from its target.
		 */
		public int getInexactRange()
		{ return range; }

		/**
		 * Gets the current status of this win condition.
		 *
		 * @return goal status
		 */
		public GoalStatus getStatus()
		{ return status; }

		/**
		 * Sets the current status of this win condition.
		 *
		 * @param status goal status
		 */
		public void setStatus(GoalStatus status)
		{ this.status = status; }
	}

	private Set<WinCondition> winConditions;

	/**
	 * Get this team's win conditions.
	 *
	 * @return collection of win conditions
	 */
	public Set<WinCondition> getWinConditions()
	{ return Collections.unmodifiableSet(winConditions); }

	// does a provided search string match this team?
	public boolean matches(String needle)
	{
		if (needle == null) return false;
		needle = needle.toLowerCase();

		String a = name, b = customName;
		if (b != null && -1 != needle.indexOf(b.toLowerCase())) return true;
		if (a != null && -1 != needle.indexOf(a.toLowerCase())) return true;
		return false;
	}

	public void startMatch()
	{
		for (WinCondition wc : winConditions)
			wc.setStatus(WinCondition.GoalStatus.NONE);

		for (AutoRefPlayer apl : getPlayers())
		{
			apl.heal();
			apl.updateCarrying();
		}
	}

	// a factory for processing config maps
	@SuppressWarnings("unchecked")
	protected static AutoRefTeam fromMap(Map<String, Object> conf, AutoRefMatch match)
	{
		AutoRefTeam newTeam = new AutoRefTeam();
		newTeam.color = ChatColor.RESET;
		newTeam.maxSize = 0;

		newTeam.match = match;
		World w = match.getWorld();

		// get name from map
		if (!conf.containsKey("name")) return null;
		newTeam.name = (String) conf.get("name");

		// get the color from the map
		if (conf.containsKey("color"))
		{
			String clr = ((String) conf.get("color")).toUpperCase();
			try { newTeam.color = ChatColor.valueOf(clr); }
			catch (IllegalArgumentException e) { }
		}

		// initialize this team for referees
		match.messageReferees("team", newTeam.getName(), "init");
		match.messageReferees("team", newTeam.getName(), "color", newTeam.color.toString());

		// get the max size from the map
		if (conf.containsKey("maxsize"))
		{
			Integer msz = (Integer) conf.get("maxsize");
			if (msz != null) newTeam.maxSize = msz.intValue();
		}

		newTeam.regions = Sets.newHashSet();
		if (conf.containsKey("regions"))
		{
			List<String> coordstrings = (List<String>) conf.get("regions");
			if (coordstrings != null) for (String coords : coordstrings)
			{
				AutoRefRegion creg = AutoRefRegion.fromCoords(coords);
				if (creg != null) newTeam.regions.add(creg);
			}
		}

		newTeam.spawn = !conf.containsKey("spawn") ? null :
			BlockVector3.fromCoords((String) conf.get("spawn")).toLocation(w);

		// setup both objective-based data-structures together
		// -- avoids an NPE with getObjectives()
		newTeam.winConditions = Sets.newHashSet();
		if (conf.containsKey("win-condition"))
		{
			List<String> slist = (List<String>) conf.get("win-condition");
			if (slist != null) for (String s : slist)
			{
				String[] sp = s.split(":");

				int range = sp.length > 2 ? Integer.parseInt(sp[2]) : match.getInexactRange();
				newTeam.addWinCondition(BlockVector3.fromCoords(sp[0]).toLocation(w),
					BlockData.unserialize(sp[1]), range);
			}
		}

		newTeam.players = Sets.newHashSet();
		return newTeam;
	}

	protected Map<String, Object> toMap()
	{
		Map<String, Object> map = Maps.newHashMap();

		// add name to the map
		map.put("name", name);

		// add string representation of the color
		map.put("color", color.name());

		// add the maximum team size
		map.put("maxsize", new Integer(maxSize));

		// set the team spawn (if there is a custom spawn)
		if (spawn != null) map.put("spawn", BlockVector3.fromLocation(spawn).toCoords());

		// convert the win conditions to strings
		List<String> wcond = Lists.newArrayList();
		for (WinCondition wc : winConditions)
		{
			String range = "";
			if (wc.getInexactRange() != match.getInexactRange())
				range = ":" + wc.getInexactRange();

			wcond.add(BlockVector3.fromLocation(wc.getLocation()).toCoords()
				+ ":" + wc.getBlockData().serialize() + range);
		}

		// add the win condition list
		map.put("win-condition", wcond);

		// convert regions to strings
		List<String> regstr = Lists.newArrayList();
		for (AutoRefRegion reg : regions) regstr.add(reg.toCoords());

		// add the region list
		map.put("regions", regstr);

		// return the map
		return map;
	}

	/**
	 * Gets a player from this team by name.
	 *
	 * @return player object if one exists, otherwise null
	 */
	public AutoRefPlayer getPlayer(String name)
	{
		AutoRefPlayer bapl = null;
		if (name != null)
		{
			int score, b = Integer.MAX_VALUE;
			for (AutoRefPlayer apl : players)
			{
				score = apl.nameSearch(name);
				if (score < b) { b = score; bapl = apl; }
			}
		}
		return bapl;
	}

	/**
	 * Gets a player from this team associated with the specified player.
	 *
	 * @return player object if one exists, otherwise null
	 */
	public AutoRefPlayer getPlayer(Player player)
	{ return player == null ? null : getPlayer(player.getName()); }

	protected void addPlayer(AutoRefPlayer apl)
	{ apl.setTeam(this); this.players.add(apl); }

	protected boolean removePlayer(AutoRefPlayer apl)
	{ return this.players.remove(apl); }

	/**
	 * Adds a player to this team. Players may not be added to teams if the match
	 * is already in progress.
	 *
	 * @return true if player was successfully added, otherwise false
	 */
	public boolean join(Player player)
	{ return join(player, false); }

	/**
	 * Adds a player to this team.
	 *
	 * @param force force join operation, even if match is in progress
	 * @return true if player was successfully added, otherwise false
	 */
	public boolean join(Player player, boolean force)
	{
		// if this player is using the client mod, they may not join
		if (player.getListeningPluginChannels().contains(AutoReferee.REFEREE_PLUGIN_CHANNEL))
		{
			if (!getMatch().isReferee(player))
				player.sendMessage("You may not join a team with a modified client");
			String warning = ChatColor.DARK_GRAY + player.getName() + " attempted to join "
				+ this.getDisplayName() + ChatColor.DARK_GRAY + " with a modified client";
			for (Player ref : getMatch().getReferees(true)) ref.sendMessage(warning);
			return false;
		}

		// create an APL object for this player.
		AutoRefPlayer apl = new AutoRefPlayer(player, this);

		// quit if they are already on this team
		if (players.contains(apl)) return true;

		// if the match is in progress, no one may join
		if (!match.getCurrentState().isBeforeMatch() && !force) return false;

		// prepare the player
		if (match != null && !match.getCurrentState().inProgress())
			player.teleport(getSpawnLocation());
		player.setGameMode(GameMode.SURVIVAL);

		this.addPlayer(apl);
		match.messageReferees("team", getName(), "player", "+" + apl.getName());
		match.messageReferees("player", apl.getName(), "login");

		String name = getColor() + apl.getName() + ChatColor.RESET;
		match.broadcast(name + " has joined " + getDisplayName());

		//FIXME if (pl.isOnline() && (pl instanceof Player))
		//	((Player) pl).setPlayerListName(StringUtils.substring(colorName, 0, 16));

		match.setupSpectators();
		match.checkTeamsReady();
		return true;
	}

	/**
	 * Removes a player from this team. Players may not be removed from teams if the
	 * match is already in progress.
	 *
	 * @return true if player was successfully removed, otherwise false
	 */
	public boolean leave(Player player)
	{ return leave(player, false); }

	/**
	 * Removes a player from this team.
	 *
	 * @param force force leave operation, even if match is in progress
	 * @return true if player was successfully removed, otherwise false
	 */
	public boolean leave(Player player, boolean force)
	{
		// if the match is in progress, no one may leave their team
		if (!match.getCurrentState().isBeforeMatch() && !force) return false;

		// create an APL object for this player.
		AutoRefPlayer apl = new AutoRefPlayer(player);
		if (!this.removePlayer(apl)) return false;

		String name = apl.getName() + ChatColor.RESET;
		match.broadcast(name + " has left " + getDisplayName());
		player.teleport(match.getWorldSpawn());

		if (player.isOnline() && (player instanceof Player))
			((Player) player).setPlayerListName(player.getName());

		match.messageReferees("team", getName(), "player", "-" + apl.getName());

		match.setupSpectators();
		match.checkTeamsReady();
		return true;
	}

	/**
	 * Returns distance from location to this team's closest region.
	 *
	 * @return distance
	 */
	public double distanceToClosestRegion(Location loc)
	{
		double distance = match.getStartRegion().distanceToRegion(loc);
		if (regions != null) for ( CuboidRegion reg : regions ) if (distance > 0)
			distance = Math.min(distance, reg.distanceToRegion(loc));
		return distance;
	}

	/**
	 * Checks if players on this team can be in a given location, including sneak distance.
	 *
	 * @return true if location is valid, otherwise false
	 */
	public boolean canEnter(Location loc)
	{ return canEnter(loc, ZoneListener.SNEAK_DISTANCE); }

	/**
	 * Checks if players on this team can be in a given location, within a specified distance.
	 *
	 * @param distance maximum distance a player may move from this location
	 * @return true if location is valid, otherwise false
	 */
	public boolean canEnter(Location loc, Double distance)
	{
		double bestdist = match.getStartRegion().distanceToRegion(loc);
		if (regions != null) for ( AutoRefRegion reg : regions ) if (bestdist > 0)
		{
			bestdist = Math.min(bestdist, reg.distanceToRegion(loc));
			if (!reg.canEnter() && reg.distanceToRegion(loc) <= distance) return false;
		}
		return bestdist <= distance;
	}

	/**
	 * Checks if players on this team can build at a given location.
	 *
	 * @return true if location is valid to build, otherwise false
	 */
	public boolean canBuild(Location loc)
	{
		// start region is a permanent no-build zone
		if (getMatch().inStartRegion(loc)) return false;

		boolean build = false;
		if (regions != null) for ( AutoRefRegion reg : regions )
			if (reg.contains(BlockVector3.fromLocation(loc)))
			{ build = true; if (!reg.canBuild()) return false; }
		return build;
	}

	/**
	 * Sets a new win condition.
	 *
	 * @param block block type and location
	 * @param range maximum range of valid placement location (0 = exact)
	 */
	public void addWinCondition(Block block, int range)
	{
		// if the block is null, forget it
		if (block == null) return;

		// add the block data to the win-condition listing
		BlockData blockdata = BlockData.fromBlock(block);
		this.addWinCondition(block.getLocation(), blockdata, range);
	}

	/**
	 * Sets a new win condition.
	 *
	 * @param loc block location
	 * @param blockdata block type
	 * @param range maximum range of valid placement location (0 = exact)
	 */
	public void addWinCondition(Location loc, BlockData blockdata, int range)
	{
		// if the block is null, forget it
		if (loc == null || blockdata == null) return;

		Set<BlockData> prevObj = getObjectives();
		winConditions.add(new WinCondition(loc, blockdata, range));
		Set<BlockData> newObj = getObjectives();

		newObj.removeAll(prevObj);
		for (BlockData nbd : newObj) match.messageReferees(
			"team", this.getName(), "obj", "+" + nbd.serialize());

		// broadcast the update
		for (Player cfg : loc.getWorld().getPlayers()) if (cfg.hasPermission("autoreferee.configure"))
			cfg.sendMessage(blockdata.getDisplayName() + " is now a win condition for " + getDisplayName() +
				" @ " + BlockVector3.fromLocation(loc).toCoords());
	}

	/**
	 * Gets a list of team objectives for this match.
	 *
	 * @return collection of block types to be retrieved
	 */
	public Set<BlockData> getObjectives()
	{
		Set<BlockData> objectives = Sets.newHashSet();
		for (WinCondition wc : winConditions)
			objectives.add(wc.getBlockData());
		objectives.remove(BlockData.AIR);
		return objectives;
	}

	private void changeObjectiveStatus(WinCondition wc, WinCondition.GoalStatus status)
	{
		if (wc.getStatus() == status) return;
		getMatch().messageReferees("team", this.getName(), "state",
			wc.getBlockData().serialize(), status.toString());
		wc.setStatus(status);
	}

	protected void updateObjectives()
	{
		objloop: for (WinCondition wc : winConditions)
		{
			if (getMatch().blockInRange(wc) != null)
			{ changeObjectiveStatus(wc, WinCondition.GoalStatus.PLACED); continue objloop; }

			for (AutoRefPlayer apl : getPlayers())
			{
				if (!apl.getCarrying().contains(wc.getBlockData())) continue;
				changeObjectiveStatus(wc, WinCondition.GoalStatus.CARRYING); continue objloop;
			}

			if (wc.getStatus() != WinCondition.GoalStatus.NONE)
			{ changeObjectiveStatus(wc, WinCondition.GoalStatus.SEEN); continue; }
		}
	}

	private int objCount(WinCondition.GoalStatus status)
	{
		int k = 0; for (WinCondition wc : winConditions)
			if (wc.getStatus() == status) ++k;
		return k;
	}

	/**
	 * Gets the number of objectives placed at their target locations.
	 *
	 * @return number of placed objectives
	 */
	public int getObjectivesPlaced()
	{ return objCount(WinCondition.GoalStatus.PLACED); }

	/**
	 * Gets the number of objectives found by this team.
	 *
	 * @return number of found objectives
	 */
	public int getObjectivesFound()
	{ return winConditions.size() - objCount(WinCondition.GoalStatus.NONE); }

	protected void updateCarrying(AutoRefPlayer apl, Set<BlockData> oldCarrying, Set<BlockData> newCarrying)
	{
		match.updateCarrying(apl, oldCarrying, newCarrying);
		this.updateObjectives();
	}

	protected void updateHealthArmor(AutoRefPlayer apl,
			int currentHealth, int currentArmor, int newHealth, int newArmor)
	{
		match.updateHealthArmor(apl,
			currentHealth, currentArmor, newHealth, newArmor);
	}

	public int compareTo(AutoRefTeam team)
	{ return this.getName().compareTo(team.getName()); }

	/**
	 * Swap the configuration of two teams, including players and custom names.
	 */
	public static void switchTeams(AutoRefTeam team1, AutoRefTeam team2)
	{
		// no work to be done
		if (team1 == null || team2 == null || team1 == team2) return;

		// must be in the same match
		if (team1.getMatch() != team2.getMatch()) return;

		// switch the sets of players
		Set<AutoRefPlayer> t1apls = team1.getPlayers();
		Set<AutoRefPlayer> t2apls = team2.getPlayers();

		team1.players = t2apls;
		team2.players = t1apls;

		for (AutoRefPlayer apl1 : team1.getPlayers()) apl1.setTeam(team1);
		for (AutoRefPlayer apl2 : team2.getPlayers()) apl2.setTeam(team2);

		// switch the custom names
		String t1cname = team1.customName;
		String t2cname = team2.customName;

		team1.customName = t2cname;
		team2.customName = t1cname;
	}
}
