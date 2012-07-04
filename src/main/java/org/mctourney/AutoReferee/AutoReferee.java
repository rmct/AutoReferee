package org.mctourney.AutoReferee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrMatcher;
import org.apache.commons.lang.text.StrTokenizer;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import org.mctourney.AutoReferee.util.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.*;

public class AutoReferee extends JavaPlugin
{
	public static final String CFG_FILENAME = "autoreferee.yml";

	// default port for a "master" server
	public static final int DEFAULT_SERVER_PORT = 43760;

	public enum eAction {
		ENTERED_VOIDLANE,
	};

	public enum eMatchStatus {
		NONE, WAITING, READY, PLAYING, COMPLETED,
	};
	
	public Logger log = null;
	public World lobby = null;
	
	// is this plugin in online mode?
	public boolean onlineMode = false;
	private RefereeClient conn = null;

	// get the match associated with the world
	private Map<UUID, AutoRefMatch> matches = null;
	
	public AutoRefMatch getMatch(World w)
	{ return matches.get(w.getUID()); }

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

	// player name -> death reason
	private Map<String, eAction> actionTaken;
	
	public void setDeathReason(Player player, eAction action)
	{ actionTaken.put(player.getName(), action); }
	
	public eAction getDeathReason(Player player)
	{ return actionTaken.remove(player.getName()); }
	
	private boolean checkPlugins(PluginManager pm)
	{
		boolean foundOtherPlugin = false;
		for ( Plugin p : pm.getPlugins() ) if (p != this)
		{
			if (!foundOtherPlugin)
				log.severe("No other plugins may be loaded in online mode...");
			log.severe("Agressively disabling plugin: " + p.getName());

			pm.disablePlugin(p);
			String pStatus = p.isEnabled() ? "NOT disabled" : "disabled";
			log.severe(p.getName() + " is " + pStatus + ".");

			foundOtherPlugin = true;
		}

		// return true if all other plugins are disabled
		for ( Plugin p : pm.getPlugins() )
			if (p != this && p.isEnabled()) return false;
		return true;
	}

	public void onEnable()
	{
		// store a reference to the plugin in the classes
		AutoRefMatch.plugin = this;
		AutoRefTeam.plugin = this;
		AutoRefPlayer.plugin = this;
		
		log = this.getLogger();
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

		actionTaken = Maps.newHashMap();
		matches = Maps.newHashMap();

		// global configuration object (can't be changed, so don't save onDisable)
		InputStream configInputStream = getResource("defaults/config.yml");
		if (configInputStream != null) getConfig().setDefaults(
			YamlConfiguration.loadConfiguration(configInputStream));
		getConfig().options().copyDefaults(true); saveConfig();

		// get server list, and attempt to determine whether we are in online mode
		List<?> serverList = getConfig().getList("server-mode.server-list", Lists.newArrayList());
		onlineMode = !(serverList.size() == 0 || !getConfig().getBoolean("server-mode.online", true));

		// wrap up, debug to follow this message
		log.info("AutoReferee loaded successfully.");
		
		// save the "lobby" world as a sort of drop-zone for discharged players
		lobby = !getConfig().isString("lobby-world") ? getServer().getWorlds().get(0)
			: getServer().getWorld(getConfig().getString("lobby-world"));

		// connect to server, or let the server operator know to set up the match manually
		if (!makeServerConnection(serverList))
			log.info("AutoReferee is running in OFFLINE mode. All setup must be done manually.");

		// update online mode to represent whether or not we have a connection
		onlineMode = (conn != null);
		if (onlineMode) onlineMode = checkPlugins(pm);
		
		// setup the map library folder
		AutoRefMatch.getMapLibrary();
		
		// process initial world(s), just in case
		for ( World w : getServer().getWorlds() ) AutoRefMatch.setupWorld(w);
	}

	public boolean makeServerConnection(List<?> serverList)
	{
		// if we are not in online mode, stop right here
		if (!onlineMode) return false;
		
		// get default key and server key
		String defkey = getConfig().getDefaults().getString("server-mode.server-key");
		String key = getConfig().getString("server-mode.server-key", null);
		
		// if there is no server key listed, or it is set to the default key
		if (key == null || key.equals(defkey))
		{
			// reference the keyserver to remind operator to get a server key
			log.severe("Please get a server key from " + getConfig().getString("keyserver"));
			return false;
		}
		
		for (Object obj : serverList) if (obj instanceof String)
		{
			// split the server address on the colon, to get the port
			String[] serv = ((String) obj).split(":", 2);
			String addr = serv[0]; int port = DEFAULT_SERVER_PORT;
			
			// if provided parse the port out of the server address
			if (serv.length > 1) try { port = Integer.parseInt(serv[1]); }
			catch (NumberFormatException e) {  }
			
			try
			{
				// create a socket and a client connection
				Socket socket = new Socket(addr, port);
				socket.setKeepAlive(true);
				conn = new RefereeClient(this, socket);
			
				// successful connection, return success
				new Thread(conn).start(); return true;
			}
			catch (UnknownHostException e) {  }
			catch (IOException e) {  }
		}
		
		// none worked, return failure
		return false;
	}

