package org.mctourney.AutoReferee;

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import org.mctourney.AutoReferee.util.*;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.*;

public class AutoReferee extends JavaPlugin
{
	public Map<UUID, AutoRefMatch> matches = null;

	// default port for a "master" server
	public static final int DEFAULT_SERVER_PORT = 43760;

	// number of seconds for a match to be readied
	public static final int READY_SECONDS = 15;
	
	public enum eAction {
		ENTERED_VOIDLANE,
	};

	public enum eMatchStatus {
		NONE, WAITING, READY, PLAYING, COMPLETED,
	};
	
	public Logger log = null;
	
	// is this plugin in online mode?
	public boolean onlineMode = false;
	private RefereeClient conn = null;

	public eMatchStatus getState(World w)
	{
		AutoRefMatch m = matches.get(w.getUID());
		return m == null ? eMatchStatus.NONE : m.currentState;
	}

	// a map from a player to info about why they were killed
	protected Map<String, AutoRefTeam> playerTeam;
	public Map<String, AutoRefPlayer> playerData;
	public Map<String, eAction> actionTaken;

	public String getMatchName(World w)
	{
		AutoRefMatch m = matches.get(w.getUID());
		return m == null ? null : m.matchName;
	}
	
	public static void worldBroadcast(World world, String msg)
	{ for (Player p : world.getPlayers()) p.sendMessage(msg); }

	public AutoRefTeam teamNameLookup(AutoRefMatch m, String name)
	{
		// if passed a null match, return null
		if (m == null) return null;
		
		// if there is no match on that world, forget it
		// is this team name a word?
		for (AutoRefTeam t : m.teams)
			if (t.match(name)) return t;

		// no team matches the name provided
		return null;
	}

	public AutoRefTeam teamNameLookup(World w, String name)
	{ return teamNameLookup(matches.get(w.getUID()), name);	}

	public AutoRefTeam getTeam(OfflinePlayer player)
	{
		// get team from player
		return playerTeam.get(player.getName());
	}

	public ChatColor getTeamColor(OfflinePlayer player)
	{
		// if not on a team, don't modify the player name
		AutoRefTeam team = getTeam(player);
		if (team == null) return ChatColor.WHITE;

		// get color of the team they are on
		return team.color;
	}

	public Location getPlayerSpawn(OfflinePlayer player)
	{
		// get player's team
		AutoRefTeam team = getTeam(player);

		// otherwise, return the appropriate location
		return team == null ? null : team.spawn;
	}
	
	public int getVanishLevel(Player p)
	{
		// referees have the highest vanish level
		if (p.hasPermission("autoreferee.referee")) return 200;
		
		// if you aren't on a team, you get a vanish level
		if (getTeam(p) == null) return 100;
		
		// streamers are ONLY able to see streamers and players
		if (p.hasPermission("autoreferee.streamer")) return 1;
		
		// players have the lowest level vanish
		return 0;
	}

	public FileConfiguration getMapConfig(World w)
	{
		AutoRefMatch m = matches.get(w.getUID());
		return m == null ? null : m.worldConfig;
	}

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
		AutoRefMatch.plugin = this;
		matches = new HashMap<UUID, AutoRefMatch>();
		
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

		playerTeam = new HashMap<String, AutoRefTeam>();
		actionTaken = new HashMap<String, eAction>();

		// global configuration object (can't be changed, so don't save onDisable)
		InputStream configInputStream = getResource("defaults/config.yml");
		if (configInputStream != null) getConfig().setDefaults(
			YamlConfiguration.loadConfiguration(configInputStream));
		getConfig().options().copyDefaults(true); saveConfig();

		// get server list, and attempt to determine whether we are in online mode
		List<?> serverList = getConfig().getList("server-mode.server-list", new ArrayList<String>());
		onlineMode = !(serverList.size() == 0 || !getConfig().getBoolean("server-mode.online", true));

		// wrap up, debug to follow this message
		if (onlineMode) onlineMode = checkPlugins(pm);
		log.info("AutoReferee loaded successfully.");

		// connect to server, or let the server operator know to set up the match manually
		if (!makeServerConnection(serverList))
			log.info("AutoReferee is running in OFFLINE mode. All setup must be done manually.");

		// update online mode to represent whether or not we have a connection
		onlineMode = (conn != null);
		
