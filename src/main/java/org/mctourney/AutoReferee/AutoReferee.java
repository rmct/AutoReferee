package org.mctourney.AutoReferee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;

import org.mcstats.Metrics;
import org.mcstats.Metrics.Graph;

import org.mctourney.AutoReferee.commands.ConfigurationCommands;
import org.mctourney.AutoReferee.commands.PlayerCommands;
import org.mctourney.AutoReferee.commands.AdminCommands;
import org.mctourney.AutoReferee.commands.RefereeCommands;
import org.mctourney.AutoReferee.listeners.ObjectiveTracker;
import org.mctourney.AutoReferee.listeners.CombatListener;
import org.mctourney.AutoReferee.listeners.RefereeChannelListener;
import org.mctourney.AutoReferee.listeners.TeamListener;
import org.mctourney.AutoReferee.listeners.WorldListener;
import org.mctourney.AutoReferee.listeners.ZoneListener;
import org.mctourney.AutoReferee.util.NullChunkGenerator;
import org.mctourney.AutoReferee.util.PlayerUtil;
import org.mctourney.AutoReferee.util.commands.CommandManager;
import org.mctourney.AutoReferee.util.metrics.IncrementPlotter;
import org.mctourney.AutoReferee.util.metrics.PieChartGraph;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Base plugin class
 *
 * @author authorblues
 */
public class AutoReferee extends JavaPlugin
{
	// singleton instance
	private static AutoReferee instance = null;

	/**
	 * Gets the singleton instance of AutoReferee
	 *
	 * @return AutoReferee instance
	 */
	public static AutoReferee getInstance()
	{ return instance; }

	protected void log(String msg)
	{ getInstance().getLogger().info(msg); }

	// expected configuration version number
	private static final int PLUGIN_CFG_VERSION = 2;

	// plugin channel encoding
	public static final String PLUGIN_CHANNEL_ENC = "UTF-8";

	// plugin channel prefix - identifies all channels as belonging to AutoReferee
	private static final String PLUGIN_CHANNEL_PREFIX = "autoref:";

	// plugin channels (referee)
	public static final String REFEREE_PLUGIN_CHANNEL = PLUGIN_CHANNEL_PREFIX + "referee";
	private RefereeChannelListener refChannelListener = null;

	// name of the stored map configuration file
	public static final String CFG_FILENAME = "autoreferee.xml";

	// prefix for temporary worlds
	public static final String WORLD_PREFIX = "world-autoref-";

	// messages
	public static final String NO_LOGIN_MESSAGE = "You are not scheduled for a match on this server.";
	public static final String COMPLETED_KICK_MESSAGE = "Thank you for playing!";
	public static final String NO_WEBSTATS_MESSAGE = "An error has occured; no webstats will be generated.";

	// query server object
	private QueryServer qserv = null;

	// command manager
	protected CommandManager commandManager = null;

	private World lobby = null;

	/**
	 * Gets the world designated as the lobby world. When the server is in automated mode, this world
	 * is where users will be teleported to when the a match is unloaded and cleaned up.
	 *
	 * @return lobby world
	 */
	public World getLobbyWorld()
	{ return lobby; }

	/**
	 * Sets the world designated as the lobby world.
	 *
	 * @param world lobby world
	 */
	public void setLobbyWorld(World world)
	{ this.lobby = world; }

	// is this plugin in online mode?
	private boolean autoMode = true;

	/**
	 * Checks if the server is in fully-automated mode.
	 *
	 * @return true if in automated mode, otherwise false
	 */
	public boolean isAutoMode()
	{ return autoMode; }

	protected boolean setAutoMode(boolean auto)
	{ return this.autoMode = auto; }

	private boolean consoleLog = false;

	protected boolean isConsoleLoggingEnabled()
	{ return consoleLog; }

	// get the match associated with the world
	private Map<UUID, AutoRefMatch> matches = Maps.newHashMap();

	/**
	 * Gets match object associated with the given world.
	 *
	 * @return match object if one exists, otherwise null
	 */
	public AutoRefMatch getMatch(World world)
	{ return world != null ? matches.get(world.getUID()) : null; }

	/**
	 * Gets all existing match objects for this server.
	 *
	 * @return a collection of match objects
	 */
	public Collection<AutoRefMatch> getMatches()
	{ return matches.values(); }