	public void onDisable()
	{
		// if there is a socket connection, close it
		if (conn != null) conn.close();
		log.info("AutoReferee disabled.");
	}
	
	public void playerDone(Player p)
	{
		// if the server is in online mode, remove them
		if (onlineMode) p.kickPlayer("Thank you for playing!");
		
		// otherwise, take them back to the lobby
		else p.teleport(lobby.getSpawnLocation());
	}

	public World createMatchWorld(String worldName, Long checksum) throws IOException
	{
		// get the folder associated with this world name
		File mapFolder = AutoRefMatch.getMapFolder(worldName, checksum);
		if (mapFolder == null) return null;
		
		// create the temporary directory where this map will be
		File destWorld = new File("world-" + Long.toHexString(new Date().getTime()));
		if (!destWorld.mkdir()) throw new IOException("Could not make temporary directory.");
		
		// copy the files over and fire up the world
		FileUtils.copyDirectory(mapFolder, destWorld);
		return getServer().createWorld(WorldCreator.name(destWorld.getName()));
	}
	
	public World createMatchWorld(String worldName) throws IOException
	{ return createMatchWorld(worldName, null); }
	
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

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		if (!(sender instanceof Player)) return false;		
		Player player = (Player) sender;
		
		World world = player.getWorld();
		AutoRefMatch match = getMatch(world);
		
		// reparse the args properly using the string tokenizer from org.apache.commons
		args = new StrTokenizer(StringUtils.join(args, ' '), StrMatcher.splitMatcher(), 
			StrMatcher.quoteMatcher()).setTrimmerMatcher(StrMatcher.trimMatcher()).getTokenArray();
		
		if ("autoref".equalsIgnoreCase(cmd.getName()))
		{
			if (args.length >= 1 && "save".equalsIgnoreCase(args[0]) && match != null)
			{ match.saveWorldConfiguration(); return true; }

			if (args.length >= 1 && "init".equalsIgnoreCase(args[0]))
			{
				// if there is not yet a match object for this map
				if (match == null)
				{
					addMatch(match = new AutoRefMatch(world));
					match.saveWorldConfiguration();
					match.setCurrentState(eMatchStatus.NONE);
				}
				else player.sendMessage("AutoReferee already initialized for " + 
					match.worldConfig.getString("map.name", "this map") + ".");
				
				return true;
			}

			if (args.length >= 2 && "load".equalsIgnoreCase(args[0])) try
			{	
				// get generate a map name from the args
				String mapName = args[1];
				World mw = createMatchWorld(mapName, null);
				
				// if there is a map folder, print the CRC
				if (mw == null) log.info("No such map: [" + mapName + "]");
				else
				{
					log.info("World created for [" + mapName + 
						"] at the request of " + player.getName());
					player.teleport(mw.getSpawnLocation());
				}
				
				return true;
			}
			catch (Exception e) { return false; }

			if (args.length >= 2 && args[0].toLowerCase().startsWith("crc")) try
			{
				// get map folder from the name provided
				String mapName = args[1];
				File mapFolder = AutoRefMatch.getMapFolder(mapName, null);
				
				// if there is a map folder, print the CRC
				if (null != mapFolder) 
				{
					long checksum = AutoRefMatch.recursiveCRC32(mapFolder);
					File cfgFile = new File(mapFolder, CFG_FILENAME);
					if (!cfgFile.exists()) return true;
					
					mapName = YamlConfiguration.loadConfiguration(cfgFile)
						.getString("map.name", "<Untitled>");
					log.info(mapName + ": [" + Long.toHexString(checksum) + "]");
				}
				else log.info("No such map: " + mapName);
				
				return true;
			}
			catch (Exception e) { return false; }

			if (args.length >= 1 && "archive".equalsIgnoreCase(args[0]) && match != null) try
			{
				// save the world and configuration first
				world.save();
				match.saveWorldConfiguration();
				
				File mapLibrary = AutoRefMatch.getMapLibrary();
				if (!mapLibrary.exists()) return true;
				
				// archive folder is "<username>-<timestamp>/"
				String folderName = player.getName() + "-" + 
					Long.toHexString(new Date().getTime());
				
				File archiveFolder = new File(mapLibrary, folderName);
				if (!archiveFolder.exists()) archiveFolder.mkdir();
				
				archiveMapData(world.getWorldFolder(), archiveFolder);
				long checksum = AutoRefMatch.recursiveCRC32(archiveFolder);
				log.info(match.getMapName() + ": [" + Long.toHexString(checksum) + "]");
				return true;
			}
			catch (Exception e) { return false; }
			
			if (args.length >= 1 && "stats".equalsIgnoreCase(args[0]) && match != null) try
			{
				if (args.length >= 2 && "dump".equalsIgnoreCase(args[1]))
				{ match.logPlayerStats(args.length >= 3 ? args[2] : null); }

				else return false;
				return true;
			}
			catch (Exception e) { return false; }
			
			if (args.length >= 2 && "state".equalsIgnoreCase(args[0]) && match != null) try
			{
				match.setCurrentState(eMatchStatus.valueOf(args[1].toUpperCase()));
				log.info("Match Status is now " + match.getCurrentState().name());
				
				return true;
			}
			catch (Exception e) { return false; }
			
			if (args.length >= 1 && "wincond".equalsIgnoreCase(args[0]))
			{
				int wincondtool = getConfig().getInt("config-mode.tools.win-condition", 284);
				
				// get the tool for setting win condition
				if (args.length >= 2 && "tool".equalsIgnoreCase(args[1]))
				{
					// get the tool used to set the winconditions
					ItemStack toolitem = new ItemStack(wincondtool);
					
					// add to the inventory and switch to holding it
					PlayerInventory inv = player.getInventory();
					inv.addItem(toolitem);
					
					// let the player know what the tool is and how to use it
					player.sendMessage("Given win condition tool: " + toolitem.getType().name());
					player.sendMessage("Right-click on a block to set it as a win-condition.");
					return true;
				}
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
					sender.sendMessage("Not a valid team: " + args[1]);
					return true;
				}

				lookupTeams = new HashSet<AutoRefTeam>();
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
				player.sendMessage("This method requires WorldEdit installed and running.");
				return true;
			}
			