		// setup the map library folder
		File mapLibrary = new File("maps");
		if (mapLibrary.exists() && !mapLibrary.isDirectory()) mapLibrary.delete();
		if (!mapLibrary.exists()) mapLibrary.mkdir();
		
		// process initial world(s), just in case
		for ( World w : getServer().getWorlds() ) processWorld(w);
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

	public void processWorld(World w)
	{
		// if this map isn't compatible with AutoReferee, quit...
		if (matches.containsKey(w.getUID()) ||
			!AutoRefMatch.isCompatible(w)) return;
		
		AutoRefMatch match = new AutoRefMatch(w);
		matches.put(w.getUID(), match);
	}
	
	public boolean createMatchWorld(String worldName, Long checksum) throws IOException
	{
		// assume worldName exists
		if (worldName == null) return false;
		
		// if there is no map library, quit
		File mapLibrary = new File("maps"), mapMaster = null;
		if (!mapLibrary.exists()) return false;
		
		// find the map being requested
		for (File f : mapLibrary.listFiles())
		{
			// skip non-directories
			if (!f.isDirectory()) continue;
			
			// if it doesn't have an autoreferee config file
			File cfgFile = new File(f, "autoreferee.yml");
			if (!cfgFile.exists()) continue;
			
			// check the map name, if it matches, this is the one we want
			FileConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
			if (!worldName.equals(cfg.getString("map.name"))) continue;
			
			// compute the checksum of the directory, make sure it matches
			if (checksum != null &&	recursiveCRC32(f) != checksum) continue;
			
			// this is the map we want
			mapMaster = f; break;
		}
		
		// no such map was found, sorry...
		if (mapMaster == null) return false;
		
		// create the temporary directory where this map will be
		File destWorld = File.createTempFile("world-", "", new File("."));
		if (!destWorld.delete() && !destWorld.mkdir())
			throw new IOException("Could not make temporary directory.");
		
		// copy the files over and fire up the world
		FileUtils.copyDirectory(mapMaster, destWorld);
		getServer().createWorld(WorldCreator.name(destWorld.getName()));
		
		return true;
	}
	
	public boolean createMatchWorld(String worldName) throws IOException
	{ return createMatchWorld(worldName, null); }
	
	private static long recursiveCRC32(File file) throws IOException
	{
		if (file.isDirectory())
		{
			long checksum = 0L;
			for (File f : file.listFiles())
				checksum ^= recursiveCRC32(f);
			return checksum;
		}
		else return FileUtils.checksumCRC32(file);
	}

	private WorldEditPlugin getWorldEdit()
	{
		Plugin plugin = getServer().getPluginManager().getPlugin("WorldEdit");

		// WorldEdit may not be loaded
		if (plugin == null || !(plugin instanceof WorldEditPlugin)) 
			return null;

		return (WorldEditPlugin) plugin;
	}
	
	public void prepareTeam(AutoRefTeam t, List<String> players)
	{
		// null team not allowed
		if (t == null) return;

		// delete all elements from the map for team 't'
		Iterator<Map.Entry<String, AutoRefTeam>> i = playerTeam.entrySet().iterator();
		while (i.hasNext()) if (t.equals(i.next().getValue())) i.remove();

		// insert all players into their list
		for (String p : players) playerTeam.put(p, t);
	}

	public String colorPlayer(OfflinePlayer player)
	{
		// color a player's name with its team's color
		ChatColor color = getTeamColor(player);
		return color + player.getName() + ChatColor.WHITE;
	}

	public void joinTeam(OfflinePlayer player, AutoRefTeam t)
	{
		// null team not allowed, and quit if they are already on this team
		if (t == null || t == getTeam(player)) return;
		
		// if the match is in progress, no one may join
		if (t.match.currentState.ordinal() >= eMatchStatus.PLAYING.ordinal()) return;

		// just in case, announce they are leaving their previous team
		leaveTeam(player);

		playerTeam.put(player.getName(), t);
		getServer().broadcastMessage(colorPlayer(player) + 
			" has joined " + t.getName());
		
		if (player.isOnline() && (player instanceof Player))
			((Player) player).setPlayerListName(colorPlayer(player));
	}

	public void leaveTeam(OfflinePlayer player)
	{
		AutoRefTeam t = getTeam(player);
		if (t == null) return;

		playerTeam.remove(player);
		getServer().broadcastMessage(colorPlayer(player) + 
			" has left " + t.getName());
		
		if (player.isOnline() && (player instanceof Player))
			((Player) player).setPlayerListName(player.getName());
	}

