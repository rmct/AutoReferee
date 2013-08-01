package org.mctourney.autoreferee;

import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import com.google.common.collect.Maps;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import org.jdom2.Element;

import org.mctourney.autoreferee.event.player.PlayerTeamJoinEvent;
import org.mctourney.autoreferee.event.player.PlayerTeamLeaveEvent;
import org.mctourney.autoreferee.event.team.ObjectiveUpdateEvent;
import org.mctourney.autoreferee.goals.AutoRefGoal;
import org.mctourney.autoreferee.goals.BlockGoal;
import org.mctourney.autoreferee.goals.scoreboard.AutoRefObjective;
import org.mctourney.autoreferee.listeners.ZoneListener;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.Metadatable;
import org.mctourney.autoreferee.util.PlayerKit;
import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;

/**
 * Represents a collection of players in a match.
 *
 * @author authorblues
 */
public class AutoRefTeam implements Metadatable, Comparable<AutoRefTeam>
{
	// reference to the match
	protected AutoRefMatch match = null;

	/**
	 * Gets this team's match.
	 *
	 * @return match object
	 */
	public AutoRefMatch getMatch()
	{ return match; }

	org.bukkit.scoreboard.Team scoreboardTeam;
	org.bukkit.scoreboard.Team  infoboardTeam;

	// player information
	protected Set<AutoRefPlayer> players = Sets.newHashSet();
	private Set<AutoRefPlayer> playersCache = Sets.newHashSet();

	private int playerlives = -1;

	public String toString()
	{ return this.getClass().getSimpleName() + "[" + this.getName() + "]"; }

	public boolean equals(Object o)
	{
		return this.getClass().isInstance(o)
			&& this.getMatch().equals(((AutoRefTeam) o).getMatch())
			&& this.name.equals(((AutoRefTeam) o).name);
	}

	public int hashCode()
	{ return this.name.hashCode() ^ (17 * this.getMatch().hashCode()); }

	protected Map<String, Object> metadata = Maps.newHashMap();

	public void addMetadata(String key, Object value)
	{ this.metadata.put(key, value); }

	public Object getMetadata(String key)
	{ return this.metadata.get(key); }

	public boolean hasMetadata(String key)
	{ return this.metadata.containsKey(key); }

	public Object removeMetadata(String key)
	{ return this.metadata.remove(key); }

	public void clearMetadata()
	{ this.metadata.clear(); }

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
	 * Gets the default name of the team.
	 */
	public String getDefaultName()
	{ return name; }

