package org.mctourney.AutoReferee;

import java.awt.image.RenderedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.StringUtils;

import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.material.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import org.mctourney.AutoReferee.goals.AutoRefGoal;
import org.mctourney.AutoReferee.listeners.RefereeChannelListener;
import org.mctourney.AutoReferee.listeners.WorldListener;
import org.mctourney.AutoReferee.listeners.ZoneListener;
import org.mctourney.AutoReferee.regions.AutoRefRegion;
import org.mctourney.AutoReferee.regions.CuboidRegion;
import org.mctourney.AutoReferee.util.ArmorPoints;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.BookUtil;
import org.mctourney.AutoReferee.util.LocationUtil;
import org.mctourney.AutoReferee.util.MapImageGenerator;
import org.mctourney.AutoReferee.util.PlayerUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Represents a game world controlled by AutoReferee.
 *
 * @author authorblues
 */
public class AutoRefMatch
{
	private static final String GENERIC_NOTIFICATION_MESSAGE =
		"A notification has been sent. Type /artp to teleport.";

	// online map list
	private static String MAPREPO = "http://s3.amazonaws.com/autoreferee/maps/";

	/**
	 * Get the base url for the map repository
	 * @return url of map repository
	 */
	public static String getMapRepo()
	{ return MAPREPO; }

	/**
	 * Sets a new map repository for the plugin to download maps
	 * @param url url of new map repository to use
	 */
	public static void changeMapRepo(String url)
	{ MAPREPO = url + "/"; }

	// local storage locations
	private static File matchSummaryDirectory = null;
	static
	{
		// determine the location of the match-summary directory
		FileConfiguration config = AutoReferee.getInstance().getConfig();
		if (config.isString("local-storage.match-summary.directory"))
			matchSummaryDirectory = new File(config.getString("local-storage.match-summary.directory"));
		else matchSummaryDirectory = new File(AutoReferee.getInstance().getDataFolder(), "summary");

		// if the folder doesnt exist, create it...
		if (!matchSummaryDirectory.exists()) matchSummaryDirectory.mkdir();
	}

	public enum AccessType
	{ PRIVATE, PUBLIC; }

	public AccessType access = AccessType.PRIVATE;

	// world this match is taking place on
	private World primaryWorld;
	private Location worldSpawn = null;

	private void setPrimaryWorld(World w)
	{
		primaryWorld = w;
		worldConfigFile = new File(w.getWorldFolder(), AutoReferee.CFG_FILENAME);
		setWorldSpawn(primaryWorld.getSpawnLocation());
	}

	public void setWorldSpawn(Location loc)
	{
		worldSpawn = loc;
		while (worldSpawn.getWorld().getBlockTypeIdAt(worldSpawn) != Material.AIR.getId())
			worldSpawn = worldSpawn.add(0, 1, 0);

		worldSpawn.getWorld().setSpawnLocation(worldSpawn.getBlockX(),
			worldSpawn.getBlockY(), worldSpawn.getBlockZ());
	}

	/**
	 * Gets the world associated with this match.
	 *
	 * @return world
	 */
	public World getWorld()
	{ return primaryWorld; }

	@Override public int hashCode()
	{ return getWorld().hashCode(); }

	/**
	 * Gets the global spawn location for this match.
	 *
	 * @return global spawn location
	 */
	public Location getWorldSpawn()
	{ return worldSpawn; }

	private boolean tmp;

	private boolean isTemporaryWorld()
	{ return tmp; }

	private long startTime = 0L;

	/**
	 * Gets the time to set the world to at the start of the match.
	 *
	 * @return world time in ticks to be set at start of the match
	 */
	public long getStartTime()
	{ return startTime; }

	/**
	 * Sets the time that will be set at the start of the match.
	 *
	 * @param time world time in ticks to set at start of the match
	 */
	public void setStartTime(long time)
	{ this.startTime = time; }

	/**
	 * Represents the status of a match.
	 *
	 * @author authorblues
	 */
	public enum MatchStatus
	{
		/**
		 * No match for this world.
		 */
		NONE,

		/**
		 * Waiting for players to join.
		 */
		WAITING(5*60*20L),

		/**
		 * Players joined, waiting for match start.
		 */
		READY(5*60*20L),

		/**
		 * Match in progress.
		 */
		PLAYING(30*60*20L),

		/**
		 * Match completed.
		 */
		COMPLETED;

		public long inactiveTicks;

		private MatchStatus()
		{ this(Long.MAX_VALUE); }

		private MatchStatus(long ticks)
		{ this.inactiveTicks = ticks; }

		/**
		 * Checks if the match has not yet started.
		 *
		 * @return true if match has not started, otherwise false
		 */
		public boolean isBeforeMatch()
		{ return this.ordinal() < PLAYING.ordinal() && this != NONE; }

		/**
		 * Checks if the match has completed.
		 *
		 * @return true if match is completed, otherwise false
		 */
		public boolean isAfterMatch()
		{ return this.ordinal() > PLAYING.ordinal() && this != NONE; }

		/**
		 * Checks if match is in progress.
		 *
		 * @return true if match is in progress, otherwise false
		 */
		public boolean inProgress()
		{ return this == PLAYING; }
	}

	// status of the match
	private MatchStatus currentState = MatchStatus.NONE;

	/**
	 * Gets the current status of this match.
	 *
	 * @return match status
	 */
	public MatchStatus getCurrentState()
	{ return currentState; }

	/**
	 * Sets the current status of this match.
	 *
	 * @param status new match status
	 */
	public void setCurrentState(MatchStatus status)
	{ this.currentState = status; this.setupSpectators(); }

	// teams participating in the match
	private Set<AutoRefTeam> teams = Sets.newHashSet();

	/**
	 * Gets the teams participating in this match.
	 *
	 * @return set of teams
	 */
	public Set<AutoRefTeam> getTeams()
	{ return teams; }

	protected List<AutoRefTeam> getSortedTeams()
	{
		List<AutoRefTeam> sortedTeams = Lists.newArrayList(getTeams());
		Collections.sort(sortedTeams);
		return sortedTeams;
	}

	public String getTeamList()
	{
		Set<String> tlist = Sets.newHashSet();
		for (AutoRefTeam team : getSortedTeams())
			tlist.add(team.getDisplayName());
		return StringUtils.join(tlist, ", ");
	}

	private AutoRefTeam winningTeam = null;

	/**
	 * Gets the team that won this match.
	 *
	 * @return team that won the match if it is over, otherwise null
	 */
	public AutoRefTeam getWinningTeam()
	{ return winningTeam; }

	/**
	 * Sets the team that won this match.
	 */
	public void setWinningTeam(AutoRefTeam team)
	{ winningTeam = team; }

	// region defined as the "start" region (safe zone)
	private Set<AutoRefRegion> startRegions = Sets.newHashSet();

	/**
	 * Gets the region designated as the start platform. This region should contain the
	 * world spawn location. Players in this region are immune to damage from other players,
	 * and mobs will not spawn in this region.
	 *
	 * @return start region
	 */
	public Set<AutoRefRegion> getStartRegions()
	{ return startRegions; }

	public void addStartRegion(AutoRefRegion reg)
	{ this.startRegions.add(reg); }

	public double distanceToStartRegion(Location loc)
	{
		double dist = Double.MAX_VALUE;
		for (AutoRefRegion reg : startRegions)
		{
			double d = reg.distanceToRegion(loc);
			if (d < dist) dist = d;
		}

		return dist;
	}

	public CuboidRegion getMapCuboid()
	{
		CuboidRegion cube = null;
		for (AutoRefRegion reg : getStartRegions())
			cube = AutoRefRegion.combine(cube, reg);

		for (AutoRefTeam team : getTeams())
			for (AutoRefRegion reg : team.getRegions())
				cube = AutoRefRegion.combine(cube, reg);
		return cube;
	}

	// name of the match
	private String matchName = null;

	/**
	 * Sets the custom name for this match.
	 *
	 * @param name custom match name
	 */
	public void setMatchName(String name)
	{ matchName = name; }

	/**
	 * Gets the name of this match.
	 *
	 * @return match name
	 */
	public String getMatchName()
	{
		// if we have a specific match name...
		if (matchName != null) return matchName;

		// generate a date string
		String date = new SimpleDateFormat("dd MMM yyyy").format(new Date());

		// if the map is named, return map name as a placeholder
		if (mapName != null) return mapName + ": " + date;

		// otherwise, just return the date
		return date;
	}

	// configuration information for the world
	private File worldConfigFile;
	private Element worldConfig;

	// basic variables loaded from file
	private String mapName = null;
	private Collection<String> mapAuthors = null;

	/**
	 * Gets the name of the map for this match.
	 *
	 * @return map name
	 */
	public String getMapName()
	{ return mapName; }

	private String versionString = "1.0";

	/**
	 * Gets the version number of the map for this match.
	 *
	 * @return version number
	 */
	public String getMapVersion()
	{ return versionString; }

	/**
	 * Gets the shorthand version string of the map for this match. This string will have the format
	 * of "MapName-vX.Y"
	 *
	 * @return version string
	 */
	public String getVersionString()
	{ return String.format("%s-v%s", normalizeMapName(this.getMapName()), this.getMapVersion()); }

	/**
	 * Gets the creators of the map for this match.
	 *
	 * @return string list of names
	 */
	public String getMapAuthors()
	{
		if (mapAuthors != null && mapAuthors.size() != 0)
			return StringUtils.join(mapAuthors, ", ");
		return "??";
	}

	private long startTicks = 0;

	private long getStartTicks()
	{ return startTicks; }

	private void setStartTicks(long startTicks)
	{ this.startTicks = startTicks; }

	/**
	 * Gets the number of seconds elapsed in this match.
	 *
	 * @return current elapsed seconds if match in progress, otherwise 0L
	 */
	public long getMatchTime()
	{
		if (!getCurrentState().inProgress()) return 0L;
		return (getWorld().getFullTime() - getStartTicks()) / 20L;
	}

	private long timeLimit = -1L;

	/**
	 * Gets the match time limit in seconds.
	 *
	 * @return time limit in seconds
	 */
	public long getTimeLimit()
	{ return timeLimit; }

	/**
	 * Checks if this match has a set time limit.
	 *
	 * @return true if a time limit is set, otherwise false
	 */
	public boolean hasTimeLimit()
	{ return timeLimit != -1L; }

	/**
	 * Gets the number of seconds remaining in this match.
	 *
	 * @return time remaining in seconds
	 */
	public long getTimeRemaining()
	{ return timeLimit - getMatchTime(); }

