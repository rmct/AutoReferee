package org.mctourney.AutoReferee;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrMatcher;
import org.apache.commons.lang.text.StrTokenizer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.conversations.BooleanPrompt;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;

import org.mctourney.AutoReferee.AutoRefMatch.MatchStatus;
import org.mctourney.AutoReferee.listeners.ObjectiveTracker;
import org.mctourney.AutoReferee.listeners.PlayerVersusPlayerListener;
import org.mctourney.AutoReferee.listeners.RefereeChannelListener;
import org.mctourney.AutoReferee.listeners.TeamListener;
import org.mctourney.AutoReferee.listeners.WorldListener;
import org.mctourney.AutoReferee.listeners.ZoneListener;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.BlockVector3;
import org.mctourney.AutoReferee.util.CuboidRegion;
import org.mctourney.AutoReferee.util.Vector3;
import org.mctourney.AutoReferee.util.TeleportationUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class AutoReferee extends JavaPlugin
{
	// singleton instance
	private static AutoReferee instance = null;

	public static AutoReferee getInstance()
	{ return instance; }

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
	public static final String CFG_FILENAME = "autoreferee.yml";

	// prefix for temporary worlds
	public static final String WORLD_PREFIX = "world-autoref-";

	// messages
	public static final String NO_LOGIN_MESSAGE = "You are not scheduled for a match on this server.";
	public static final String COMPLETED_KICK_MESSAGE = "Thank you for playing!";
	public static final String NO_WEBSTATS_MESSAGE = "An error has occured; no webstats will be generated.";

	// query server object
	public QueryServer qserv = null;

	private World lobby = null;

	public World getLobbyWorld()
	{ return lobby; }

	public void setLobbyWorld(World w)
	{ this.lobby = w; }

	// is this plugin in online mode?
	private boolean autoMode = true;

	public boolean isAutoMode()
	{ return autoMode; }

	public boolean setAutoMode(boolean m)
	{ return this.autoMode = m; }

	public boolean consoleLog = false;

	// get the match associated with the world
	private Map<UUID, AutoRefMatch> matches = Maps.newHashMap();

	public AutoRefMatch getMatch(World w)
	{ return w != null ? matches.get(w.getUID()) : null; }

	public Collection<AutoRefMatch> getMatches()
	{ return matches.values(); }

	public void addMatch(AutoRefMatch match)
	{ matches.put(match.getWorld().getUID(), match); }

	public void clearMatch(AutoRefMatch match)
	{ matches.remove(match.getWorld().getUID()); }

	public AutoRefTeam getTeam(Player pl)
	{
		// go through all the matches
		for (AutoRefMatch match : matches.values())
		{
			// if the player is on a team for this match...
			AutoRefTeam team = match.getPlayerTeam(pl);
			if (team != null) return team;
		}

		// this player is on no known teams
		return null;
	}

	public AutoRefTeam getExpectedTeam(Player pl)
	{
		AutoRefTeam actualTeam = getTeam(pl);
		if (actualTeam != null) return actualTeam;

		// go through all the matches
		for (AutoRefMatch match : matches.values())
		{
			// if the player is expected for any of these teams
			for (AutoRefTeam team : match.getTeams())
				if (team.getExpectedPlayers().contains(pl))
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

		// events related to team management, chat, whitelists, respawn
		pm.registerEvents(new TeamListener(this), this);

		// events related to PvP, damage, death, mobs
		pm.registerEvents(new PlayerVersusPlayerListener(this), this);

		// events related to safe zones, creating zones, map conditions
		pm.registerEvents(new ZoneListener(this), this);

		// events related to worlds
		pm.registerEvents(new WorldListener(this), this);

		// events related to tracking objectives during a match
		pm.registerEvents(new ObjectiveTracker(this), this);

		// events related to referee channel
		pm.registerEvents(refChannelListener = new RefereeChannelListener(this), this);

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

		// wrap up, debug to follow this message
		getLogger().info(this.getName() + " loaded successfully" +
			(hasSportBukkitApi() ? " with SportBukkit API" : "") + ".");

		// save the "lobby" world as a sort of drop-zone for discharged players
		String lobbyname = getConfig().getString("lobby-world", null);
		World lobbyworld = lobbyname == null ? null : getServer().getWorld(lobbyname);
		setLobbyWorld(lobbyworld == null ? getServer().getWorlds().get(0) : lobbyworld);

		// connect to server, or let the server operator know to set up the match manually
		if (!makeServerConnection(qurl))
			getLogger().info(this.getName() + " is running in OFFLINE mode. All setup must be done manually.");

		// update online mode to represent whether or not we have a connection
		if (isAutoMode()) setAutoMode(checkPlugins(pm));

		// are ties allowed? (auto-mode always allows for ties)
		AutoRefMatch.allowTies = isAutoMode() || getConfig().getBoolean("allow-ties", false);

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
	}

	public boolean makeServerConnection(String qurl)
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

	public void playerDone(Player p)
	{
		// take them back to the lobby, one way or another
		if (p.getWorld() != getLobbyWorld())
		{
			p.setGameMode(WorldListener.getDefaultGamemode(getLobbyWorld()));
			p.teleport(getLobbyWorld().getSpawnLocation());
		}

		// if the server is in online mode, remove them as well
		if (isAutoMode()) p.kickPlayer(AutoReferee.COMPLETED_KICK_MESSAGE);
	}

	private WorldEditPlugin getWorldEdit()
	{
		Plugin plugin = getServer().getPluginManager().getPlugin("WorldEdit");

		// WorldEdit may not be loaded
		if (plugin == null || !(plugin instanceof WorldEditPlugin))
			return null;

		return (WorldEditPlugin) plugin;
	}

	public boolean playerWhitelisted(Player player)
	{
		if (player.hasPermission("autoreferee.admin")) return true;
		if (player.hasPermission("autoreferee.referee")) return true;
		return getExpectedTeam(player) != null;
	}

	// save previous location before teleport
	private Map<String, Location> prevLocation = Maps.newHashMap();

	private World consoleWorld = null;
	public World getConsoleWorld()
	{
		List<World> worlds = getServer().getWorlds();
		return worlds.size() == 1 ? worlds.get(0) : consoleWorld;
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		World world = null;
		Player player = null;

		if (sender instanceof Player)
		{
			player = (Player) sender;
			world = player.getWorld();
		}
		else world = getConsoleWorld();
		AutoRefMatch match = getMatch(world);

		// reparse the args properly using the string tokenizer from org.apache.commons
		args = new StrTokenizer(StringUtils.join(args, ' '), StrMatcher.splitMatcher(),
			StrMatcher.quoteMatcher()).setTrimmerMatcher(StrMatcher.trimMatcher()).getTokenArray();

		if ("autoref".equalsIgnoreCase(cmd.getName()) && sender.hasPermission("autoreferee.configure"))
		{
			// CMD: /autoref cfg ...
			if (args.length > 1 && "cfg".equalsIgnoreCase(args[0]))
			{
				// CMD: /autoref cfg save
				if (args.length >= 2 && "save".equalsIgnoreCase(args[1]) && match != null)
				{
					match.saveWorldConfiguration();
					sender.sendMessage(ChatColor.GREEN + CFG_FILENAME + " saved.");
				}

				// CMD: /autoref init
				if (args.length >= 2 && "init".equalsIgnoreCase(args[1]))
				{
					// if there is not yet a match object for this map
					if (match == null)
					{
						addMatch(match = new AutoRefMatch(world, false));
						match.saveWorldConfiguration();
						match.setCurrentState(MatchStatus.NONE);

						sender.sendMessage(ChatColor.GREEN + CFG_FILENAME + " generated.");
					}
					else sender.sendMessage(this.getName() + " already initialized for " +
						match.worldConfig.getString("map.name", "this map") + ".");
				}

				// CMD: /autoref cfg reload
				if (args.length >= 2 && "reload".equalsIgnoreCase(args[1]) && match != null)
				{
					match.reload();
					sender.sendMessage(ChatColor.GREEN + CFG_FILENAME + " reload complete!");
				}

				return true;
			}

			// CMD: /autoref archive [zip]
			if (args.length >= 1 && "archive".equalsIgnoreCase(args[0]) && match != null) try
			{
				// LAST MINUTE CLEANUP!!!
				match.clearEntities();
				world.setTime(match.getStartTime());

				// save the world and configuration first
				world.save();
				match.saveWorldConfiguration();

				File archiveFolder = null;
				if (args.length >= 2 && "zip".equalsIgnoreCase(args[1]))
					archiveFolder = match.distributeMap();
				else archiveFolder = match.archiveMapData();

				String resp = String.format("%s %s", match.getVersionString(),
					archiveFolder == null ? "failed to archive." : "archived!");
				sender.sendMessage(ChatColor.GREEN + resp); getLogger().info(resp);
				return true;
			}
			catch (Exception e) { return false; }

			// CMD: /autoref debug [console]
			if (args.length >= 1 && "debug".equalsIgnoreCase(args[0]) && match != null)
			{
				if (match.isDebugMode())
				{ match.setDebug(null); return true; }

				boolean consoleDebug = args.length >= 2 && "console".equalsIgnoreCase(args[1]);
				match.setDebug(consoleDebug ? getServer().getConsoleSender() : sender);

				return true;
			}

			// CMD: /autoref tool <type>
			if (args.length >= 1 && "tool".equalsIgnoreCase(args[0]))
			{
				// get the tool for setting win condition
				if (args.length >= 2 && "wincond".equalsIgnoreCase(args[1]))
				{
					// get the tool used to set the win conditions
					int toolID = ZoneListener.parseTool(getConfig().getString(
						"config-mode.tools.win-condition", null), Material.GOLD_SPADE);
					ItemStack toolitem = new ItemStack(toolID);

					// add to the inventory and switch to holding it
					PlayerInventory inv = player.getInventory();
					inv.addItem(toolitem);

					// let the player know what the tool is and how to use it
					sender.sendMessage("Given win condition tool: " + toolitem.getType().name());
					sender.sendMessage("Right-click on a block to set it as a win-condition.");
					return true;
				}
				// get the tool for setting starting mechanisms
				if (args.length >= 2 && "startmech".equalsIgnoreCase(args[1]))
				{
					// get the tool used to set the starting mechanisms
					int toolID = ZoneListener.parseTool(getConfig().getString(
						"config-mode.tools.start-mechanism", null), Material.GOLD_AXE);
					ItemStack toolitem = new ItemStack(toolID);

					// add to the inventory and switch to holding it
					PlayerInventory inv = player.getInventory();
					inv.addItem(toolitem);

					// let the player know what the tool is and how to use it
					sender.sendMessage("Given start mechanism tool: " + toolitem.getType().name());
					sender.sendMessage("Right-click on a device to set it as a starting mechanism.");
					return true;
				}
				// get the tool for setting protected entities
				if (args.length >= 2 && "protect".equalsIgnoreCase(args[1]))
				{
					// get the tool used to set the starting mechanisms
					int toolID = ZoneListener.parseTool(getConfig().getString(
						"config-mode.tools.protect-entities", null), Material.GOLD_SWORD);
					ItemStack toolitem = new ItemStack(toolID);

					// add to the inventory and switch to holding it
					PlayerInventory inv = player.getInventory();
					inv.addItem(toolitem);

					// let the player know what the tool is and how to use it
					sender.sendMessage("Given entity protection tool: " + toolitem.getType().name());
					sender.sendMessage("Right-click on an entity to protect it from butchering.");
					return true;
				}
			}

			// CMD: /autoref nocraft
			if (args.length >= 1 && "nocraft".equalsIgnoreCase(args[0]) && match != null)
			{
				ItemStack item = player.getItemInHand();
				if (item != null) match.addIllegalCraft(BlockData.fromItemStack(item));
				return true;
			}

			// CMD: /autoref setspawn <team>
			if (args.length >= 2 && "setspawn".equalsIgnoreCase(args[0]) && match != null && player != null)
			{
				AutoRefTeam team = match.teamNameLookup(args[1]);
				if (team == null)
				{
					// team name is invalid. let the player know
					sender.sendMessage(ChatColor.DARK_GRAY + args[1] +
						ChatColor.RESET + " is not a valid team.");
					sender.sendMessage("Teams are " + match.getTeamList());
				}
				else
				{
					team.setSpawnLocation(player.getLocation());
					String coords = BlockVector3.fromLocation(player.getLocation()).toCoords();
					sender.sendMessage(ChatColor.GRAY + "Spawn set to " +
						coords + " for " + team.getName());
				}
				return true;
			}
		}

		if ("autoref".equalsIgnoreCase(cmd.getName()) && sender.hasPermission("autoreferee.admin"))
		{
			// CMD: /autoref world <world>
			if (args.length == 2 && "world".equalsIgnoreCase(args[0]))
			{
				consoleWorld = getServer().getWorld(args[1]);
				if (consoleWorld != null) sender.sendMessage("Selected world " + consoleWorld.getName());
				return consoleWorld != null;
			}

			// CMD: /autoref load <map> [<custom>]
			if (args.length >= 2 && "load".equalsIgnoreCase(args[0])) try
			{
				// get generate a map name from the args
				String mapName = args[1];

				// may specify a custom world name as the 3rd argument
				String customName = args.length >= 3 ? args[2] : null;

				// get world setup for match
				sender.sendMessage(ChatColor.GREEN + "Please wait...");
				AutoRefMap.loadMap(sender, mapName, customName);

				return true;
			}
			catch (Exception e) { e.printStackTrace(); return true; }

			// CMD: /autoref loadurl <url> [<custom>]
			if (args.length >= 2 && "loadurl".equalsIgnoreCase(args[0])) try
			{
				// may specify a custom world name as the 3rd argument
				String customName = args.length >= 3 ? args[2] : null;

				// get world setup for match
				sender.sendMessage(ChatColor.GREEN + "Please wait...");
				AutoRefMap.loadMapFromURL(sender, args[1], customName);

				return true;
			}
			catch (Exception e) { e.printStackTrace(); return true; }

			// CMD: /autoref unload
			if (args.length == 1 && "unload".equalsIgnoreCase(args[0]) && match != null)
			{
				match.destroy();
				return true;
			}

			// CMD: /autoref reload
			if (args.length == 1 && "reload".equalsIgnoreCase(args[0]) && match != null) try
			{
				AutoRefMap map = AutoRefMap.getMap(match.getMapName());
				if (map == null || !map.isInstalled())
				{
					sender.sendMessage(ChatColor.DARK_GRAY +
						"No archive of this map exists " + match.getMapName());
					return true;
				}

				sender.sendMessage(ChatColor.DARK_GRAY +
					"Preparing a new copy of " + map.getVersionString());

				AutoRefMatch newmatch = AutoRefMap.createMatch(map, null);
				for (Player p : match.getWorld().getPlayers())
					p.teleport(newmatch.getWorldSpawn());

				match.destroy();
				return true;
			}
			catch (Exception e) { e.printStackTrace(); return true; }

			// CMD: /autoref maplist
			if (args.length == 1 && "maplist".equalsIgnoreCase(args[0]))
			{
				List<AutoRefMap> maps = Lists.newArrayList(AutoRefMap.getAvailableMaps());
				Collections.sort(maps);

				sender.sendMessage(ChatColor.GOLD + String.format("Available Maps (%d):", maps.size()));
				for (AutoRefMap mapInfo : maps)
				{
					ChatColor color = mapInfo.isInstalled() ? ChatColor.WHITE : ChatColor.DARK_GRAY;
					sender.sendMessage("* " + color + mapInfo.getVersionString());
				}

				return true;
			}

			// CMD: /autoref update [force]
			if (args.length >= 1 && "update".equalsIgnoreCase(args[0]))
			{
				boolean force = (args.length >= 2 && args[1].startsWith("f"));
				AutoRefMap.getUpdates(sender, force);
				return true;
			}

			// CMD: /autoref state [<new state>]
			if (args.length >= 1 && "state".equalsIgnoreCase(args[0]) &&
				match != null && match.isDebugMode()) try
			{
				if (args.length >= 2)
					match.setCurrentState(MatchStatus.valueOf(args[1].toUpperCase()));
				getLogger().info("Match Status is now " + match.getCurrentState().name());

				return true;
			}
			catch (Exception e) { return false; }

			// CMD: /autoref autoinvite <players...>
			if (args.length > 1 && "autoinvite".equalsIgnoreCase(args[0]))
			{
				for (int i = 1; i < args.length; ++i)
				{
					// first, remove this player from all expected player lists
					OfflinePlayer opl = getServer().getOfflinePlayer(args[i]);
					for (AutoRefMatch m : getMatches()) m.removeExpectedPlayer(opl);

					// add them to the expected players list
					match.addExpectedPlayer(opl);

					// if this player cannot be found, skip
					Player invited = getServer().getPlayer(args[i]);
					if (invited == null) continue;

					// if this player is already busy competing in a match, skip
					AutoRefMatch m = getMatch(invited.getWorld());
					if (m != null && m.isPlayer(invited) && m.getCurrentState().inProgress()) continue;

					// otherwise, let's drag them in (no asking)
					match.acceptInvitation(invited);
				}

				return true;
			}

			// CMD: /autoref send <msg> [<recipient>]
			if (args.length >= 2 && "send".equalsIgnoreCase(args[0]) &&
				match != null && match.isDebugMode())
			{
				Set<Player> targets = match.getReferees();
				if (args.length >= 3) targets = Sets.newHashSet(getServer().getPlayer(args[2]));

				for (Player ref : targets) if (ref != null) ref.sendPluginMessage(this,
					AutoReferee.REFEREE_PLUGIN_CHANNEL, args[1].getBytes());

				return true;
			}
		}

		if ("autoref".equalsIgnoreCase(cmd.getName()) && sender.hasPermission("autoreferee.referee"))
		{
			// CMD: /autoref teamname <currentname> <newname>
			if (args.length == 3 && "teamname".equalsIgnoreCase(args[0]) && match != null)
			{
				AutoRefTeam team = match.teamNameLookup(args[1]);
				if (team == null)
				{
					sender.sendMessage(ChatColor.DARK_GRAY + args[1] +
						ChatColor.RESET + " is not a valid team.");
					sender.sendMessage("Teams are " + match.getTeamList());
				}
				else team.setName(args[2]);
				return true;
			}

			// CMD: /autoref hud ...
			if (args.length >= 1 && "hud".equalsIgnoreCase(args[0]) && match != null)
			{
				// CMD: /autoref hud swap
				if (args.length >= 2 && "swap".equalsIgnoreCase(args[1]))
				{
					match.messageReferee(player, "match", match.getWorld().getName(), "swap");
					return true;
				}
			}

			// CMD: /autoref swapteams <team1> <team2>
			if (args.length == 3 && "swapteams".equalsIgnoreCase(args[0]) && match != null)
			{
				AutoRefTeam team1 = match.teamNameLookup(args[1]);
				AutoRefTeam team2 = match.teamNameLookup(args[2]);

				if (team1 == null)
				{
					sender.sendMessage(ChatColor.DARK_GRAY + args[1] +
						ChatColor.RESET + " is not a valid team.");
					sender.sendMessage("Teams are " + match.getTeamList());
				}
				else if (team2 == null)
				{
					sender.sendMessage(ChatColor.DARK_GRAY + args[2] +
						ChatColor.RESET + " is not a valid team.");
					sender.sendMessage("Teams are " + match.getTeamList());
				}
				else AutoRefTeam.switchTeams(team1, team2);
				return true;
			}

			// CMD: /autoref countdown [<seconds>]
			if (args.length >= 1 && "countdown".equalsIgnoreCase(args[0]) && match != null)
			{
				int sec = 3;
				if (args.length >= 2)
					try { sec = Integer.parseInt(args[1]); }
					catch (NumberFormatException e) {  }

				match.startCountdown(sec, false);
				return true;
			}

			// CMD: /autoref timelimit <min>
			if (args.length == 2 && "timelimit".equalsIgnoreCase(args[0]) && match != null)
			{
				long time = -1L;
				if (!"none".equalsIgnoreCase(args[1]))
					try { time = Integer.parseInt(args[1]) * 60L; }
					catch (NumberFormatException e) { return true; }

				// set the time limit
				match.setTimeLimit(time);
				return true;
			}

			// CMD: /autoref endmatch [<winner>]
			if (args.length >= 1 && "endmatch".equalsIgnoreCase(args[0]) && match != null)
			{
				if (args.length >= 2)
				{
					if ("none".equalsIgnoreCase(args[1]) || "tie".equalsIgnoreCase(args[1]))
						match.matchComplete(null);
					else match.matchComplete(match.teamNameLookup(args[1]));
				}
				else match.matchComplete();
				return true;
			}
		}

		if ("autoref".equalsIgnoreCase(cmd.getName()))
		{
			// CMD: /autoref invite <players...>
			if (args.length > 1 && "invite".equalsIgnoreCase(args[0]) &&
				match != null && match.getCurrentState().isBeforeMatch())
			{
				// who is doing the inviting
				String from = (sender instanceof Player) ? match.getPlayerName(player) : "This server";

				for (int i = 1; i < args.length; ++i)
				{
					// if this player cannot be found, skip
					Player invited = getServer().getPlayer(args[i]);
					if (invited == null) continue;

					// if this player is already busy competing in a match, skip
					AutoRefMatch m = getMatch(invited.getWorld());
					if (m != null && m.isPlayer(invited) && m.getCurrentState().inProgress()) continue;

					// otherwise, invite them
					if (invited.getWorld() != match.getWorld())
						new Conversation(this, invited, new InvitationPrompt(match, from)).begin();
				}

				return true;
			}

			// CMD: /autoref version
			if (args.length >= 1 && "version".equalsIgnoreCase(args[0]))
			{
				sender.sendMessage(ChatColor.DARK_GRAY + "This server is running " +
					ChatColor.BLUE + this.getDescription().getFullName());
				return true;
			}

			// CMD: /autoref getrole <player>
			if (args.length >= 2 && "getrole".equalsIgnoreCase(args[0]))
			{
				AutoRefRole role = getRole(args[1]);
				sender.sendMessage(ChatColor.RED + args[1] + "'s role is: " + role);
				return true;
			}
		}

		if ("zones".equalsIgnoreCase(cmd.getName()) && match != null)
		{
			Set<AutoRefTeam> lookupTeams = null;

			// if a team has been specified as an argument
			if (args.length > 1)
			{
				AutoRefTeam t = match.teamNameLookup(args[1]);
				if (t == null)
				{
					// team name is invalid. let the player know
					sender.sendMessage(ChatColor.DARK_GRAY + args[1] +
						ChatColor.RESET + " is not a valid team.");
					return true;
				}

				lookupTeams = Sets.newHashSet();
				lookupTeams.add(t);
			}

			// otherwise, just print all the teams
			else lookupTeams = match.getTeams();

			// sanity check...
			if (lookupTeams == null) return false;

			// for all the teams being looked up
			for (AutoRefTeam team : lookupTeams)
			{
				// print team-name header
				sender.sendMessage(team.getName() + "'s Regions:");

				// print all the regions owned by this team
				if (team.getRegions().size() > 0) for (CuboidRegion reg : team.getRegions())
				{
					Vector3 mn = reg.getMinimumPoint(), mx = reg.getMaximumPoint();
					sender.sendMessage("  (" + mn.toCoords() + ") => (" + mx.toCoords() + ")");
				}

				// if there are no regions, print None
				else sender.sendMessage("  <None>");
			}

			return true;
		}

		if ("zone".equalsIgnoreCase(cmd.getName()) && match != null)
		{
			WorldEditPlugin worldEdit = getWorldEdit();
			if (worldEdit == null)
			{
				// world edit not installed
				sender.sendMessage("This method requires WorldEdit installed and running.");
				return true;
			}

			if (args.length == 0)
			{
				// command is invalid. let the player know
				sender.sendMessage("Must specify a team as this zone's owner.");
				return true;
			}

			// START is a sentinel Team object representing the start region
			AutoRefTeam t, START = new AutoRefTeam(); String tname = args[0];
			t = "start".equals(tname) ? START : match.teamNameLookup(tname);

			if (t == null)
			{
				// team name is invalid. let the player know
				sender.sendMessage(ChatColor.DARK_GRAY + tname +
					ChatColor.RESET + " is not a valid team.");
				sender.sendMessage("Teams are " + match.getTeamList());
				return true;
			}

			Selection sel = worldEdit.getSelection(player);
			if ((sel instanceof CuboidSelection))
			{
				CuboidSelection csel = (CuboidSelection) sel;
				CuboidRegion reg = new CuboidRegion(
					new Vector3(csel.getNativeMinimumPoint()),
					new Vector3(csel.getNativeMaximumPoint())
				);

				// sentinel value represents the start region
				if (t == START)
				{
					// set the start region to the selection
					match.setStartRegion(reg);
					sender.sendMessage("Region now marked as " +
						"the start region!");
				}
				else
				{
					AutoRefRegion areg = new AutoRefRegion(reg);
					if (args.length >= 2) for (String f : args[1].split(",")) areg.toggle(f);

					// add the region to the team, announce
					t.getRegions().add(areg);
					sender.sendMessage("Region now marked as " +
						t.getName() + "'s zone! " + areg.getFlags());
				}
			}
			return true;
		}

		if ("matchinfo".equalsIgnoreCase(cmd.getName()))
		{
			if (match != null) match.sendMatchInfo(player);
			else sender.sendMessage(ChatColor.GRAY +
				this.getName() + " is not running for this world!");

			return true;
		}

		if ("jointeam".equalsIgnoreCase(cmd.getName()) && match != null && !isAutoMode())
		{
			// get the target team
			AutoRefTeam team = args.length > 0 ? match.teamNameLookup(args[0]) :
				match.getArbitraryTeam();

			if (team == null)
			{
				// team name is invalid. let the player know
				if (args.length > 0)
				{
					sender.sendMessage(ChatColor.DARK_GRAY + args[0] +
						ChatColor.RESET + " is not a valid team.");
					sender.sendMessage("Teams are " + match.getTeamList());
				}
				return true;
			}

			// if there are players specified on the command line, add them
			if (args.length >= 2 && sender.hasPermission("autoreferee.referee"))
				for (int i = 1; i < args.length; ++i)
			{
				Player target = getServer().getPlayer(args[i]);
				if (target != null) match.joinTeam(target, team, true);
			}

			// otherwise, add yourself
			else match.joinTeam(player, team, sender.hasPermission("autoreferee.referee"));
			return true;
		}

		if ("leaveteam".equalsIgnoreCase(cmd.getName()) && match != null && !isAutoMode())
		{
			// if there are players specified on the command line, remove them
			if (args.length >= 1 && sender.hasPermission("autoreferee.referee"))
				for (int i = 0; i < args.length; ++i)
			{
				Player target = getServer().getPlayer(args[i]);
				if (target != null) match.leaveTeam(target, true);
			}

			// otherwise, remove yourself
			else match.leaveTeam(player, sender.hasPermission("autoreferee.referee"));
			return true;
		}

		if ("viewinventory".equalsIgnoreCase(cmd.getName()) && args.length >= 0
			&& match != null && player != null)
		{
			if (!match.isReferee(player))
			{ sender.sendMessage("You do not have permission."); return true; }

			AutoRefPlayer target = args.length > 0 ? match.getPlayer(args[0])
				: match.getNearestPlayer(player.getLocation());
			if (target != null) target.showInventory(player);

			return true;
		}

		if ("ready".equalsIgnoreCase(cmd.getName()) &&
			match != null && match.getCurrentState().isBeforeMatch())
		{
			boolean rstate = true;
			if (args.length > 0)
			{
				String rstr = args[0].toLowerCase();
				rstate = !rstr.startsWith("f") && !rstr.startsWith("n");
			}

			if (match.isReferee(player))
			{
				// attempt to set the ready delay if one is specified
				if (args.length > 0) try { match.setReadyDelay(Integer.parseInt(args[0])); }
				catch (NumberFormatException e) {  };

				match.setRefereeReady(rstate);
			}
			else
			{
				AutoRefTeam team = match.getPlayerTeam(player);
				if (team != null) team.setReady(rstate);
			}

			match.checkTeamsStart();
			return true;
		}

		if ("artp".equalsIgnoreCase(cmd.getName()) && match != null && player != null)
		{
			// just dump location into this for teleporting later
			Location tplocation = null;

			if (args.length > 0)
			{
				if (args[0].startsWith("-") && args[0].length() == 2)
					switch (args[0].toLowerCase().charAt(1))
				{
					case 'b':
					{
						AutoRefPlayer apl = match.getPlayer(args[1]);
						Location bedloc = apl.getBedLocation();

						if (bedloc == null || bedloc.getBlock().getType() != Material.BED_BLOCK)
							player.sendMessage(apl.getName() + ChatColor.DARK_GRAY + " does not have a bed set.");
						else tplocation = TeleportationUtil.blockTeleport(bedloc);
						break;
					}

					case 'd':
						if (args.length > 1)
						{
							AutoRefPlayer apl = match.getPlayer(args[1]);
							if (apl != null) tplocation = apl.getLastDeathLocation();
						}
						else tplocation = match.getLastDeathLocation();
						tplocation = TeleportationUtil.locationTeleport(tplocation);
						break;

					case 'l':
						if (args.length > 1)
						{
							AutoRefPlayer apl = match.getPlayer(args[1]);
							if (apl != null) tplocation = apl.getLastLogoutLocation();
						}
						else tplocation = match.getLastLogoutLocation();
						tplocation = TeleportationUtil.locationTeleport(tplocation);
						break;

					case 't':
						if (args.length > 1)
						{
							AutoRefPlayer apl = match.getPlayer(args[1]);
							if (apl != null) tplocation = apl.getLastTeleportLocation();
						}
						else tplocation = match.getLastTeleportLocation();
						tplocation = TeleportationUtil.locationTeleport(tplocation);
						break;

					case 's':
						if (args.length > 1)
						{
							AutoRefTeam team = match.teamNameLookup(args[1]);
							if (team != null) tplocation = team.getSpawnLocation();
						}
						else tplocation = match.getWorld().getSpawnLocation();
						tplocation = TeleportationUtil.locationTeleport(tplocation);
						break;

					case 'o':
						if (args.length > 1)
						{
							AutoRefTeam team = match.teamNameLookup(args[1]);
							if (team != null) tplocation = team.getLastObjectiveLocation();
						}
						else tplocation = match.getLastObjectiveLocation();
						tplocation = TeleportationUtil.locationTeleport(tplocation);
						break;

					case 'v':
						if (args.length > 1)
						{
							AutoRefTeam team = match.teamNameLookup(args[1]);
							if (team != null) tplocation = team.getVictoryMonumentLocation();
						}
						tplocation = TeleportationUtil.locationTeleport(tplocation);
						break;

					case 'x':
						try
						{
							String[] coords = StringUtils.split(args[1], ',');
							tplocation = new Location(match.getWorld(),
								Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
							tplocation = TeleportationUtil.blockTeleport(tplocation);
						}
						catch (NumberFormatException e) {  }
						break;

					case 'r':
						// get location in lookup table, or null
						tplocation = prevLocation.get(player.getName());
						break;
				}
				else
				{
					AutoRefPlayer apl = match.getPlayer(args[0]);
					tplocation = TeleportationUtil.playerTeleport(apl);
				}
			}
			else tplocation = TeleportationUtil.locationTeleport(match.getLastNotificationLocation());

			// if we ever found a valid teleport, take it!
			if (tplocation != null)
			{
				prevLocation.put(player.getName(), player.getLocation());
				player.setFlying(true);
				player.teleport(tplocation);
			}

			return true;
		}

		return false;
	}

	public class InvitationPrompt extends BooleanPrompt
	{
		public InvitationPrompt(AutoRefMatch match, String from)
		{ this.match = match; this.from = from; }

		private AutoRefMatch match;
		private String from;

		@Override
		public String getPromptText(ConversationContext context)
		{ return String.format(">>> %s is inviting you to %s. Do you accept?", from, match.getMapName()); }

		@Override
		protected Prompt acceptValidatedInput(ConversationContext context, boolean response)
		{
			if (response && context.getForWhom() instanceof Player)
				match.acceptInvitation((Player) context.getForWhom());
			return Prompt.END_OF_CONVERSATION;
		}

	}

	public enum AutoRefRole
	{
		NONE("None"),
		PLAYER("Player"),
		SPECTATOR("Spectator"),
		REFEREE("Referee"),
		STREAMER("Streamer"),
		ADMIN("Administrator");

		private String title;

		AutoRefRole(String title)
		{ this.title = title; }

		@Override
		public String toString()
		{ return title; }
	}

	public AutoRefRole getRole(String pname)
	{
		OfflinePlayer opl = getServer().getOfflinePlayer(pname);
		if (opl.isOnline())
		{
			Player pl = opl.getPlayer();
			if (pl == null) return AutoRefRole.NONE;

			// get the match that the player is in
			AutoRefMatch match = getMatch(pl.getWorld());

			if (match != null && match.isPlayer(pl)) return AutoRefRole.PLAYER;
			if (pl.hasPermission("autoreferee.admin")) return AutoRefRole.ADMIN;
			if (pl.hasPermission("autoreferee.streamer")) return AutoRefRole.STREAMER;
			if (match != null && match.isReferee(pl)) return AutoRefRole.REFEREE;
			if (match != null) return AutoRefRole.SPECTATOR;
		}
		else
		{
			//if (opl.hasPermission("autoreferee.admin")) return AutoRefRole.ADMIN;
			//if (opl.hasPermission("autoreferee.streamer")) return AutoRefRole.STREAMER;
			//if (opl.hasPermission("autoreferee.referee")) return AutoRefRole.REFEREE;
		}

		// if everything else fails, return none
		return AutoRefRole.NONE;
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
	public static long parseTimeString(String t)
	{
		// "Some people, when confronted with a problem, think 'I know, I'll use
		// regular expressions.' Now they have two problems." -- Jamie Zawinski
		Pattern pattern = Pattern.compile("(\\d{1,5})(:(\\d{2}))?((a|p)m?)?", Pattern.CASE_INSENSITIVE);
		Matcher match = pattern.matcher(t);

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

	public static boolean hasSportBukkitApi()
	{ return mAffectsSpawning != null && mCollidesWithEntities != null; }

	public static void setAffectsSpawning(Player p, boolean yes)
	{
		if (mAffectsSpawning != null) try
		{ mAffectsSpawning.invoke(p, yes); }
		catch (Exception e) {  }
	}

	public static void setCollidesWithEntities(Player p, boolean yes)
	{
		if (mCollidesWithEntities != null) try
		{ mCollidesWithEntities.invoke(p, yes); }
		catch (Exception e) {  }
	}
}
