package org.mctourney.AutoReferee;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.material.Colorable;

import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;
import org.mctourney.AutoReferee.util.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Maps;

public class AutoRefTeam
{
	public static AutoReferee plugin = null;

	// reference to the match
	private AutoRefMatch match = null;
	
	public AutoRefMatch getMatch()
	{ return match; }

	// player information
	private Set<AutoRefPlayer> players = Sets.newHashSet();

	public Set<AutoRefPlayer> getPlayers()
	{
		if (players != null) return players;
		return Sets.newHashSet();
	}
	
	private Set<OfflinePlayer> expectedPlayers = Sets.newHashSet();
	
	public void addExpectedPlayer(OfflinePlayer op)
	{ expectedPlayers.add(op); }
	
	public void addExpectedPlayer(String name)
	{ addExpectedPlayer(plugin.getServer().getOfflinePlayer(name)); }
	
	public Set<OfflinePlayer> getExpectedPlayers()
	{ return expectedPlayers; }
	
	// team's name, may or may not be color-related
	private String name = null;

	public String getName()
	{ return color + name + ChatColor.RESET; }

	// color to use for members of this team
	private ChatColor color = null;

	public ChatColor getColor()
	{ return color; }

	public void setColor(ChatColor color)
	{ this.color = color; }

	// maximum size of a team (for manual mode only)
	public int maxSize = 0;
	
	// is this team ready to play?
	private boolean ready = false;

	public boolean isReady()
	{ return ready; }

	public void setReady(boolean ready)
	{ this.ready = ready; }

	// list of regions
	private Set<CuboidRegion> regions = null;
	
	public Set<CuboidRegion> getRegions()
	{ return regions; }

	// location of custom spawn
	private Location spawn;
	
	public Location getSpawnLocation()
	{ return spawn == null ? match.getWorld().getSpawnLocation() : spawn; }

	// win-conditions, locations mapped to expected block data
	public Map<Location, BlockData> winConditions;
	public Map<BlockData, SourceInventory> targetChests;

	// does a provided search string match this team?
	public boolean matches(String needle)
	{ return -1 != name.toLowerCase().indexOf(needle.toLowerCase()); }

	// a factory for processing config maps
	@SuppressWarnings("unchecked")
	public static AutoRefTeam fromMap(Map<String, Object> conf, AutoRefMatch match)
	{
		AutoRefTeam newTeam = new AutoRefTeam();
		newTeam.color = ChatColor.RESET;
		newTeam.maxSize = 0;
		
		newTeam.match = match;
		World w = match.getWorld();

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

		newTeam.regions = Sets.newHashSet();
		if (conf.containsKey("regions"))
		{
			List<String> coordstrings = (List<String>) conf.get("regions");
			if (coordstrings != null) for (String coords : coordstrings)
			{
				CuboidRegion creg = CuboidRegion.fromCoords(coords);
				if (creg != null) newTeam.regions.add(creg);
			}
		}

		newTeam.winConditions = Maps.newHashMap();
		newTeam.targetChests = Maps.newHashMap();
		if (conf.containsKey("win-condition"))
		{
			List<String> wclist = (List<String>) conf.get("win-condition");
			if (wclist != null) for (String wc : wclist)
			{
				String[] wcparts = wc.split(":");
				
				BlockVector3 v = new BlockVector3(Vector3.fromCoords(wcparts[0]));
				newTeam.winConditions.put(new Location(w, v.x, v.y, v.z), 
					BlockData.fromString(wcparts[1]));
			}
		}

		newTeam.players = Sets.newHashSet();
		return newTeam;
	}

	public Map<String, Object> toMap()
	{
		Map<String, Object> map = Maps.newHashMap();

		// add name to the map
		map.put("name", name);

		// add string representation of the color
		map.put("color", color.name());

		// add the maximum team size
		map.put("maxsize", new Integer(maxSize));
		
		// convert the win conditions to strings
		List<String> wcond = Lists.newArrayList();
		for (Map.Entry<Location, BlockData> e : winConditions.entrySet())
			wcond.add(BlockVector3.fromLocation(e.getKey()).toCoords() 
				+ ":" + e.getValue());

		// add the win condition list
		map.put("win-condition", wcond);

		// convert regions to strings
		List<String> regstr = Lists.newArrayList();
		for (CuboidRegion reg : regions) regstr.add(reg.toCoords());

		// add the region list
		map.put("regions", regstr);

		// return the map
		return map;
	}

