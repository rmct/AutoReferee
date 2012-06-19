package org.mctourney.AutoReferee;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import org.mctourney.AutoReferee.AutoReferee.MatchStarter;
import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;

import org.mctourney.AutoReferee.util.CuboidRegion;

public class AutoRefMatch
{
	// world this match is taking place on
	public World world;
	
	// time to set the world to at the start of the match
	public long startTime = 8000L;
	
	// status of the match
	public eMatchStatus currentState = eMatchStatus.NONE;
	
	// teams participating in the match
	public Set<AutoRefTeam> teams = null;
	
	// region defined as the "start" region (safe zone)
	public CuboidRegion startRegion = null;
	
	// name of the match
	public String matchName = "Scheduled Match";
	
	// configuration information for the world
	public File worldConfigFile;
	public FileConfiguration worldConfig;
	
	// basic variables loaded from file
	public boolean allowFriendlyFire = false;
	
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

		teams = new HashSet<AutoRefTeam>();
		
		List<Map<?, ?>> cfgTeams = worldConfig.getMapList("match.teams");
		for (Map<?, ?> map : cfgTeams) teams.add(AutoRefTeam.fromMap((Map<String, Object>) map, this));
		
		// get the start region (safe for both teams, no pvp allowed)
		if (worldConfig.isString("match.start-region"))
			startRegion = AutoReferee.coordsToRegion(worldConfig.getString("match.start-region"));
		
		// get the time the match is set to start
		if (worldConfig.isString("match.start-time"))
			startTime = AutoReferee.parseTimeString(worldConfig.getString("match.start-time"));
		
		// get the extra settings cached
		allowFriendlyFire = worldConfig.getBoolean("match.allow-ff", false);
	}

	void saveWorldConfiguration() 
	{
		// if there is no configuration object or file, nothin' doin'...
		if (worldConfigFile == null || worldConfig == null) return;

		// create and save the team data list
		List<Map<String, Object>> teamData = new ArrayList<Map<String, Object>>();
		for (AutoRefTeam t : teams) teamData.add(t.toMap());
		worldConfig.set("match.teams", teamData);
		
		// save the start region
		if (startRegion != null)
			worldConfig.set("match.start-region", AutoReferee.regionToCoords(startRegion));

		// save the configuration file back to the original filename
		try { worldConfig.save(worldConfigFile); }

		// log errors, report which world did not save
		catch (java.io.IOException e)
		{ plugin.log.info("Could not save world config: " + world.getName()); }
	}
}