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

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.*;
import com.sk89q.worldedit.regions.CuboidRegion;

public class AutoReferee extends JavaPlugin
{
	// default port for a "master" server
	public static final int DEFAULT_SERVER_PORT = 43760;
	
	public enum eAction {
		ENTERED_VOIDLANE,
	};

	public enum eMatchStatus {
		NONE, WAITING, READY, PLAYING, COMPLETED,
	};
	
	private World world;
	private long startTime = 8000;

	// is this plugin in online mode?
	public boolean onlineMode = false;
	private RefereeClient conn = null;

	// status always starts with NONE
	private eMatchStatus currentState = eMatchStatus.PLAYING;
	public eMatchStatus getState() { return currentState; }

	// all valid teams for this map
	public List<Team> teams = null;

	// a map from a team to a list of cuboid regions that are "safe"
	private CuboidRegion startRegion;

	// a map from a player to info about why they were killed
	public HashMap<Player, eAction> actionTaken;

	protected String matchName = "[Scheduled Match]";
	public String getMatchName() { return matchName; }

	public Integer teamNameLookup(String name)
	{
		// is this team name a word?
		for (int t = 0; t < teams.size(); ++t)
			if (teams.get(t).match(name))
				return new Integer(t);

		// is this team name a number?
		try
		{
			// we check that this is a valid team
			Integer z = Integer.parseInt(name) - 1;
			return z < teams.size() ? z : null;
		}
		catch (NumberFormatException e) { return null; }
	}

	protected HashMap<String, Integer> playerTeam;
	public Integer getTeam(Player player)
	{
		// get team from player
		return playerTeam.get(player.getName());
	}

	public ChatColor getTeamColor(Player player)
	{
		// if not on a team, don't modify the player name
		Integer team = getTeam(player);
		if (team == null) return ChatColor.WHITE;

		// get color of the team they are on
		return teams.get(team).color;
	}

	protected HashMap<Integer, Location> teamSpawn;
	public Location getPlayerSpawn(Player player)
	{
		// get player's team
		Integer team = getTeam(player);

		// if they are not on a team, or their team does not have a special spawn
		if (team == null || !teamSpawn.containsKey(team)) return null;

		// otherwise, return the appropriate location
		return teamSpawn.get(team);
	}

	public Logger log = Logger.getLogger("Minecraft");
	public void onEnable()
	{
		PluginManager pm = getServer().getPluginManager(); 

		// load main world's configuration file BEFORE the listeners
		// (some listeners will cache the map configuration settings)
		world = getServer().getWorlds().get(0);
		loadWorldConfiguration(world);

		// events related to team management, chat, whitelists, respawn
		pm.registerEvents(new TeamListener(this), this);

		// events related to PvP, damage, death, mobs
		pm.registerEvents(new PlayerVersusPlayerListener(this), this);

		// events related to safe zones, creating zones, map conditions
		pm.registerEvents(new ZoneListener(this), this);

		playerTeam = new HashMap<String, Integer>();
		actionTaken = new HashMap<Player, eAction>();

		// global configuration object (can't be changed, so don't save onDisable)
		getConfig().options().copyDefaults(true); saveConfig();

		// get server list, and attempt to determine whether we are in online mode
		List<?> serverList = getConfig().getList("server-mode.server-list", new ArrayList<String>());
		onlineMode = !(serverList.size() == 0 || !getConfig().getBoolean("server-mode.online", true));

		// wrap up, debug to follow this message
		log.info("AutoReferee loaded successfully.");

		// connect to server, or let the server operator know to set up the match manually
		if (!makeServerConnection(serverList))
			log.info("AutoReferee is running in OFFLINE mode. All setup must be done manually.");

		// update online mode to represent whether or not we have a connection
		onlineMode = (conn != null);
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
		// if autosaving is on, save the world config file
		if (getConfig().getBoolean("config-mode.autosave", false))
			saveWorldConfiguration(world);

		// if there is a socket connection, close it
		if (conn != null) conn.close();
		log.info("AutoReferee disabled.");
	}

	private File worldConfigFile = null;
	private FileConfiguration worldConfig = null;

	public FileConfiguration getMapConfig() { return worldConfig; }

	@SuppressWarnings("unchecked")
	private void loadWorldConfiguration(World world) 
	{
		// file stream and configuration object (located in world folder)
		worldConfigFile = new File(world.getWorldFolder(), "autoreferee.yml");

		// notify user if there is no configuration file
		if (!worldConfigFile.exists())
			log.info("No configuration file exists for this map. Creating one...");
		worldConfig = YamlConfiguration.loadConfiguration(worldConfigFile);

		// load up our default values file, so that we can have a base to work with
		InputStream defConfigStream = getResource("map.yml");
		if (defConfigStream != null) worldConfig.setDefaults(
			YamlConfiguration.loadConfiguration(defConfigStream));

		// make sure any defaults get copied into the map file
		worldConfig.options().copyDefaults(true);
		worldConfig.options().header(this.getDescription().getFullName());
		worldConfig.options().copyHeader(false);

		teams = new ArrayList<Team>();
		
		List<Map<?, ?>> cfgTeams = worldConfig.getMapList("match.teams");
		for (Map<?, ?> map : cfgTeams) teams.add(Team.fromMap((Map<String, Object>) map, world));
		
		// get the start region (safe for both teams, no pvp allowed)
		if (worldConfig.isString("match.start-region"))
			startRegion = coordsToRegion(worldConfig.getString("match.start-region"));
		
		// get the time the match is set to start
		if (worldConfig.isString("match.start-time"))
			startTime = parseStartTime(worldConfig.getString("match.start-time"));
	}