			if (args.length == 0)
			{
				// command is invalid. let the player know
				player.sendMessage("Must specify a team as this zone's owner.");
				return true;
			}
			
			// START is a sentinel Team object representing the start region
			AutoRefTeam t, START = new AutoRefTeam(); String tname = args[0];
			t = "start".equals(tname) ? START : match.teamNameLookup(tname);
			
			if (t == null)
			{
				// team name is invalid. let the player know
				player.sendMessage("Not a valid team: " + tname);
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
					player.sendMessage("Region now marked as " +
						"the start region!");
				}
				else
				{
					// add the region to the team, announce
					t.getRegions().add(reg);
					player.sendMessage("Region now marked as " + 
						t.getName() + "'s zone!");
				}
			}
			return true;
		}

		if ("ready".equalsIgnoreCase(cmd.getName()) && match != null)
		{
			match.prepareMatch();
		}
		if ("jointeam".equalsIgnoreCase(cmd.getName()) && match != null && !onlineMode)
		{
			// get the target team
			AutoRefTeam team = args.length > 0 
				? match.teamNameLookup(args[0]) 
				: match.getArbitraryTeam();
			
			if (team == null)
			{
				// team name is invalid. let the player know
				if (args.length > 0)
					sender.sendMessage("Not a valid team: " + args[0]);
				return true;
			}

			Player target = player;
			if (args.length > 1)
				target = getServer().getPlayer(args[1]);

			if (target == null)
			{ sender.sendMessage("Must specify a valid user."); return true; }

			team.join(target);
			return true;
		}
		if ("leaveteam".equalsIgnoreCase(cmd.getName()) && match != null && !onlineMode)
		{
			Player target = player;
			if (args.length > 0)
				target = getServer().getPlayer(args[0]);

			if (target == null)
			{ sender.sendMessage("Must specify a valid user."); return true; }

			match.leaveTeam(target);
			return true;
		}
		// WARNING: using ordinals on enums is typically frowned upon,
		// but we will consider the enums "partially-ordered"
		if ("ready".equalsIgnoreCase(cmd.getName()) && match != null &&
			match.getCurrentState().ordinal() < eMatchStatus.PLAYING.ordinal())
		{
			match.prepareMatch();
			return true;
		}

		return false;
	}

	private void archiveMapData(File worldFolder, File archiveFolder) throws IOException
	{
		// (1) copy the configuration file:
		FileUtils.copyFileToDirectory(
			new File(worldFolder, CFG_FILENAME), archiveFolder);
		
		// (2) copy the level.dat:
		FileUtils.copyFileToDirectory(
			new File(worldFolder, "level.dat"), archiveFolder);
		
		// (3) copy the region folder (only the .mca files):
		FileUtils.copyDirectory(new File(worldFolder, "region"), 
			new File(archiveFolder, "region"), 
			FileFilterUtils.suffixFileFilter(".mca"));
		
		// (4) make an empty data folder:
		new File(archiveFolder, "data").mkdir();
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
}

