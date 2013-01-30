package org.mctourney.AutoReferee;

import java.util.Collections;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import org.jdom2.Element;

import org.mctourney.AutoReferee.goals.AutoRefGoal;
import org.mctourney.AutoReferee.goals.BlockGoal;
import org.mctourney.AutoReferee.listeners.ZoneListener;
import org.mctourney.AutoReferee.regions.AutoRefRegion;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.LocationUtil;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;

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
	private Set<AutoRefPlayer> playersCache = Sets.newHashSet();

	/**
	 * Gets the members of this team.
	 *
	 * @return collection of players
	 */
	public Set<AutoRefPlayer> getPlayers()
	{ return players; }

	public Set<AutoRefPlayer> getCachedPlayers()
	{ return playersCache; }

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
		for (AutoRefGoal goal : goals)
		{
			Vector v = goal.getTarget().toVector().add(HALF_BLOCK_VECTOR);
			vmin = vmin == null ? v : Vector.getMinimum(vmin, v);
			vmax = vmax == null ? v : Vector.getMaximum(vmax, v);
		}

		World w = getMatch().getWorld();
		return vmin.getMidpoint(vmax).toLocation(w);
	}

	/**
	 * Gets all regions owned by this team.
	 *
	 * @return collection of regions
	 */
	public Set<AutoRefRegion> getRegions()
	{ return match.getRegions(this); }

	public boolean addRegion(AutoRefRegion reg)
	{
		for (AutoRefRegion ereg : match.getRegions())
			if (reg.equals(ereg)) { ereg.addOwners(this); return true; }

		reg.addOwners(this);
		match.getRegions().add(reg);
		return true;
	}

	// location of custom spawn
	private AutoRefRegion spawn;

	/**
	 * Sets this team's spawn location.
	 */
	public void setSpawnLocation(Location loc)
	{
		getMatch().broadcast("Set " + getDisplayName() + "'s spawn to " +
			LocationUtil.toBlockCoords(loc));
		this.spawn = new org.mctourney.AutoReferee.regions.PointRegion(loc);
	}

	/**
	 * Gets this team's spawn location.
	 */
	public Location getSpawnLocation()
	{ return spawn == null ? match.getWorldSpawn() : spawn.getRandomLocation(); }

	private Set<AutoRefGoal> goals = Sets.newHashSet();

	/**
	 * Get this team's win conditions.
	 *
	 * @return collection of win conditions
	 */
	public Set<AutoRefGoal> getTeamGoals()
	{ return Collections.unmodifiableSet(goals); }

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
		for (AutoRefGoal goal : goals) if (goal.hasItem())
			goal.setItemStatus(AutoRefGoal.ItemStatus.NONE);

		for (AutoRefPlayer apl : getPlayers())
		{
			apl.heal();
			apl.updateCarrying();
		}

		// save all players currently on team
		playersCache.addAll(players);
	}

	// a factory for processing config xml
	protected static AutoRefTeam fromElement(Element elt, AutoRefMatch match)
	{
		// the element we are building on needs to be a team element
		assert elt.getName().toLowerCase() == "team";

		AutoRefTeam newTeam = new AutoRefTeam();
		newTeam.color = ChatColor.RESET;
		newTeam.maxSize = 0;

		newTeam.match = match;

		// get name from map
		if (null == (newTeam.name = elt.getChildTextTrim("name"))) return null;

		String clr = elt.getAttributeValue("color");
		String msz = elt.getAttributeValue("max");

		if (clr != null) try
		{ newTeam.color = ChatColor.valueOf(clr.toUpperCase()); }
		catch (IllegalArgumentException e) { }

		// initialize this team for referees
		match.messageReferees("team", newTeam.getName(), "init");
		match.messageReferees("team", newTeam.getName(), "color", newTeam.color.toString());

		// get the max size from the map
		if (msz != null) newTeam.maxSize = Integer.parseInt(msz);

		Element spawn = elt.getChild("spawn");
		newTeam.spawn = spawn == null ? null :
			AutoRefRegion.fromElement(match, spawn.getChildren().get(0));

		newTeam.players = Sets.newHashSet();
		return newTeam;
	}

	public Element toElement()
	{
		Element elt = new Element("team");
		elt.addContent(new Element("name").setText(getName()));

		if (this.getColor() != ChatColor.RESET) elt.setAttribute(
			"color", this.getColor().name());
		if (this.maxSize != 0) elt.setAttribute(
			"maxsize", Integer.toString(this.maxSize));

		if (this.spawn != null) elt.addContent(
			new Element("spawn").addContent(this.spawn.toElement()));

		return elt;
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
	{
		apl.setTeam(this); this.players.add(apl);
		if (this.getMatch() != null && this.getMatch().getCurrentState().inProgress())
			this.playersCache.add(apl);
	}

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
			player.teleport(this.getSpawnLocation());
		player.setGameMode(GameMode.SURVIVAL);

		Location bed = player.getBedSpawnLocation();
		if (bed != null && bed.getWorld() != match.getWorld())
			player.setBedSpawnLocation(null);

		this.addPlayer(apl);
		match.messageReferees("team", getName(), "player", "+" + apl.getName());
		match.messageReferees("player", apl.getName(), "login");

		match.broadcast(apl.getDisplayName() + " has joined " + getDisplayName());
		AutoReferee.setOverheadName(player, apl.getDisplayName());

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
		if (!match.getCurrentState().isBeforeMatch() && !force &&
			match.getReferees().size() > 0) return false;

		String name = match.getDisplayName(player);
		if (!this.leaveQuietly(player)) return false;

		match.broadcast(name + " has left " + getDisplayName());
		return true;
	}

	/**
	 * Removes a player from this team quietly.
	 *
	 * @return true if player was successfully removed, otherwise false
	 */
	public boolean leaveQuietly(Player player)
	{
		// create an APL object for this player.
		AutoRefPlayer apl = new AutoRefPlayer(player);
		if (!this.removePlayer(apl)) return false;

		// by the time this is actually called, they may have left the world to join
		// a different match. this teleport shouldn't occur if they aren't in this world
		if (player.getWorld() == match.getWorld())
			player.teleport(match.getWorldSpawn());

		match.messageReferees("team", getName(), "player", "-" + apl.getName());
		AutoReferee.setOverheadName(player, player.getName());

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
		double distance = match.distanceToStartRegion(loc);
		Set<AutoRefRegion> regions = getRegions();

		if (regions != null) for ( AutoRefRegion reg : regions ) if (distance > 0)
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
		double bestdist = match.distanceToStartRegion(loc);
		Set<AutoRefRegion> regions = getRegions();

		if (regions != null) for ( AutoRefRegion reg : regions ) if (bestdist > 0)
		{
			bestdist = Math.min(bestdist, reg.distanceToRegion(loc));
			if (reg.is(AutoRefRegion.Flag.NO_ENTRY) &&
				reg.distanceToRegion(loc) <= distance) return false;
		}
		return bestdist <= distance;
	}

	/**
	 * Checks if a region is marked with a specific region flag.
	 *
	 * @return true if location contains flag, otherwise false
	 */
	public boolean hasFlag(Location loc, AutoRefRegion.Flag flag)
	{
		// check start region flags
		if (getMatch().inStartRegion(loc))
			return getMatch().getStartRegionFlags().contains(flag);

		boolean is = flag.defaultValue; Set<AutoRefRegion> regions = getRegions();
		if (regions != null) for ( AutoRefRegion reg : regions )
			if (reg.contains(loc)) { is = false; if (reg.is(flag)) return true; }
		return is;
	}

	/**
	 * Sets a new win condition.
	 */
	public void addGoal(Element elt)
	{ this.addGoal(AutoRefGoal.fromElement(this, elt)); }

	/**
	 * Sets a new win condition.
	 */
	public void addGoal(AutoRefGoal goal)
	{
		if (goal == null) return;

		goals.add(goal);
		for (Player ref : getMatch().getReferees(false))
			goal.updateReferee(ref);

		// broadcast the update
		for (Player cfg : getMatch().getWorld().getPlayers()) if (cfg.hasPermission("autoreferee.configure"))
			cfg.sendMessage(goal.toString() + " is now a win condition for " + getDisplayName());
	}

	/**
	 * Gets a list of team objectives for this match.
	 *
	 * @return collection of block types to be retrieved
	 */
	public Set<BlockData> getObjectives()
	{
		Set<BlockData> objectives = Sets.newHashSet();
		for (AutoRefGoal goal : goals)
			if (goal.hasItem()) objectives.add(goal.getItem());
		objectives.remove(BlockData.AIR);
		return objectives;
	}

	public boolean canCraft(BlockData bdata)
	{
		for (AutoRefGoal goal : goals)
		 if (goal.hasItem() && goal.getItem().equals(bdata) && goal.canCraftItem())
			return false;
		return true;
	}

	private void changeObjectiveStatus(AutoRefGoal goal, AutoRefGoal.ItemStatus status)
	{
		if (!goal.hasItem() || goal.getItemStatus() == status) return;
		getMatch().messageReferees("team", this.getName(), "state",
			goal.getItem().serialize(), status.toString());
		goal.setItemStatus(status);
	}

	protected void updateObjectives()
	{
		objloop: for (AutoRefGoal goal : goals) if (goal.hasItem())
		{
			if (goal instanceof BlockGoal && getMatch().blockInRange((BlockGoal) goal) != null)
			{ changeObjectiveStatus(goal, AutoRefGoal.ItemStatus.TARGET); continue objloop; }

			for (AutoRefPlayer apl : getPlayers())
			{
				if (!apl.getCarrying().contains(goal.getItem())) continue;
				changeObjectiveStatus(goal, AutoRefGoal.ItemStatus.CARRYING); continue objloop;
			}

			if (goal.getItemStatus() != AutoRefGoal.ItemStatus.NONE)
			{ changeObjectiveStatus(goal, AutoRefGoal.ItemStatus.SEEN); continue; }
		}
	}

	private int objCount(AutoRefGoal.ItemStatus status)
	{
		int k = 0; for (AutoRefGoal goal : goals)
			if (goal.getItemStatus() == status) ++k;
		return k;
	}

	/**
	 * Gets the number of objectives placed at their target locations.
	 *
	 * @return number of placed objectives
	 */
	public int getObjectivesPlaced()
	{ return objCount(AutoRefGoal.ItemStatus.TARGET); }

	/**
	 * Gets the number of objectives found by this team.
	 *
	 * @return number of found objectives
	 */
	public int getObjectivesFound()
	{ return goals.size() - objCount(AutoRefGoal.ItemStatus.NONE); }

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