	/**
	 * Sets match time limit in seconds.
	 *
	 * @param limit new time limit in seconds
	 */
	public void setTimeLimit(long limit)
	{ this.timeLimit = limit; }

	/**
	 * Gets current match time, default value separator (colon).
	 *
	 * @return current match timestamp
	 */
	public String getTimestamp()
	{ return getTimestamp(":"); }

	/**
	 * Gets current match time, with value separator.
	 *
	 * @param sep time value separator
	 * @return current match timestamp
	 */
	public String getTimestamp(String sep)
	{
		long timestamp = this.getMatchTime();
		return String.format("%02d%s%02d%s%02d", timestamp/3600L,
			sep, (timestamp/60L)%60L, sep, timestamp%60L);
	}

	// task that starts the match
	private AutoRefMatch.CountdownTask matchStarter = null;

	// mechanisms to open the starting gates
	private Set<StartMechanism> startMechanisms = Sets.newHashSet();

	// protected entities - only protected from "butchering"
	private Set<UUID> protectedEntities = Sets.newHashSet();

	public boolean isProtected(UUID uuid)
	{ return protectedEntities.contains(uuid); }

	public void protect(UUID uuid)
	{ protectedEntities.add(uuid); }

	public void unprotect(UUID uuid)
	{ protectedEntities.remove(uuid); }

	public void toggleProtection(UUID uuid)
	{ if (isProtected(uuid)) unprotect(uuid); else protect(uuid); }

	private boolean allowFriendlyFire = true;

	/**
	 * Checks if friendly fire is allowed in this match.
	 *
	 * @return true if friendly fire is allowed, otherwise false
	 */
	public boolean allowFriendlyFire()
	{ return allowFriendlyFire; }

	/**
	 * Sets whether friendly fire is allowed in this match.
	 */
	public void setFriendlyFire(boolean b)
	{ this.allowFriendlyFire = b; }

	private boolean allowObjectiveCraft = false;

	/**
	 * Checks if players are allowed to craft objectives in this match.
	 *
	 * @return true if players may craft objectives, otherwise false
	 */
	public boolean allowObjectiveCraft()
	{ return allowObjectiveCraft; }

	/**
	 * Sets whether players are allowed to craft objectives in this match.
	 */
	public void setObjectiveCraft(boolean b)
	{ this.allowObjectiveCraft = b; }

	// provided by configuration file
	private static boolean allowTies = false;

	/**
	 * Checks if ties are allowed on this server.
	 *
	 * @return true if ties are allowed, otherwise false
	 */
	public static boolean areTiesAllowed()
	{ return allowTies; }

	/**
	 * Sets whether ties are allowed on this server.
	 */
	public static void setAllowTies(boolean b)
	{ AutoRefMatch.allowTies = b; }

	// list of items players may not craft
	private Set<BlockData> prohibitCraft = Sets.newHashSet();

	// range of inexact placement
	private int inexactRange = 2;

	/**
	 * Gets the distance an objective may be placed from its target location.
	 *
	 * @return range of inexact objective placement
	 */
	public int getInexactRange()
	{ return inexactRange; }

	// transcript of every event in the match
	private List<TranscriptEvent> transcript;

	private boolean refereeReady = false;

	/**
	 * Checks if the referees are ready for the match to start.
	 *
	 * @return true if referees are ready or there are no referees, otherwise false
	 */
	public boolean isRefereeReady()
	{ return getReferees().size() == 0 || refereeReady; }

	/**
	 * Sets whether the referees are ready for the match to start.
	 */
	public void setRefereeReady(boolean r)
	{ refereeReady = r; }

	private CommandSender debugRecipient = null;

	/**
	 * Checks if the match is in debug mode.
	 *
	 * @return true if match is in debug mode, otherwise false
	 */
	public boolean isDebugMode()
	{ return debugRecipient != null; }

	/**
	 * Sends a debug message to the debug recipient.
	 *
	 * @param msg debug message
	 */
	public void debug(String msg)
	{ if (debugRecipient != null) debugRecipient.sendMessage(msg); }

	/**
	 * Sets the recipient of debug messages for this match.
	 *
	 * @param recipient a recipient for debug messages, or null to disable debug
	 */
	public void setDebug(CommandSender recipient)
	{
		if (recipient != null && recipient.hasPermission("autoreferee.streamer"))
			AutoReferee.getInstance().getLogger().info(
				"You may not direct debug message to a streamer!");

		debugRecipient = recipient;
		debug(ChatColor.GREEN + "Debug mode is now " +
			(isDebugMode() ? "on" : "off"));
	}

	private ReportGenerator matchReportGenerator = new ReportGenerator();

	/**
	 * Gets the mutable report generator object.
	 *
	 * @return report generator object
	 */
	public ReportGenerator getReportGenerator()
	{ return matchReportGenerator; }

	public void saveMapImage()
	{
		try
		{
			RenderedImage mapImage = getMapImage();
			ImageIO.write(mapImage, "png", new File(getWorld().getWorldFolder(), "map.png"));
		}
		catch (IOException e) { e.printStackTrace(); }
	}

	public RenderedImage getMapImage()
	{
		CuboidRegion cube = getMapCuboid();
		Location min = cube.getMinimumPoint(),
			max = cube.getMaximumPoint();

		return MapImageGenerator.generateFromWorld(getWorld(),
			min.getBlockX(), max.getBlockX(), min.getBlockZ(), max.getBlockZ());
	}

	// number of seconds for each phase
	public static final int READY_SECONDS = 15;
	public static final int COMPLETED_SECONDS = 180;

	private int customReadyDelay = -1;

	/**
	 * Gets number of seconds between start of countdown and match starting.
	 *
	 * @return number of seconds for match countdown
	 */
	public int getReadyDelay()
	{
		if (customReadyDelay >= 0) return customReadyDelay;
		return AutoReferee.getInstance().getConfig().getInt(
			"delay-seconds.ready", AutoRefMatch.READY_SECONDS);
	}

	/**
	 * Sets number of seconds between start of countdown and match starting.
	 */
	public void setReadyDelay(int delay)
	{ this.customReadyDelay = delay; }

	public void notify(Location loc, String message)
	{
		// give spectators a location to warp to (null is acceptable)
		this.setLastNotificationLocation(loc);

		// send a notification message
		if (message.trim().isEmpty()) message = GENERIC_NOTIFICATION_MESSAGE;
		String m = ChatColor.DARK_GRAY + "[N] " + message;
		for (Player pl : this.getReferees(false)) pl.sendMessage(m);
	}

	private Location lastNotificationLocation = null;

	public Location getLastNotificationLocation()
	{ return lastNotificationLocation; }

	/**
	 * Sets a notification location for referees and streamers. This location should be the
	 * exact location of the event. The teleportation suite will find a suitable vantage point
	 * to observe the event.
	 *
	 * @param loc notification location
	 */
	public void setLastNotificationLocation(Location loc)
	{ lastNotificationLocation = loc; }

	private Location lastDeathLocation = null;

	public Location getLastDeathLocation()
	{ return lastDeathLocation; }

	public void setLastDeathLocation(Location loc)
	{
		lastDeathLocation = loc;
		setLastNotificationLocation(loc);
	}

	private Location lastLogoutLocation = null;

	public Location getLastLogoutLocation()
	{ return lastLogoutLocation; }

	public void setLastLogoutLocation(Location loc)
	{
		lastLogoutLocation = loc;
		setLastNotificationLocation(loc);
	}

	private Location lastTeleportLocation = null;

	public Location getLastTeleportLocation()
	{ return lastTeleportLocation; }

	public void setLastTeleportLocation(Location loc)
	{
		lastTeleportLocation = loc;
		setLastNotificationLocation(loc);
	}

	private Location lastObjectiveLocation = null;

	public Location getLastObjectiveLocation()
	{ return lastObjectiveLocation; }

	public void setLastObjectiveLocation(Location loc)
	{
		lastObjectiveLocation = loc;
		setLastNotificationLocation(loc);
	}

	private class PlayerCountTask implements Runnable
	{
		private long lastOccupiedTick = 0;

		public PlayerCountTask()
		{ lastOccupiedTick = getWorld().getFullTime(); }

		public void run()
		{
			long tick = getWorld().getFullTime();

			// if there are people in this world/match, reset last-occupied
			if (getUserCount() != 0) lastOccupiedTick = tick;

			// if this world has been inactive for long enough, just unload it
			if (tick - lastOccupiedTick >= getCurrentState().inactiveTicks) destroy();
		}
	}

	public AutoRefMatch(World world, boolean tmp, MatchStatus state)
	{ this(world, tmp); setCurrentState(state); }

	public AutoRefMatch(World world, boolean tmp)
	{
		setPrimaryWorld(world);
		loadWorldConfiguration();

		// is this world a temporary world?
		this.tmp = tmp;

		// brand new match transcript
		transcript = Lists.newLinkedList();

		// fix vanish
		this.setupSpectators();

		// startup the player count timer (for automatic unloading)
		Plugin plugin = AutoReferee.getInstance();
		plugin.getServer().getScheduler().runTaskTimer(plugin, new PlayerCountTask(), 0L, 60*20L);
	}

	/**
	 * Gets number of users (players and spectators) present in this match.
	 *
	 * @return number of users
	 */
	public int getUserCount()
	{ return primaryWorld.getPlayers().size(); }

	public Set<AutoRefPlayer> getPlayers()
	{
		Set<AutoRefPlayer> players = Sets.newHashSet();
		for (AutoRefTeam team : teams)
			players.addAll(team.getPlayers());
		return players;
	}

	/**
	 * Gets all non-streamer referees present in this match.
	 *
	 * @return collection of referees
	 */
	public Set<Player> getReferees()
	{ return getReferees(true); }

	/**
	 * Gets referees present in this match, possibly excluding streamers.
	 *
	 * @param excludeStreamers whether streamers should be included
	 * @return collection of referees
	 */
	public Set<Player> getReferees(boolean excludeStreamers)
	{
		Set<Player> refs = Sets.newHashSet();
		for (Player p : primaryWorld.getPlayers())
			if (isReferee(p) && !(excludeStreamers && isStreamer(p))) refs.add(p);
		return refs;
	}

	/**
	 * Gets streamers present in this match.
	 *
	 * @return collection of streamers
	 */
	public Set<Player> getStreamers()
	{
		Set<Player> streamers = Sets.newHashSet();
		for (Player p : primaryWorld.getPlayers())
			if (isStreamer(p)) streamers.add(p);
		return streamers;
	}