	public boolean playerWhitelisted(Player player)
	{
		return player.isOp() || player.hasPermission("autoreferee.referee") ||
			playerTeam.containsKey(player.getName());
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		if (!(sender instanceof Player)) return false;		
		Player player = (Player) sender;
		
		World world = player.getWorld();
		AutoRefMatch m = matches.get(world.getUID());
		
		if ("autoref".equalsIgnoreCase(cmd.getName()))
		{
			if (args.length >= 1 && "save".equalsIgnoreCase(args[0]) && m != null)
			{ m.saveWorldConfiguration(); return true; }

			if (args.length >= 1 && "init".equalsIgnoreCase(args[0]))
			{
				// if there is not yet a match object for this map
				if (m == null)
				{
					m = new AutoRefMatch(world);
					matches.put(world.getUID(), m);
					m.saveWorldConfiguration();
				}
				else player.sendMessage("AutoReferee already initialized for " + 
					m.worldConfig.getString("map.name", "this map") + ".");
				
				return true;
			}

			if (args.length >= 2 && "load".equalsIgnoreCase(args[0])) try
			{
				return true;
			}
			catch (Exception e) { return false; }
			
			if (args.length >= 1 && "stats".equalsIgnoreCase(args[0]) && m != null) try
			{
				if (args.length >= 2 && "dump".equalsIgnoreCase(args[1]))
				{ logPlayerStats(args.length >= 3 ? args[2] : null); }

				else return false;
				return true;
			}
			catch (Exception e) { return false; }
			
			if (args.length >= 2 && "state".equalsIgnoreCase(args[0]) && m != null) try
			{
				m.currentState = eMatchStatus.valueOf(args[1]);
				log.info("Match Status is now " + m.currentState.name());
				
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

			if (args.length >= 1 && "zones".equalsIgnoreCase(args[0]) && m != null)
			{
				Set<AutoRefTeam> lookupTeams = null;

				// if a team has been specified as an argument
				if (args.length > 1)
				{
					AutoRefTeam t = teamNameLookup(m, args[1]);
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
				else lookupTeams = m.teams;
				
				// sanity check...
				if (lookupTeams == null) return false;

				// for all the teams being looked up
				for (AutoRefTeam team : lookupTeams)
				{
					// print team-name header
					sender.sendMessage(team.getName() + "'s Regions:");

					// print all the regions owned by this team
					if (team.regions.size() > 0) for (CuboidRegion reg : team.regions)
					{
						Vector3 mn = reg.getMinimumPoint(), mx = reg.getMaximumPoint();
						sender.sendMessage("  (" + vectorToCoords(mn) + ") => (" + vectorToCoords(mx) + ")");
					}

					// if there are no regions, print None
					else sender.sendMessage("  <None>");
				}

				return true;
			}
		}
		
		if ("zone".equalsIgnoreCase(cmd.getName()) && m != null)
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
			AutoRefTeam t, START = new AutoRefTeam();
			t = "start".equals(args[0]) ? START : teamNameLookup(m, args[0]);
			
			if (t == null)
			{
				// team name is invalid. let the player know
				player.sendMessage("Not a valid team: " + args[0]);
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
					m.startRegion = reg;
					player.sendMessage("Region now marked as " +
						"the start region!");
				}
				else
				{
					// add the region to the team, announce
					t.regions.add(reg);
					player.sendMessage("Region now marked as " + 
						t.getName() + "'s zone!");
				}
			}
			return true;
		}

		if ("jointeam".equalsIgnoreCase(cmd.getName()) && m != null && !onlineMode)
		{
			AutoRefTeam t = args.length > 0 ? teamNameLookup(m, args[0]) : getArbitraryTeam(m);
			if (t == null)
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

			joinTeam(target, t);
			return true;
		}
		if ("leaveteam".equalsIgnoreCase(cmd.getName()) && m != null && !onlineMode)
		{
			Player target = player;
			if (args.length > 0)
				target = getServer().getPlayer(args[0]);

			if (target == null)
			{ sender.sendMessage("Must specify a valid user."); return true; }

			leaveTeam(target);
			return true;
		}
		// WARNING: using ordinals on enums is typically frowned upon,
		// but we will consider the enums "partially-ordered"
		if ("ready".equalsIgnoreCase(cmd.getName()) && m != null &&
			m.currentState.ordinal() < eMatchStatus.PLAYING.ordinal())
		{
			prepareWorld(player.getWorld());
			return true;
		}

		return false;
	}