	/**
	 * Adds a given match to be tracked by the server.
	 */
	public void addMatch(AutoRefMatch match)
	{ matches.put(match.getWorld().getUID(), match); }

	/**
	 * Removes a match from the server.
	 */
	public void clearMatch(AutoRefMatch match)
	{ matches.remove(match.getWorld().getUID()); }

	/**
	 * Gets team object associated with a player. Searches all matches for this player,
	 * returns team object for the team containing this player.
	 *
	 * @return team object if player is on team, otherwise null
	 */
	public AutoRefTeam getTeam(Player player)
	{
		// go through all the matches
		for (AutoRefMatch match : matches.values())
		{
			// if the player is on a team for this match...
			AutoRefTeam team = match.getPlayerTeam(player);
			if (team != null) return team;
		}

		// this player is on no known teams
		return null;
	}

	/**
	 * Gets the team the player is expected to join. Matches setup by automated match
	 * configurations may designate certain players for certain teams.
	 *
	 * @return player's team, null if no such team
	 */
	public AutoRefTeam getExpectedTeam(Player player)
	{
		AutoRefTeam actualTeam = getTeam(player);
		if (actualTeam != null) return actualTeam;

		// go through all the matches
		for (AutoRefMatch match : matches.values())
		{
			// if the player is expected for any of these teams
			for (AutoRefTeam team : match.getTeams())
				if (team.getExpectedPlayers().contains(player))
					return team;
		}

		// this player is expected on no known teams
		return null;
	}

	private boolean checkPlugins(PluginManager pm)
	{
		boolean foundOtherPlugin = false;
		for ( Plugin p : pm.getPlugins() ) if (p != this)
		{
			if (!foundOtherPlugin)
				getLogger().severe("No other plugins may be loaded in online mode...");
			getLogger().severe("Agressively disabling plugin: " + p.getName());

			pm.disablePlugin(p);
			String pStatus = p.isEnabled() ? "NOT disabled" : "disabled";
			getLogger().severe(p.getName() + " is " + pStatus + ".");

			foundOtherPlugin = true;
		}

		// return true if all other plugins are disabled
		for ( Plugin p : pm.getPlugins() )
			if (p != this && p.isEnabled()) return false;
		return true;
	}

	public void onEnable()
	{
		// store the singleton instance
		instance = this;

		PluginManager pm = getServer().getPluginManager();

		// listener utility classes, subdivided for organization
		pm.registerEvents(new TeamListener(this), this);
		pm.registerEvents(new CombatListener(this), this);
		pm.registerEvents(new ZoneListener(this), this);
		pm.registerEvents(new WorldListener(this), this);
		pm.registerEvents(new ObjectiveTracker(this), this);

		// save this reference to use for setting up the referee channel later
		pm.registerEvents(refChannelListener = new RefereeChannelListener(this), this);

		// user interface commands in a custom command manager
		commandManager = new CommandManager();
		commandManager.registerCommands(new PlayerCommands(this), this);
		commandManager.registerCommands(new AdminCommands(this), this);
		commandManager.registerCommands(new RefereeCommands(this), this);
		commandManager.registerCommands(new ConfigurationCommands(this), this);

		// global configuration object (can't be changed, so don't save onDisable)
		InputStream configInputStream = getResource("defaults/config.yml");
		if (configInputStream != null) getConfig().setDefaults(
			YamlConfiguration.loadConfiguration(configInputStream));
		getConfig().options().copyDefaults(true); saveConfig();

		// ensure we are dealing with the right type of config file
		int configVersion = getConfig().getInt("config-version", -1);
		if (configVersion != PLUGIN_CFG_VERSION && configVersion != -1)
			getLogger().severe(String.format("!!! Incorrect config-version (expected %d, got %d)",
				PLUGIN_CFG_VERSION, configVersion));

		// get server list, and attempt to determine whether we are in online mode
		String qurl = getConfig().getString("server-mode.query-server", null);
		setAutoMode(getServer().getOnlineMode() && qurl != null &&
			this.getConfig().getBoolean("server-mode.online", true));

		// setup a possible alternate map repository
		String mrepo = this.getConfig().getString("server-mode.map-repo", null);
		if (mrepo != null) AutoRefMatch.changeMapRepo(mrepo);

		// attempt to setup the plugin channels
		setupPluginChannels();

		// fire up the plugin metrics
		try { setupPluginMetrics(); }
		catch (IOException e) { getLogger().severe("Plugin Metrics not enabled."); }

		// wrap up, debug to follow this message
		getLogger().info(this.getName() + " loaded successfully" +
			(hasSportBukkitApi() ? " with SportBukkit API" : "") + ".");

		// connect to server, or let the server operator know to set up the match manually
		if (!makeServerConnection(qurl))
			getLogger().info(this.getName() + " is running in OFFLINE mode. All setup must be done manually.");

		// update online mode to represent whether or not we have a connection
		if (isAutoMode()) setAutoMode(checkPlugins(pm));

		// are ties allowed? (auto-mode always allows for ties)
		AutoRefMatch.setAllowTies(isAutoMode() || getConfig().getBoolean("allow-ties", false));

		// log messages to console?
		consoleLog = getConfig().getBoolean("console-log", false);

		// setup the map library folder
		AutoRefMap.getMapLibrary();

		// process initial world(s), just in case
		for ( World w : getServer().getWorlds() )
			AutoRefMatch.setupWorld(w, false);

		// update maps automatically if auto-update is enabled
		if (getConfig().getBoolean("auto-update", true))
			AutoRefMap.getUpdates(Bukkit.getConsoleSender(), false);

		// get the lobby world ASAP (allow for null lobby world)
		getServer().getScheduler().runTask(this, new Runnable()
		{
			@Override
			public void run()
			{
				String lobby = getConfig().getString("lobby-world", null);
				if (lobby != null) setLobbyWorld(getServer().getWorld(lobby));
			}
		});
	}

