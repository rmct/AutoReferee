package org.mctourney.autoreferee;

import java.awt.image.RenderedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import com.google.common.collect.Iterables;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;

import org.bukkit.Bukkit;
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
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.material.Button;
import org.bukkit.material.Lever;
import org.bukkit.material.MaterialData;
import org.bukkit.material.PressurePlate;
import org.bukkit.material.PressureSensor;
import org.bukkit.material.Redstone;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import org.jdom2.Element;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import org.mctourney.autoreferee.event.match.MatchCompleteEvent;
import org.mctourney.autoreferee.event.match.MatchStartEvent;
import org.mctourney.autoreferee.event.match.MatchTranscriptEvent;
import org.mctourney.autoreferee.event.match.MatchUnloadEvent;
import org.mctourney.autoreferee.event.match.MatchUploadStatsEvent;
import org.mctourney.autoreferee.event.player.PlayerMatchJoinEvent;
import org.mctourney.autoreferee.event.player.PlayerMatchLeaveEvent;
import org.mctourney.autoreferee.event.player.PlayerTeamJoinEvent;
import org.mctourney.autoreferee.goals.AutoRefGoal;
import org.mctourney.autoreferee.goals.TimeGoal;
import org.mctourney.autoreferee.goals.scoreboard.AutoRefObjective;
import org.mctourney.autoreferee.listeners.SpectatorListener;
import org.mctourney.autoreferee.listeners.ZoneListener;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.regions.CuboidRegion;
import org.mctourney.autoreferee.util.ArmorPoints;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.BookUtil;
import org.mctourney.autoreferee.util.LocationUtil;
import org.mctourney.autoreferee.util.MapImageGenerator;
import org.mctourney.autoreferee.util.Metadatable;
import org.mctourney.autoreferee.util.PlayerKit;
import org.mctourney.autoreferee.util.PlayerUtil;
import org.mctourney.autoreferee.util.QueryUtil;
import org.mctourney.autoreferee.util.ReportGenerator;
import org.mctourney.autoreferee.util.SportBukkitUtil;
import org.mctourney.autoreferee.util.TeleportationUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Represents a game world controlled by AutoReferee.
 *
 * @author authorblues
 */
public class AutoRefMatch implements Metadatable
{
	// modify the internal NMS scoreboard instance with a custom scoreboard
	private static final boolean REPLACE_INTERNAL_SCOREBOARD = false;

	private static final String GENERIC_NOTIFICATION_MESSAGE =
		"A notification has been sent. Type /artp to teleport.";

	// online map list
	private static String MAPREPO = "http://autoreferee.s3.amazonaws.com/";

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

	// set this to false to not give match info books to players
	public static boolean giveMatchInfoBooks = true;

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

	public enum AccessType
	{ PRIVATE, PUBLIC; }

	public AccessType access = AccessType.PRIVATE;

	protected boolean currentlyTied = false;

	// world this match is taking place on
	private World primaryWorld;

	private AutoRefRegion worldSpawn = null;
	private AutoRefRegion specSpawn = null;

	private void setPrimaryWorld(World w)
	{
		primaryWorld = w;
		worldConfigFile = new File(w.getWorldFolder(), AutoReferee.CFG_FILENAME);
		setWorldSpawn(primaryWorld.getSpawnLocation());
	}

	public void setWorldSpawn(Location loc)
	{
		while (!TeleportationUtil.isBlockPassable(loc.getWorld().getBlockAt(loc))) loc = loc.add(0, 1, 0);
		worldSpawn = new org.mctourney.autoreferee.regions.PointRegion(loc);
		loc.getWorld().setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
	}

	public void setSpectatorSpawn(Location loc)
	{
		while (!TeleportationUtil.isBlockPassable(loc.getWorld().getBlockAt(loc))) loc = loc.add(0, 1, 0);
		specSpawn = new org.mctourney.autoreferee.regions.PointRegion(loc);
	}

	public boolean isPracticeMode()
	{
		if (!this.getCurrentState().inProgress()) return false;

		int existingteams = 0;
		for (AutoRefTeam team : this.getTeams())
			if (!team.isEmptyTeam()) ++existingteams;
		return existingteams < 2;
	}

	protected boolean previewMode = false;

	public void setPreviewMode(boolean b)
	{ this.previewMode = b; }

	public boolean isPreviewMode()
	{ return this.previewMode; }

	/**
	 * Gets the world associated with this match.
	 *
	 * @return world
	 */
	public World getWorld()
	{ return primaryWorld; }

	@Override public int hashCode()
	{ return getWorld().hashCode(); }

	@Override public String toString()
	{
		return String.format("%s[%s, w=%s]", this.getClass().getSimpleName(),
			this.mapName, this.getWorld().getName());
	}

	/**
	 * Gets the global spawn location for this match.
	 *
	 * @return global spawn location
	 */
	public Location getWorldSpawn()
	{ return worldSpawn.getLocation(); }

	/**
	 * Gets the global spawn location for this match.
	 *
	 * @return global spawn location
	 */
	public Location getSpectatorSpawn()
	{ return specSpawn != null ? specSpawn.getLocation() : getWorldSpawn(); }

	private boolean tmp;

	public AutoRefMatch temporary()
	{ this.tmp = true; return this; }

	public boolean isTemporaryWorld()
	{ return tmp; }

	private long startClock = 0L;
	protected boolean lockTime = false;

	/**
	 * Gets the time to set the world to at the start of the match.
	 *
	 * @return world time in ticks to be set at start of the match
	 */
	public long getStartClock()
	{ return startClock; }

	/**
	 * Sets the time that will be set at the start of the match.
	 *
	 * @param time world time in ticks to set at start of the match
	 */
	public void setStartClock(long time)
	{ this.startClock = time; }

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
		WAITING(5*60*1000L),

		/**
		 * Players joined, waiting for match start.
		 */
		READY(5*60*1000L),

		/**
		 * Match in progress.
		 */
		PLAYING(30*60*1000L),

		/**
		 * Match completed.
		 */
		COMPLETED(5*60*1000L);

		public long inactiveMillis;

		MatchStatus()
		{ this(Long.MAX_VALUE); }

		MatchStatus(long ms)
		{ this.inactiveMillis = ms; }

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

	// custom scoreboard
	protected final Scoreboard scoreboard;
	protected final Scoreboard  infoboard;
	protected Objective infoboardObjective;

	public Scoreboard getScoreboard()
	{ return scoreboard; }

	Scoreboard getInfoboard()
	{ return infoboard; }

	Objective getInfoboardObjective()
	{ return infoboardObjective; }

	// teams participating in the match
	protected Set<AutoRefTeam> teams;

	/**
	 * Gets the teams participating in this match.
	 *
	 * @return set of teams
	 */
	public Set<AutoRefTeam> getTeams()
	{ return teams; }

	public String getTeamList()
	{
		Set<String> tlist = Sets.newHashSet();
		for (AutoRefTeam team : getTeams())
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

	protected Map<String, PlayerKit> kits;

	public PlayerKit getKit(String name)
	{ return kits.get(name); }

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

	private Set<AutoRefRegion.Flag> startRegionFlags = Sets.newHashSet
	(	AutoRefRegion.Flag.NO_BUILD
	,	AutoRefRegion.Flag.SAFE
	,	AutoRefRegion.Flag.NO_EXPLOSIONS
	);

	public Set<AutoRefRegion.Flag> getStartRegionFlags()
	{ return Collections.unmodifiableSet(startRegionFlags); }

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
	protected File worldConfigFile;
	protected Element worldConfig;
	private boolean saveConfig = true;

	// basic variables loaded from file
	protected String mapName = null;
	protected Collection<String> mapAuthors = null;

	/**
	 * Gets the name of the map for this match.
	 *
	 * @return map name
	 */
	public String getMapName()
	{ return mapName; }

	protected String versionString = "1.0";

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

	public AutoRefMap getMap()
	{ return AutoRefMap.getMap(mapName); }

	/**
	 * Gets the creators of the map for this match.
	 *
	 * @return collection of names
	 */
	public Collection<String> getMapAuthors()
	{ return mapAuthors; }

	/**
	 * Gets the creators of the map for this match.
	 *
	 * @return string list of names
	 */
	public String getAuthorList()
	{
		if (mapAuthors != null && mapAuthors.size() != 0)
			return StringUtils.join(mapAuthors, ", ");
		return "??";
	}

	private long startTime = 0;

	public long getStartTime()
	{ return startTime; }

	public void setStartTime(long time)
	{ this.startTime = time; }

	/**
	 * Gets the number of seconds elapsed in this match.
	 *
	 * @return current elapsed seconds if match in progress, otherwise 0L
	 */
	public long getElapsedSeconds()
	{
		if (!getCurrentState().inProgress()) return 0L;
		return (ManagementFactory.getRuntimeMXBean().getUptime() - getStartTime()) / 1000L;
	}

	private long timeLimit = 0L;

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
	{ return timeLimit > 0L; }

	/**
	 * Gets the number of seconds remaining in this match.
	 *
	 * @return time remaining in seconds
	 */
	public long getTimeRemaining()
	{ return timeLimit - getElapsedSeconds(); }

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
		long timestamp = this.getElapsedSeconds();
		return String.format("%02d%s%02d%s%02d", timestamp/3600L,
			sep, (timestamp/60L)%60L, sep, timestamp%60L);
	}