	private File getLogDirectory()
	{
		// create the log directory if it doesn't exist
		File logdir = new File(getDataFolder(), "logs");
		if (!logdir.exists()) logdir.mkdir();
		
		// return the reference to the log directory
		return logdir;
	}

	private void logPlayerStats(String h) throws IOException
	{
		if (playerData == null)
		{ log.severe("No stats available at this time."); return; }

		String hdl = h != null ? h : 
			new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
		
		File sfile = new File(getLogDirectory(), hdl + ".log");
		PrintWriter fw = new PrintWriter(sfile);

		for (Map.Entry<String, AutoRefPlayer> entry : playerData.entrySet())
		{
			String pname = entry.getKey();
			AutoRefPlayer data = entry.getValue();
			
			fw.println("Stats for " + pname + ": (" + Integer.toString(data.totalKills)
				+ "/" + Integer.toString(data.totalDeaths) + ")");
			
			for (Map.Entry<String, Integer> kill : data.kills.entrySet())
				fw.println("\t" + pname + " killed " + kill.getKey() + " " 
					+ kill.getValue().toString() + " time(s).");
			
			for (Map.Entry<AutoRefPlayer.DamageCause, Integer> death : data.deaths.entrySet())
				fw.println("\t" + death.getKey().toString() + " killed " + pname 
					+ " " + death.getValue().toString() + " time(s).");
			
			for (Map.Entry<AutoRefPlayer.DamageCause, Integer> damage : data.damage.entrySet())
				fw.println("\t" + damage.getKey().toString() + " caused " + pname 
					+ " " + damage.getValue().toString() + " damage.");
		}
		
		fw.close();
	}

	private AutoRefTeam getArbitraryTeam(AutoRefMatch m)
	{
		// minimum size of any one team, and an array to hold valid teams
		int minsize = Integer.MAX_VALUE;
		List<AutoRefTeam> vteams = new ArrayList<AutoRefTeam>();
		
		// get the number of players on each team: Map<TeamNumber -> NumPlayers>
		Map<AutoRefTeam,Integer> count = new HashMap<AutoRefTeam,Integer>();
		for (AutoRefTeam t : m.teams) count.put(t, 0);
		
		for (AutoRefTeam t : playerTeam.values())
			if (count.containsKey(t)) count.put(t, count.get(t)+1);
		
		// determine the size of the smallest team
		for (Integer c : count.values())
			if (c < minsize) minsize = c.intValue();

		// make a list of all teams with this size
		for (Map.Entry<AutoRefTeam,Integer> e : count.entrySet())
			if (e.getValue().intValue() == minsize) vteams.add(e.getKey());

		// return a random element from this list
		return vteams.get(new Random().nextInt(vteams.size()));
	}

	public void addWinCondition(Block block, AutoRefTeam team)
	{
		if (block == null || team == null) return;
		
		// add the block data to the win-condition listing
		BlockData bd = BlockData.fromBlock(block);
		team.winconditions.put(block.getLocation(), bd);
		
		String bname = bd.mat.name();
		switch (block.getType())
		{
		case WOOL:
			DyeColor color = DyeColor.getByData(bd.data);
			bname = color.name() + " " + bname;
			break;
		}
		
		// broadcast the update using bname (a reconstructed name for the block)
		getServer().broadcastMessage(bname + " is now a win condition for " + 
			team.getName() + " @ " + vectorToCoords(locationToBlockVector(block.getLocation())));
	}

	public void checkWinConditions(World world, Location aloc)
	{
		// this code is only called in BlockPlaceEvent and BlockBreakEvent when
		// we have confirmed that the state is PLAYING, so we know we are definitely
		// in a match if this function is being called
		
		AutoRefMatch m = matches.get(world.getUID());
		if (m != null) for (AutoRefTeam t : m.teams)
		{
			// if there are no win conditions set, skip this team
			if (t.winconditions.size() == 0) continue;
			
			// check all win condition blocks (AND together)
			boolean win = true;
			for (Map.Entry<Location, BlockData> pair : t.winconditions.entrySet())
			{
				BlockData bd = pair.getValue();
				win &= pair.getKey().equals(aloc) ? bd.mat == Material.AIR : 
					bd.matches(world.getBlockAt(pair.getKey()));
			}
			
			if (win)
			{
				// announce the victory and set the match to completed
				getServer().broadcastMessage(t.getName() + " Wins!");
				for (Player p : world.getPlayers())
					if (playerTeam.containsKey(p.getName()))
						p.teleport(world.getSpawnLocation());
				m.currentState = eMatchStatus.COMPLETED;
			}
		}
	}
	