	private void saveWorldConfiguration(World world) 
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
		{ log.info("Could not save world config: " + world.getName()); }
	}

	private WorldEditPlugin getWorldEdit()
	{
		Plugin plugin = getServer().getPluginManager().getPlugin("WorldEdit");

		// WorldEdit may not be loaded
		if (plugin == null || !(plugin instanceof WorldEditPlugin)) 
			return null;

		return (WorldEditPlugin) plugin;
	}

	public void prepareTeam(Integer t, List<String> players)
	{
		// null team not allowed
		if (t == null) return;

		// delete all elements from the map for team 't'
		Iterator<Map.Entry<String, Integer>> i = playerTeam.entrySet().iterator();
		while (i.hasNext()) if (t.equals(i.next().getValue())) i.remove();

		// insert all players into their list
		for (String p : players) playerTeam.put(p, t);
	}

	public String colorPlayer(Player player)
	{
		// color a player's name with its team's color
		ChatColor color = getTeamColor(player);
		return color + player.getName() + ChatColor.WHITE;
	}

	public void joinTeam(Player player, Integer t)
	{
		// null team not allowed
		if (t == null) return;

		// just in case, announce they are leaving their previous team
		leaveTeam(player);

		playerTeam.put(player.getName(), t);
		Team team = teams.get(t);

		getServer().broadcastMessage(colorPlayer(player) + 
			" has joined " + team.getName());
	}

	public void leaveTeam(Player player)
	{
		Integer t = playerTeam.get(player.getName());
		if (t == null) return;

		playerTeam.remove(player.getName());
		Team team = teams.get(t);

		getServer().broadcastMessage(colorPlayer(player) + 
			" has left " + team.getName());
	}

	public boolean playerWhitelisted(Player player)
	{
		return player.isOp() || player.hasPermission("autoreferee.referee") ||
			playerTeam.containsKey(player.getName());
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		Player player = (Player) sender;

		if (cmd.getName().equalsIgnoreCase("zone"))
		{
			if (args.length == 0)
			{
				// command is invalid. let the player know
				player.sendMessage("Must specify a team as this zone's owner.");
				return true;
			}
			
			Integer t = teamNameLookup(args[0]);
			if ("start".equals(args[0])) t = -1;
			else if (t == null)
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

				if (t == -1)
				{
					startRegion = reg;
					player.sendMessage("Region now marked as " +
						"the start region!");
				}
				else
				{
					Team team = teams.get(t);
					team.regions.add(reg);
	
					player.sendMessage("Region now marked as " + 
						team.getName() + "'s zone!");
				}
			}
			return true;
		}
		if (cmd.getName().equalsIgnoreCase("zones"))
		{
			List<Team> lookupTeams = null;

			// if a team has been specified as an argument
			if (args.length > 0)
			{
				Integer t = teamNameLookup(args[0]);
				if (t == null)
				{
					// team name is invalid. let the player know
					player.sendMessage("Not a valid team: " + args[0]);
					return true;
				}

				lookupTeams = new ArrayList<Team>();
				lookupTeams.add(teams.get(t));
			}

			// otherwise, just print all the teams
			else lookupTeams = teams;

			// for all the teams being looked up
			for (Team team : lookupTeams)
			{
				// print team-name header
				player.sendMessage(team.getName() + "'s Regions:");

				// print all the regions owned by this team
				if (team.regions.size() > 0) for (CuboidRegion reg : team.regions)
				{
					Vector mn = reg.getMinimumPoint(), mx = reg.getMaximumPoint();
					player.sendMessage("   (" + vectorToCoords(mn) + ") => (" + vectorToCoords(mx) + ")");
				}

				// if there are no regions, print None
				else player.sendMessage("   <None>");
			}

			return true;
		}

		if (cmd.getName().equalsIgnoreCase("jointeam") && !onlineMode)
		{
			Integer t = (args.length > 0) ? teamNameLookup(args[0]) : getArbitraryTeam();
			if (t == null)
			{
				// team name is invalid. let the player know
				player.sendMessage("Not a valid team: " + args[0]);
				return true;
			}

			Player target = player;
			if (args.length > 1)
			{
				target = getServer().getPlayer(args[1]);
				if (target == null) return true;
			}

			joinTeam(target, t);
			return true;
		}
		if (cmd.getName().equalsIgnoreCase("leaveteam") && !onlineMode)
		{
			Player target = player;
			if (args.length > 0)
			{
				target = getServer().getPlayer(args[0]);
				if (target == null) return true;
			}

			leaveTeam(player);
			return true;
		}
		if (cmd.getName().equalsIgnoreCase("ready"))
		{
			prepareWorld(world);
			return true;
		}
		if (cmd.getName().equalsIgnoreCase("wincond"))
		{
			int wincondtool = getConfig().getInt("config-mode.tools.win-condition", 284);
			
			// get the tool for setting win condition
			if (args.length == 1 && "tool".equalsIgnoreCase(args[0]))
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
			// change win-condition setting mode
			else if (args.length == 2 && "mode".equalsIgnoreCase(args[0]))
			{
				
				return true;
			}
		}

		return false;
	}

	private Integer getArbitraryTeam()
	{
		// minimum size of any one team, and an array to hold valid teams
		int minsize = Integer.MAX_VALUE;
		List<Integer> vteams = new ArrayList<Integer>();
		
		// get the number of players on each team: Map<TeamNumber -> NumPlayers>
		Map<Integer,Integer> count = new HashMap<Integer,Integer>();
		for (Integer v : playerTeam.values())
			count.put(v, count.containsKey(v) ? count.get(v)+1 : 1);
		
		// determine the size of the smallest team
		for (Integer c : count.values())
			if (c < minsize) minsize = c.intValue();
		
		// make a list of all teams with this size
		for (Map.Entry<Integer,Integer> e : count.entrySet())
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
			team.getName() + " @ " + vectorToCoords(locationToVector(block.getLocation())));
	}

	public void checkWinConditions(Location aloc)
	{
		// this code is only called in BlockPlaceEvent and BlockBreakEvent when
		// we have confirmed that the state is PLAYING, so we know we are definitely
		// in a match if this function is being called
		
		for (Team t : teams)
		{
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
				getServer().broadcastMessage(t.getName() + " WINS!");
				for (Player p : world.getPlayers())
					if (playerTeam.containsKey(p.getDisplayName()))
						p.teleport(world.getSpawnLocation());
				currentState = eMatchStatus.COMPLETED;
			}
		}
	}
	
	public List<Team> locationOwnership(Location loc)
	{
		// teams who own this location
		List<Team> owners = new ArrayList<Team>();
		
		// convert location to a WorldEdit vector
		Vector pos = new Vector(loc.getX(), loc.getY(), loc.getZ());

		// check all safe regions for that team
		for (Team team : teams) for (CuboidRegion reg : team.regions)
		{
			// if the location is inside the region, add it
			if (!reg.contains(pos)) owners.add(team);
		}
		
		return owners;
	}

	// simple getter for the start region
	public CuboidRegion getStartRegion() { return startRegion; }
	
	// is location in start region?
	public boolean inStartRegion(Location loc)
	{
		Vector vec = new Vector(loc.getX(), loc.getY(), loc.getZ());
		return startRegion != null && startRegion.contains(vec);
	}

	// TRUE = this location *is* within the player's regions
	// FALSE = this location is *not* within player's regions
	public Boolean checkPosition(Player player, Location loc)
	{
		// get the player's team (if they are not on a team, ignore them)
		Integer pteam = playerTeam.get(player.getName());
		if (pteam == null) return true;
		
		// is the player's location owned by the player's team?
		return locationOwnership(loc).contains(teams.get(pteam));
	}

	public void checkTeamsReady() 
	{
		
	}
	
	public void prepareWorld(World w)
	{
		// set the current time to the start time
		w.setTime(startTime);
		
		// remove all mobs, animals, and items
		for (Entity e : w.getEntitiesByClasses(Monster.class, 
			Animals.class, Item.class)) e.remove();
		
		// turn off weather
		w.setStorm(false);
	}
	
	// ABANDON HOPE, ALL YE WHO ENTER HERE!
	public static long parseStartTime(String t)
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
	{ return new Vector(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()); }
	
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
}

class Team
{
	// team's name, may or may not be color-related
	public String name = null;

	// color to use for members of this team
	public ChatColor color = null;

	// maximum size of a team (for manual mode only)
	public int maxSize = 0;

	// list of regions
	public List<CuboidRegion> regions = null;

	// winconditions, locations mapped to expected block data
	public Map<Location, AutoReferee.BlockData> winconditions;

	// does a provided search string match this team?
	public boolean match(String needle)
	{ return -1 != name.toLowerCase().indexOf(needle.toLowerCase()); }

	// a factory for processing config maps
	@SuppressWarnings("unchecked")
	public static Team fromMap(Map<String, Object> conf, World w)
	{
		Team newTeam = new Team();
		newTeam.color = ChatColor.WHITE;
		newTeam.maxSize = 0;

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
			wcond.add(AutoReferee.vectorToCoords(AutoReferee.locationToVector(e.getKey())) 
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