	private boolean makeServerConnection(String qurl)
	{
		// if we are not in online mode, stop right here
		if (!isAutoMode()) return false;

		// get default key and server key
		String defkey = getConfig().getDefaults().getString("server-mode.key");
		String key = getConfig().getString("server-mode.key", null);

		// if there is no server key listed, or it is set to the default key
		if (key == null || key.equals(defkey))
		{
			// reference the keyserver to remind operator to get a server key
			getLogger().severe("Please get a server key from " +
				getConfig().getString("server-mode.keyserver"));
			return setAutoMode(false);
		}

		// setup query server and return response from ACK
		qserv = new QueryServer(qurl, key);
		return setAutoMode(qserv.ack());
	}

	public void onDisable()
	{
		getLogger().info(this.getName() + " disabled.");
	}

	private void setupPluginChannels()
	{
		Messenger m = getServer().getMessenger();

		// setup referee plugin channels
		m.registerOutgoingPluginChannel(this, REFEREE_PLUGIN_CHANNEL);
		m.registerIncomingPluginChannel(this, REFEREE_PLUGIN_CHANNEL, refChannelListener);
	}

	protected PieChartGraph playedMapsTracker = null;
	protected IncrementPlotter matchesPlayed = null;

	private void setupPluginMetrics()
		throws IOException
	{
		Metrics metrics = new Metrics(this);

		Set<String> mapNames = Sets.newHashSet();
		for (AutoRefMap map : AutoRefMap.getRemoteMaps())
			mapNames.add(map.getName());

		Graph gMaps = metrics.createGraph("Most Popular Maps");
		playedMapsTracker = new PieChartGraph(gMaps, mapNames);

		metrics.addCustomData(matchesPlayed = new IncrementPlotter("Matches Played"));

		metrics.start();
	}

	public void sendPlayerToLobby(Player p)
	{
		// take them back to the lobby, one way or another
		World wLobby = getLobbyWorld();
		if (wLobby != null && p.getWorld() != wLobby)
		{
			p.setGameMode(WorldListener.getDefaultGamemode(wLobby));
			p.teleport(wLobby.getSpawnLocation());
		}

		// resets the player to default state
		PlayerUtil.reset(p);

		// if the server is in online mode, remove them as well
		if (isAutoMode()) p.kickPlayer(AutoReferee.COMPLETED_KICK_MESSAGE);
	}

	public WorldEditPlugin getWorldEdit()
	{
		Plugin x = getServer().getPluginManager().getPlugin("WorldEdit");
		return (x != null && x instanceof WorldEditPlugin) ? (WorldEditPlugin) x : null;
	}

