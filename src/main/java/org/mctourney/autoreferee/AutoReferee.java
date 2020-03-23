package org.mctourney.autoreferee;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;

import org.mcstats.Metrics;
import org.mcstats.Metrics.Graph;

import org.mctourney.autoreferee.commands.AdminCommands;
import org.mctourney.autoreferee.commands.ConfigurationCommands;
import org.mctourney.autoreferee.commands.PlayerCommands;
import org.mctourney.autoreferee.commands.PracticeCommands;
import org.mctourney.autoreferee.commands.ScoreboardCommands;
import org.mctourney.autoreferee.commands.SpectatorCommands;
import org.mctourney.autoreferee.entity.EntityAREnderPearl;
import org.mctourney.autoreferee.listeners.CombatListener;
import org.mctourney.autoreferee.listeners.ObjectiveTracker;
import org.mctourney.autoreferee.listeners.ObjectiveTracer;
import org.mctourney.autoreferee.listeners.SpectatorListener;
import org.mctourney.autoreferee.listeners.TeamListener;
import org.mctourney.autoreferee.listeners.WorldListener;
import org.mctourney.autoreferee.listeners.ZoneListener;
import org.mctourney.autoreferee.listeners.lobby.LobbyListener;
import org.mctourney.autoreferee.listeners.lobby.LobbyListener.LobbyMode;
import org.mctourney.autoreferee.util.NullChunkGenerator;
import org.mctourney.autoreferee.util.SportBukkitUtil;
import org.mctourney.autoreferee.util.commands.CommandManager;
import org.mctourney.autoreferee.util.metrics.PieChartGraph;