	/**
	 * Gets the name of the team.
	 */
	public String getName()
	{
		if (customName != null) return customName;
		return this.getDefaultName();
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

		if (!oldName.equals(getDisplayName()))
			match.broadcast(oldName + " is now known as " + getDisplayName());
		scoreboardTeam.setDisplayName(name);

		// update objectives to propagate name changes
		this.updateObjectives();
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

	// maximum size of a team
	protected Integer maxsize = null;
	protected Integer minsize = null;

	public int getMaxSize()
	{ return maxsize == null ? 4 : maxsize; }

	public int getMinSize()
	{ return minsize == null ? (3 * getMaxSize() / 4) : minsize; }

	// is this team ready to play?
	private boolean ready = false;

	/**
	 * Checks if this team is ready for the match to begin.
	 *
	 * @return true if team is ready, otherwise false
	 */
	public boolean isReady()
	{ return ready || this.isEmptyTeam(); }

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
		for (BlockGoal goal : this.getTeamGoals(BlockGoal.class))
		{
			Vector v = goal.getTarget().toVector().add(HALF_BLOCK_VECTOR);
			vmin = vmin == null ? v : Vector.getMinimum(vmin, v);
			vmax = vmax == null ? v : Vector.getMaximum(vmax, v);
		}

		// if we didn't find any block goals, no victory monument
		if (vmin == null || vmax == null) return null;

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
	private Set<AutoRefRegion> spawnRegions = Sets.newHashSet();
	private static Random random = new Random();

	/**
	 * Clears this team's spawn locations.
	 */
	public void clearSpawnRegions()
	{ this.spawnRegions = Sets.newHashSet(); }

	/**
	 * Adds to this team's spawn locations.
	 */
	public void addSpawnRegion(AutoRefRegion reg)
	{ this.spawnRegions.add(reg); }

	/**
	 * Adds to this team's spawn locations.
	 */
	public void addSpawnRegion(Location loc)
	{ this.addSpawnRegion(new org.mctourney.autoreferee.regions.PointRegion(loc)); }

	/**
	 * Gets a valid spawn location for this team.
	 */
	public Location getSpawnLocation()
	{
		if (spawnRegions == null || spawnRegions.isEmpty())
			return match.getWorldSpawn();

		AutoRefRegion[] regs = spawnRegions.toArray(new AutoRefRegion[0]);
		return regs[random.nextInt(spawnRegions.size())].getLocation();
	}

	private Set<AutoRefGoal> goals = Sets.newHashSet();

	/**
	 * Get this team's win conditions.
	 *
	 * @return collection of win conditions
	 */
	public Set<AutoRefGoal> getTeamGoals()
	{ return Collections.unmodifiableSet(goals); }

	/**
	 * Get this team's win conditions by type.
	 *
	 * @return collection of win conditions
	 */
	public <T extends AutoRefGoal> Set<T> getTeamGoals(Class<T> clazz)
	{
		Set<T> typedGoals = Sets.newHashSet();
		for (AutoRefGoal goal : goals)
			if (clazz.isInstance(goal)) typedGoals.add((T) goal);
		return typedGoals;
	}

	Set<AutoRefObjective> scoreboardObjectives;

	public void updateObjectives()
	{
		if (scoreboardObjectives != null)
			for (AutoRefObjective obj : scoreboardObjectives)
				obj.update();
	}

	// does a provided search string match this team?
	public int matches(String needle)
	{
		if (needle == null) return 0;
		needle = needle.toLowerCase();

		String a = name, b = customName;
		if (b != null && needle.contains(b.toLowerCase())) return b.length();
		if (a != null && needle.contains(a.toLowerCase())) return a.length();
		return 0;
	}

	public void startMatch()
	{
		// if there is no match associated, most of this work is moot
		assert getMatch() != null : "Match is null";

		for (AutoRefGoal goal : goals) if (goal.hasItem())
			goal.setItemStatus(AutoRefGoal.ItemStatus.NONE);

		for (AutoRefPlayer apl : getPlayers())
		{
			Player player = apl.getPlayer();
			if (player != null && !getMatch().inStartRegion(player.getLocation()))
				player.teleport(getMatch().getPlayerSpawn(player));

			apl.heal();
			apl.updateCarrying();
		}

		// save all players currently on team
		playersCache.addAll(players);
	}

	// a factory for processing config xml
	public static AutoRefTeam fromElement(Element elt, AutoRefMatch match)
	{
		// the element we are building on needs to be a team element
		assert "team".equals(elt.getName().toLowerCase());

		AutoRefTeam newTeam = new AutoRefTeam();
		newTeam.color = ChatColor.RESET;
		newTeam.match = match;

		// get name from map
		if (null == (newTeam.name = elt.getChildTextTrim("name"))) return null;

		String clr = elt.getAttributeValue("color");
		String maxsz = elt.getAttributeValue("maxsize");
		String minsz = elt.getAttributeValue("minsize");

		if (clr != null) try
		{ newTeam.color = ChatColor.valueOf(clr.toUpperCase()); }
		catch (IllegalArgumentException e) {  }

		// initialize this team for referees
		match.messageReferees("team", newTeam.getName(), "init");
		match.messageReferees("team", newTeam.getName(), "color", newTeam.color.toString());

		// get the min and max size from the team tag
		if (maxsz != null) newTeam.maxsize = Integer.parseInt(maxsz);
		if (minsz != null) newTeam.minsize = Integer.parseInt(minsz);

		if (elt.getAttributeValue("kit") != null)
		{
			newTeam.setKit(match.getKit(elt.getAttributeValue("kit")));
			if (!Boolean.parseBoolean(match.getWorld().getGameRuleValue("keepInventory")))
			{
				AutoReferee.log("A kit has been specified with keepInventory=false", Level.WARNING);
				AutoReferee.log("To turn this feature on, type '/gamerule keepInventory true'", Level.WARNING);
				AutoReferee.log("This map should (maybe) be reconfigured with keepInventory", Level.WARNING);
			}
		}

		Element spawn = elt.getChild("spawn");
		if (spawn != null) for (Element reg : spawn.getChildren())
			newTeam.addSpawnRegion(AutoRefRegion.fromElement(match, reg));

		if (elt.getAttribute("lives") != null)
			try { newTeam.playerlives = Integer.parseInt(elt.getAttributeValue("lives").trim()); }
			catch (NumberFormatException e) { e.printStackTrace(); }

		newTeam.setupScoreboard();
		newTeam.players = Sets.newHashSet();
		return newTeam;
	}

	// a factory for creating raw teams
	public static AutoRefTeam create(AutoRefMatch match, String name, ChatColor color)
	{
		AutoRefTeam newTeam = new AutoRefTeam();
		newTeam.color = color;
		newTeam.match = match;

		newTeam.name = name;

		// initialize this team for referees
		match.messageReferees("team", newTeam.getName(), "init");
		match.messageReferees("team", newTeam.getName(), "color", newTeam.color.toString());

		newTeam.setupScoreboard();
		newTeam.players = Sets.newHashSet();
		return newTeam;
	}

	private void setupScoreboard()
	{
		String teamslug = "ar#" + name;
		if (teamslug.length() > 16) teamslug = teamslug.substring(0, 16);

		// set team data on spectators' scoreboard
		infoboardTeam = match.getInfoboard().registerNewTeam(teamslug);
		infoboardTeam.setPrefix(color.toString());
		infoboardTeam.setDisplayName(getName());

		// set team data on players' scoreboard
		scoreboardTeam = match.getScoreboard().registerNewTeam(teamslug);
		scoreboardTeam.setPrefix(color.toString());
		scoreboardTeam.setDisplayName(getName());

		// this stuff is only really necessary for the players themselves
		scoreboardTeam.setAllowFriendlyFire(match.allowFriendlyFire());
		scoreboardTeam.setCanSeeFriendlyInvisibles(true);
	}

	public Element toElement()
	{
		Element elt = new Element("team");
		elt.addContent(new Element("name").setText(getDefaultName()));

		if (this.getColor() != ChatColor.RESET) elt.setAttribute("color", this.getColor().name());
		if (this.maxsize != null) elt.setAttribute("maxsize", Integer.toString(this.maxsize));
		if (this.minsize != null) elt.setAttribute("minsize", Integer.toString(this.minsize));
		if (this.playerlives > 0) elt.setAttribute("lives", Integer.toString(this.playerlives));

		PlayerKit teamKit = this.getKit();
		if (teamKit != null) elt.setAttribute("kit", teamKit.getName());

		if (this.spawnRegions != null)
		{
			Element spawnElement = new Element("spawn");
			for (AutoRefRegion reg : this.spawnRegions)
				spawnElement.addContent(reg.toElement());
			elt.addContent(spawnElement);
		}

		return elt;
	}

	private PlayerKit startKit = null;

	public PlayerKit getKit()
	{ return startKit; }

	public void setKit(PlayerKit kit)
	{ this.startKit = kit; }

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
		if (scoreboardTeam != null) scoreboardTeam.addPlayer(Bukkit.getOfflinePlayer(apl.getName()));
		if ( infoboardTeam != null)  infoboardTeam.addPlayer(Bukkit.getOfflinePlayer(apl.getName()));

		apl.setTeam(this); this.players.add(apl);
		if (this.getMatch() != null && this.getMatch().getCurrentState().inProgress())
			this.playersCache.add(apl);
	}