	/**
	 * Checks if the player should be white-listed on this server. Only used in auto-mode.
	 *
	 * @return true if player should be whitelisted, otherwise false
	 */
	public boolean playerWhitelisted(Player player)
	{
		if (player.hasPermission("autoreferee.admin")) return true;
		if (player.hasPermission("autoreferee.referee")) return true;
		return getExpectedTeam(player) != null;
	}

	private World consoleWorld = null;

	/**
	 * Gets the world that the console user has selected.
	 *
	 * @return The world selected by the console user
	 */
	public World getConsoleWorld()
	{
		List<World> worlds = getServer().getWorlds();
		return consoleWorld == null ? worlds.get(0) : consoleWorld;
	}

	/**
	 * Sets the world that the console user has selected.
	 */
	public void setConsoleWorld(World world)
	{ consoleWorld = world; }

	/**
	 * Sets the world that the console user has selected.
	 */
	public void setConsoleWorld(String name)
	{ this.setConsoleWorld(getServer().getWorld(name)); }

	/**
	 * Gets the world associated with a command sender.
	 */
	public World getSenderWorld(CommandSender sender)
	{
		if (sender instanceof Player)
			return ((Player) sender).getWorld();
		return getConsoleWorld();
	}

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldname, String id)
	{ return new NullChunkGenerator(); }

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		return false;
	}

	/**
	 * Force a message to be sent synchronously. Safe to use from an asynchronous task.
	 *
	 * @param msg message to be sent
	 */
	public void sendMessageSync(CommandSender sender, String msg)
	{
		getServer().getScheduler().runTask(this, new SyncMessageTask(sender, msg));
	}

	private class SyncMessageTask implements Runnable
	{
		private String message = null;
		private CommandSender sender = null;

		public SyncMessageTask(CommandSender sender, String message)
		{ this.sender = sender; this.message = message; }

		@Override public void run()
		{
			if (this.message != null && this.sender != null)
				this.sender.sendMessage(this.message);
		}
	}

	File getLogDirectory()
	{
		// create the log directory if it doesn't exist
		File logdir = new File(getDataFolder(), "logs");
		if (!logdir.exists()) logdir.mkdir();

		// return the reference to the log directory
		return logdir;
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
		catch (NumberFormatException e) {  };

		// default time: 6am
		return 0L;
	}

	// ************************ CUSTOM API ************************

	private static Method mAffectsSpawning = null;
	private static Method mCollidesWithEntities = null;

	static
	{
		try
		{
			mAffectsSpawning = HumanEntity.class.getDeclaredMethod("setAffectsSpawning", boolean.class);
			mCollidesWithEntities = Player.class.getDeclaredMethod("setCollidesWithEntities", boolean.class);
		}
		catch (Exception e) {  }
	}

	/**
	 * Checks if AutoReferee is installed on a system supporting the SportBukkit API
	 *
	 * @return true if SportBukkit is installed, false otherwise
	 * @see http://www.github.com/rmct/SportBukkit
	 */
	public static boolean hasSportBukkitApi()
	{ return mAffectsSpawning != null && mCollidesWithEntities != null; }

	/**
	 * Sets whether player affects spawning via natural spawn and mob spawners.
	 * Uses last_username's affects-spawning API from SportBukkit
	 *
	 * @param affectsSpawning Set whether player affects spawning
	 * @see http://www.github.com/rmct/SportBukkit
	 */
	public static void setAffectsSpawning(Player player, boolean affectsSpawning)
	{
		if (mAffectsSpawning != null) try
		{ mAffectsSpawning.invoke(player, affectsSpawning); }
		catch (Exception e) {  }
	}

	/**
	 * Sets whether player collides with entities, including items and arrows.
	 * Uses last_username's collides-with-entities API from SportBukkit
	 *
	 * @param collidesWithEntities Set whether player collides with entities
	 * @see http://www.github.com/rmct/SportBukkit
	 */
	public static void setCollidesWithEntities(Player player, boolean collidesWithEntities)
	{
		if (mCollidesWithEntities != null) try
		{ mCollidesWithEntities.invoke(player, collidesWithEntities); }
		catch (Exception e) {  }
	}
}