import com.google.common.collect.Lists;
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

	// AutoReferee internal properties
	private static Properties properties = null;

	/**
	 * Gets the singleton instance of AutoReferee
	 *
	 * @return AutoReferee instance
	 */
	public static AutoReferee getInstance()
	{ return instance; }

	public static void log(String msg, Level level)
	{ getInstance().getLogger().log(level, ChatColor.stripColor(msg)); }

	public static void log(String msg)
	{ log(msg, Level.INFO); }

	public String getCommit()
	{ return properties == null ? "??" : properties.getProperty("git-sha-1"); }

	// expected configuration version number
	private static final int PLUGIN_CFG_VERSION = 2;

	// plugin channel encoding
	public static final String PLUGIN_CHANNEL_ENC = "UTF-8";

	// plugin channel prefix - identifies all channels as belonging to AutoReferee
	private static final String PLUGIN_CHANNEL_PREFIX = "autoref:";

	// plugin channels (referee)
	public static final String REFEREE_PLUGIN_CHANNEL = PLUGIN_CHANNEL_PREFIX + "referee";
	private SpectatorListener refChannelListener = null;

	// name of the stored map configuration file
	public static final String CFG_FILENAME = "autoreferee.xml";

	// prefix for temporary worlds
	public static final String WORLD_PREFIX = "world-autoref-";

	// messages
	public static final String NO_LOGIN_MESSAGE = "You are not scheduled for a match on this server.";
	public static final String COMPLETED_KICK_MESSAGE = "Thank you for playing!";
	public static final String NO_WEBSTATS_MESSAGE = "An error has occured; no webstats will be generated.";

	// command manager
	protected CommandManager commandManager = null;

	public CommandManager getCommandManager()
	{ return this.commandManager; }

	public static void callEvent(Event event)
	{ Bukkit.getServer().getPluginManager().callEvent(event); }

	private World lobby = null;

	/**
	 * Gets the world designated as the lobby world. When the server is in automated mode, this world
	 * is where users will be teleported to when the a match is unloaded and cleaned up.
	 *
	 * @return lobby world
	 */
	public World getLobbyWorld()
	{
		if (lobby == null)
		{
			String lname = getConfig().getString("lobby.world", null);
			if (lname != null) this.setLobbyWorld(Bukkit.getWorld(lname));
		}
		return lobby;
	}

	/**
	 * Sets the world designated as the lobby world.
	 *
	 * @param world lobby world
	 */
	public void setLobbyWorld(World world)
	{
		if (world != null && !AutoRefMatch.isCompatible(world))
		{
			// change the lobby world
			this.lobby = world;

			// reflect this change in the config file
			this.getConfig().set("lobby.world", this.lobby.getName());
			this.saveConfig();
		}
	}

	private boolean consoleLog = true;
	private boolean consoleLogInColor = true;

	protected boolean isConsoleLoggingEnabled()
	{ return consoleLog; }

	protected boolean isColoredConsoleLoggingEnabled()
	{ return consoleLogInColor; }

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

	// track owner of a piece of tnt (perhaps with propagation)
	private Map<UUID, AutoRefPlayer> tntOwner = Maps.newHashMap();

	/**
	 * Gets the player responsible for a primed TNT.
	 * @param entity primed tnt entity
	 */
	public AutoRefPlayer getTNTOwner(Entity entity)
	{ return tntOwner.get(entity.getUniqueId()); }

	/**
	 * Sets the player responsible for a primed TNT.
	 * @param entity primed tnt entity
	 * @param apl player responsible for tnt
	 */
	public void setTNTOwner(Entity entity, AutoRefPlayer apl)
	{
		if (entity.getType() == EntityType.PRIMED_TNT)
			tntOwner.put(entity.getUniqueId(), apl);
	}

	/**
	 * Clears out a primed TNT from the tracked list.
	 * @param entity primed tnt entity
	 */
	public AutoRefPlayer clearTNTOwner(Entity entity)
	{ return tntOwner.remove(entity.getUniqueId()); }

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
				if (team.getExpectedPlayers().contains(player.getName()))
					return team;
		}

		// this player is expected on no known teams
		return null;
	}

	public void onEnable()
	{
		// store the singleton instance
		instance = this;
		updateConfigurationFile();

		// ensure we are dealing with the right type of config file
		int configVersion = getConfig().getInt("config-version", PLUGIN_CFG_VERSION);
		if (configVersion != PLUGIN_CFG_VERSION) getLogger().severe(String.format(
			"!!! Incorrect config-version (expected %d, got %d)", PLUGIN_CFG_VERSION, configVersion));

		// get properties file data
		InputStream propStream = getResource("autoreferee.properties");
		if (propStream != null) try
		{
			properties = new Properties();
			properties.load(propStream);
		}
		catch (IOException e)
		{ AutoReferee.log("Failed to load properties file.", Level.SEVERE); }

		PluginManager pm = getServer().getPluginManager();
		PracticeCommands practice = new PracticeCommands(this);

		String lobbymode = getConfig().getString("lobby.mode", "manual");
		LobbyListener lobbyListener = LobbyMode.fromConfig(lobbymode).getInstance(this);

		// listener utility classes, subdivided for organization
		pm.registerEvents(new TeamListener(this), this);
		pm.registerEvents(new CombatListener(this), this);
		pm.registerEvents(new ZoneListener(this), this);
		pm.registerEvents(new WorldListener(this), this);
		pm.registerEvents(new ObjectiveTracker(this), this);
		pm.registerEvents(new ObjectiveTracer(this), this);

		// save this reference to use for setting up the referee channel later
		pm.registerEvents(refChannelListener = new SpectatorListener(this), this);

		pm.registerEvents(lobbyListener, this);
		pm.registerEvents(practice, this);

		// user interface commands in a custom command manager
		commandManager = new CommandManager();
		commandManager.registerCommands(new PlayerCommands(this), this);
		commandManager.registerCommands(new AdminCommands(this), this);
		commandManager.registerCommands(new SpectatorCommands(this), this);
		commandManager.registerCommands(new ConfigurationCommands(this), this);
		commandManager.registerCommands(new ScoreboardCommands(this), this);

		commandManager.registerCommands(lobbyListener, this);
		commandManager.registerCommands(practice, this);

		// go ahead and generate all help for the plugin
		commandManager.generateHelp(this);

		// global configuration object (can't be changed, so don't save onDisable)
		InputStream configInputStream = getResource("defaults/config.yml");
		if (configInputStream != null) getConfig().setDefaults(
			YamlConfiguration.loadConfiguration(configInputStream));
		getConfig().options().copyDefaults(true); saveConfig();

		// setup a possible alternate map repository
		String mrepo = this.getConfig().getString("map-repo", null);
		if (mrepo != null) AutoRefMatch.changeMapRepo(mrepo);

		// attempt to setup the plugin channels
		setupPluginChannels();

		// fire up the plugin metrics
		try { setupPluginMetrics(); }
		catch (IOException e) { getLogger().severe("Plugin Metrics not enabled."); }

		// wrap up, debug to follow this message
		getLogger().info(this.getName() + " (" + Bukkit.getName() + ") loaded successfully" +
			(SportBukkitUtil.hasSportBukkitApi() ? " with SportBukkit API" : "") + ".");
		getLogger().info(this.getName() + " Git: " + this.getCommit());

		// are ties allowed? (auto-mode always allows for ties)
		AutoRefMatch.setAllowTies(getConfig().getBoolean("allow-ties", false));

		// log messages to console?
		consoleLog = getConfig().getBoolean("console-log", true);
		consoleLogInColor = getConfig().getBoolean("console-colors", true);

		// experimental mode?
		if(this.isExperimentalMode()) {
			getLogger().info(this.getName() + " loaded in Experimental Mode. This is not intended for regular use!");
			
			if(EntityAREnderPearl.patch())
				getLogger().info("Successfully patched EntityEnderPearl!");
			else
				getLogger().severe("Failed to patch EntityEnderPearl! Please let a dev know about this.");
		}
		
		// setup the map library folder
		AutoRefMap.getMapLibrary();

		// process initial world(s), just in case
		for ( World w : getServer().getWorlds() )
			AutoRefMatch.setupWorld(w, false);

		// update maps automatically if auto-update is enabled
		if (getConfig().getBoolean("auto-update", true))
			AutoRefMap.getUpdates(Bukkit.getConsoleSender(), false);
	}

	public void onDisable()
	{
		for (AutoRefMatch match : getMatches())
			match.saveWorldConfiguration();
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

	private void setupPluginMetrics()
		throws IOException
	{
		Metrics metrics = new Metrics(this);

		Set<String> mapNames = Sets.newHashSet();
		for (AutoRefMap map : AutoRefMap.getRemoteMaps())
			mapNames.add(map.getName());

		Graph gMaps = metrics.createGraph("Most Popular Maps");
		playedMapsTracker = new PieChartGraph(gMaps, mapNames);

		metrics.start();
	}

	public static WorldEditPlugin getWorldEdit()
	{
		Plugin x = Bukkit.getPluginManager().getPlugin("WorldEdit");
		return (x != null && x instanceof WorldEditPlugin) ? (WorldEditPlugin) x : null;
	}

	private UUID consoleWorld = null;

	/**
	 * Gets the world that the console user has selected.
	 *
	 * @return The world selected by the console user
	 */
	public World getConsoleWorld()
	{
		return consoleWorld == null || getServer().getWorld(consoleWorld) == null
			? getServer().getWorlds().get(0) : getServer().getWorld(consoleWorld);
	}

	/**
	 * Sets the world that the console user has selected.
	 */
	public void setConsoleWorld(World world)
	{ consoleWorld = world == null ? null : world.getUID(); }

	/**
	 * Sets the world that the console user has selected.
	 */
	public void setConsoleWorld(String name)
	{
		World world = getServer().getWorld(name);
		if (world == null)
		{
			Player player = getServer().getPlayer(name);
			if (player != null) world = player.getWorld();
		}

		this.setConsoleWorld(world);
	}

	/**
	 * Gets the world associated with a command sender.
	 */
	public World getSenderWorld(CommandSender sender)
	{
		if (sender instanceof Player)
			return ((Player) sender).getWorld();
		if (sender instanceof BlockCommandSender)
			return ((BlockCommandSender) sender).getBlock().getWorld();
		return getConsoleWorld();
	}

	public Scoreboard getWorldScoreboard(World world)
	{
		AutoRefMatch match = this.getMatch(world);
		if (match == null || match.getScoreboard() == null)
			return Bukkit.getScoreboardManager().getMainScoreboard();
		else return match.getScoreboard();
	}

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldname, String id)
	{ return new NullChunkGenerator(); }

	private SyncMessageTask messageQueue = new SyncMessageTask();

	/**
	 * Force a message to be sent synchronously. Safe to use from an asynchronous task.
	 *
	 * @param msgs messages to be sent
	 */
	public void sendMessageSync(CommandSender recipient, String ...msgs)
	{
		if (recipient != null) for (String msg : msgs)
			messageQueue.addMessage(recipient, msg);

		try { messageQueue.runTask(this); }
		catch (IllegalStateException ignored) {  }
	}

	/**
	 * Get whether server is in experimental mode or not
	 * @author char
	 * 
	 * @return Whether server is in experimental mode
	 */
	public boolean isExperimentalMode() {
		return this.getConfig().getBoolean("experimental-mode", false);
	}
	
	/**
	 * Get whether to create scoreboard or not
	 * @author char
	 *
	 * @return if to create scoreboard
	 */
	public boolean doScoreboard() {
		return this.getConfig().getBoolean("scoreboard", true);
	}
	
	private class SyncMessageTask extends BukkitRunnable
	{
		private class RoutedMessage
		{
			public CommandSender recipient;
			public String message;

			public RoutedMessage(CommandSender r, String m)
			{ recipient = r; message = m; }
		}

		private List<RoutedMessage> msgQueue = Lists.newLinkedList();

		public SyncMessageTask addMessage(CommandSender recipient, String message)
		{ msgQueue.add(new RoutedMessage(recipient, message)); return this; }

		@Override public void run()
		{
			AutoReferee.this.messageQueue = new SyncMessageTask();
			for (RoutedMessage msg : msgQueue) if (msg.recipient != null)
				msg.recipient.sendMessage(msg.message);
			msgQueue.clear();
		}
	}

	// this is a catch-all to make general upgrades to old configuration files
	private void updateConfigurationFile()
	{
		FileConfiguration config = getConfig();

		// v2.2, 2013/05/04, remove remains of server mode
		if (config.get("server-mode") != null)
			config.set("server-mode", null);

		// v2.3, 2013/05/17, update lobby info
		if (config.getString("lobby-world") != null)
		{
			// set new parameter paths
			config.set("lobby.world", config.getString("lobby-world"));
			config.set("lobby.mode", "manual");

			// remove old configuration flag
			config.set("lobby-world", null);
		}

		// v2.3, 2013/06/03, allow for lobby mode enum (not "auto" boolean)
		if (config.getString("lobby.auto") != null)
		{
			boolean autoMode = config.getBoolean("lobby.auto", false);
			config.set("lobby.mode", autoMode ? "auto" : "manual");
			config.set("lobby.auto", null);
		}
	}
}