	/**
	 * Checks if the specified player is a referee for this match.
	 *
	 * @return true if player is a referee and not on a team, otherwise false
	 */
	public boolean isReferee(Player player)
	{
		if (isPlayer(player)) return false;
		return player.hasPermission("autoreferee.referee");
	}

	/**
	 * Checks if the specified player is a streamer for this match.
	 *
	 * @return true if player is a referee and not on a team, otherwise false
	 */
	public boolean isStreamer(Player player)
	{
		if (isPlayer(player)) return false;
		return player.hasPermission("autoreferee.streamer");
	}

	/**
	 * Checks if the specified player is a spectator for this match.
	 *
	 * @return true if player is a spectator, otherwise false
	 */
	public boolean isSpectator(Player player)
	{
		if (isReferee(player)) return true;
		return !getCurrentState().isBeforeMatch() && !isPlayer(player);
	}

	public enum Role
	{
		// this list provides an ordering of roles. do not permute.
		NONE, PLAYER, SPECTATOR, STREAMER, REFEREE;

		public int getRank()
		{ return this.ordinal(); }
	}

	/**
	 * Gets the role that this player has in this match.
	 *
	 * @return a role corresponding to this player
	 */
	public Role getRole(OfflinePlayer player)
	{
		if (isPlayer(player)) return Role.PLAYER;
		if (!player.isOnline()) return Role.NONE;

		Player pl = player.getPlayer();
		if (pl.hasPermission("autoreferee.streamer")) return Role.STREAMER;
		if (pl.hasPermission("autoreferee.referee")) return Role.REFEREE;
		if (!getCurrentState().isBeforeMatch()) return Role.SPECTATOR;

		return Role.NONE;
	}

	/**
	 * Checks if the given world is compatible with AutoReferee
	 * @param world world to check
	 * @return true if the world contains a config file, otherwise false
	 */
	public static boolean isCompatible(World world)
	{ return new File(world.getWorldFolder(), AutoReferee.CFG_FILENAME).exists(); }

	/**
	 * Reloads world configuration from config file.
	 */
	public void reload()
	{ this.loadWorldConfiguration(); }

	private void loadWorldConfiguration()
	{
		try
		{
			// file stream and configuration object (located in world folder)
			InputStream cfgStream = worldConfigFile.exists() ? new FileInputStream(worldConfigFile)
				: AutoReferee.getInstance().getResource("defaults/map.xml");
			worldConfig = new SAXBuilder().build(cfgStream).getRootElement();
		}
		catch (Exception e) { e.printStackTrace(); return; }

		teams = Sets.newHashSet();
		messageReferees("match", getWorld().getName(), "init");
		setCurrentState(MatchStatus.WAITING);

		// get the extra settings cached
		Element meta = worldConfig.getChild("meta");
		if (meta != null)
		{
			mapName = meta.getChildText("name");
			versionString = meta.getChildText("version");

			mapAuthors = Lists.newLinkedList();
			for (Element e : meta.getChild("creators").getChildren("creator"))
				mapAuthors.add(e.getText());
		}

		for (Element e : worldConfig.getChild("teams").getChildren("team"))
			teams.add(AutoRefTeam.fromElement(e, this));

		protectedEntities = Sets.newHashSet();
		prohibitCraft = Sets.newHashSet();

		Element eProtect = worldConfig.getChild("protect");
		if (eProtect != null) for (Element c : eProtect.getChildren())
			try { protectedEntities.add(UUID.fromString(c.getTextTrim())); }
			catch (Exception e) {  }

		// HELPER: ensure all protected entities are still present in world
		Set<UUID> uuidSearch = Sets.newHashSet(protectedEntities);
		for (Entity e : getWorld().getEntities()) uuidSearch.remove(e.getUniqueId());
		if (!uuidSearch.isEmpty()) this.broadcast(ChatColor.RED + "" + ChatColor.BOLD + "WARNING: " +
			ChatColor.RESET + "One or more protected entities are missing!");

		// get the start region (safe for both teams, no pvp allowed)
		assert worldConfig.getChild("startregion") != null;
		for (Element e : worldConfig.getChild("startregion").getChildren())
			addStartRegion(AutoRefRegion.fromElement(this, e));

		String attrSpawn = worldConfig.getChild("startregion").getAttributeValue("spawn");
		if (attrSpawn != null) setWorldSpawn(LocationUtil.fromCoords(getWorld(), attrSpawn));

		Element gameplay = worldConfig.getChild("gameplay");
		if (gameplay != null) this.parseExtraGameRules(gameplay);

		Element regions = worldConfig.getChild("regions");
		for (Element reg : regions.getChildren())
			this.addRegion(AutoRefRegion.fromElement(this, reg));

		Element goals = worldConfig.getChild("goals");
		if (goals != null) for (Element teamgoals : goals.getChildren("teamgoals"))
		{
			AutoRefTeam team = this.teamNameLookup(teamgoals.getAttributeValue("team"));
			AutoReferee.getInstance().getLogger().info("Loading goals for " + team.getName());
			if (team != null) for (Element gelt : teamgoals.getChildren()) team.addGoal(gelt);
		}

		Element mechanisms = worldConfig.getChild("mechanisms");
		if (mechanisms != null) for (Element mech : mechanisms.getChildren())
		{
			boolean state = Boolean.parseBoolean(mech.getText());
			Location mechloc = LocationUtil.fromCoords(getWorld(), mech.getAttributeValue("pos"));
			this.addStartMech(getWorld().getBlockAt(mechloc), state);
		}

		// restore competitive settings and some default values
		primaryWorld.setPVP(true);
		primaryWorld.setSpawnFlags(true, true);

		primaryWorld.setTicksPerAnimalSpawns(-1);
		primaryWorld.setTicksPerMonsterSpawns(-1);

		// last, send an update about the match to everyone logged in
		for (Player pl : primaryWorld.getPlayers()) sendMatchInfo(pl);
	}

	private static Difficulty getDifficulty(String d)
	{
		Difficulty diff = Difficulty.valueOf(d.toUpperCase());
		try { diff = Difficulty.getByValue(Integer.parseInt(d)); }
		catch (NumberFormatException e) {  }

		return diff;
	}

	private void parseExtraGameRules(Element gameplay)
	{
		// get the time the match is set to start
		if (gameplay.getChild("clockstart") != null)
			startTime = AutoReferee.parseTimeString(gameplay.getChildText("clockstart"));

		// allow or disallow friendly fire
		if (gameplay.getChild("friendlyfire") != null)
			setFriendlyFire(Boolean.parseBoolean(gameplay.getChildText("friendlyfire")));

		// attempt to set world difficulty as best as possible
		Difficulty diff = Difficulty.HARD;
		if (gameplay.getChild("difficulty") != null)
			diff = getDifficulty(gameplay.getChildText("difficulty"));
		primaryWorld.setDifficulty(diff);

		// respawn mode
		if (gameplay.getChild("respawn") != null)
		{
			String rtext = gameplay.getChildTextTrim("respawn");
			RespawnMode rmode = null;

			if (rtext != null && !rtext.isEmpty())
				rmode = RespawnMode.valueOf(rtext.toUpperCase());
			setRespawnMode(rmode == null ? RespawnMode.ALLOW : rmode);
		}
	}

	/**
	 * Saves copy of autoreferee.xml back to the world folder.
	 */
	public void saveWorldConfiguration()
	{
		// if there is no configuration object or file, nothin' doin'...
		if (worldConfig == null)
		{
			try
			{
				InputStream mapXML = AutoReferee.getInstance().getResource("defaults/map.xml");
				worldConfig = new SAXBuilder().build(mapXML).getRootElement();
			}
			catch (Exception e) { e.printStackTrace(); return; }
		}

		else
		{
			// get the teams object
			Element eTeams = worldConfig.getChild("teams");
			if (eTeams == null) worldConfig.addContent(eTeams = new Element("team"));

			// reset the teams to whatever has been saved
			eTeams.removeContent();
			for (AutoRefTeam team : teams)
				eTeams.addContent(team.toElement());

			// get the regions object
			Element eRegions = worldConfig.getChild("regions");
			if (eRegions == null) worldConfig.addContent(eRegions = new Element("regions"));

			// reset the regions to whatever has been saved
			eRegions.removeContent();
			for (AutoRefRegion reg : this.getRegions())
				eRegions.addContent(reg.toElement());

			// get startregion object
			Element eStartRegions = worldConfig.getChild("startregion");
			if (getWorldSpawn() != null) eStartRegions.setAttribute("spawn",
				LocationUtil.toBlockCoordsWithYaw(getWorldSpawn()));

			eStartRegions.removeContent();
			for (AutoRefRegion reg : this.getStartRegions())
				eStartRegions.addContent(reg.toElement());

			// get the protections object
			Element eProtect = worldConfig.getChild("protect");
			if (eProtect == null) worldConfig.addContent(eProtect = new Element("protect"));

			// reset the protections to whatever has been saved
			eProtect.removeContent();
			for (UUID uid : protectedEntities)
			{
				AutoReferee.getInstance().getLogger().info(uid.toString());
				eProtect.addContent(new Element("entity").setText(uid.toString()));
			}

			// get the goals object
			Element eGoals = worldConfig.getChild("goals");
			if (eGoals == null) worldConfig.addContent(eGoals = new Element("goals"));

			// reset the goals to whatever has been saved
			eGoals.removeContent();
			for (AutoRefTeam team : this.getTeams())
			{
				Element tgoals = new Element("teamgoals")
					.setAttribute("team", team.getName());
				eGoals.addContent(tgoals);
				for (AutoRefGoal goal : team.getTeamGoals())
					tgoals.addContent(goal.toElement());
			}

			// get the mechanisms object
			Element eMechanisms = worldConfig.getChild("mechanisms");
			if (eMechanisms == null) worldConfig.addContent(eProtect = new Element("mechanisms"));

			// reset the mechanisms to whatever has been saved
			eMechanisms.removeContent();
			for (StartMechanism mech : this.startMechanisms)
				eMechanisms.addContent(mech.toElement());
		}

		// save the configuration file back to the original filename
		try
		{
			XMLOutputter xmlout = new XMLOutputter(Format.getPrettyFormat());
			xmlout.output(worldConfig, new FileOutputStream(worldConfigFile));
		}

		// log errors, report which world did not save
		catch (java.io.IOException e)
		{ AutoReferee.getInstance().getLogger().info("Could not save world config: " + primaryWorld.getName()); }
	}