	// wrote this dumb helper function because `distanceToRegion` was looking ugly...
	public static double multimax( double base, double ... more )
	{ for ( double x : more ) base = Math.max(base, x); return base; }
	
	// distance from region, axially aligned (value less than actual distance, but
	// appropriate for measurements on cuboid regions)
	public static double distanceToRegion(Location v, CuboidRegion reg)
	{
		// not a region, infinite distance away
		if (reg == null) return Double.POSITIVE_INFINITY;
		
		double x = v.getX(), y = v.getY(), z = v.getZ();
		Vector3 mx = reg.getMaximumPoint(), mn = reg.getMinimumPoint();
		
		// return maximum distance from this region
		// (max on all sides, axially-aligned)
		return multimax ( 0
		,	mn.x - x, x - mx.x - 1
		,	mn.y - y, y - mx.y - 1
		,	mn.z - z, z - mx.z - 1
		);
	}
	
	public Set<AutoRefTeam> locationOwnership(Location loc)
	{
		// teams who own this location
		Set<AutoRefTeam> owners = new HashSet<AutoRefTeam>();

		// check all safe regions for that team
		AutoRefMatch m = matches.get(loc.getWorld().getUID());
		if (m != null) for (AutoRefTeam team : m.teams)
			for (CuboidRegion reg : team.regions)
		{
			// if the location is inside the region, add it
			if (distanceToRegion(loc, reg) < ZoneListener.SNEAK_DISTANCE) 
				owners.add(team);
		}
		
		return owners;
	}

	// simple getter for the start region
	public CuboidRegion getStartRegion(World w)
	{
		AutoRefMatch m = matches.get(w.getUID());
		return m == null ? null : m.startRegion;
	}
	
	// is location in start region?
	public boolean inStartRegion(Location loc)
	{
		CuboidRegion reg = getStartRegion(loc.getWorld());
		return distanceToRegion(loc, reg) < ZoneListener.SNEAK_DISTANCE;
	}
	
	// distance from the closest owned region
	public double distanceToClosestRegion(Player p)
	{ return distanceToClosestRegion(getTeam(p), p.getLocation()); }
	
	public double distanceToClosestRegion(AutoRefTeam team, Location loc)
	{
		if (team == null) return 0;
		double distance = distanceToRegion(loc, getStartRegion(loc.getWorld()));
		
		for ( CuboidRegion reg : team.regions ) if (distance > 0)
			distance = Math.min(distance, distanceToRegion(loc, reg));
		
		return distance;
	}

	// TRUE = this location *is* within the player's regions
	// FALSE = this location is *not* within player's regions
	public Boolean checkPosition(Player player, Location loc)
	{
		// get the player's team (if they are not on a team, ignore them)
		AutoRefTeam team = getTeam(player);
		if (team == null) return true;
		
		// is the player's location owned by the player's team?
		return locationOwnership(loc).contains(team);
	}

	public void checkTeamsReady(AutoRefMatch m) 
	{
		if (m == null) return;
		
		// if there is no one on the server
		if (m.world.getPlayers().size() == 0)
		{
			// set all the teams to not ready and status as waiting
			for ( AutoRefTeam t : m.teams ) t.ready = false;
			m.currentState = eMatchStatus.WAITING; return;
		}
		
		// this function is only useful if we are waiting
		if (m.currentState != eMatchStatus.WAITING) return;
		
		// if we aren't in online mode, assume we are always ready
		if (!onlineMode) { m.currentState = eMatchStatus.READY; return; }
		
		// check if all the players are here
		boolean ready = true; Server s = getServer();
		for ( String p : playerTeam.keySet() ) 
			ready &= s.getOfflinePlayer(p).isOnline();
		
		// set status based on whether the players are online
		m.currentState = ready ? eMatchStatus.READY : eMatchStatus.WAITING;
	}

	public void checkTeamsReady(World w) 
	{ checkTeamsReady(matches.get(w.getUID())); }
	
	class MatchStarter implements Runnable
	{
		public int task = -1;
		private int secs = 3;
		