	// task that starts the match
	protected CountdownTask matchStarter = null;

	// mechanisms to open the starting gates
	protected Set<StartMechanism> startMechanisms;

	// protected entities - only protected from "butchering"
	private Set<UUID> protectedEntities;

	public boolean isProtected(UUID uuid)
	{ return protectedEntities.contains(uuid); }

	public void protect(UUID uuid)
	{ protectedEntities.add(uuid); }

	public void unprotect(UUID uuid)
	{ protectedEntities.remove(uuid); }

	public void toggleProtection(UUID uuid)
	{ if (isProtected(uuid)) unprotect(uuid); else protect(uuid); }

	protected boolean playersBecomeSpectators = true;

	protected boolean allowFriendlyFire = true;

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
	protected static boolean allowTies = false;

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
	protected Set<BlockData> prohibitCraft = Sets.newHashSet();

	// range of inexact placement
	protected int inexactRange = 2;

	/**
	 * Gets the distance an objective may be placed from its target location.
	 *
	 * @return range of inexact objective placement
	 */
	public int getInexactRange()
	{ return inexactRange; }

	// transcript of every event in the match
	protected List<TranscriptEvent> transcript;

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

	public RenderedImage getMapImage() throws IOException
	{
		CuboidRegion cube = getMapCuboid();
		if (cube == null) throw new IOException("No start regions defined.");

		Location min = cube.getMinimumPoint(),
			max = cube.getMaximumPoint();

		return MapImageGenerator.generateFromWorld(getWorld(),
			min.getBlockX(), max.getBlockX(), min.getBlockZ(), max.getBlockZ());
	}

	protected GameMode gamemode;

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

	public class BedUpdateTask extends BukkitRunnable
	{
		private Map<AutoRefPlayer, Boolean> hasBed = Maps.newHashMap();
		private String breakerName, breakAction = "broken";
		private AutoRefPlayer breaker;

		public BedUpdateTask(AutoRefPlayer breaker)
		{ this(breaker.getDisplayName()); this.breaker = breaker; }

		public BedUpdateTask(Entity ent)
		{
			AutoReferee plugin = AutoReferee.getInstance();
			switch (ent.getType())
			{
				case CREEPER: breakerName = "Creeper"; break;
				case LIGHTNING: breakerName = "Lightning"; break;
				case WITHER_SKULL: breakerName = "Wither Skull"; break;
				case WITHER: breakerName = "Wither"; break;
				case ENDER_CRYSTAL: breakerName = "Ender Crystal"; break;
				case ENDER_DRAGON: breakerName = "Ender Dragon"; break;

				case FIREBALL:
				case SMALL_FIREBALL:
					breakerName = "Fireball"; break;

				case PRIMED_TNT:
					AutoRefPlayer tntOwner = plugin.getTNTOwner(ent);
					if (tntOwner == null) breakerName = "TNT";
					else breakerName = String.format("%s's TNT", tntOwner.getDisplayName());
					break;
			}

			for (AutoRefPlayer apl : getPlayers())
				hasBed.put(apl, apl.hasBed());
			breakAction = "blown up";
		}

		public BedUpdateTask(String breakerName)
		{
			this.breakerName = breakerName;
			for (AutoRefPlayer apl : getPlayers())
				hasBed.put(apl, apl.hasBed());
			breakAction = "broken";
		}

		public void run()
		{
			Set<AutoRefPlayer> lostBed = Sets.newHashSet();
			String bedBreakNotification;

			for (AutoRefPlayer apl : getPlayers())
				if (hasBed.get(apl) != apl.hasBed()) lostBed.add(apl);

			// if no one's bed changed, quit here
			if (lostBed.isEmpty()) return;

			// don't print or do anything if the bed's owner breaks it himself
			if (breaker != null && lostBed.contains(breaker)) return;

			if (lostBed.size() == 1)
				bedBreakNotification = String.format("%s's bed has been %s by %s.",
					((AutoRefPlayer) lostBed.toArray()[0]).getDisplayName(), breakAction, breakerName);
			else
			{
				// get the team that owns this bed (null if owned by more than one team)
				AutoRefTeam teamOwner = ((AutoRefPlayer) lostBed.toArray()[0]).getTeam();
				for (AutoRefPlayer apl : lostBed) if (apl.getTeam() != teamOwner) teamOwner = null;

				bedBreakNotification = teamOwner != null
					? String.format("%s's bed has been %s by %s.", teamOwner.getDisplayName(), breakAction, breakerName)
					: String.format("%s has %s a bed.", breakerName, breakAction);
			}

			for (Player ref : getReferees(false))
				ref.sendMessage(bedBreakNotification);
		}
	}

	private class PlayerCountTask extends BukkitRunnable
	{
		private long lastOccupiedTime = 0;

		public PlayerCountTask()
		{ lastOccupiedTime = ManagementFactory.getRuntimeMXBean().getUptime(); }

		public void run()
		{
			long tick = ManagementFactory.getRuntimeMXBean().getUptime();

			// if there are people in this world/match, reset last-occupied
			if (getUserCount() != 0) lastOccupiedTime = tick;

			// if this world has been inactive for long enough, just unload it
			if (tick - lastOccupiedTime >= getCurrentState().inactiveMillis)
				destroy(MatchUnloadEvent.Reason.EMPTY);
		}
	}

	PlayerCountTask countTask = null;

	public AutoRefMatch(World world, boolean tmp, MatchStatus state)
	{ this(world, tmp); setCurrentState(state); }