	/**
	 * Sends a referee plugin channel message to all referees, properly delimited.
	 */
	public void messageReferees(String ...parts)
	{
		if (this.isDebugMode()) this.debug(StringUtils.join(parts, RefereeChannelListener.DELIMITER));
		for (Player ref : getReferees(false)) messageReferee(ref, parts);
	}

	/**
	 * Sends a referee plugin channel message to a specific referee, properly delimited.
	 *
	 * @param ref referee to recieve the plugin channel message
	 */
	public void messageReferee(Player ref, String ...parts)
	{
		try
		{
			String msg = StringUtils.join(parts, RefereeChannelListener.DELIMITER);
			ref.sendPluginMessage(AutoReferee.getInstance(), AutoReferee.REFEREE_PLUGIN_CHANNEL,
				msg.getBytes(AutoReferee.PLUGIN_CHANNEL_ENC));
		}
		catch (UnsupportedEncodingException e)
		{ AutoReferee.getInstance().getLogger().info("Unsupported encoding: " + AutoReferee.PLUGIN_CHANNEL_ENC); }
	}

	/**
	 * Sends all information to a single referee necessary to sync a match's current status.
	 *
	 * @param ref referee to recieve the plugin channel messages
	 */
	public void updateReferee(Player ref)
	{
		messageReferee(ref, "match", getWorld().getName(), "init");
		messageReferee(ref, "match", getWorld().getName(), "map", getMapName());

		if (getCurrentState().inProgress())
			messageReferee(ref, "match", getWorld().getName(), "time", getTimestamp(","));

		for (AutoRefTeam team : getTeams())
		{
			messageReferee(ref, "team", team.getName(), "init");
			messageReferee(ref, "team", team.getName(), "color", team.getColor().toString());

			for (AutoRefGoal goal : team.getTeamGoals())
				goal.updateReferee(ref);

			for (AutoRefPlayer apl : team.getPlayers())
			{
				messageReferee(ref, "team", team.getName(), "player", "+" + apl.getName());
				updateRefereePlayerInfo(ref, apl);
			}
		}
	}

	private void updateRefereePlayerInfo(Player ref, AutoRefPlayer apl)
	{
		messageReferee(ref, "player", apl.getName(), "kills", Integer.toString(apl.getKills()));
		messageReferee(ref, "player", apl.getName(), "deaths", Integer.toString(apl.getDeaths()));
		messageReferee(ref, "player", apl.getName(), "streak", Integer.toString(apl.getStreak()));
		apl.sendAccuracyUpdate(ref);

		Player pl = apl.getPlayer();
		if (pl != null)
		{
			messageReferee(ref, "player", apl.getName(), "hp", Integer.toString(pl.getHealth()));
			messageReferee(ref, "player", apl.getName(), "armor", Integer.toString(ArmorPoints.fromPlayer(pl)));
		}

		for (AutoRefPlayer en : getPlayers()) if (apl.isDominating(en))
			messageReferee(ref, "player", apl.getName(), "dominate", en.getName());

		messageReferee(ref, "player", apl.getName(), apl.isOnline() ? "login" : "logout");
		messageReferee(ref, "player", apl.getName(), "cape", apl.getCape());
	}

	/**
	 * Sends a message to all players in this match, including referees and streamers.
	 *
	 * @param msg message to be sent
	 */
	public void broadcast(String msg)
	{
		if (AutoReferee.getInstance().isConsoleLoggingEnabled())
			AutoReferee.getInstance().getLogger().info(ChatColor.stripColor(msg));
		for (Player p : primaryWorld.getPlayers()) p.sendMessage(msg);
	}

	/**
	 * Force a broadcast to be sent synchronously. Safe to use from an asynchronous task.
	 *
	 * @param msg message to be sent
	 */
	public void broadcastSync(String msg)
	{
		Plugin plugin = AutoReferee.getInstance();
		plugin.getServer().getScheduler().runTask(plugin, new SyncBroadcastTask(msg));
	}

	private class SyncBroadcastTask implements Runnable
	{
		private String message = null;

		public SyncBroadcastTask(String message)
		{ this.message = message; }

		@Override public void run()
		{ if (this.message != null) broadcast(this.message); }
	}

	/**
	 * Removes any non-alphanumeric characters from a map name. Prepares a map name
	 * to be used as a file name or a target in a chat command.
	 *
	 * @param name original map name
	 * @return normalized version of map name
	 */
	public static String normalizeMapName(String name)
	{ return name == null ? null : name.replaceAll("[^0-9a-zA-Z]+", ""); }

	/**
	 * Assigns a world a match object. Best suited for retro-fitting worlds that
	 * have already been loaded.
	 *
	 * @param world loaded AutoReferee-compatible world
	 * @param tmp whether this world should be unloaded when the match completes
	 */
	public static void setupWorld(World world, boolean tmp)
	{
		// if this map isn't compatible with AutoReferee, quit...
		if (AutoReferee.getInstance().getMatch(world) != null || !isCompatible(world)) return;
		AutoReferee.getInstance().addMatch(new AutoRefMatch(world, tmp, MatchStatus.WAITING));
	}

	/**
	 * Archives this map and stores a clean copy in the map library. Clears unnecessary
	 * files and attempts to generate a minimal copy of the map, ready for distribution.
	 *
	 * @return root folder of the archived map
	 * @throws IOException if archive cannot be created
	 */
	public File archiveMapData() throws IOException
	{
		// make sure the folder exists first
		File archiveFolder = new File(AutoRefMap.getMapLibrary(), this.getVersionString());
		if (!archiveFolder.exists()) archiveFolder.mkdir();

		// (1) copy the configuration file:
		FileUtils.copyFileToDirectory(
			new File(getWorld().getWorldFolder(), AutoReferee.CFG_FILENAME), archiveFolder);

		// (2) copy the level.dat:
		FileUtils.copyFileToDirectory(
			new File(getWorld().getWorldFolder(), "level.dat"), archiveFolder);

		// (3) copy the region folder (only the .mca files):
		FileUtils.copyDirectory(new File(getWorld().getWorldFolder(), "region"),
			new File(archiveFolder, "region"), FileFilterUtils.suffixFileFilter(".mca"));

		// (4) make an empty data folder:
		new File(archiveFolder, "data").mkdir();

		// remove any existing checksum file
		File checksum = new File(getWorld().getWorldFolder(), "checksum");
		if (checksum.exists()) checksum.delete();

		return archiveFolder;
	}

	private static void addToZip(ZipOutputStream zip, File f, File base) throws IOException
	{
		zip.putNextEntry(new ZipEntry(base.toURI().relativize(f.toURI()).getPath()));
		if (f.isDirectory()) for (File c : f.listFiles()) addToZip(zip, c, base);
		else IOUtils.copy(new FileInputStream(f), zip);
	}

	/**
	 * Packages and compresses (zip) map folder for easy distribution.
	 *
	 * @return generated zip file
	 * @throws IOException if map cannot be archived
	 */
	public File distributeMap() throws IOException
	{
		File archiveFolder = this.archiveMapData();
		File outZipfile = new File(AutoRefMap.getMapLibrary(), this.getVersionString() + ".zip");

		ZipOutputStream zip = new ZipOutputStream(new
			BufferedOutputStream(new FileOutputStream(outZipfile)));
		zip.setMethod(ZipOutputStream.DEFLATED);
		addToZip(zip, archiveFolder, AutoRefMap.getMapLibrary());

		zip.close();

		// rewrite a checksum file for this zip
		File checksum = new File(archiveFolder, "checksum");
		String md5 = DigestUtils.md5Hex(new FileInputStream(outZipfile));
		FileUtils.writeStringToFile(checksum, md5);

		return archiveFolder;
	}

	private class WorldFolderDeleter implements Runnable
	{
		private File worldFolder;
		public BukkitTask task = null;

		WorldFolderDeleter(World w)
		{ this.worldFolder = w.getWorldFolder(); }

		@Override
		public void run()
		{
			AutoReferee autoref = AutoReferee.getInstance();
			try
			{
				// if we fail, we loop back around again on the next try...
				FileUtils.deleteDirectory(worldFolder);
				autoref.getLogger().info(worldFolder.getName() + " deleted!");

				// otherwise, stop the repeating task
				task.cancel();
			}
			catch (IOException e)
			{ autoref.getLogger().info("File lock held on " + worldFolder.getName()); }
		}
	}

	public void ejectPlayer(Player player)
	{
		// resets the player to default state
		PlayerUtil.reset(player);

		// teleport them if there is somewhere else they can go
		AutoReferee plugin = AutoReferee.getInstance();
		World target; if ((target = plugin.getLobbyWorld()) == null)
		{
			// find a non-AutoReferee world to drop them in
			for (World w : plugin.getServer().getWorlds())
				if (!AutoRefMatch.isCompatible(w)) { target = w; break; }
		}

		// if there is a reasonable place to teleport them, do so
		if (target != null)
		{
			player.setGameMode(WorldListener.getDefaultGamemode(target));
			player.teleport(target.getSpawnLocation());
		}

		// otherwise, kick them from the server
		else player.kickPlayer(AutoReferee.COMPLETED_KICK_MESSAGE);
	}

	/**
	 * Unloads and cleans up this match. Players will be teleported out or kicked,
	 * the map will be unloaded, and the map folder may be deleted.
	 */
	public void destroy()
	{
		// first, handle all the players
		for (Player p : primaryWorld.getPlayers()) this.ejectPlayer(p);

		// if everyone has been moved out of this world, clean it up
		if (primaryWorld.getPlayers().size() == 0)
		{
			// if we are running in auto-mode and this is OUR world
			AutoReferee plugin = AutoReferee.getInstance();
			if (plugin.isAutoMode() || this.isTemporaryWorld())
			{
				// only change the state if we are sure we are going to unload
				this.setCurrentState(MatchStatus.NONE);
				plugin.clearMatch(this);

				plugin.getServer().unloadWorld(primaryWorld, false);
				if (!plugin.getConfig().getBoolean("save-worlds", false))
				{
					WorldFolderDeleter wfd = new WorldFolderDeleter(primaryWorld);
					wfd.task = plugin.getServer().getScheduler()
						.runTaskTimer(plugin, wfd, 0L, 10 * 20L);
				}
			}
		}
	}

	/**
	 * Checks if a item is prohibited from crafting.
	 *
	 * @param blockdata block data object for the item being queried
	 * @return true if item may be crafted, otherwise false
	 */
	public boolean canCraft(BlockData blockdata)
	{
		for (BlockData nc : prohibitCraft)
			if (nc.equals(blockdata)) return false;
		return true;
	}

