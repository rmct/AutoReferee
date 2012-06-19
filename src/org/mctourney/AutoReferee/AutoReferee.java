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

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import org.mctourney.AutoReferee.util.*;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.*;
import com.sk89q.worldedit.regions.CuboidRegion;

public class AutoReferee extends JavaPlugin
{
	public static class AutoRefMatch
	{
		// world this match is taking place on
		public World world;
		
		// time to set the world to at the start of the match
		public long startTime = 8000L;
		
		// status of the match
		public eMatchStatus currentState = eMatchStatus.NONE;
		
		// teams participating in the match
		public Set<Team> teams = null;
		
		// region defined as the "start" region (safe zone)
		public CuboidRegion startRegion = null;
		
		// name of the match
		public String matchName = "Scheduled Match";
		
		// configuration information for the world
		public File worldConfigFile;
		public FileConfiguration worldConfig;
		
		// task that starts the match
		public MatchStarter matchStarter = null;

		public AutoRefMatch(World world)
		{
			this.world = world;
			loadWorldConfiguration();
		}
		
		public static boolean isCompatible(World w)
		{ return new File(w.getWorldFolder(), "autoreferee.yml").exists(); }

		public static AutoReferee plugin;
		
		@SuppressWarnings("unchecked")
		private void loadWorldConfiguration()
		{
			// file stream and configuration object (located in world folder)
			worldConfigFile = new File(world.getWorldFolder(), "autoreferee.yml");
			worldConfig = YamlConfiguration.loadConfiguration(worldConfigFile);

			// load up our default values file, so that we can have a base to work with
			InputStream defConfigStream = plugin.getResource("defaults/map.yml");
			if (defConfigStream != null) worldConfig.setDefaults(
				YamlConfiguration.loadConfiguration(defConfigStream));

			// make sure any defaults get copied into the map file
			worldConfig.options().copyDefaults(true);
			worldConfig.options().header(plugin.getDescription().getFullName());
			worldConfig.options().copyHeader(false);

			teams = new HashSet<Team>();
			
			List<Map<?, ?>> cfgTeams = worldConfig.getMapList("match.teams");
			for (Map<?, ?> map : cfgTeams) teams.add(Team.fromMap((Map<String, Object>) map, this));
			
			// get the start region (safe for both teams, no pvp allowed)
			if (worldConfig.isString("match.start-region"))
				startRegion = coordsToRegion(worldConfig.getString("match.start-region"));
			
			// get the time the match is set to start
			if (worldConfig.isString("match.start-time"))
				startTime = parseTimeString(worldConfig.getString("match.start-time"));
		}

		private void saveWorldConfiguration() 
		{
			// if there is no configuration object or file, nothin' doin'...
			if (worldConfigFile == null || worldConfig == null) return;

			// create and save the team data list
			List<Map<String, Object>> teamData = new ArrayList<Map<String, Object>>();
			for (Team t : teams) teamData.add(t.toMap());
			worldConfig.set("match.teams", teamData);
			
			// save the start region
			if (startRegion != null)
				worldConfig.set("match.start-region", regionToCoords(startRegion));

			// save the configuration file back to the original filename
			try { worldConfig.save(worldConfigFile); }

			// log errors, report which world did not save
			catch (java.io.IOException e)
			{ plugin.log.info("Could not save world config: " + world.getName()); }
		}
	}
	
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
	protected Map<String, Team> playerTeam;
	public Map<String, AutoRefPlayer> playerData;
	public Map<String, eAction> actionTaken;

	public String getMatchName(World w)
	{
		AutoRefMatch m = matches.get(w.getUID());
		return m == null ? null : m.matchName;
	}
	
	public static void worldBroadcast(World world, String msg)
	{ for (Player p : world.getPlayers()) p.sendMessage(msg); }

	public Team teamNameLookup(AutoRefMatch m, String name)
	{
		// if passed a null match, return null
		if (m == null) return null;
		
		// if there is no match on that world, forget it
		// is this team name a word?
		for (Team t : m.teams)
			if (t.match(name)) return t;

		// no team matches the name provided
		return null;
	}