	public AutoRefMatch(World world, boolean tmp)
	{
		setPrimaryWorld(world);
		world.setKeepSpawnInMemory(true);

		// is this world a temporary world?
		this.tmp = tmp;

		// should eliminated players become spectators?
		this.playersBecomeSpectators = AutoReferee.getInstance().getConfig()
			.getBoolean("players-become-spectators", true);

		// setup custom scoreboard
		scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
		 infoboard = Bukkit.getScoreboardManager().getNewScoreboard();

		if (AutoRefMatch.REPLACE_INTERNAL_SCOREBOARD) try
		{
			Method wHandle = world.getClass().getDeclaredMethod("getHandle");
			Object nmsWorld = wHandle.invoke(world);

			Method sHandle = scoreboard.getClass().getDeclaredMethod("getHandle");
			Object nmsScoreboard = sHandle.invoke(scoreboard);

			Field fScoreboard = nmsWorld.getClass().getField("scoreboard");
			fScoreboard.setAccessible(true);
			fScoreboard.set(nmsWorld, nmsScoreboard);
		}
		catch (Exception e)
		{
			AutoReferee.log("A problem occured whilst modifying NMS scoreboard internal values.");
			AutoReferee.log("Are you sure you are using a CraftBukkit variant?");
			AutoReferee.log("Please file a bug report, as this is a somewhat serious error.");
			e.printStackTrace();
		}

		loadWorldConfiguration();

		messageReferees("match", getWorld().getName(), "init");
		setCurrentState(MatchStatus.WAITING);

		// restore competitive settings and some default values
		primaryWorld.setPVP(true);
		primaryWorld.setSpawnFlags(true, true);

		primaryWorld.setTicksPerAnimalSpawns(-1);
		primaryWorld.setTicksPerMonsterSpawns(-1);

		// last, send an update about the match to everyone logged in
		for (Player pl : primaryWorld.getPlayers()) sendMatchInfo(pl);

		// brand new match transcript
		transcript = Lists.newLinkedList();

		// fix vanish
		this.setupSpectators();

		// setup player count task (after assigning the world)
		countTask = new PlayerCountTask();

		// startup the player count timer (for automatic unloading)
		countTask.runTaskTimer(AutoReferee.getInstance(), 5L, 60*20L);
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

	public Set<AutoRefPlayer> getCachedPlayers()
	{
		Set<AutoRefPlayer> players = Sets.newHashSet();
		for (AutoRefTeam team : teams)
			players.addAll(team.getCachedPlayers());
		return players;
	}

	/**
	 * Gets all match spectators (spectators, referees, and streamers).
	 *
	 * @return collection of spectators
	 */
	public Set<Player> getSpectators()
	{
		Set<Player> specs = Sets.newHashSet();
		for (Player p : primaryWorld.getPlayers())
			if (!isPlayer(p)) specs.add(p);
		return specs;
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
		if (isPlayer(player) || getExpectedPlayers().contains(player.getName())) return false;
		return player.hasPermission("autoreferee.referee");
	}

	/**
	 * Checks if the specified player is a streamer for this match.
	 *
	 * @return true if player is a streamer and not on a team, otherwise false
	 */
	public boolean isStreamer(Player player)
	{
		if (isPlayer(player) || getExpectedPlayers().contains(player.getName())) return false;
		return isSpectator(player) && getSpectator(player).isStreamer();
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

	Map<String, AutoRefSpectator> spectators = Maps.newHashMap();

	public AutoRefSpectator getSpectator(Player player)
	{
		if (!isSpectator(player)) return null;
		String name = player.getName();

		AutoRefSpectator spectator = this.spectators.get(name);
		if (spectator == null) this.spectators.put(name,
			spectator = new AutoRefSpectator(name, this));
		return spectator;
	}

	public enum Role
	{
		// this list provides an ordering of roles. do not permute.
		NONE, PLAYER, SPECTATOR, STREAMER, REFEREE;

		public int getRank()
		{ return this.ordinal(); }

		public boolean atLeast(Role other)
		{ return getRank() >= other.getRank(); }
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

	protected void loadWorldConfiguration()
	{
		try
		{
			// file stream and configuration object (located in world folder)
			File f = worldConfigFile;
			loadWorldConfiguration(f.exists() ? new FileInputStream(f)
				: AutoReferee.getInstance().getResource("defaults/map.xml"));
		}
		catch (FileNotFoundException e) {  }
	}

	protected void clearScoreboardData(Scoreboard sb)
	{
		// unregister all old objectives (created by AutoReferee)
		for (Objective obj : scoreboard.getObjectives())
			if (obj.getName().startsWith("ar#")) obj.unregister();

		// unregister all old teams (created by AutoReferee)
		for (Team team : scoreboard.getTeams())
			if (team.getName().startsWith("ar#")) team.unregister();
	}

	protected void loadScoreboardData()
	{
		clearScoreboardData(scoreboard);
		clearScoreboardData( infoboard);

		// register our custom objective for the sideboard
		long randx = System.currentTimeMillis() % (1L << 16);
		infoboardObjective = infoboard.registerNewObjective(
			String.format("ar#scores_%x", randx), "dummy");

		try
		{
			File dataFolder = new File(primaryWorld.getWorldFolder(), "data");
			File scoreboardFile = new File(dataFolder, "scoreboard.xml");
			Element sbroot = new SAXBuilder().build(scoreboardFile).getRootElement();

			for (Element teamnode : sbroot.getChild("teams").getChildren("team"))
			{
				Team team = scoreboard.registerNewTeam(teamnode.getAttributeValue("name"));
				team.setPrefix(teamnode.getAttributeValue("prefix"));
				team.setSuffix(teamnode.getAttributeValue("suffix"));
			}

			for (Element objroot : sbroot.getChild("objectives").getChildren("objective"))
			{
				Objective obj = scoreboard.registerNewObjective(
					objroot.getAttributeValue("name"),
					objroot.getAttributeValue("criteria"));

				if (objroot.getAttributeValue("display") != null)
					obj.setDisplaySlot(DisplaySlot.valueOf(objroot.getAttributeValue("display")));
			}

			AutoReferee.log("Loaded custom scoreboard data.");
		}
		catch (Exception e)
		{  }
	}

	public void saveScoreboardData()
	{ saveScoreboardData(scoreboard); }

	public void saveScoreboardData(Scoreboard sb)
	{
		Element teams = new Element("teams");
		for (Team team : sb.getTeams())
		{
			Element teamnode = new Element("team");
			teamnode.setAttribute("name", team.getName());
			teamnode.setAttribute("prefix", team.getPrefix());
			teamnode.setAttribute("suffix", team.getSuffix());
			teams.addContent(teamnode);
		}

		teams.sortChildren(new Comparator<Element>()
		{
			@Override
			public int compare(Element a, Element b)
			{
				String aname = a.getAttributeValue("name");
				String bname = b.getAttributeValue("name");
				return aname.compareToIgnoreCase(bname);
			}
		});

		Element objectives = new Element("objectives");
		for (Objective objective : sb.getObjectives())
		{
			Element objnode = new Element("objective");
			objnode.setAttribute("name", objective.getName());
			objnode.setAttribute("criteria", objective.getCriteria());
			if (objective.getDisplaySlot() != null)
				objnode.setAttribute("display", objective.getDisplaySlot().name());
			objectives.addContent(objnode);
		}

		objectives.sortChildren(new Comparator<Element>()
		{
			@Override
			public int compare(Element a, Element b)
			{
				String aname = a.getAttributeValue("name");
				String bname = b.getAttributeValue("name");
				return aname.compareToIgnoreCase(bname);
			}
		});

		Element sbroot = new Element("scoreboard");
		sbroot.addContent(teams);
		sbroot.addContent(objectives);

		try
		{
			XMLOutputter xmlout = new XMLOutputter(Format.getPrettyFormat());
			File dataFolder = new File(primaryWorld.getWorldFolder(), "data");
			xmlout.output(sbroot, new FileOutputStream(new File(dataFolder, "scoreboard.xml")));
		}
		catch (java.io.IOException e)
		{ AutoReferee.log("Could not save scoreboard data: " + primaryWorld.getName()); }
	}

	protected void loadWorldConfiguration(InputStream cfg)
	{
		try
		{
			// until told otherwise, assume that what we have should not be
			// saved (to prevent a bad config from being destroyed)
			saveConfig = false;

			// build configuration file from
			worldConfig = new SAXBuilder().build(cfg).getRootElement();

			// turn on saving functionality if we loaded a configuration properly
			assert "map".equals(worldConfig.getName());
			saveConfig = true;
		}
		catch (JDOMParseException e)
		{
			AutoReferee.log(String.format(">> With configuration file: %s [%s]",
				worldConfigFile.getPath(), getWorld().getName()), Level.SEVERE);
			AutoReferee.log(e.getLocalizedMessage(), Level.SEVERE);

			// maybe try to salvage the partially parsed document?
			worldConfig = e.getPartialDocument().getRootElement();
			assert "map".equals(worldConfig.getName());
		}
		catch (Exception e) { e.printStackTrace(); return; }

		loadScoreboardData();

		this.gamemode = GameMode.SURVIVAL;

		// get the extra settings cached
		Element meta = worldConfig.getChild("meta");
		if (meta != null)
		{
			mapName = meta.getChildText("name");
			infoboardObjective.setDisplayName(ChatColor.BOLD + mapName);
			versionString = meta.getChildText("version");

			mapAuthors = Lists.newLinkedList();
			for (Element e : meta.getChild("creators").getChildren("creator"))
				mapAuthors.add(e.getText());
		}

		// set the time limit based on the server config
		long limit_min = AutoReferee.getInstance().getConfig().getLong("time-limit", 0L);
		this.setTimeLimit(60L * limit_min);

		Element kitsElt = worldConfig.getChild("kits");
		kits = Maps.newHashMap();

		// parse kits before parsing teams
		if (kitsElt != null) for (Element kitElt : kitsElt.getChildren("kit"))
		{
			PlayerKit kit = new PlayerKit(kitElt);
			kits.put(kit.getName(), kit);
		}

		teams = Sets.newHashSet();
		for (Element e : worldConfig.getChild("teams").getChildren("team"))
			teams.add(AutoRefTeam.fromElement(e, this));

		Element eProtect = worldConfig.getChild("protect");
		protectedEntities = Sets.newHashSet();

		if (eProtect != null) for (Element c : eProtect.getChildren())
			try { protectedEntities.add(UUID.fromString(c.getTextTrim())); }
			catch (Exception e) {  }

		// get the start region (safe for both teams, no pvp allowed)
		assert worldConfig.getChild("startregion") != null;
		for (Element e : worldConfig.getChild("startregion").getChildren())
			addStartRegion(AutoRefRegion.fromElement(this, e));

		String attrSpawn = worldConfig.getChild("startregion").getAttributeValue("spawn");
		if (attrSpawn != null) setWorldSpawn(LocationUtil.fromCoords(getWorld(), attrSpawn));

		String attrSpecSpawn = worldConfig.getChild("startregion").getAttributeValue("spec");
		if (attrSpecSpawn != null) setSpectatorSpawn(LocationUtil.fromCoords(getWorld(), attrSpecSpawn));

		Element gameplay = worldConfig.getChild("gameplay");
		if (gameplay != null) this.parseExtraGameRules(gameplay);

		Element regElt = worldConfig.getChild("regions");
		regions = Sets.newHashSet();

		for (Element reg : regElt.getChildren())
			if (!this.addRegion(AutoRefRegion.fromElement(this, reg)))
				AutoReferee.log("Region did not load correctly: " + reg.getName(), java.util.logging.Level.SEVERE);

		Element goals = worldConfig.getChild("goals");
		if (goals != null) for (Element teamgoals : goals.getChildren("teamgoals"))
		{
			AutoRefTeam team = this.getTeam(teamgoals.getAttributeValue("team"));
			if (team != null) for (Element gelt : teamgoals.getChildren()) team.addGoal(gelt);
		}

		Element mechanisms = worldConfig.getChild("mechanisms");
		startMechanisms = Sets.newHashSet();

		if (mechanisms != null) for (Element mech : mechanisms.getChildren())
		{
			boolean state = Boolean.parseBoolean(mech.getText());
			Location mechloc = LocationUtil.fromCoords(getWorld(), mech.getAttributeValue("pos"));
			this.toggleStartMech(getWorld().getBlockAt(mechloc), state);
		}

		// setup scoreboard for the teams (on next server tick)
		setupScoreboardObjectives();
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
		{
			startClock = AutoRefMatch.parseTimeString(gameplay.getChildText("clockstart"));
			lockTime = gameplay.getChild("clockstart").getAttributeValue("lock") != null;

			// @since 1.6.1, "doDaylightCycle=false" locks time
			if (primaryWorld.isGameRule("doDaylightCycle"))
			{
				// set the gamerule to lock the time (or don't lock the time, see if I care!)
				primaryWorld.setGameRuleValue("doDaylightCycle", "" + !lockTime);

				// disable here to prevent the version based on setting the time
				lockTime = false;
			}
		}

		// allow or disallow friendly fire
		if (gameplay.getChild("friendlyfire") != null)
			setFriendlyFire(Boolean.parseBoolean(gameplay.getChildText("friendlyfire")));

		// attempt to set world difficulty as best as possible
		Difficulty diff = Difficulty.HARD;
		if (gameplay.getChild("difficulty") != null)
			diff = getDifficulty(gameplay.getChildText("difficulty"));
		primaryWorld.setDifficulty(diff);

		if (gameplay.getChild("maxtime") != null)
			this.setTimeLimit(TimeGoal.parseTime(gameplay.getChildText("maxtime")));

		// respawn mode
		if (gameplay.getChild("respawn") != null)
		{
			String rtext = gameplay.getChildTextTrim("respawn");
			RespawnMode rmode = null;

			if (rtext != null && !rtext.isEmpty())
				rmode = RespawnMode.valueOf(rtext.toUpperCase());
			setRespawnMode(rmode == null ? RespawnMode.ALLOW : rmode);
		}

		if (gameplay.getChild("nocraft") != null)
		{
			for (Element item : gameplay.getChild("nocraft").getChildren("item"))
				this.addIllegalCraft(BlockData.unserialize(item.getAttributeValue("id")));
		}

		if (gameplay.getChild("gamemode") != null)
		{
			String gm = gameplay.getChildTextNormalize("gamemode");
			this.gamemode = GameMode.valueOf(gm.toUpperCase());

			try { this.gamemode = GameMode.getByValue(Integer.parseInt(gm)); }
			catch (NumberFormatException e) {  }

			if (this.gamemode == null)
				this.gamemode = GameMode.SURVIVAL;
		}
	}

	private void setupScoreboardObjectives()
	{
		// defer to prevent exception on server start,
		// before any worlds are fully loaded
		new BukkitRunnable()
		{
			@Override
			public void run()
			{
				// setup the objectives for each team
				for (AutoRefTeam team : getTeams())
				{
					team.scoreboardObjectives = AutoRefObjective.fromTeam(infoboardObjective, team);
					if (!team.scoreboardObjectives.isEmpty())
						infoboardObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
				}
			}
		// run this task on the next server tick
		}.runTask(AutoReferee.getInstance());
	}

	/**
	 * Saves copy of autoreferee.xml back to the world folder.
	 */
	public void saveWorldConfiguration()
	{
		// if for some reason we have disabled the saveConfig flag,
		// just do nothing. more than likely, trying to save will do more
		// harm than good, so best to just skip this entirely
		if (!saveConfig) return;

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

			if (specSpawn != null) eStartRegions.setAttribute("spec",
				LocationUtil.toBlockCoordsWithYaw(specSpawn.getCenter()));

			eStartRegions.removeContent();
			for (AutoRefRegion reg : this.getStartRegions())
				eStartRegions.addContent(reg.toElement());

			// get the protections object
			Element eProtect = worldConfig.getChild("protect");
			if (eProtect == null) worldConfig.addContent(eProtect = new Element("protect"));

			// reset the protections to whatever has been saved
			eProtect.removeContent();
			for (UUID uid : protectedEntities)
				eProtect.addContent(new Element("entity").setText(uid.toString()));

			// get the goals object
			Element eGoals = worldConfig.getChild("goals");
			if (eGoals == null) worldConfig.addContent(eGoals = new Element("goals"));

			// reset the goals to whatever has been saved
			eGoals.removeContent();
			for (AutoRefTeam team : this.getTeams())
			{
				Element tgoals = new Element("teamgoals")
					.setAttribute("team", team.getDefaultName());
				eGoals.addContent(tgoals);
				for (AutoRefGoal goal : team.getTeamGoals())
					tgoals.addContent(goal.toElement());
			}

			// get the mechanisms object
			Element eMechanisms = worldConfig.getChild("mechanisms");
			if (eMechanisms == null) worldConfig.addContent(eMechanisms = new Element("mechanisms"));

			// reset the mechanisms to whatever has been saved
			eMechanisms.removeContent();
			for (StartMechanism mech : this.startMechanisms)
				eMechanisms.addContent(mech.toElement());

			Element eGameplay = worldConfig.getChild("gameplay");
			if (eGameplay == null) worldConfig.addContent(eGameplay = new Element("gameplay"));

			if (this.prohibitCraft.size() > 0)
			{
				Element eNoCraft = eGameplay.getChild("nocraft");
				if (eNoCraft == null) eGameplay.addContent(eNoCraft = new Element("nocraft"));

				eNoCraft.removeContent();
				for (BlockData bd : prohibitCraft)
				{
					Element nocraft = new Element("item").setText(bd.getName());
					eNoCraft.addContent(nocraft.setAttribute("id", bd.serialize()));
				}
			}
		}

		// save the configuration file back to the original filename
		try
		{
			XMLOutputter xmlout = new XMLOutputter(Format.getPrettyFormat());
			xmlout.output(worldConfig, new FileOutputStream(worldConfigFile));
		}

		// log errors, report which world did not save
		catch (java.io.IOException e)
		{ AutoReferee.log("Could not save world config: " + primaryWorld.getName()); }
	}

	/**
	 * Sends a referee plugin channel message to all referees, properly delimited.
	 */
	public void messageReferees(String ...parts)
	{
		for (Player ref : getReferees(false)) messageReferee(ref, parts);
	}

	/**
	 * Sends a referee plugin channel message to a specific referee, properly delimited.
	 *
	 * @param ref referee to recieve the plugin channel message
	 */
	public static void messageReferee(Player ref, String ...parts)
	{
		try
		{
			String msg = StringUtils.join(parts, SpectatorListener.DELIMITER);
			ref.sendPluginMessage(AutoReferee.getInstance(), AutoReferee.REFEREE_PLUGIN_CHANNEL,
				msg.getBytes(AutoReferee.PLUGIN_CHANNEL_ENC));
		}
		catch (UnsupportedEncodingException e)
		{ AutoReferee.log("Unsupported encoding: " + AutoReferee.PLUGIN_CHANNEL_ENC); }
	}

	/**
	 * Sends all information to a single referee necessary to sync a match's current status.
	 *
	 * @param ref referee to receive the plugin channel messages
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

	private class ItemElevatorDetectionTask extends BukkitRunnable
	{
		private static final long INTERVAL = 5L;
		private static final double DISTANCE_THRESHOLD = 1.8;
		private static final double YDELTA_THRESHOLD = 0.8;

		private Map<UUID, Location> itemLocations = Maps.newHashMap();
		private Map<UUID, Location> lastStoppedLocation = Maps.newHashMap();

		@Override public void run()
		{
			items: for (Entity e : getWorld().getEntitiesByClasses(Item.class))
			{
				Item item = (Item) e;
				UUID uuid = item.getUniqueId();

				Location prev = itemLocations.get(uuid);
				Location curr = e.getLocation();
				Location stop = lastStoppedLocation.get(uuid);

				if (prev == null) continue items;
				boolean pass = TeleportationUtil.isBlockPassable(curr.getBlock());

				// if the item is moving upwards and is currently in a passable
				double ydelta = curr.getY() - prev.getY();
				if (ydelta > YDELTA_THRESHOLD && !pass && !elevatedItem.containsKey(uuid))
					elevatedItem.put(uuid, false);

				double dy = stop == null ? 0.0 : curr.getY() - stop.getY();
				if (elevatedItem.containsKey(uuid) && dy >= DISTANCE_THRESHOLD)
					elevatedItem.put(uuid, true);

				if (ydelta < 0.001)
				{
					// record the last location it was stopped at
					lastStoppedLocation.put(uuid, curr);

					boolean atrest = !TeleportationUtil.isBlockPassable(curr.getBlock().getRelative(0, -1, 0));
					if (elevatedItem.containsKey(uuid) && elevatedItem.get(uuid) && atrest)
					{
						// if the item didn't elevate high enough, don't worry about it
						if (dy < DISTANCE_THRESHOLD) { elevatedItem.remove(uuid); continue items; }
						setLastNotificationLocation(curr);

						String coords = LocationUtil.toBlockCoords(curr);
						String msg = ChatColor.DARK_GRAY + String.format(
							"Possible Item Elevator @ (%s) [y%+d] %s", coords, Math.round(dy),
							new BlockData(item.getItemStack()).getDisplayName());

						for (Player ref : getReferees()) ref.sendMessage(msg);
						AutoReferee.log(msg);
					}
				}
			}

			itemLocations.clear();
			for (Entity e : getWorld().getEntitiesByClasses(Item.class))
				itemLocations.put(e.getUniqueId(), e.getLocation());
		}
	}

	public Map<UUID, Boolean> elevatedItem = Maps.newHashMap();
	protected ItemElevatorDetectionTask itemElevatorDetectionTask = null;

	/**
	 * Sends a message to all players in this match, including referees and streamers.
	 *
	 * @param msgs messages to be sent
	 */
	public void broadcast(String ...msgs)
	{
		for (String msg : msgs)
		{
			if (AutoReferee.getInstance().isConsoleLoggingEnabled())
				AutoReferee.log(ChatColor.stripColor(msg));
			for (Player p : primaryWorld.getPlayers()) p.sendMessage(msg);
		}
	}

	private SyncBroadcastTask broadcastTask = new SyncBroadcastTask();

	/**
	 * Force a broadcast to be sent synchronously. Safe to use from an asynchronous task.
	 *
	 * @param msgs messages to be sent
	 */
	public void broadcastSync(String ...msgs)
	{
		for (String msg : msgs)
			broadcastTask.addMessage(msg);

		try { broadcastTask.runTask(AutoReferee.getInstance()); }
		catch (IllegalStateException e) {  }
	}

	private class SyncBroadcastTask extends BukkitRunnable
	{
		private List<String> msgQueue = Lists.newLinkedList();

		public SyncBroadcastTask addMessage(String message)
		{ msgQueue.add(message); return this; }

		@Override public void run()
		{
			AutoRefMatch.this.broadcastTask = new SyncBroadcastTask();
			AutoRefMatch.this.broadcast(msgQueue.toArray(new String[]{ }));
			msgQueue.clear();
		}
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

	private static final File PACKAGING_DIRECTORY = FileUtils.getTempDirectory();

	private static class FilenameSetFilter implements FilenameFilter
	{
		private final Set<String> names;

		public FilenameSetFilter(final Set<String> names)
		{ this.names = names; }

		@Override public boolean accept(File dir, String filename)
		{ return names.contains(filename); }
	}

	private static final IOFileFilter DATA_FOLDER_FILTER =
		FileFilterUtils.asFileFilter(new FilenameSetFilter(Sets.newHashSet
		(   "scoreboard.dat"
		,	"scoreboard.xml"
		)));

	/**
	 * Archives this map and stores a clean copy in the map library. Clears unnecessary
	 * files and attempts to generate a minimal copy of the map, ready for distribution.
	 *
	 * @return root folder of the archived map
	 * @throws IOException if archive cannot be created
	 */
	private File archiveMapData() throws IOException
	{
		this.clearEntities();
		primaryWorld.setTime(this.getStartClock());

		// save the world and configuration first, then archive
		primaryWorld.save();
		this.saveWorldConfiguration();

		// make sure the folder exists first
		File archiveFolder = new File(PACKAGING_DIRECTORY, this.getVersionString());
		if (!archiveFolder.exists()) FileUtils.forceMkdir(archiveFolder);
		FileUtils.cleanDirectory(archiveFolder);

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
		FileUtils.copyDirectory(new File(getWorld().getWorldFolder(), "data"),
			new File(archiveFolder, "data"), DATA_FOLDER_FILTER);

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
		addToZip(zip, archiveFolder, PACKAGING_DIRECTORY);

		zip.close();
		FileUtils.deleteQuietly(archiveFolder);
		return outZipfile;
	}

	private class WorldFolderDeleter extends BukkitRunnable
	{
		private File worldFolder;
		private int deleteAttempts = 5;

		WorldFolderDeleter(World w)
		{ this.worldFolder = w.getWorldFolder(); }

		@Override
		public void run()
		{
			World world = AutoReferee.getInstance().getServer().getWorld(worldFolder.getName());
			if (world == null && worldFolder.exists()) try
			{
				// if we fail, we loop back around again on the next try...
				FileUtils.deleteDirectory(worldFolder);
				AutoReferee.log(worldFolder.getName() + " deleted!");
			}
			catch (IOException e)
			{ if (deleteAttempts-- > 0) AutoReferee.log("File lock held on " + worldFolder.getName()); }

			// stop the repeating task if the file is gone
			if (!worldFolder.exists()) this.cancel();
		}
	}

	protected class PlayerEjectTask extends BukkitRunnable
	{
		private Player player;
		private Location target;

		protected PlayerEjectTask(Player player, Location target)
		{
			this.player = player;
			this.target = target;
		}

		@Override
		public void run()
		{ player.teleport(target); }
	}

	public void ejectPlayer(Player player)
	{
		PlayerMatchLeaveEvent event = new PlayerMatchLeaveEvent(player, this);
		AutoReferee.callEvent(event);
		if (event.isCancelled()) return;

		// resets the player to default state
		PlayerUtil.reset(player);

		// if there is a lobby to teleport them, do so
		World target = AutoReferee.getInstance().getLobbyWorld();
		if (target == null) for (World w : Bukkit.getWorlds())
			if (!AutoRefMatch.isCompatible(w)) { target = w; break; }

		if (target != null)
		{
			PlayerUtil.setGameMode(player, GameMode.SURVIVAL);
			new PlayerEjectTask(player, target.getSpawnLocation()).runTask(AutoReferee.getInstance());
		}

		// otherwise, kick them from the server
		else player.kickPlayer(AutoReferee.COMPLETED_KICK_MESSAGE);
	}

	/**
	 * Unloads and cleans up this match. Players will be teleported out or kicked,
	 * the map will be unloaded, and the map folder may be deleted.
	 */
	public void destroy(MatchUnloadEvent.Reason reason)
	{
		// fire match unload event
		MatchUnloadEvent event = new MatchUnloadEvent(this, reason);
		AutoReferee.callEvent(event);
		if (event.isCancelled()) return;

		// first, handle all the players
		for (Player p : primaryWorld.getPlayers()) this.ejectPlayer(p);

		// if everyone has been moved out of this world, clean it up
		if (primaryWorld.getPlayers().size() == 0)
		{
			// if this is OUR world (we can delete it if we want)
			AutoReferee plugin = AutoReferee.getInstance();
			if (this.isTemporaryWorld())
			{
				plugin.clearMatch(this);
				this.countTask.cancel();

				plugin.getServer().unloadWorld(primaryWorld, true);
				if (!plugin.getConfig().getBoolean("save-worlds", false))
					new WorldFolderDeleter(primaryWorld).runTaskTimer(plugin, 0L, 10 * 20L);
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

	private Set<AutoRefRegion> regions;

	public Set<AutoRefRegion> getRegions()
	{ return regions; }

	public <T extends AutoRefRegion> Set<T> getRegions(Class<T> clazz)
	{
		Set<T> typedRegions = Sets.newHashSet();
		for (AutoRefRegion reg : regions)
			if (clazz.isInstance(reg))
				typedRegions.add((T) reg);
		return typedRegions;
	}

	public Set<AutoRefRegion> getRegions(AutoRefTeam team)
	{
		Set<AutoRefRegion> teamRegions = Sets.newHashSet();
		for (AutoRefRegion reg : regions)
			if (reg.isOwner(team)) teamRegions.add(reg);
		return teamRegions;
	}

	public boolean addRegion(AutoRefRegion reg)
	{ return reg != null && !regions.contains(reg) && regions.add(reg); }

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
		private Block block = null;
		private BlockState state = null;
		private boolean flip = true;

		public StartMechanism(Block block, boolean flip)
		{
			this.flip = flip;
			this.block = block;
			state = block.getState();
		}

		public Element toElement()
		{
			return new Element(state.getType().name().toLowerCase())
				.setAttribute("pos", LocationUtil.toBlockCoords(block.getLocation()))
				.setText(Boolean.toString(flip));
		}

		public StartMechanism(Block block)
		{ this(block, true); }

		@Override public int hashCode()
		{ return block.hashCode() ^ state.hashCode(); }

		@Override public boolean equals(Object o)
		{ return (o instanceof StartMechanism) && hashCode() == o.hashCode(); }

		public String serialize()
		{ return LocationUtil.toBlockCoords(block.getLocation()) + ":" + Boolean.toString(flip); }

		public static StartMechanism fromElement(Element e, World w)
		{
			Block block = w.getBlockAt(LocationUtil.fromCoords(w, e.getAttributeValue("pos")));
			boolean state = Boolean.parseBoolean(e.getTextTrim());

			return new StartMechanism(block, state);
		}

		@Override public String toString()
		{ return state.getType().name() + "(" + this.serialize() + ")"; }

		public Block getBlock()
		{ return block; }

		public BlockState getBlockState()
		{ return state; }

		public boolean getFlippedPosition()
		{ return flip; }

		public boolean active()
		{
			MaterialData bdata = state.getData();

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

	static final Set<Material> EXPECTED_MECHANISMS = Sets.newHashSet
	(	Material.LEVER
	,	Material.STONE_BUTTON
	,	Material.WOOD_BUTTON
	);

	/**
	 * Adds a new start mechanism for this map. These mechanisms are activated automatically
	 * at the start of a match when using SportBukkit, and players may interact with them
	 * normally in the start region when using vanilla CraftBukkit.
	 *
	 * @param block block containing the start mechanism
	 * @param state whether this mechanism should be set powered or unpowered
	 * @return generated start mechanism object
	 * @see <a href="http://www.github.com/rmct/SportBukkit">SportBukkit</a>
	 */
	public StartMechanism toggleStartMech(Block block, boolean state)
	{
		if (block.getType() != Material.LEVER) state = true;
		StartMechanism sm = new StartMechanism(block, state);

		boolean adding = startMechanisms.add(sm);
		if (!adding) { startMechanisms.remove(sm); return null; }

		if (adding && !EXPECTED_MECHANISMS.contains(block.getType()))
			AutoReferee.log("Unexpected start mechanism: " + block.getType().name(), Level.WARNING);
		return sm;
	}

	/**
	 * Gets the start mechanism associated with this location.
	 *
	 * @return start mechanism located at that position, otherwise null
	 */
	public StartMechanism getStartMechanism(Block block)
	{
		if (block == null) return null;
		for (StartMechanism sm : startMechanisms)
			if (block.equals(sm.getBlock())) return sm;
		return null;
	}

	/**
	 * Checks if a specified block location is a start mechanism for this match.
	 *
	 * @return true if a start mechanism is located at that position, otherwise false
	 */
	public boolean isStartMechanism(Block block)
	{ return getStartMechanism(block) != null; }

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
	protected void _startMatch()
	{
		// set up the world time one last time
		primaryWorld.setTime(startClock);
		this.setStartTime(ManagementFactory.getRuntimeMXBean().getUptime());

		addEvent(new TranscriptEvent(this, TranscriptEvent.EventType.MATCH_START, "Match began.", null));

		// send referees the start event
		messageReferees("match", getWorld().getName(), "start");

		// remove all mobs, animals, and items (again)
		this.clearEntities();

		// loop through all the redstone mechanisms required to start / FIXME BUKKIT-1858
		if (SportBukkitUtil.hasSportBukkitApi()) for (StartMechanism sm : startMechanisms)
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

		if (specSpawn != null)
			for (Player spec : this.getSpectators())
				if (this.inStartRegion(spec.getLocation()))
					spec.teleport(specSpawn.getLocation());

		// set the current state to playing
		setCurrentState(MatchStatus.PLAYING);

		// match minute timer
		AutoReferee plugin = AutoReferee.getInstance();
		clockTask = new MatchClockTask();
		clockTask.runTaskTimer(plugin, 60 * 20L, 60 * 20L);

		if (plugin.playedMapsTracker != null)
			plugin.playedMapsTracker.increment(normalizeMapName(this.getMapName()));
	}

	private static final Set<Long> announceMinutes =
		Sets.newHashSet(60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L);

	// handle to the clock task
	protected MatchClockTask clockTask;

	protected class MatchClockTask extends BukkitRunnable
	{
		public void run()
		{
			AutoRefMatch match = AutoRefMatch.this;

			if (match.hasTimeLimit())
			{
				long minutesRemaining = match.getTimeRemaining() / 60L;
				if (minutesRemaining == 0L)
				{
					String timelimit = (match.getTimeLimit() / 60L) + " min";
					match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.MATCH_END,
						"Match time limit reached: " + timelimit, null));
					match.endMatch();
				}
				else if (AutoRefMatch.announceMinutes.contains(minutesRemaining))
					match.broadcast(">>> " + ChatColor.GREEN +
						"Match ends in " + minutesRemaining + "m");
			}

			// send clock updates to ensure that client hud stays sync'd
			messageReferees("match", getWorld().getName(), "time", getTimestamp(","));

			if (lockTime) primaryWorld.setTime(startClock);
			AutoRefMatch.this.checkWinConditions();
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
	protected void setupVanish(Player view, Player subj)
	{
		if (isSpectator(subj) && getSpectator(subj).isInvisible()) view.hidePlayer(subj);
		if (getVanishLevel(view) < getVanishLevel(subj) &&
			this.getCurrentState().inProgress()) view.hidePlayer(subj);
		else view.showPlayer(subj);
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
		if (getCurrentState().isBeforeMatch()) setSpectatorMode(player, isReferee(player) || isPreviewMode());
		else setSpectatorMode(player, !isPlayer(player) || getCurrentState().isAfterMatch());

		// redo visibility
		setupVisibility(player);

		// if this player is a spectator
		if (isSpectator(player))
		{
			// apply night vision if necessary
			AutoRefSpectator s = getSpectator(player);
			if (s.hasNightVision()) s.applyNightVision();
		}
	}

	/**
	 * Reconfigures visibility, to and from the specified player.
	 */
	public void setupVisibility(Player player)
	{
		for ( Player x : getWorld().getPlayers() )
		{
			// setup vanish in both directions
			setupVanish(player, x);
			setupVanish(x, player);
		}
	}

	/**
	 * Sets whether a specified player is in spectator mode, explicitly setting gamemode.
	 *
	 * @param player player to set spectator mode for
	 * @param spec true to set spectator mode on, false to set spectator mode off
	 */
	public void setSpectatorMode(Player player, boolean spec)
	{
		PlayerUtil.setSpectatorSettings(player, spec, this.gamemode);
		player.setScoreboard(spec ? getInfoboard() : getScoreboard());
		for (AutoRefTeam team : getTeams()) team.updateObjectives();

		if (!player.getAllowFlight()) player.setFallDistance(0.0f);
		SportBukkitUtil.setAffectsSpawning(player, !spec);

		boolean noEntityCollide = spec && getCurrentState().inProgress();
		SportBukkitUtil.setCollidesWithEntities(player, !noEntityCollide);
	}

	/**
	 * Removes unprotected entities from the world.
	 */
	public void clearEntities()
	{
		for (Entity e : primaryWorld.getEntitiesByClasses(Arrow.class, Item.class,
				Monster.class, Animals.class, Ambient.class, ExperienceOrb.class))
			if (!protectedEntities.contains(e.getUniqueId())) e.remove();
	}

	/**
	 * Checks if the match start countdown is running.
	 *
	 * @return true if the countdown is in progress, otherwise false
	 */
	public boolean isCountdownRunning()
	{ return matchStarter != null; }

	/**
	 * Cancels the match countdown in progress.
	 */
	public void cancelCountdown()
	{
		if (isCountdownRunning()) matchStarter.cancel();
		matchStarter = null;
	}

	// helper class for starting match, synchronous task
	private static class CountdownTask extends BukkitRunnable
	{
		public static final ChatColor COLOR = ChatColor.GREEN;
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
			else if (remainingSeconds <= 0)
			{
				// setup world to go!
				if (this.start) match._startMatch();
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
	}

	// prepare this world to start
	public void startMatch(MatchStartEvent.Reason reason)
	{
		// match has already started, don't try to start it again
		if (!this.getCurrentState().isBeforeMatch()) return;

		MatchStartEvent event = new MatchStartEvent(this, reason);
		AutoReferee.callEvent(event);
		if (!refereeReady && event.isCancelled()) return;

		// nothing to do if the countdown is running
		if (isCountdownRunning()) return;

		// update all the objectives
		for (AutoRefTeam team : getTeams())
			team.updateObjectives();

		// set the current time to the start time
		primaryWorld.setTime(this.startClock);

		// remove all mobs, animals, and items
		this.clearEntities();

		// turn off weather forever (or for a long time)
		primaryWorld.setStorm(false);
		primaryWorld.setWeatherDuration(Integer.MAX_VALUE);

		// prepare all players for the match
		for (AutoRefPlayer apl : getPlayers()) apl.heal();

		// announce the match starting in X seconds
		int readyDelay = this.getReadyDelay();
		this.broadcast(CountdownTask.COLOR + "Match will begin in "
			+ ChatColor.WHITE + Integer.toString(readyDelay) + CountdownTask.COLOR + " seconds.");

		// send referees countdown notification
		messageReferees("match", getWorld().getName(), "countdown", Integer.toString(readyDelay));
		startCountdown(readyDelay, true);

		// save a copy of the map image quickly before the match starts...
		saveMapImage();

		itemElevatorDetectionTask = new ItemElevatorDetectionTask();
		itemElevatorDetectionTask.runTaskTimer(AutoReferee.getInstance(),
			0L, ItemElevatorDetectionTask.INTERVAL);
	}

	/**
	 * Starts a countdown.
	 *
	 * @param delay number of seconds before end of countdown
	 * @param start true if countdown should start match, otherwise false
	 */
	public void startCountdown(int delay, boolean start)
	{
		// cancel any previous match-start task
		this.cancelCountdown();

		// schedule the task to announce and prepare the match
		this.matchStarter = new CountdownTask(this, delay, start);
		this.matchStarter.runTaskTimer(AutoReferee.getInstance(), 0L, 20L);
	}

	/**
	 * Checks if teams have any players missing and are ready to play.
	 */
	public void checkTeamsReady()
	{
		// this function is only useful if called prior to the match
		if (!getCurrentState().isBeforeMatch()) return;

		// if there are no players on the server
		if (getPlayers().isEmpty())
		{
			// set all the teams to not ready and status as waiting
			for ( AutoRefTeam t : teams ) t.setReady(false);
			setCurrentState(MatchStatus.WAITING); return;
		}

		// check if all the players are here
		boolean ready = true;
		for ( String name : getExpectedPlayers() )
		{
			OfflinePlayer opl = Bukkit.getOfflinePlayer(name);
			ready &= opl.isOnline() && isPlayer(opl.getPlayer()) &&
				getPlayer(opl.getPlayer()).isPresent();
		}

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
			teamsReady &= t.isReady();

		boolean ready = getReferees().size() == 0 ? teamsReady : isRefereeReady();
		if (teamsReady && !ready) for (Player p : getReferees())
			p.sendMessage(ChatColor.GRAY + "Teams are ready. Type /ready to begin the match.");

		// everyone is ready, let's go!
		if (ready) this.startMatch(MatchStartEvent.Reason.READY);
	}

	/**
	 * Checks if any team has satisfied the win conditions.
	 */
	public void checkWinConditions()
	{
		Plugin plugin = AutoReferee.getInstance();
		plugin.getServer().getScheduler().runTask(plugin,
			new Runnable(){ public void run(){ _checkWinConditions(); } });
	}

	private void _checkWinConditions()
	{
		if (getCurrentState().inProgress())
		{
			Set<AutoRefTeam> winningTeams = Sets.newHashSet();
			for (AutoRefTeam team : this.teams)
			{
				// pass this information along to the scoreboard
				team.updateObjectives();

				// if there are no win conditions set, skip this team
				if (team.getTeamGoals().size() == 0) continue;

				// check all win condition blocks (AND together)
				boolean win = true;
				for (AutoRefGoal goal : team.getTeamGoals())
					win &= goal.isSatisfied(this);

				// force an update of objective status
				team.updateBlockGoals();

				// if the team won, mark the match as completed
				if (win) winningTeams.add(team);
			}

			// if there is one "winning" team, they win
			if (winningTeams.size() == 1)
				endMatch(Iterables.getOnlyElement(winningTeams));

			// if we are just waiting for this match to end, check always
			else if (currentlyTied) endMatch();
		}
	}

	// helper class for terminating world, synchronous task
	private class MatchUnloadTask extends BukkitRunnable
	{
		public void run()
		{ destroy(MatchUnloadEvent.Reason.COMPLETE); }
	}

	private static class TiebreakerComparator implements Comparator<AutoRefTeam>
	{
		public int compare(AutoRefTeam a, AutoRefTeam b)
		{
			// break ties based on goal scores (FIXME)
			return (int) Math.signum(b.getObjectiveScore() - a.getObjectiveScore());
		}
	}

	/**
	 * Ends match, allowing AutoReferee to break ties according to its own policies.
	 */
	public void endMatch()
	{
		TiebreakerComparator cmp = new TiebreakerComparator();
		List<AutoRefTeam> sortedTeams = Lists.newArrayList(getTeams());

		// sort the teams based on their "score"
		Collections.sort(sortedTeams, cmp);

		if (0 != cmp.compare(sortedTeams.get(0), sortedTeams.get(1)))
		{ endMatch(sortedTeams.get(0)); return; }

		if (AutoRefMatch.areTiesAllowed()) { endMatch(null); return; }

		if (currentlyTied) return;
		currentlyTied = true;

		for (Player ref : getReferees())
		{
			ref.sendMessage(ChatColor.DARK_GRAY + "This match is currently tied.");
			ref.sendMessage(ChatColor.DARK_GRAY + "Use '/autoref endmatch <team>' to declare a winner.");
		}

		// let the console know that the match cannot be ruled upon
		AutoReferee.log("Match tied. Deferring to referee intervention...");
		if (clockTask != null) clockTask.cancel();
	}

	/**
	 * Ends match in favor of a specified team.
	 *
	 * @param team winning team, or null for no winner
	 */
	public void endMatch(AutoRefTeam team)
	{
		MatchCompleteEvent event = new MatchCompleteEvent(this, team);
		AutoReferee.callEvent(event);
		if (event.isCancelled()) return;

		AutoReferee plugin = AutoReferee.getInstance();

		// update winner from the match complete event
		team = event.getWinner();

		// announce the victory and set the match to completed
		if (team != null) this.broadcast(team.getDisplayName() + " Wins!");
		else this.broadcast("Match terminated!");

		// don't have to delay this anymore :)
		clearEntities();

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
		addEvent(new TranscriptEvent(this, TranscriptEvent.EventType.MATCH_END, "Match ended." + winner, null));
		setCurrentState(MatchStatus.COMPLETED);

		setWinningTeam(team);
		logPlayerStats();

		if (clockTask != null) clockTask.cancel();

		int termDelay = plugin.getConfig().getInt(
			"delay-seconds.completed", COMPLETED_SECONDS);

		if (plugin.getLobbyWorld() != null)
			new MatchUnloadTask().runTaskLater(plugin, termDelay * 20L);

		if (itemElevatorDetectionTask != null) itemElevatorDetectionTask.cancel();
		itemElevatorDetectionTask = null;

		// set the time to day
		getWorld().setTime(0L);
	}

	/**
	 * Finds a team whose name matches the given string.
	 *
	 * @param name team name to look up, either custom team name or base team name
	 * @return team object matching the name if one exists, otherwise null
	 */
	public AutoRefTeam getTeam(String name)
	{
		AutoRefTeam mteam = null;
		int bsz = 0;

		// if there is no match on that world, forget it
		// is this team name a word?
		for (AutoRefTeam t : teams)
		{
			// get the "match size"
			int msz = t.matches(name);

			// update the best match (null if multiple matches)
			if (msz > bsz) { mteam = t; bsz = msz; }
			else if (msz == bsz) mteam = null;
		}

		// return the matched team (or null if no match)
		return mteam;
	}

	/**
	 * Finds a team whose scoreboard team name matches the given string.
	 *
	 * @param name scoreboard team name to look up
	 * @return team object matching the name if one exists, otherwise null
	 */
	public AutoRefTeam getScoreboardTeam(String name)
	{
		for (AutoRefTeam t : teams)
			if (name.equalsIgnoreCase(t.getScoreboardTeamName()))
				return t;
		return null;
	}

	Set<String> expectedPlayers = Sets.newHashSet();

	public Set<String> getExpectedPlayers()
	{
		Set<String> eps = Sets.newHashSet(expectedPlayers);
		for (AutoRefTeam team : teams)
			eps.addAll(team.getExpectedPlayers());
		return eps;
	}

	protected Map<String, String> playerCapes = Maps.newHashMap();

	public void addCape(String name, String cape)
	{ playerCapes.put(name.toLowerCase(), cape); }

	public void addCape(OfflinePlayer opl, String cape)
	{ addCape(opl.getName(), cape); }

	/**
	 * Adds a player to the list of expected players, without a team affiliation.
	 */
	public void addExpectedPlayer(OfflinePlayer opl)
	{ expectedPlayers.add(opl.getName().toLowerCase()); }

	public void addExpectedPlayer(OfflinePlayer opl, String cape)
	{ addExpectedPlayer(opl); addCape(opl, cape); }

	/**
	 * Gets the team the specified player is expected to join.
	 *
	 * @return team player is expected to join, otherwise null
	 */
	public AutoRefTeam expectedTeam(OfflinePlayer opl)
	{
		String name = opl.getName().toLowerCase();
		for (AutoRefTeam team : teams)
			if (team.getExpectedPlayers().contains(name)) return team;
		return null;
	}

	/**
	 * Checks if the specified player is expected to join this match.
	 *
	 * @return true if player is expected, otherwise false
	 */
	public boolean isPlayerExpected(OfflinePlayer opl)
	{ return getExpectedPlayers().contains(opl.getName().toLowerCase()); }

	/**
	 * Removes a specified player from any expected player lists for this match.
	 */
	public void removeExpectedPlayer(OfflinePlayer opl)
	{
		String name = opl.getName().toLowerCase();
		for (AutoRefTeam t : teams)
			t.getExpectedPlayers().remove(name);
		expectedPlayers.remove(name);
	}

	/**
	 * Teleports a player to a match they have been added to, joining the team inviting them.
	 */
	public void joinMatch(Player player)
	{
		PlayerMatchJoinEvent event = new PlayerMatchJoinEvent(player, this);
		AutoReferee.callEvent(event);
		if (event.isCancelled()) return;

		// if already here, skip this
		if (this.isPlayer(player)) return;

		// if this player needs to be placed on a team, go for it
		AutoRefTeam team = this.expectedTeam(player);
		if (team != null) this.joinTeam(player,
			team, PlayerTeamJoinEvent.Reason.EXPECTED, false);

		// otherwise, get them into the world
		else if (player.getWorld() != this.getWorld())
			player.teleport(this.getPlayerSpawn(player));

		// remove name from all lists
		this.removeExpectedPlayer(player);
		this.checkTeamsReady();
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
	public boolean joinTeam(Player player, AutoRefTeam team, PlayerTeamJoinEvent.Reason reason, boolean force)
	{
		AutoRefTeam pteam = getPlayerTeam(player);
		if (team == pteam) return true;

		if (pteam != null) pteam.leave(player, force);
		return team.join(player, reason, force);
	}

	/**
	 * Removes player from all teams.
	 *
	 * @param player player to be removed
	 * @param force should player be removed, even if match is in progress
	 */
	public void leaveTeam(Player player, boolean force)
	{ for (AutoRefTeam team : teams) team.leave(player, force); }

	private List<String> sortedPlayers;

	protected void updatePlayerList()
	{
		sortedPlayers = Lists.newLinkedList();
		for (AutoRefPlayer apl : getPlayers())
			sortedPlayers.add(apl.getName());
		Collections.sort(sortedPlayers);
	}

	protected String getCycleNextPlayer(String name)
	{ return getCycleRelativePlayer(name, +1); }

	protected String getCyclePrevPlayer(String name)
	{ return getCycleRelativePlayer(name, -1); }

	private String getCycleRelativePlayer(String name, int z)
	{
		if (name == null) return sortedPlayers.get(0);
		int k = Collections.binarySearch(sortedPlayers, name);

		int len = sortedPlayers.size();
		return sortedPlayers.get((k + len + z) % len);
	}

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
		if (!this.playersBecomeSpectators) this.ejectPlayer(player);
		this.checkWinConditions();
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
			if (pl == null || pl.getWorld() != loc.getWorld()) continue;

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

		boolean useWorldSpawn = getCurrentState().isBeforeMatch();
		return useWorldSpawn ? this.getWorldSpawn() : this.getSpectatorSpawn();
	}

	/**
	 * Checks if a region is marked with a specific region flag.
	 *
	 * @return true if location contains flag, otherwise false
	 */
	public boolean hasFlag(Location loc, AutoRefRegion.Flag flag)
	{
		// check start region flags
		if (inStartRegion(loc)) return getStartRegionFlags().contains(flag);

		boolean is = flag.defaultValue; Set<AutoRefRegion> regions = getRegions();
		if (regions != null) for ( AutoRefRegion reg : regions )
			if (reg.contains(loc)) { is = false; if (reg.is(flag)) return true; }
		return is;
	}

	private class MatchReportSaver extends BukkitRunnable
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

			MatchUploadStatsEvent event = new MatchUploadStatsEvent(AutoRefMatch.this, report);
			AutoReferee.callEvent(event);
			report = event.getWebstats();

			String webstats = null;
			if (!event.isCancelled())
			{
				if (this.localStorage != null)
				{
					String localFileID = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss").format(new Date()) + ".html";
					File localReport = new File(this.localStorage, localFileID);

					try
					{
						FileUtils.writeStringToFile(localReport, report);
						localReport.setReadable(true);
					}
					catch (IOException e) { e.printStackTrace(); }
					webstats = serveLocally() ? (webDirectory + localFileID) : uploadReport(report);
				}
				else webstats = uploadReport(report);
			}

			if (webstats == null) broadcastSync(ChatColor.RED + AutoReferee.NO_WEBSTATS_MESSAGE);
			else broadcastSync(ChatColor.RED + "Match Summary: " + ChatColor.RESET + webstats);
		}
	}

	private void logPlayerStats()
	{
		// upload WEBSTATS (do via an async query in case uploading the stats lags the main thread)
		new MatchReportSaver().runTaskAsynchronously(AutoReferee.getInstance());
	}

	private String uploadReport(String report)
	{
		String failure = "<unknown reason>";
		try
		{
			// submit our request to pastehtml, get back a link to the report
			return QueryUtil.syncQuery("http://pastehtml.com/upload/create",
				"input_type=html&result=address&minecraft=1",
				"txt=" + URLEncoder.encode(report, "UTF-8"));
		}
		catch (IOException e) { failure = e.getLocalizedMessage(); }

		// somewhat quietly log the reason for the failed upload
		AutoReferee.log("Report upload failed: " + failure, Level.SEVERE);
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
			PLAYER_DEATH("player-death", true, EventVisibility.NONE),
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

			EventType(String eventClass, boolean hasFilter, EventVisibility visibility)
			{ this(eventClass, hasFilter, visibility, null); }

			EventType(String eventClass, boolean hasFilter,
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

		public Set<Object> actors;

		private EventType type;

		public EventType getType()
		{ return type; }

		private String message;

		public String getMessage()
		{ return ChatColor.stripColor(message); }

		public String getColoredMessage()
		{ return message; }

		private Location location;
		private long timestamp;

		public TranscriptEvent(AutoRefMatch match, EventType type, String message,
			Location loc, Object ...actors)
		{
			this.type = type;
			this.message = type.getColor() != null ? type.getColor() + message + ChatColor.RESET :
				message.contains("" + ChatColor.COLOR_CHAR) ? message : match.colorMessage(message);

			// if no location is given, use the spawn location
			this.location = (loc != null) ? loc :
				match.getWorld().getSpawnLocation();

			this.actors = Sets.newHashSet(actors);
			this.timestamp = match.getElapsedSeconds();
		}

		public String getTimestamp()
		{
			long t = getSeconds();
			return String.format("%02d:%02d:%02d",
				t/3600L, (t/60L)%60L, t%60L);
		}

		@Override
		public String toString()
		{ return String.format("[%s] %s", this.getTimestamp(), this.getMessage()); }

		public Location getLocation()
		{ return location; }

		public long getSeconds()
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
		AutoReferee.callEvent(new MatchTranscriptEvent(this, event));
		transcript.add(event);

		Collection<Player> recipients = null;
		switch (event.getType().getVisibility())
		{
			case REFEREES: recipients = getReferees(false); break;
			case ALL: recipients = getWorld().getPlayers(); break;
			default: break;
		}

		ChatColor clr = event.getType().getColor();
		String message = event.getColoredMessage();

		if (recipients != null) for (Player player : recipients)
			player.sendMessage(message);

		if (plugin.isConsoleLoggingEnabled())
			AutoReferee.log(event.toString());
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
		for (AutoRefPlayer apl : getPlayers()) if (apl != null)
			message = message.replaceAll(apl.getName(), apl.getDisplayName());
		return ChatColor.RESET + message;
	}

	// ABANDON HOPE, ALL YE WHO ENTER HERE!
	/**
	 * Converts a human-readable time to a clock tick in Minecraft. Converts times such as "8am",
	 * "1600", or "3:45p" to a valid clock tick setting that can be used to change the world time.
	 *
	 * @param time A string representing a human-readable time.
	 * @return Equivalent clock tick
	 */
	public static long parseTimeString(String time)
	{
		// "Some people, when confronted with a problem, think 'I know, I'll use
		// regular expressions.' Now they have two problems." -- Jamie Zawinski
		Pattern pattern = Pattern.compile("(\\d{1,5})(:(\\d{2}))?((a|p)m?)?", Pattern.CASE_INSENSITIVE);
		Matcher match = pattern.matcher(time);

		// if the time matches something sensible
		if (match.matches()) try
		{
			// get the primary value (could be hour, could be entire time in ticks)
			long prim = Long.parseLong(match.group(1));
			if (match.group(1).length() > 2 || prim > 24) return prim;

			// parse am/pm distinction (12am == midnight, 12pm == noon)
			if (match.group(5) != null)
				prim = ("p".equals(match.group(5)) ? 12 : 0) + (prim % 12L);

			// ticks are 1000/hour, group(3) is the minutes portion
			long ticks = prim * 1000L + (match.group(3) == null ? 0L :
					(Long.parseLong(match.group(3)) * 1000L / 60L));

			// ticks (18000 == midnight, 6000 == noon)
			return (ticks + 18000L) % 24000L;
		}
		catch (NumberFormatException e) {  }

		// default time: 6am
		return 0L;
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
		meta.setAuthor(ChatColor.DARK_GRAY + this.getAuthorList());

		List<String> pages = Lists.newArrayList();

		// PAGE 1
		pages.add(BookUtil.makePage(
			BookUtil.center(ChatColor.BOLD + "[" + AutoReferee.getInstance().getName() + "]")
		,	BookUtil.center(ChatColor.DARK_GRAY + this.getMapName())
		,	BookUtil.center(" by " + ChatColor.DARK_GRAY + this.getAuthorList())
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
		,	BookUtil.center(" by " + ChatColor.DARK_GRAY + this.getAuthorList())
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

	public void giveMatchInfoBook(Player player, boolean force)
	{
		if (force || AutoRefMatch.giveMatchInfoBooks)
			player.getInventory().addItem(this.getMatchInfoBook());
	}

	public void giveMatchInfoBook(Player player)
	{ this.giveMatchInfoBook(player, false); }

	/**
	 * Send updated match information to a player.
	 */
	public void sendMatchInfo(CommandSender sender)
	{
		sender.sendMessage(ChatColor.RESET + "Map: " + ChatColor.GRAY + getMapName() +
			" v" + getMapVersion() + ChatColor.ITALIC + " by " + getAuthorList());

		if (sender instanceof Player)
		{
			Player player = (Player) sender;
			AutoRefPlayer apl = getPlayer(player);
			String tmpflag = tmp ? "*" : "";

			if (apl != null && apl.getTeam() != null)
				player.sendMessage("You are on team: " + apl.getTeam().getDisplayName());
			else if (isReferee(player)) player.sendMessage(ChatColor.GRAY + "You are a referee! " + tmpflag);
			else player.sendMessage("You are not on a team! Type " + ChatColor.GRAY + "/jointeam");
		}

		for (AutoRefTeam team : getTeams())
			sender.sendMessage(String.format("%s (%d) - %s",
				team.getDisplayName(), team.getPlayers().size(), team.getPlayerList()));

		sender.sendMessage("Match status is currently " + ChatColor.GRAY + getCurrentState().name() +
			(this.getCurrentState().isBeforeMatch() ? (" [" + this.access.name() + "]") : ""));
		sender.sendMessage("Map difficulty is set to: " + ChatColor.GRAY + getWorld().getDifficulty().name());

		long timestamp = this.getElapsedSeconds(), timelimit = this.getTimeLimit();
		if (getCurrentState().inProgress()) sender.sendMessage(this.hasTimeLimit()
			? String.format(ChatColor.GRAY + "The current match time is: " +
				"%02d:%02d:%02d / %02d:%02d:%02d", timestamp/3600L, (timestamp/60L)%60L, timestamp%60L,
				timelimit/3600L, (timelimit/60L)%60L, timelimit%60L)
			: String.format(ChatColor.GRAY + "The current match time is: " +
				"%02d:%02d:%02d", timestamp/3600L, (timestamp/60L)%60L, timestamp%60L));
	}
}