		private AutoRefMatch match = null;
		public MatchStarter(AutoRefMatch m)
		{
			match = m;
		}
		
		public void run()
		{
			// if the countdown has ended...
			if (secs == 0)
			{
				// set the current time to the start time (again)
				match.world.setTime(match.startTime);
				
				// setup world to go!
				match.currentState = eMatchStatus.PLAYING;
				worldBroadcast(match.world, ">>> " + ChatColor.GREEN + "GO!");
				
				// cancel the task
				getServer().getScheduler().cancelTask(task);
			}
			
			// report number of seconds remaining
			else worldBroadcast(match.world, ">>> " + 
				ChatColor.GREEN + Integer.toString(secs--) + "...");
		}
	}
	
	public void prepareWorld(World w)
	{
		AutoRefMatch match = matches.get(w.getUID());
		if (match == null) return;
		
		// set the current time to the start time
		w.setTime(match.startTime);
		
		// remove all mobs, animals, and items
		for (Entity e : w.getEntitiesByClasses(Monster.class, 
			Animals.class, Item.class, ExperienceOrb.class)) e.remove();
		
		// turn off weather forever (or for a long time)
		w.setStorm(false);
		w.setWeatherDuration(Integer.MAX_VALUE);
		
		// prepare all players for the match
		playerData = new HashMap<String, AutoRefPlayer>();
		for (Player p : w.getPlayers())
			if (playerTeam.containsKey(p.getName())) preparePlayer(p);
		
		// vanish players appropriately
		for ( Player pv : w.getPlayers() ) // <--- viewer
		for ( Player ps : w.getPlayers() ) // <--- subject
		{
			if (getVanishLevel(pv) >= getVanishLevel(ps))
				pv.showPlayer(ps); else pv.hidePlayer(ps);
		}
		
		// announce the match starting in X seconds
		worldBroadcast(w, "Match will begin in "
			+ Integer.toString(READY_SECONDS) + " seconds.");
		
		// cancel any previous match-start task
		if (match.matchStarter != null && match.matchStarter.task != -1)
			getServer().getScheduler().cancelTask(match.matchStarter.task);
		
		// schedule the task to announce and prepare the match
		match.matchStarter = new MatchStarter(match);
		match.matchStarter.task = getServer().getScheduler().scheduleSyncRepeatingTask(
				this, match.matchStarter, READY_SECONDS * 20L, 20L);
	}
	
	public void preparePlayer(Player p)
	{
		p.setHealth    ( 20 ); // 10 hearts
		p.setFoodLevel ( 20 ); // full food
		p.setSaturation(  5 ); // saturation depletes hunger
		p.setExhaustion(  0 ); // exhaustion depletes saturation
		
		// setup an empty PlayerData object for this player
		log.info("Making record for: " + p.getName());
		playerData.put(p.getName(), new AutoRefPlayer(p));
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
	
	public static Vector3 locationToVector(Location loc)
	{ return new Vector3(loc.getX(), loc.getY(), loc.getZ()); }
	
	public static BlockVector3 locationToBlockVector(Location loc)
	{ return new BlockVector3(locationToVector(loc)); }
	
	public static Vector3 coordsToVector(String coords)
	{
		try
		{
			String[] values = coords.split(",");
			return new Vector3( // vector 1
				Integer.parseInt(values[0]),
				Integer.parseInt(values[1]),
				Integer.parseInt(values[2]));
		}
		catch (Exception e) { return null; }
	}

	public static CuboidRegion coordsToRegion(String coords)
	{
		// split the region coordinates into two corners
		String[] values = coords.split(":");
		
		// generate the region by the two vectors
		Vector3 v1 = coordsToVector(values[0]), v2 = coordsToVector(values[1]);
		return (v1 == null || v2 == null ? null : new CuboidRegion(v1, v2));
	}
	
	public static String vectorToCoords(Vector3 v)
	{ return vectorToCoords(new BlockVector3(v)); }
	
	public static String vectorToCoords(BlockVector3 v)
	{ return v.x + "," + v.y + "," + v.z; }

	public static String regionToCoords(CuboidRegion reg)
	{
		// save region as "minX minY minZ maxX maxY maxZ"
		Vector3 mn = reg.getMinimumPoint(), mx = reg.getMaximumPoint();
		return vectorToCoords(mn) +	":" + vectorToCoords(mx);
	}
}