	/**
	 * Prohibits an item from being crafted during a match.
	 *
	 * @param blockdata block data object for the prohibited item
	 */
	public void addIllegalCraft(BlockData blockdata)
	{
		this.prohibitCraft.add(blockdata);
		this.broadcast("Crafting " + blockdata.getDisplayName() + " is now prohibited");
	}

	/**
	 * Gets an arbitrary team, attempting to maintain balanced teams if possible.
	 *
	 * @return an arbitrary team
	 */
	public AutoRefTeam getArbitraryTeam()
	{
		// minimum size of any one team, and an array to hold valid teams
		int minsize = Integer.MAX_VALUE;
		List<AutoRefTeam> vteams = Lists.newArrayList();

		// determine the size of the smallest team
		for (AutoRefTeam team : getTeams())
			if (team.getPlayers().size() < minsize)
				minsize = team.getPlayers().size();

		// make a list of all teams with this size
		for (AutoRefTeam team : getTeams())
			if (team.getPlayers().size() == minsize) vteams.add(team);

		// return a random element from this list
		return vteams.get(new Random().nextInt(vteams.size()));
	}

	private Set<AutoRefRegion> regions = Sets.newHashSet();

	public Set<AutoRefRegion> getRegions()
	{ return regions; }

	public Set<AutoRefRegion> getRegions(AutoRefTeam team)
	{
		Set<AutoRefRegion> teamRegions = Sets.newHashSet();
		for (AutoRefRegion reg : regions)
			if (reg.isOwner(team)) teamRegions.add(reg);
		return teamRegions;
	}

	public boolean addRegion(AutoRefRegion reg)
	{ return !regions.contains(reg) && regions.add(reg); }

	public boolean removeRegion(AutoRefRegion reg)
	{ return regions.remove(reg); }

	public void clearRegions() { regions.clear(); }

	/**
	 * A redstone mechanism necessary to start a match.
	 *
	 * @author authorblues
	 */
	public static class StartMechanism
	{
		private Location loc = null;
		private BlockState state = null;
		private boolean flip = true;

		public StartMechanism(Block block, boolean flip)
		{
			this.flip = flip;
			loc = block.getLocation();
			state = block.getState();
		}

		public Element toElement()
		{
			return new Element(state.getType().name().toLowerCase())
				.setAttribute("pos", LocationUtil.toBlockCoords(loc))
				.setText(Boolean.toString(flip));
		}

		public StartMechanism(Block block)
		{ this(block, true); }

		@Override public int hashCode()
		{ return loc.hashCode() ^ state.hashCode(); }

		@Override public boolean equals(Object o)
		{ return (o instanceof StartMechanism) && hashCode() == o.hashCode(); }

		public String serialize()
		{ return LocationUtil.toBlockCoords(loc) + ":" + Boolean.toString(flip); }

		public static StartMechanism fromElement(Element e, World w)
		{
			Block block = w.getBlockAt(LocationUtil.fromCoords(w, e.getAttributeValue("pos")));
			boolean state = Boolean.parseBoolean(e.getTextTrim());

			return new StartMechanism(block, state);
		}

		@Override public String toString()
		{ return state.getType().name() + "(" + this.serialize() + ")"; }

		public Location getLocation()
		{ return loc; }

		public BlockState getBlockState()
		{ return state; }

		public boolean getFlippedPosition()
		{ return flip; }

		public boolean active()
		{
			MaterialData bdata = getLocation().getBlock().getState().getData();

			if (bdata instanceof Redstone)
				return flip == ((Redstone) bdata).isPowered();
			if (bdata instanceof PressureSensor)
				return flip == ((PressureSensor) bdata).isPressed();
			return false;
		}

		public boolean canFlip(AutoRefMatch match)
		{
			MatchStatus mstate = match.getCurrentState();
			if (mstate.isBeforeMatch()) return false;
			return !mstate.isBeforeMatch() && !active();
		}
	}

	/**
	 * Adds a new start mechanism for this map. These mechanisms are activated automatically
	 * at the start of a match when using SportBukkit, and players may interact with them
	 * normally in the start region when using vanilla CraftBukkit.
	 *
	 * @param block block containing the start mechanism
	 * @param state whether this mechanism should be set powered or unpowered
	 * @return generated start mechanism object
	 * @see http://www.github.com/rmct/SportBukkit
	 */
	public StartMechanism addStartMech(Block block, boolean state)
	{
		if (block.getType() != Material.LEVER) state = true;
		StartMechanism sm = new StartMechanism(block, state);
		startMechanisms.add(sm);

		AutoReferee.getInstance().getLogger().info(
			sm.toString() + " is a start mechanism.");
		return sm;
	}

	/**
	 * Gets the start mechanism associated with this location.
	 *
	 * @return start mechanism located at that position, otherwise null
	 */
	public StartMechanism getStartMechanism(Location loc)
	{
		if (loc == null) return null;
		for (StartMechanism sm : startMechanisms)
			if (loc.equals(sm.getLocation())) return sm;
		return null;
	}

	/**
	 * Checks if a specified block location is a start mechanism for this match.
	 *
	 * @return true if a start mechanism is located at that position, otherwise false
	 */
	public boolean isStartMechanism(Location loc)
	{ return getStartMechanism(loc) != null; }

	/**
	 * Parameters necessary to configure a match.
	 * @author authorblues
	 */
	public static class MatchParams
	{
		public static class TeamInfo
		{
			private String name;

			public String getName()
			{ return name; }

			private List<String> players;

			public List<String> getPlayers()
			{ return Collections.unmodifiableList(players); }
		}

		// info about all the teams
		private List<TeamInfo> teams;

		public List<TeamInfo> getTeams()
		{ return Collections.unmodifiableList(teams); }

		// match tag for reporting
		private String tag;

		public String getTag()
		{ return tag; }

		// map name
		private String map;

		public String getMap()
		{ return map; }
	}

	/**
	 * Starts the match.
	 */
	public void start()
	{
		// set up the world time one last time
		primaryWorld.setTime(startTime);
		this.setStartTicks(primaryWorld.getFullTime());

		addEvent(new TranscriptEvent(this, TranscriptEvent.EventType.MATCH_START,
			"Match began.", null, null, null));

		// send referees the start event
		messageReferees("match", getWorld().getName(), "start");

		// remove all mobs, animals, and items (again)
		this.clearEntities();

		// loop through all the redstone mechanisms required to start / FIXME BUKKIT-1858
		if (AutoReferee.getInstance().isAutoMode() || AutoReferee.hasSportBukkitApi())
			for (StartMechanism sm : startMechanisms)
		{
			MaterialData mdata = sm.getBlockState().getData();
			switch (sm.getBlockState().getType())
			{
				case LEVER:
					// flip the lever to the correct state
					((Lever) mdata).setPowered(sm.getFlippedPosition());
					break;

				case STONE_BUTTON:
					// press (or depress) the button
					((Button) mdata).setPowered(sm.getFlippedPosition());
					break;

				case WOOD_PLATE:
				case STONE_PLATE:
					// press (or depress) the pressure plate
					((PressurePlate) mdata).setData((byte)(sm.getFlippedPosition() ? 0x1 : 0x0));
					break;

				default:
					break;
			}

			// save the block state and fire an update
			sm.getBlockState().setData(mdata);
			sm.getBlockState().update(true);
		}

		// set teams as started
		for (AutoRefTeam team : getTeams())
			team.startMatch();

		// set the current state to playing
		setCurrentState(MatchStatus.PLAYING);

		// match minute timer
		AutoReferee plugin = AutoReferee.getInstance();
		clockTask = new MatchClockTask();

		clockTask.task = plugin.getServer().getScheduler().runTaskTimer(
			plugin, clockTask, 60 * 20L, 60 * 20L);

		if (plugin.playedMapsTracker != null)
			plugin.playedMapsTracker.increment(normalizeMapName(this.getMapName()));
	}

	private static final Set<Long> announceMinutes =
		Sets.newHashSet(60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L);

	// handle to the clock task
	private MatchClockTask clockTask = null;

	private void cancelClock()
	{
		if (clockTask != null && clockTask.task != null) clockTask.task.cancel();
		clockTask = null;
	}

	private class MatchClockTask implements Runnable
	{
		public BukkitTask task;