	public AutoRefPlayer getPlayer(String name)
	{
		if (name != null) for (AutoRefPlayer apl : players)
			if (name.equalsIgnoreCase(apl.player.getName()))
				return apl;
		return null;
	}

	public AutoRefPlayer getPlayer(Player pl)
	{ return getPlayer(pl.getName()); }

	public void join(Player pl)
	{
		// create an APL object for this player.
		AutoRefPlayer apl = new AutoRefPlayer(pl, this);
		
		// null team not allowed, and quit if they are already on this team
		if (players.contains(apl)) return;
		
		players.add(apl);
		pl.teleport(getSpawnLocation());
		
		// if the match is in progress, no one may join
		if (match.getCurrentState().ordinal() >= eMatchStatus.PLAYING.ordinal()) return;
	
		String colorName = getPlayerName(pl);
		match.broadcast(colorName + " has joined " + getName());
		
		if (pl.isOnline() && (pl instanceof Player))
			((Player) pl).setPlayerListName(colorName);

		match.checkTeamsReady();
	}

	public void leave(Player pl)
	{
		// create an APL object for this player.
		AutoRefPlayer apl = new AutoRefPlayer(pl);
		if (!players.remove(apl)) return;
		
		String colorName = getPlayerName(pl);
		match.broadcast(colorName + " has left " + getName());
		
		if (pl.isOnline() && (pl instanceof Player))
			((Player) pl).setPlayerListName(pl.getName());

		match.checkTeamsReady();
	}
	
	public String getPlayerName(Player pl)
	{
		AutoRefPlayer apl = new AutoRefPlayer(pl);
		if (!players.contains(apl)) return pl.getName();
		return color + pl.getName() + ChatColor.RESET;
	}

	// TRUE = this location *is* within this team's regions
	// FALSE = this location is *not* within team's regions
	public boolean checkPosition(Location loc)
	{
		// is the provided location owned by this team?
		return distanceToClosestRegion(loc) < ZoneListener.SNEAK_DISTANCE;
	}

	public double distanceToClosestRegion(Location loc)
	{
		double distance = match.getStartRegion().distanceToRegion(loc);
		for ( CuboidRegion reg : regions ) if (distance > 0)
			distance = Math.min(distance, reg.distanceToRegion(loc));
		return distance;
	}

	public class SourceInventory
	{
		public Location location;
		public Inventory inventory;
		public BlockData blockdata;

		@Override public String toString()
		{ return BlockVector3.fromLocation(location).toCoords(); }
	}

	public void addSourceInventory(Block block)
	{
		if (block == null) return;

		if (!(block.getState() instanceof InventoryHolder)) return;
		InventoryHolder cblock = (InventoryHolder) block.getState();

		SourceInventory src = new SourceInventory();
		src.location = block.getLocation();
		src.inventory = cblock.getInventory();
		
		BlockData bd = BlockData.fromInventory(src.inventory);
		if (bd == null) return;
		
		targetChests.put(src.blockdata = bd, src);
		match.broadcast(BlockVector3.fromLocation(block.getLocation()).toCoords() + 
			" is a source chest for " + bd.getName());
	}
	
	public void addWinCondition(Block block)
	{
		// if the block is null, forget it
		if (block == null) return;
		
		// add the block data to the win-condition listing
		BlockData bd = BlockData.fromBlock(block);
		winConditions.put(block.getLocation(), bd);
		
		// broadcast the update
		match.broadcast(bd.getName() + " is now a win condition for " + getName() + 
			" @ " + BlockVector3.fromLocation(block.getLocation()).toCoords());
	}
}
