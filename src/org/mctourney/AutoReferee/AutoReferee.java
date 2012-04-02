package org.mctourney.AutoReferee;

import java.io.File;
import java.io.InputStream;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.*;
import com.sk89q.worldedit.regions.CuboidRegion;

public class AutoReferee extends JavaPlugin 
{	
	public enum eAction {
		ENTERED_VOIDLANE,
	};

	public enum eMatchStatus {
		NONE, WAITING, READY, PLAYING, COMPLETED,
	};

	// is this plugin in online mode?
	public boolean onlineMode = false;
	private Socket serverConn = null;

	// status always starts with NONE
	private eMatchStatus currentState = eMatchStatus.NONE;
	public eMatchStatus getState() { return currentState; }

	// all valid teams for this map
	public List<Team> teams = null;

	// a map from a team to a list of cuboid regions that are "safe"
	private List<CuboidRegion> sharedRegions;

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
		World world = getServer().getWorlds().get(0);
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

		// create lists for each team of safe regions (initially empty)
		sharedRegions = new ArrayList<CuboidRegion>();

		// get server list, and attempt to determine whether we are in online mode
		List<String> serverList = getConfig().getList("server-mode.server-list", new ArrayList<String>());
		onlineMode = (serverList.size() == 0 || getConfig().getBoolean("online", true));

		// wrap up, debug to follow this message
		log.info("AutoReferee loaded successfully.");

		// connect to server, or let the server operator know to set up the match manually
		if (onlineMode && makeServerConnection(serverList));
		else log.info("AutoReferee is running in OFFLINE mode. All setup must be done manually.");

	}

	public boolean makeServerConnection(List<String> servers)
	{
		// TODO: Make socket connection!
		return false;
	}

	public void onDisable()
	{
		// these settings can (and will) be changed
		World world = getServer().getWorlds().get(0);
		saveWorldConfiguration(world);

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
		List<Object> cfgTeams = worldConfig.getList("match.teams");
		for (Object obj : cfgTeams) if (obj instanceof Map<?, ?>)
			teams.add(Team.fromMap((Map<String, Object>) obj));
	}

	private void saveWorldConfiguration(World world) 
	{
		// if there is no configuration object or file, nothin' doin'...
		if (worldConfigFile == null || worldConfig == null) return;

		// create the team data list
		List<Map<String, Object>> teamData = new ArrayList<Map<String, Object>>();
		for (Team t : teams) teamData.add(t.toMap());

		// save this list
		worldConfig.set("match.teams", teamData);

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

	public String colorPlayer(Player player)
	{
		// color a team's name with its team's color
		ChatColor color = getTeamColor(player);
		return color + player.getName() + ChatColor.WHITE;
	}

	public void joinTeam(Player player, Integer t)
	{
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

				Team team = teams.get(t);
				team.regions.add(reg);

				player.sendMessage("Region now marked as " + 
					team.getName() + "'s zone!");
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
					player.sendMessage("   (" + mn.getBlockX() + ", " + mn.getBlockY() + ", " + mn.getBlockZ() + 
						") => (" + mx.getBlockX() + ", " + mx.getBlockY() + ", " + mx.getBlockZ() + ")");
				}

				// if there are no regions, print None
				else player.sendMessage("   <None>");
			}

			return true;
		}

		if (cmd.getName().equalsIgnoreCase("jointeam") && !onlineMode)
		{
			if (args.length == 0)
			{
				// command is invalid. let the player know
				player.sendMessage("Must specify a team to join.");
				return true;
			}

			Integer t = teamNameLookup(args[0]);
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
		if (cmd.getName().equalsIgnoreCase("wincond"))
		{
			return true;
		}

		return false;
	}

	public Boolean checkPosition(Player player, Location loc, Boolean move)
	{
		// get the player's team (if they are not on a team, ignore them)
		Integer pteam = playerTeam.get(player.getName());
		if (pteam == null) return false;

		// convert their location to a WorldEdit vector
		Vector pos = new Vector(loc.getX(), loc.getY(), loc.getZ());

		// check all safe regions for that player's team
		for (CuboidRegion reg : teams.get(pteam).regions)
		{
			// if the player is inside this region, they are okay
			if (!reg.contains(pos)) return true;
		}

		return false;
	}

	public void checkTeamsReady() 
	{

	}

	public static CuboidRegion coordsToRegion(String coords)
	{
		// split the coords along whitespace
		String[] values = coords.split("\\s+");

		try
		{
			// attempt to parse integers and create vectors
			return new CuboidRegion(
				new Vector( // vector 1
					Integer.parseInt(values[0]),
					Integer.parseInt(values[1]),
					Integer.parseInt(values[2])),
				new Vector( // vector 2
					Integer.parseInt(values[3]),
					Integer.parseInt(values[4]),
					Integer.parseInt(values[5]))
				);
		}

		// return garbage, something failed...
		catch (Exception e) { return null; }
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

	// does a provided search string match this team?
	public boolean match(String needle)
	{ return name.toLowerCase().startsWith(needle); }

	// a factory for processing config maps
	@SuppressWarnings("unchecked")
	public static Team fromMap(Map<String, Object> conf)
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
			for (String coords : coordstrings)
			{
				CuboidRegion creg = AutoReferee.coordsToRegion(coords);
				if (creg != null) newTeam.regions.add(creg);
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

		// convert regions to strings
		List<String> regstr = new ArrayList<String>();
		for (CuboidRegion reg : regions)
		{
			// save region as "minX minY minZ maxX maxY maxZ"
			Vector mn = reg.getMinimumPoint(), mx = reg.getMaximumPoint();
			regstr.add(mn.getBlockX() + " " + mn.getBlockY() + " " + mn.getBlockZ() + 
				" " + mx.getBlockX() + " " + mx.getBlockY() + " " + mx.getBlockZ());
		}

		// add the region list
		map.put("regions", regstr);

		// return the map
		return map;
	}

	public String getName()
	{ return color + name + ChatColor.WHITE; }
}