		public void run()
		{
			AutoRefMatch match = AutoRefMatch.this;
			long minutesRemaining = match.getTimeRemaining() / 60L;

			// drop out just in case
			if (!match.hasTimeLimit()) return;

			if (minutesRemaining == 0L)
			{
				String timelimit = (match.getTimeLimit() / 60L) + " min";
				match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.MATCH_END,
					"Match time limit reached: " + timelimit, null, null, null));
				match.matchComplete();
			}
			else if (AutoRefMatch.announceMinutes.contains(minutesRemaining))
				match.broadcast(">>> " + ChatColor.GREEN +
					"Match ends in " + minutesRemaining + "m");
		}
	}

	private int getVanishLevel(Player p)
	{
		// if this person is a player, lowest vanish level
		if (isPlayer(p)) return 0;

		// streamers are ONLY able to see streamers and players
		if (isStreamer(p)) return 1;

		// referees have the highest vanish level (see everything)
		if (isReferee(p)) return 200;

		// spectators can only be seen by referees
		return 100;
	}

	// either vanish or show the player `subj` from perspective of `view`
	private void setupVanish(Player view, Player subj)
	{
		if (getVanishLevel(view) >= getVanishLevel(subj) ||
			!this.getCurrentState().inProgress()) view.showPlayer(subj);
		else view.hidePlayer(subj);
	}

	/**
	 * Reconfigures spectator mode for all connected players.
	 */
	public void setupSpectators()
	{ for ( Player pl : getWorld().getPlayers() ) setupSpectators(pl); }

	/**
	 * Reconfigures spectator mode for a single player. Useful for updating all
	 * players when one player logs in.
	 *
	 * @param player player to configure spectator mode for
	 */
	public void setupSpectators(Player player)
	{
		if (getCurrentState().isBeforeMatch()) setSpectatorMode(player, isReferee(player));
		else setSpectatorMode(player, !isPlayer(player) || getCurrentState().isAfterMatch());

		for ( Player x : getWorld().getPlayers() )
		{
			// setup vanish in both directions
			setupVanish(player, x);
			setupVanish(x, player);
		}
	}

	/**
	 * Sets whether a specified player is in spectator mode.
	 *
	 * @param player player to set spectator mode for
	 * @param b true to set spectator mode on, false to set spectator mode off
	 */
	public void setSpectatorMode(Player player, boolean b)
	{
		GameMode gm = b ? GameMode.CREATIVE : GameMode.SURVIVAL;
		this.setSpectatorMode(player, b, gm);
	}

	/**
	 * Sets whether a specified player is in spectator mode, explicitly setting gamemode.
	 *
	 * @param player player to set spectator mode for
	 * @param b true to set spectator mode on, false to set spectator mode off
	 * @param gamemode player's new gamemode
	 */
	public void setSpectatorMode(Player player, boolean b, GameMode gamemode)
	{
		player.setGameMode(gamemode);
		if (!player.getAllowFlight()) player.setFallDistance(0.0f);
		AutoReferee.setAffectsSpawning(player, !b);

		boolean noEntityCollide = b && getCurrentState().inProgress();
		AutoReferee.setCollidesWithEntities(player, !noEntityCollide);
	}

	/**
	 * Removes unprotected entities from the world.
	 */
	public void clearEntities()
	{
		for (Entity e : primaryWorld.getEntitiesByClasses(Monster.class,
			Animals.class, Item.class, ExperienceOrb.class, Arrow.class))
			if (!protectedEntities.contains(e.getUniqueId())) e.remove();
	}

	/**
	 * Checks if the match start countdown is running.
	 *
	 * @return true if the countdown is in progress, otherwise false
	 */
	public boolean isCountdownRunning()
	{ return matchStarter != null && matchStarter.task != null; }

	/**
	 * Cancels the match countdown in progress.
	 */
	public void cancelCountdown()
	{
		if (isCountdownRunning()) matchStarter.task.cancel();
		matchStarter = null;
	}

	// helper class for starting match, synchronous task
	private static class CountdownTask implements Runnable
	{
		public static final ChatColor COLOR = ChatColor.GREEN;

		public BukkitTask task = null;
		private int remainingSeconds = 3;

		private AutoRefMatch match = null;
		private boolean start = false;

		public CountdownTask(AutoRefMatch m, int time, boolean start)
		{
			match = m;
			remainingSeconds = time;
			this.start = start;
		}

		public void run()
		{
			if (remainingSeconds > 3)
			{
				// currently nothing...
			}

			// if the countdown has ended...
			else if (remainingSeconds == 0)
			{
				// setup world to go!
				if (this.start) match.start();
				match.broadcast(">>> " + CountdownTask.COLOR + "GO!");

				// cancel the task
				match.cancelCountdown();
			}

			// report number of seconds remaining
			else match.broadcast(">>> " + CountdownTask.COLOR +
				Integer.toString(remainingSeconds) + "...");

			// count down
			--remainingSeconds;
		}

		public int getRemainingSeconds()
		{ return remainingSeconds; }
	}

	// prepare this world to start
	private void prepareMatch()
	{
		// nothing to do if the countdown is running
		if (isCountdownRunning()) return;

		// set the current time to the start time
		primaryWorld.setTime(this.startTime);

		// remove all mobs, animals, and items
		this.clearEntities();

		// turn off weather forever (or for a long time)
		primaryWorld.setStorm(false);
		primaryWorld.setWeatherDuration(Integer.MAX_VALUE);

		// save a copy of the map image quickly before the match starts...
		saveMapImage();

		// prepare all players for the match
		for (AutoRefPlayer apl : getPlayers()) apl.heal();

		// announce the match starting in X seconds
		int readyDelay = this.getReadyDelay();
		this.broadcast(CountdownTask.COLOR + "Match will begin in "
			+ ChatColor.WHITE + Integer.toString(readyDelay) + CountdownTask.COLOR + " seconds.");

		// send referees countdown notification
		messageReferees("match", getWorld().getName(), "countdown", Integer.toString(readyDelay));
		startCountdown(readyDelay, true);
	}

	/**
	 * Starts a countdown.
	 *
	 * @param delay number of seconds before end of countdown
	 * @param start true if countdown should start match, otherwise false
	 */
	public void startCountdown(int delay, boolean start)
	{
		// get a copy of the bukkit scheduling daemon
		BukkitScheduler scheduler = AutoReferee.getInstance().getServer().getScheduler();

		// cancel any previous match-start task
		if (this.matchStarter != null && this.matchStarter.task != null)
			this.matchStarter.task.cancel();

		// schedule the task to announce and prepare the match
		this.matchStarter = new CountdownTask(this, delay, start);
		this.matchStarter.task = scheduler.runTaskTimer(
				AutoReferee.getInstance(), this.matchStarter, 0L, 20L);
	}

	/**
	 * Checks if teams have any players missing and are ready to play.
	 */
	public void checkTeamsReady()
	{
		// this function is only useful if called prior to the match
		if (!getCurrentState().isBeforeMatch()) return;

		// if there are no players on the server
		if (getPlayers().size() == 0)
		{
			// set all the teams to not ready and status as waiting
			for ( AutoRefTeam t : teams ) t.setReady(false);
			setCurrentState(MatchStatus.WAITING); return;
		}

		// if we aren't in online mode, assume we are always ready
		if (!AutoReferee.getInstance().isAutoMode())
		{ setCurrentState(MatchStatus.READY); return; }

		// check if all the players are here
		boolean ready = true;
		for ( OfflinePlayer opl : getExpectedPlayers() )
			ready &= opl.isOnline() && isPlayer(opl.getPlayer()) &&
				getPlayer(opl.getPlayer()).isPresent();

		// set status based on whether the players are online
		setCurrentState(ready ? MatchStatus.READY : MatchStatus.WAITING);
	}

	/**
	 * Checks if teams and referees are ready for the match to start.
	 */
	public void checkTeamsStart()
	{
		boolean teamsReady = true;
		for ( AutoRefTeam t : teams )
			teamsReady &= t.isReady() || t.isEmptyTeam();

		boolean ready = getReferees().size() == 0 ? teamsReady : isRefereeReady();
		if (teamsReady && !ready) for (Player p : getReferees())
			p.sendMessage(ChatColor.GRAY + "Teams are ready. Type /ready to begin the match.");

		// everyone is ready, let's go!
		if (ready) this.prepareMatch();
	}

	/**
	 * Checks if a given block type exists within a cube centered around a location.
	 *
	 * @param blockdata block type being searched for
	 * @param loc center point of searchable cube
	 * @param radius radius of searchable cube
	 * @return location of a matching block within the region if one exists, otherwise null
	 */
	public Location blockInRange(BlockData blockdata, Location loc, int radius)
	{
		Block b = getWorld().getBlockAt(loc);
		for (int x = -radius; x <= radius; ++x)
		for (int y = -radius; y <= radius; ++y)
		for (int z = -radius; z <= radius; ++z)
		{
			Block rel = b.getRelative(x, y, z);
			if (blockdata.matchesBlock(rel)) return rel.getLocation();
		}

		return null;
	}

	/**
	 * Checks if a given block type exists within a cube centered around a location.
	 *
	 * @param goal win condition object
	 * @return location of a matching block within the region if one exists, otherwise null
	 */
	public Location blockInRange(org.mctourney.AutoReferee.goals.BlockGoal goal)
	{ return blockInRange(goal.getItem(), goal.getTarget(), goal.getInexactRange()); }

	/**
	 * Checks if any team has satisfied the win conditions.
	 */
	public void checkWinConditions()
	{
		Plugin plugin = AutoReferee.getInstance();
		plugin.getServer().getScheduler().runTask(plugin,
			new Runnable(){ public void run(){ delayedCheckWinConditions(); } });
	}

	private void delayedCheckWinConditions()
	{
		// this code is only called in BlockPlaceEvent and BlockBreakEvent when
		// we have confirmed that the state is PLAYING, so we know we are definitely
		// in a match if this function is being called

		if (getCurrentState().inProgress()) for (AutoRefTeam team : this.teams)
		{
			// if there are no win conditions set, skip this team
			if (team.getTeamGoals().size() == 0) continue;

			// check all win condition blocks (AND together)
			boolean win = true;
			for (AutoRefGoal goal : team.getTeamGoals())
				win &= goal.isSatisfied(this);

			// force an update of objective status
			team.updateObjectives();

			// if the team won, mark the match as completed
			if (win) matchComplete(team);
		}
	}

	// helper class for terminating world, synchronous task
	private class MatchEndTask implements Runnable
	{
		public void run()
		{ destroy(); }
	}

	private static class TiebreakerComparator implements Comparator<AutoRefTeam>
	{
		public int compare(AutoRefTeam a, AutoRefTeam b)
		{
			// break ties first on the number of objectives placed, then number found
			int vmd = b.getObjectivesPlaced() - a.getObjectivesPlaced();
			return vmd == 0 ? b.getObjectivesFound() - a.getObjectivesFound() : vmd;
		}
	}

	/**
	 * Ends match, allowing AutoReferee to break ties according to its own policies.
	 */
	public void matchComplete()
	{
		TiebreakerComparator cmp = new TiebreakerComparator();
		List<AutoRefTeam> sortedTeams = Lists.newArrayList(getTeams());

		// sort the teams based on their "score"
		Collections.sort(sortedTeams, cmp);

		if (0 != cmp.compare(sortedTeams.get(0), sortedTeams.get(1)))
		{ matchComplete(sortedTeams.get(0)); return; }

		if (AutoRefMatch.areTiesAllowed()) { matchComplete(null); return; }
		for (Player ref : getReferees())
		{
			ref.sendMessage(ChatColor.DARK_GRAY + "This match is currently tied.");
			ref.sendMessage(ChatColor.DARK_GRAY + "Use '/autoref endmatch <team>' to declare a winner.");
		}

		// let the console know that the match cannot be ruled upon
		AutoReferee.getInstance().getLogger().info("Match tied. Deferring to referee intervention...");
		cancelClock();
	}

	/**
	 * Ends match in favor of a specified team.
	 *
	 * @param team winning team, or null for no winner
	 */
	public void matchComplete(AutoRefTeam team)
	{
		// announce the victory and set the match to completed
		if (team != null) this.broadcast(team.getDisplayName() + " Wins!");
		else this.broadcast("Match terminated!");

		// remove all mobs, animals, and items
		this.clearEntities();

		for (AutoRefPlayer apl : getPlayers())
		{
			Player pl = apl.getPlayer();
			if (pl == null) continue;
			pl.getInventory().clear();
		}

		// update the client clock to ensure it syncs with match summary
		messageReferees("match", getWorld().getName(), "time", getTimestamp(","));

		// send referees the end event
		if (team != null) messageReferees("match", getWorld().getName(), "end", team.getName());
		else messageReferees("match", getWorld().getName(), "end");

		// reset and report kill streaks
		for (AutoRefPlayer apl : getPlayers()) apl.resetKillStreak();

		String winner = team == null ? "" : (" " + team.getName() + " wins!");
		addEvent(new TranscriptEvent(this, TranscriptEvent.EventType.MATCH_END,
			"Match ended." + winner, null, null, null));
		setCurrentState(MatchStatus.COMPLETED);

		setWinningTeam(team);
		logPlayerStats();

		cancelClock();

		// increment the metrics for number of matches played
		AutoReferee plugin = AutoReferee.getInstance();
		if (plugin.matchesPlayed != null) plugin.matchesPlayed.increment();

		int termDelay = plugin.getConfig().getInt(
			"delay-seconds.completed", COMPLETED_SECONDS);

		plugin.getServer().getScheduler().runTaskLater(
			plugin, new MatchEndTask(), termDelay * 20L);

		// set the time to day
		getWorld().setTime(0L);
	}

	/**
	 * Finds a team whose name matches the given string.
	 *
	 * @param name team name to look up, either custom team name or base team name
	 * @return team object matching the name if one exists, otherwise null
	 */
	public AutoRefTeam teamNameLookup(String name)
	{
		AutoRefTeam mteam = null;

		// if there is no match on that world, forget it
		// is this team name a word?
		for (AutoRefTeam t : teams) if (t.matches(name))
		{ if (mteam == null) mteam = t; else return null; }

		// return the matched team (or null if no match)
		return mteam;
	}

	Set<OfflinePlayer> expectedPlayers = Sets.newHashSet();

	public Set<OfflinePlayer> getExpectedPlayers()
	{
		Set<OfflinePlayer> eps = Sets.newHashSet(expectedPlayers);
		for (AutoRefTeam team : teams)
			eps.addAll(team.getExpectedPlayers());
		return eps;
	}

	/**
	 * Adds a player to the list of expected players, without a team affiliation.
	 */
	public void addExpectedPlayer(OfflinePlayer opl)
	{ expectedPlayers.add(opl); }

	/**
	 * Gets the team the specified player is expected to join.
	 *
	 * @return team player is expected to join, otherwise null
	 */
	public AutoRefTeam expectedTeam(OfflinePlayer opl)
	{
		for (AutoRefTeam team : teams)
			if (team.getExpectedPlayers().contains(opl)) return team;
		return null;
	}

	/**
	 * Checks if the specified player is expected to join this match.
	 *
	 * @return true if player is expected, otherwise false
	 */
	public boolean isPlayerExpected(OfflinePlayer opl)
	{ return getExpectedPlayers().contains(opl); }

	/**
	 * Removes a specified player from any expected player lists for this match.
	 */
	public void removeExpectedPlayer(OfflinePlayer opl)
	{
		for (AutoRefTeam t : teams)
			t.getExpectedPlayers().remove(opl);
		expectedPlayers.remove(opl);
	}

	/**
	 * Teleports a player to a match they have been added to, joining the team inviting them.
	 */
	public void joinMatch(Player player)
	{
		// if already here, skip this
		if (this.isPlayer(player)) return;

		// if this player needs to be placed on a team, go for it
		AutoRefTeam team = this.expectedTeam(player);
		if (team != null) this.joinTeam(player, team, false);

		// otherwise, get them into the world
		else if (player.getWorld() != this.getWorld())
			player.teleport(this.getPlayerSpawn(player));

		// remove name from all lists
		this.removeExpectedPlayer(player);
	}

	/**
	 * Adds a player to the specified team. Removes the player from any other teams first,
	 * if necessary. Roster changes are restricted while a match is in progress, unless forced.
	 *
	 * @param player player to be added to team
	 * @param team team to add player to
	 * @param force should player be added to team, even if match is in progress
	 * @return true if player was added to team, otherwise false
	 */
	public boolean joinTeam(Player player, AutoRefTeam team, boolean force)
	{
		AutoRefTeam pteam = getPlayerTeam(player);
		if (team == pteam) return true;

		if (pteam != null) pteam.leave(player, force);
		return team.join(player, force);
	}

	/**
	 * Removes player from all teams.
	 *
	 * @param player player to be removed
	 * @param force should player be removed, even if match is in progress
	 */
	public void leaveTeam(Player player, boolean force)
	{ for (AutoRefTeam team : teams) team.leave(player, force); }

	public enum RespawnMode
	{ ALLOW, BEDSONLY, DISALLOW; }

	private RespawnMode respawnMode = RespawnMode.ALLOW;

	public RespawnMode getRespawnMode()
	{ return respawnMode; }

	public void setRespawnMode(RespawnMode mode)
	{ this.respawnMode = mode; }


	/**
	 * Eliminates player from the match.
	 */
	public void eliminatePlayer(Player player)
	{
		AutoRefTeam team = getPlayerTeam(player);
		if (team == null) return;

		String name = this.getDisplayName(player);
		if (!team.leaveQuietly(player)) return;

		this.broadcast(name + " has been eliminated!");
		this.ejectPlayer(player);
	}

	/**
	 * Gets AutoRefPlayer object associated with a given player.
	 *
	 * @param name player name
	 * @return matching AutoRefPlayer object, or null if no match
	 */
	public AutoRefPlayer getPlayer(String name)
	{
		AutoRefPlayer bapl = null;
		if (name != null)
		{
			int score, b = Integer.MAX_VALUE;
			for (AutoRefPlayer apl : getPlayers())
			{
				score = apl.nameSearch(name);
				if (score < b) { b = score; bapl = apl; }
			}
		}
		return bapl;
	}

	/**
	 * Gets AutoRefPlayer object associated with a given player.
	 *
	 * @return matching AutoRefPlayer object, or null if no match
	 */
	public AutoRefPlayer getPlayer(OfflinePlayer player)
	{ return player == null ? null : getPlayer(player.getName()); }

	/**
	 * Checks if the specified player is on a team
	 *
	 * @return true if player is on a team, otherwise false
	 */
	public boolean isPlayer(OfflinePlayer pl)
	{ return getPlayer(pl) != null; }

	/**
	 * Gets the player nearest to a specified location.
	 *
	 * @return player object for closest player, or null if no players
	 */
	public AutoRefPlayer getNearestPlayer(Location loc)
	{
		AutoRefPlayer apl = null;
		double distance = Double.POSITIVE_INFINITY;

		for (AutoRefPlayer a : getPlayers())
		{
			Player pl = a.getPlayer();
			if (pl == null) continue;

			double d = loc.distanceSquared(pl.getLocation());
			if (d < distance) { apl = a; distance = d; }
		}

		return apl;
	}

	/**
	 * Gets the team for a specified player.
	 *
	 * @return associated team object if one exists, otherwise null
	 */
	public AutoRefTeam getPlayerTeam(Player player)
	{
		for (AutoRefTeam team : teams)
			if (team.getPlayer(player) != null) return team;
		return null;
	}

	/**
	 * Gets colored player name for a specified player.
	 *
	 * @return colored player name
	 */
	public String getDisplayName(Player player)
	{
		AutoRefPlayer apl = getPlayer(player);
		return (apl == null) ? player.getName() : apl.getDisplayName();
	}

	/**
	 * Gets spawn location for the specified player, based on team.
	 *
	 * @return team-specific spawn location, or world spawn if not set
	 */
	public Location getPlayerSpawn(Player player)
	{
		AutoRefTeam team = getPlayerTeam(player);
		if (team != null) return team.getSpawnLocation();
		return primaryWorld.getSpawnLocation();
	}

	/**
	 * Checks if the specified location is inside a "safe" region. While inside a safe region,
	 * players will not attract mobs. Also, new mobs will never spawn within the safe zone.
	 *
	 * @return true if the location is within a safe zone, otherwise false
	 */
	public boolean isSafeZone(Location loc)
	{
		if (this.inStartRegion(loc)) return true;
		for (AutoRefTeam team : getTeams()) for (AutoRefRegion reg : team.getRegions())
			if (reg.contains(loc) && reg.isSafeZone()) return true;
		return false;
	}

	private class MatchReportSaver implements Runnable
	{
		private File localStorage = null;
		private String webDirectory = null;

		public boolean serveLocally()
		{ return webDirectory != null; }

		public MatchReportSaver()
		{
			String localDirectory = AutoReferee.getInstance().getConfig()
				.getString("local-storage.match-summary.directory", null);
			this.localStorage = localDirectory != null ? new File(localDirectory) :
				new File(AutoReferee.getInstance().getDataFolder(), "summary");

			if (!this.localStorage.exists())
				try { FileUtils.forceMkdir(this.localStorage); }
				catch (IOException e) { this.localStorage = null; }

			this.webDirectory = AutoReferee.getInstance().getConfig()
				.getString("local-storage.match-summary.web-directory", null);
		}

		public void run()
		{
			broadcastSync(ChatColor.RED + "Generating Match Summary...");
			String report = matchReportGenerator.generate(AutoRefMatch.this);

			String webstats = null;
			if (this.localStorage == null) webstats = uploadReport(report);
			{
				String localFileID = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss").format(new Date()) + ".html";
				File localReport = new File(this.localStorage, localFileID);

				try { FileUtils.writeStringToFile(localReport, report); }
				catch (IOException e) { e.printStackTrace(); }

				webstats = serveLocally() ? (webDirectory + localFileID) : uploadReport(report);
			}

			if (webstats == null) broadcastSync(ChatColor.RED + AutoReferee.NO_WEBSTATS_MESSAGE);
			else broadcastSync(ChatColor.RED + "Match Summary: " + ChatColor.RESET + webstats);
		}
	}

	private void logPlayerStats()
	{
		// upload WEBSTATS (do via an async query in case uploading the stats lags the main thread)
		Plugin plugin = AutoReferee.getInstance();
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new MatchReportSaver());
	}

	private String uploadReport(String report)
	{
		try
		{
			// submit our request to pastehtml, get back a link to the report
			return QueryServer.syncQuery("http://pastehtml.com/upload/create",
				"input_type=html&result=address", "txt=" + URLEncoder.encode(report, "UTF-8"));
		}
		catch (UnsupportedEncodingException e) {  }
		return null;
	}

	/**
	 * Gets distance player is from their team's nearest region.
	 *
	 * @return distance to team's nearest region
	 */
	public double distanceToClosestRegion(Player player)
	{
		AutoRefTeam team = getPlayerTeam(player);
		if (team != null) return team.distanceToClosestRegion(player.getLocation());
		return Double.MAX_VALUE;
	}

	/**
	 * Checks if a specified location is within the start region.
	 *
	 * @return true if location is inside start region, otherwise false
	 */
	public boolean inStartRegion(Location loc)
	{
		if (getStartRegions() != null) for (AutoRefRegion reg : getStartRegions())
			if (reg.distanceToRegion(loc) < ZoneListener.SNEAK_DISTANCE) return true;
		return false;
	}

	public void updateCarrying(AutoRefPlayer apl, Set<BlockData> oldCarrying, Set<BlockData> newCarrying)
	{
		Set<BlockData> add = Sets.newHashSet(newCarrying);
		add.removeAll(oldCarrying);

		Set<BlockData> rem = Sets.newHashSet(oldCarrying);
		rem.removeAll(newCarrying);

		Player player = apl.getPlayer();
		for (BlockData bd : add) messageReferees("player", player.getName(), "goal", "+" + bd.serialize());
		for (BlockData bd : rem) messageReferees("player", player.getName(), "goal", "-" + bd.serialize());
	}

	public void updateHealthArmor(AutoRefPlayer apl, int oldHealth,
			int oldArmor, int newHealth, int newArmor)
	{
		Player player = apl.getPlayer();

		if (oldHealth != newHealth) messageReferees("player", player.getName(),
			"hp", Integer.toString(newHealth));

		if (oldArmor != newArmor) messageReferees("player", player.getName(),
			"armor", Integer.toString(newArmor));
	}

	/**
	 * An event to be later reported in match statistics. Events are announced when they happen,
	 * and each type has its own visibility level to denote who will see the even happen live.
	 *
	 * @author authorblues
	 */
	public static class TranscriptEvent
	{
		public enum EventVisibility
		{ NONE, REFEREES, ALL }

		public enum EventType
		{
			// generic match start and end events
			MATCH_START("match-start", false, EventVisibility.NONE),
			MATCH_END("match-end", false, EventVisibility.NONE),

			// player messages (except kill streak) should be broadcast to players
			PLAYER_DEATH("player-death", true, EventVisibility.ALL),
			PLAYER_STREAK("player-killstreak", false, EventVisibility.NONE, ChatColor.DARK_GRAY),
			PLAYER_DOMINATE("player-dominate", true, EventVisibility.ALL, ChatColor.DARK_GRAY),
			PLAYER_REVENGE("player-revenge", true, EventVisibility.ALL, ChatColor.DARK_GRAY),

			// objective events should not be broadcast to players
			OBJECTIVE_FOUND("objective-found", true, EventVisibility.REFEREES),
			OBJECTIVE_PLACED("objective-place", true, EventVisibility.REFEREES);

			private String eventClass;
			private EventVisibility visibility;
			private ChatColor color;
			private boolean supportsFiltering;

			private EventType(String eventClass, boolean hasFilter, EventVisibility visibility)
			{ this(eventClass, hasFilter, visibility, null); }

			private EventType(String eventClass, boolean hasFilter,
				EventVisibility visibility, ChatColor color)
			{
				this.eventClass = eventClass;
				this.visibility = visibility;
				this.color = color;
				this.supportsFiltering = hasFilter;
			}

			public String getEventClass()
			{ return eventClass; }

			public String getEventName()
			{ return StringUtils.capitalize(name().toLowerCase().replaceAll("_", " ")); }

			public EventVisibility getVisibility()
			{ return visibility; }

			public ChatColor getColor()
			{ return color; }

			public boolean hasFilter()
			{ return supportsFiltering; }
		}

		public Object icon1;
		public Object icon2;

		private EventType type;

		public EventType getType()
		{ return type; }

		private String message;

		public String getMessage()
		{ return message; }

		private Location location;
		private long timestamp;

		public TranscriptEvent(AutoRefMatch match, EventType type, String message,
			Location loc, Object icon1, Object icon2)
		{
			this.type = type;
			this.message = message;

			// if no location is given, use the spawn location
			this.location = (loc != null) ? loc :
				match.getWorld().getSpawnLocation();

			this.icon1 = icon1;
			this.icon2 = icon2;

			this.timestamp = match.getMatchTime();
		}

		public String getTimestamp()
		{
			long t = getTimestampTick();
			return String.format("%02d:%02d:%02d",
				t/3600L, (t/60L)%60L, t%60L);
		}

		@Override
		public String toString()
		{ return String.format("[%s] %s", this.getTimestamp(), this.getMessage()); }

		public Location getLocation()
		{ return location; }

		public long getTimestampTick()
		{ return timestamp; }
	}

	/**
	 * Adds an event to the match transcript. Announces the event to the appropriate recipients.
	 *
	 * @param event event to be added to the transcript
	 */
	public void addEvent(TranscriptEvent event)
	{
		AutoReferee plugin = AutoReferee.getInstance();
		transcript.add(event);

		Collection<Player> recipients = null;
		switch (event.getType().getVisibility())
		{
			case REFEREES: recipients = getReferees(false); break;
			case ALL: recipients = getWorld().getPlayers(); break;
			default: break;
		}

		ChatColor clr = event.getType().getColor();
		String message = event.getMessage();

		if (clr == null) message = colorMessage(message);
		else message = (clr + message + ChatColor.RESET);

		if (recipients != null) for (Player player : recipients)
			player.sendMessage(message);

		if (plugin.isConsoleLoggingEnabled())
			plugin.getLogger().info(event.toString());
	}

	/**
	 * Gets the current match transcript up to this point in time.
	 *
	 * @return immutable copy of the match transcript
	 */
	public List<TranscriptEvent> getTranscript()
	{ return Collections.unmodifiableList(transcript); }

	/**
	 * Colors a message with team and objective colors. Prepares a message for broadcasting
	 * to the chat, and should be used as a pre-processing step whenever a message needs to
	 * be pretty-printed.
	 *
	 * @param message plain message
	 * @return colored message
	 */
	public String colorMessage(String message)
	{
		message = ChatColor.stripColor(message);
		for (AutoRefPlayer apl : getPlayers()) if (apl != null)
			message = message.replaceAll(apl.getName(), apl.getDisplayName());
		return ChatColor.RESET + message;
	}

	private ItemStack customMatchInfoBook = null;

	/**
	 * Sets a custom book given to players upon joining this match.
	 *
	 * @param book book given to players, or null for default (generated) book.
	 */
	public void setCustomMatchInfoBook(ItemStack book)
	{
		if (book != null) assert book.getType() == Material.WRITTEN_BOOK;
		this.customMatchInfoBook = book;
	}

	/**
	 * Gets the book given to players upon joining this match.
	 */
	public ItemStack getMatchInfoBook()
	{
		if (this.customMatchInfoBook != null)
			return this.customMatchInfoBook.clone();

		ItemStack book = new ItemStack(Material.WRITTEN_BOOK, 1);
		BookMeta meta = (BookMeta) book.getItemMeta();

		meta.setTitle(ChatColor.RED + "" + ChatColor.BOLD + this.getMapName());
		meta.setAuthor(ChatColor.DARK_GRAY + this.getMapAuthors());

		List<String> pages = Lists.newArrayList();

		// PAGE 1
		pages.add(BookUtil.makePage(
			BookUtil.center(ChatColor.BOLD + "[" + AutoReferee.getInstance().getName() + "]")
		,	BookUtil.center(ChatColor.DARK_GRAY + this.getMapName())
		,	BookUtil.center(" by " + ChatColor.DARK_GRAY + this.getMapAuthors())
		,	BookUtil.center("(v" + this.getMapVersion() + ")")
		,	""
		,	BookUtil.center(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "-- Teams --")
		,	BookUtil.center(this.getTeamList())
		,	""
		,	ChatColor.BOLD + "Pg2." + ChatColor.RESET + " About the Map"
		,	ChatColor.BOLD + "Pg3." + ChatColor.RESET + " About the Plugin"
		));

		// PAGE 2
		pages.add(BookUtil.makePage(
			BookUtil.center(ChatColor.BOLD + "[" + AutoReferee.getInstance().getName() + "]")
		,	BookUtil.center(ChatColor.DARK_GRAY + this.getMapName())
		,	BookUtil.center(" by " + ChatColor.DARK_GRAY + this.getMapAuthors())
		,	BookUtil.center("(v" + this.getMapVersion() + ")")
		,	""
		,	BookUtil.center("Coming soon...")
		));

		// PAGE 3
		pages.add(BookUtil.makePage(
			BookUtil.center(ChatColor.BOLD + "[" + AutoReferee.getInstance().getName() + "]")
		,	""
		,	ChatColor.BOLD + "/jointeam <team>"
		,	"  Join team"
		,	""
		,	ChatColor.BOLD + "/jointeam"
		,	"  Join random team"
		,	""
		,	ChatColor.BOLD + "/leaveteam"
		,	"  Leave current team"
		,	""
		,	ChatColor.BOLD + "/ready"
		,	"  Mark team as ready"
		));

		meta.setPages(pages);
		book.setItemMeta(meta);
		return book;
	}

	/**
	 * Send updated match information to a player.
	 */
	public void sendMatchInfo(CommandSender sender)
	{
		sender.sendMessage(ChatColor.RESET + "Map: " + ChatColor.GRAY + getMapName() +
			" v" + getMapVersion() + ChatColor.ITALIC + " by " + getMapAuthors());

		if (sender instanceof Player)
		{
			Player player = (Player) sender;
			AutoRefPlayer apl = getPlayer(player);
			String tmpflag = tmp ? "*" : "";

			if (apl != null) player.sendMessage("You are on team: " + apl.getTeam().getDisplayName());
			else if (isReferee(player)) player.sendMessage(ChatColor.GRAY + "You are a referee! " + tmpflag);
			else player.sendMessage("You are not on a team! Type " + ChatColor.GRAY + "/jointeam");
		}

		for (AutoRefTeam team : getSortedTeams())
			sender.sendMessage(String.format("%s (%d) - %s",
				team.getDisplayName(), team.getPlayers().size(), team.getPlayerList()));

		sender.sendMessage("Match status is currently " + ChatColor.GRAY + getCurrentState().name() +
			(this.getCurrentState().isBeforeMatch() ? (" [" + this.access.name() + "]") : ""));
		sender.sendMessage("Map difficulty is set to: " + ChatColor.GRAY + getWorld().getDifficulty().name());

		long timestamp = (getWorld().getFullTime() - getStartTicks()) / 20L;
		if (getCurrentState().inProgress())
			sender.sendMessage(String.format(ChatColor.GRAY + "The current match time is: %02d:%02d:%02d",
				timestamp/3600L, (timestamp/60L)%60L, timestamp%60L));
	}
}