	public Team teamNameLookup(World w, String name)
	{ return teamNameLookup(matches.get(w.getUID()), name);	}

	public Team getTeam(OfflinePlayer player)
	{
		// get team from player
		return playerTeam.get(player.getName());
	}

	public ChatColor getTeamColor(OfflinePlayer player)
	{
		// if not on a team, don't modify the player name
		Team team = getTeam(player);
		if (team == null) return ChatColor.WHITE;

		// get color of the team they are on
		return team.color;
	}

	public Location getPlayerSpawn(OfflinePlayer player)
	{
		// get player's team
		Team team = getTeam(player);

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
	/*	boolean foundOtherPlugin = false;
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
	*/	return true;
	}

	public void onEnable()
	{
		AutoRefMatch.plugin = this;
		
		log = this.getLogger();
		PluginManager pm = getServer().getPluginManager();

		// events related to team management, chat, whitelists, respawn
		pm.registerEvents(new TeamListener(this), this);

		// events related to PvP, damage, death, mobs
		pm.registerEvents(new PlayerVersusPlayerListener(this), this);

		// events related to safe zones, creating zones, map conditions
		pm.registerEvents(new ZoneListener(this), this);

		// events related to worlds
		WorldListener worldListener = new WorldListener(this);
		pm.registerEvents(worldListener, this);

		matches = new HashMap<UUID, AutoRefMatch>();
		playerTeam = new HashMap<String, Team>();
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
	
	public boolean createMatchWorld(String worldName) throws IOException
	{
		File mapLibrary = new File("maps");
		if (!mapLibrary.exists() || !mapLibrary.isDirectory())
			mapLibrary.mkdir();
		
		File mapMaster = new File(mapLibrary, worldName);
		if (!mapMaster.exists()) return false;
		
		return true;
	}
	
	private WorldEditPlugin getWorldEdit()
	{
		Plugin plugin = getServer().getPluginManager().getPlugin("WorldEdit");

		// WorldEdit may not be loaded
		if (plugin == null || !(plugin instanceof WorldEditPlugin)) 
			return null;

		return (WorldEditPlugin) plugin;
	}
	
	public void prepareTeam(Team t, List<String> players)
	{
		// null team not allowed
		if (t == null) return;

		// delete all elements from the map for team 't'
		Iterator<Map.Entry<String, Team>> i = playerTeam.entrySet().iterator();
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

	public void joinTeam(OfflinePlayer player, Team t)
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
		Team t = getTeam(player);
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
				Set<Team> lookupTeams = null;

				// if a team has been specified as an argument
				if (args.length > 1)
				{
					Team t = teamNameLookup(m, args[1]);
					if (t == null)
					{
						// team name is invalid. let the player know
						sender.sendMessage("Not a valid team: " + args[1]);
						return true;
					}

					lookupTeams = new HashSet<Team>();
					lookupTeams.add(t);
				}

				// otherwise, just print all the teams
				else lookupTeams = m.teams;
				
				// sanity check...
				if (lookupTeams == null) return false;

				// for all the teams being looked up
				for (Team team : lookupTeams)
				{
					// print team-name header
					sender.sendMessage(team.getName() + "'s Regions:");

					// print all the regions owned by this team
					if (team.regions.size() > 0) for (CuboidRegion reg : team.regions)
					{
						Vector mn = reg.getMinimumPoint(), mx = reg.getMaximumPoint();
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
			if (args.length == 0)
			{
				// command is invalid. let the player know
				player.sendMessage("Must specify a team as this zone's owner.");
				return true;
			}
			
			// START is a sentinel Team object representing the start region
			Team t, START = new Team();
			t = "start".equals(args[0]) ? START : teamNameLookup(m, args[0]);
			
			if (t == null)
			{
				// team name is invalid. let the player know
				player.sendMessage("Not a valid team: " + args[0]);
				return true;
			}

			Selection sel = getWorldEdit().getSelection(player);
			if ((sel instanceof CuboidSelection))
			{
				CuboidSelection csel = (CuboidSelection) sel;
				CuboidRegion reg = new CuboidRegion(
					csel.getNativeMinimumPoint(), 
					csel.getNativeMaximumPoint()
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
			Team t = args.length > 0 ? teamNameLookup(m, args[0]) : getArbitraryTeam(m);
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
			
			for (Map.Entry<DamageCause, Integer> death : data.deaths.entrySet())
				fw.println("\t" + death.getKey().toString() + " killed " + pname 
					+ " " + death.getValue().toString() + " time(s).");
			
			for (Map.Entry<DamageCause, Integer> damage : data.damage.entrySet())
				fw.println("\t" + damage.getKey().toString() + " caused " + pname 
					+ " " + damage.getValue().toString() + " damage.");
		}
		
		fw.close();
	}

	private Team getArbitraryTeam(AutoRefMatch m)
	{
		// minimum size of any one team, and an array to hold valid teams
		int minsize = Integer.MAX_VALUE;
		List<Team> vteams = new ArrayList<Team>();
		
		// get the number of players on each team: Map<TeamNumber -> NumPlayers>
		Map<Team,Integer> count = new HashMap<Team,Integer>();
		for (Team t : m.teams) count.put(t, 0);
		
		for (Team t : playerTeam.values())
			if (count.containsKey(t)) count.put(t, count.get(t)+1);
		
		// determine the size of the smallest team
		for (Integer c : count.values())
			if (c < minsize) minsize = c.intValue();

		// make a list of all teams with this size
		for (Map.Entry<Team,Integer> e : count.entrySet())
			if (e.getValue().intValue() == minsize) vteams.add(e.getKey());

		// return a random element from this list
		return vteams.get(new Random().nextInt(vteams.size()));
	}

	public void addWinCondition(Block block, Team team)
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
		if (m != null) for (Team t : m.teams)
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
		Vector mx = reg.getMaximumPoint(), mn = reg.getMinimumPoint();
		
		// return maximum distance from this region
		// (max on all sides, axially-aligned)
		return multimax ( 0
		,	mn.getX() - x, x - mx.getX() - 1
		,	mn.getY() - y, y - mx.getY() - 1
		,	mn.getZ() - z, z - mx.getZ() - 1
		);
	}
	
	public Set<Team> locationOwnership(Location loc)
	{
		// teams who own this location
		Set<Team> owners = new HashSet<Team>();

		// check all safe regions for that team
		AutoRefMatch m = matches.get(loc.getWorld().getUID());
		if (m != null) for (Team team : m.teams)
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
	
	public double distanceToClosestRegion(Team team, Location loc)
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
		Team team = getTeam(player);
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
			for ( Team t : m.teams ) t.ready = false;
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
	
	private class MatchStarter implements Runnable
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
	
	public static Vector locationToVector(Location loc)
	{ return new Vector(loc.getX(), loc.getY(), loc.getZ()); }
	
	public static BlockVector locationToBlockVector(Location loc)
	{ return locationToVector(loc).toBlockVector(); }
	
	public static Vector coordsToVector(String coords)
	{
		try
		{
			String[] values = coords.split(",");
			return new Vector( // vector 1
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
		Vector v1 = coordsToVector(values[0]), v2 = coordsToVector(values[1]);
		return (v1 == null || v2 == null ? null : new CuboidRegion(v1, v2));
	}
	
	public static String vectorToCoords(Vector v)
	{ return v.getBlockX() + "," + v.getBlockY() + "," + v.getBlockZ(); }

	public static String regionToCoords(CuboidRegion reg)
	{
		// save region as "minX minY minZ maxX maxY maxZ"
		Vector mn = reg.getMinimumPoint(), mx = reg.getMaximumPoint();
		return vectorToCoords(mn) +	":" + vectorToCoords(mx);
	}

	static class BlockData
	{
		public Material mat;
		public byte data;

		// material value and metadata (-1 = no metadata)
		public BlockData(Material m, byte d) { mat = m; data = d; }

		@Override public int hashCode()
		{ return mat.hashCode() ^ new Byte(data).hashCode(); }

		@Override public boolean equals(Object o)
		{
			// if the object is a mismatched type, its not equal
			if (o == null || !(o instanceof BlockData)) return false;
			
			// otherwise, check that the data is all equivalent
			BlockData ob = (BlockData) o; 
			return ob.mat.equals(mat) && ob.data == data;
		}

		// does this block data match the given block?
		public boolean matches(Block b)
		{
			// matches if materials and metadata are same
			return (b != null && b.getType().equals(mat)
				&& (data == -1 || data == b.getData()));
		}

		@Override public String toString()
		{
			String s = Integer.toString(mat.ordinal());
			return data == -1 ? s : (s + "," + Integer.toString(data));
		}
		
		static BlockData fromString(String s)
		{
			// format: mat[,data]
			String[] units = s.split(",", 2);
			
			try 
			{
				// parse out the material (and potentially meta-data)
				Material mat = Material.getMaterial(Integer.parseInt(units[0]));
				byte data = units.length < 2 ? -1 : Byte.parseByte(units[1]);
				return new BlockData(mat, data);
			}
			
			// if there is a problem with parsing a material, assume the worst
			catch (NumberFormatException e) { return null; }
		}
		
		// generate block data object from a CraftBlock
		static BlockData fromBlock(Block b)
		{ return new BlockData(b.getType(), b.getData()); }
	}

	static class AutoRefPlayer
	{
		// stored player reference
		protected Player player;
		
		// number of times this player has killed other players
		public Map<String, Integer> kills;
		public int totalKills = 0;

		// number of times player has died and damage taken
		public Map<DamageCause, Integer> deaths;
		public Map<DamageCause, Integer> damage;
		public int totalDeaths = 0;
		
		// constructor for simply setting up the variables
		public AutoRefPlayer(Player p)
		{
			kills = new HashMap<String, Integer>();
			deaths = new HashMap<DamageCause, Integer>();
			damage = new HashMap<DamageCause, Integer>();
			player = p;
		}
		
		// register that we just received this damage
		public void registerDamage(EntityDamageEvent e)
		{
			// get the last damage cause, and mark that as the cause of the damage
			DamageCause dc = DamageCause.fromDamageEvent(e);
			damage.put(dc, e.getDamage() + (damage.containsKey(dc) ? damage.get(dc) : 0));
		}
		
		// register that we just died
		public void registerDeath(PlayerDeathEvent e)
		{
			// get the last damage cause, and mark that as the cause of one death
			DamageCause dc = DamageCause.fromDamageEvent(e.getEntity().getLastDamageCause());
			deaths.put(dc, 1 + (deaths.containsKey(dc) ? deaths.get(dc) : 0));
			++totalDeaths;
		}
		
		// register that we killed the Player who fired this event
		public void registerKill(PlayerDeathEvent e)
		{
			String pname = e.getEntity().getName();
			kills.put(pname, 1 + (kills.containsKey(pname) ? kills.get(pname) : 0));
			++totalKills;
		}
	}
	
	static class DamageCause
	{
		// cause of damage, primary value for damage cause
		public EntityDamageEvent.DamageCause damageCause;
		
		// extra information to accompany damage cause
		public Object payload = null;
		
		// generate a hashcode
		@Override public int hashCode()
		{ return (payload == null ? 0 : payload.hashCode()) ^ 
			damageCause.hashCode(); }

		@Override public boolean equals(Object o)
		{ return hashCode() == o.hashCode(); }
		
		public DamageCause(EntityDamageEvent.DamageCause c, Object p)
		{ damageCause = c; payload = p; }
		
		public static DamageCause fromDamageEvent(EntityDamageEvent e)
		{
			EntityDamageEvent.DamageCause c = e.getCause();
			Object p = null;
			
			EntityDamageByEntityEvent edEvent = null;
			if ((e instanceof EntityDamageByEntityEvent))
				edEvent = (EntityDamageByEntityEvent) e;
			
			switch (c)
			{
				case ENTITY_ATTACK:
				case ENTITY_EXPLOSION:
					// get the entity that did the killing
					if (edEvent != null)
						p = edEvent.getDamager();
					break;

				case PROJECTILE:
				case MAGIC:
					// get the shooter from the projectile
					if (edEvent != null && edEvent.getDamager() != null)
						p = ((Projectile) edEvent.getDamager()).getShooter();
					
					// change damage cause to ENTITY_ATTACK
					//c = EntityDamageEvent.DamageCause.ENTITY_ATTACK;
					break;
			}
			
			if ((p instanceof Monster))
				p = ((Monster) p).getType();
			return new DamageCause(c, p);
		}
		
		@Override public String toString()
		{
			String damager = null;
			
			// generate a 'damager' string for more information
			if ((payload instanceof Player))
				damager = ((Player) payload).getName();
			if ((payload instanceof EntityType))
				damager = ((EntityType) payload).name();
			
			// return a string representing this damage cause
			return (damager == null ? "" : (damager + "'s "))
				+ damageCause.name();
		}
	}

	static class Team
	{
		// reference to the match
		public AutoRefMatch match = null;
		
		// team's name, may or may not be color-related
		public String name = null;
	
		// color to use for members of this team
		public ChatColor color = null;
	
		// maximum size of a team (for manual mode only)
		public int maxSize = 0;
		
		// is this team ready to play?
		public boolean ready = false;
	
		// list of regions
		public List<CuboidRegion> regions = null;
		
		// location of custom spawn
		public Location spawn;
	
		// win-conditions, locations mapped to expected block data
		public Map<Location, BlockData> winconditions;
	
		// does a provided search string match this team?
		public boolean match(String needle)
		{ return -1 != name.toLowerCase().indexOf(needle.toLowerCase()); }
	
		// a factory for processing config maps
		@SuppressWarnings("unchecked")
		public static Team fromMap(Map<String, Object> conf, AutoRefMatch match)
		{
			Team newTeam = new Team();
			newTeam.color = ChatColor.WHITE;
			newTeam.maxSize = 0;
			
			newTeam.match = match;
			World w = match.world;
	
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
	
			// get the max size from the map
			if (conf.containsKey("maxsize"))
			{
				Integer msz = (Integer) conf.get("maxsize");
				if (msz != null) newTeam.maxSize = msz.intValue();
			}
	
			newTeam.regions = new ArrayList<CuboidRegion>();
			if (conf.containsKey("regions"))
			{
				List<String> coordstrings = (List<String>) conf.get("regions");
				if (coordstrings != null) for (String coords : coordstrings)
				{
					CuboidRegion creg = AutoReferee.coordsToRegion(coords);
					if (creg != null) newTeam.regions.add(creg);
				}
			}
	
			newTeam.winconditions = new HashMap<Location, AutoReferee.BlockData>();
			if (conf.containsKey("win-condition"))
			{
				List<String> wclist = (List<String>) conf.get("win-condition");
				if (wclist != null) for (String wc : wclist)
				{
					String[] wcparts = wc.split(":");
					
					Vector v = AutoReferee.coordsToVector(wcparts[0]);
					Location loc = new Location(w, v.getBlockX(), v.getBlockY(), v.getBlockZ());
					newTeam.winconditions.put(loc, AutoReferee.BlockData.fromString(wcparts[1]));
				}
			}
	
			return newTeam;
		}
	
		public Map<String, Object> toMap()
		{
			Map<String, Object> map = new HashMap<String, Object>();
	
			// add name to the map
			map.put("name", name);
	
			// add string representation of the color
			map.put("color", color.name());
	
			// add the maximum team size
			map.put("maxsize", new Integer(maxSize));
			
			// convert the win conditions to strings
			List<String> wcond = new ArrayList<String>();
			for (Map.Entry<Location, AutoReferee.BlockData> e : winconditions.entrySet())
				wcond.add(AutoReferee.vectorToCoords(AutoReferee.locationToBlockVector(e.getKey())) 
					+ ":" + e.getValue());
	
			// add the win condition list
			map.put("win-condition", wcond);
	
			// convert regions to strings
			List<String> regstr = new ArrayList<String>();
			for (CuboidRegion reg : regions)
				regstr.add(AutoReferee.regionToCoords(reg));
	
			// add the region list
			map.put("regions", regstr);
	
			// return the map
			return map;
		}
	
		public String getName()
		{ return color + name + ChatColor.WHITE; }
	}
}