	protected boolean removePlayer(AutoRefPlayer apl)
	{
		if (scoreboardTeam != null) scoreboardTeam.removePlayer(Bukkit.getOfflinePlayer(apl.getName()));
		if ( infoboardTeam != null)  infoboardTeam.removePlayer(Bukkit.getOfflinePlayer(apl.getName()));

		return this.players.remove(apl);
	}

	/**
	 * Adds a player to this team. Players may not be added to teams if the match
	 * is already in progress.
	 *
	 * @return true if player was successfully added, otherwise false
	 */
	public boolean join(Player player, PlayerTeamJoinEvent.Reason reason)
	{ return join(player, reason, false); }

	/**
	 * Adds a player to this team.
	 *
	 * @param force force join operation, even if match is in progress
	 * @return true if player was successfully added, otherwise false
	 */
	public boolean join(Player player, PlayerTeamJoinEvent.Reason reason, boolean force)
	{
		PlayerTeamJoinEvent event = new PlayerTeamJoinEvent(player, this, reason);
		AutoReferee.callEvent(event);
		if (event.isCancelled()) return false;

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
		if (this.playerlives > 0) apl.setLivesRemaining(this.playerlives);

		// quit if they are already on this team
		if (players.contains(apl)) return true;

		// if there is no match object, drop out here
		if (match == null) return false;

		// if the match is in progress, no one may join
		if (!match.getCurrentState().isBeforeMatch() && !force) return false;

		// prepare the player
		if (!match.getCurrentState().inProgress())
			player.teleport(this.getSpawnLocation());

		Location bed = player.getBedSpawnLocation();
		if (bed != null && bed.getWorld() != match.getWorld())
			player.setBedSpawnLocation(null);

		this.addPlayer(apl);
		match.messageReferees("team", getName(), "player", "+" + apl.getName());
		match.messageReferees("player", apl.getName(), "login");
		match.updatePlayerList();

		match.broadcast(apl.getDisplayName() + " has joined " + getDisplayName());
		match.setupSpectators(player);
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
		PlayerTeamLeaveEvent event = new PlayerTeamLeaveEvent(player, this);
		AutoReferee.callEvent(event);
		if (event.isCancelled()) return false;

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
		match.updatePlayerList();

		// by the time this is actually called, they may have left the world to join
		// a different match. this teleport shouldn't occur if they aren't in this world
		if (player.getWorld() == match.getWorld())
			player.teleport(match.getWorldSpawn());

		match.messageReferees("team", getName(), "player", "-" + apl.getName());
		match.setupSpectators(player);
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
	public boolean hasFlag(Block b, AutoRefRegion.Flag flag)
	{ return hasFlag(b, flag, flag.defaultValue); }

	/**
	 * Checks if a region is marked with a specific region flag.
	 *
	 * @return true if location contains flag, otherwise false
	 */
	public boolean hasFlag(Block b, AutoRefRegion.Flag flag, boolean def)
	{ return hasFlag(b.getLocation().clone().add(0.5, 0.5, 0.5), flag, def); }

	/**
	 * Checks if a region is marked with a specific region flag.
	 *
	 * @return true if location contains flag, otherwise false
	 */
	public boolean hasFlag(Location loc, AutoRefRegion.Flag flag)
	{ return hasFlag(loc, flag, flag.defaultValue); }

	/**
	 * Checks if a region is marked with a specific region flag.
	 *
	 * @return true if location contains flag, otherwise false
	 */
	public boolean hasFlag(Location loc, AutoRefRegion.Flag flag, boolean def)
	{
		// check start region flags
		if (getMatch().inStartRegion(loc))
			return getMatch().getStartRegionFlags().contains(flag);

		boolean is = def; Set<AutoRefRegion> regions = getRegions();
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

		ObjectiveUpdateEvent event = new ObjectiveUpdateEvent(goal);
		AutoReferee.callEvent(event);
	}

	protected void updateBlockGoals()
	{
		objloop: for (BlockGoal goal : this.getTeamGoals(BlockGoal.class))
		{
			if (goal.isSatisfied(getMatch()))
			{ changeObjectiveStatus(goal, AutoRefGoal.ItemStatus.TARGET); continue objloop; }

			for (AutoRefPlayer apl : getPlayers())
			{
				if (!apl.getCarrying().contains(goal.getItem())) continue;
				changeObjectiveStatus(goal, AutoRefGoal.ItemStatus.CARRYING); continue objloop;
			}

			if (goal.getItemStatus() != AutoRefGoal.ItemStatus.NONE)
			{ changeObjectiveStatus(goal, AutoRefGoal.ItemStatus.SEEN); continue objloop; }
		}
	}

	public double getObjectiveScore()
	{
		double score = 0.0f;
		for (AutoRefGoal goal : getTeamGoals())
			score += goal.getScore(this.match);
		return score;
	}

	protected void updateCarrying(AutoRefPlayer apl, Set<BlockData> oldCarrying, Set<BlockData> newCarrying)
	{
		match.updateCarrying(apl, oldCarrying, newCarrying);
		this.updateBlockGoals();

		// pass this information along to the scoreboard
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
