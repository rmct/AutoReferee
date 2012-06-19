package org.mctourney.AutoReferee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;

import org.mctourney.AutoReferee.util.*;

class AutoRefTeam
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
	public static AutoRefTeam fromMap(Map<String, Object> conf, AutoRefMatch match)
	{
		AutoRefTeam newTeam = new AutoRefTeam();
		newTeam.color = ChatColor.RESET;
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

		newTeam.winconditions = new HashMap<Location, BlockData>();
		if (conf.containsKey("win-condition"))
		{
			List<String> wclist = (List<String>) conf.get("win-condition");
			if (wclist != null) for (String wc : wclist)
			{
				String[] wcparts = wc.split(":");
				
				BlockVector3 v = new BlockVector3(AutoReferee.coordsToVector(wcparts[0]));
				newTeam.winconditions.put(new Location(w, v.x, v.y, v.z), 
					BlockData.fromString(wcparts[1]));
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
		for (Map.Entry<Location, BlockData> e : winconditions.entrySet())
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
	{ return color + name + ChatColor.RESET; }
}