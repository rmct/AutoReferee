package org.mctourney.AutoReferee;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.StringUtils;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.material.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import org.mctourney.AutoReferee.listeners.ZoneListener;
import org.mctourney.AutoReferee.util.ArmorPoints;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.CuboidRegion;
import org.mctourney.AutoReferee.util.Vector3;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class AutoRefMatch
{
	// online map list
	private static final String MAPREPO = "http://s3.amazonaws.com/autoreferee/maps/";
	private static final String MAPLIST = MAPREPO + "list.csv";
	
	// world this match is taking place on
	private World world;
	private boolean tmp;

	public World getWorld()
	{ return world; }

	private boolean isTemporaryWorld()
	{ return tmp; }

	// time to set the world to at the start of the match
	private long startTime = 8000L;
	
	public long getStartTime()
	{ return startTime; }

	public void setStartTime(long startTime)
	{ this.startTime = startTime; }
	
	public enum MatchStatus {
		NONE, WAITING, READY, PLAYING, COMPLETED;

		public boolean isBeforeMatch()
		{ return this.ordinal() < PLAYING.ordinal() && this != NONE; }

		public boolean isAfterMatch()
		{ return this.ordinal() > PLAYING.ordinal() && this != NONE; }

		public boolean inProgress()
		{ return this == PLAYING; }
	}
	
	// status of the match
	private MatchStatus currentState = MatchStatus.NONE;

	public MatchStatus getCurrentState()
	{ return currentState; }

	public void setCurrentState(MatchStatus s)
	{ this.currentState = s; this.setupSpectators(); }
	
	// teams participating in the match
	private Set<AutoRefTeam> teams = null;

	public Set<AutoRefTeam> getTeams()
	{ return teams; }

	public List<AutoRefTeam> getSortedTeams()
	{
		List<AutoRefTeam> sortedTeams = Lists.newArrayList(getTeams());
		Collections.sort(sortedTeams);
		return sortedTeams;
	}

	public String getTeamList()
	{
		Set<String> tlist = Sets.newHashSet();
		for (AutoRefTeam team : getSortedTeams())
			tlist.add(team.getName());
		return StringUtils.join(tlist, ", ");
	}
	
	private AutoRefTeam winningTeam = null;
	
	public AutoRefTeam getWinningTeam()
	{ return winningTeam; }
	
	public void setWinningTeam(AutoRefTeam t)
	{ winningTeam = t; }
	
	// region defined as the "start" region (safe zone)
	private CuboidRegion startRegion = null;

	public CuboidRegion getStartRegion()
	{ return startRegion; }

	public void setStartRegion(CuboidRegion startRegion)
	{ this.startRegion = startRegion; }
	
	// name of the match
	private String matchName = null;
	
	public void setMatchName(String nm)
	{ matchName = nm; }

	public String getMatchName()
	{
		// if we have a specific match name...
		if (matchName != null) return matchName;
		
		// generate a date string
		String date = new SimpleDateFormat("dd MMM yyyy").format(new Date());
		
		// if the map is named, return map name as a placeholder
		if (mapName != null) return mapName + ": " + date;
		
		// otherwise, just return the date
		return date;
	}
	
	// configuration information for the world
	public File worldConfigFile;
	public FileConfiguration worldConfig;
	
	// basic variables loaded from file
	private String mapName = null;
	private Collection<String> mapAuthors = null;

	public String getMapName() 
	{ return mapName; }
	
	private String versionString = "1.0";
	
	public String getVersion()
	{ return versionString; }

	public String getMapAuthors()
	{
		if (mapAuthors != null && mapAuthors.size() != 0)
			return StringUtils.join(mapAuthors, ", ");
		return "??";
	}
	
	private long startTicks = 0;
	
	public long getStartTicks()
	{ return startTicks; }

	public void setStartTicks(long startTicks)
	{ this.startTicks = startTicks; }

	public long getMatchTime()
	{
		if (!getCurrentState().inProgress()) return 0L;
		return (getWorld().getFullTime() - getStartTicks()) / 20L;
	}

	public String getTimestamp()
	{ return getTimestamp(":"); }

	public String getTimestamp(String sep)
	{
		long timestamp = this.getMatchTime();
		return String.format("%02d%s%02d%s%02d", timestamp/3600L,
			sep, (timestamp/60L)%60L, sep, timestamp%60L);
	}

	// task that starts the match
	public AutoRefMatch.MatchStartTask matchStarter = null;
	
	// mechanisms to open the starting gates
	public Set<StartMechanism> startMechanisms = null;
	
	// protected entities - only protected from "butchering"
	public Set<UUID> protectedEntities = null;
	
	public boolean allowFriendlyFire = false;
	public boolean allowCraft = false;
	
	// list of items players may not craft
	private Set<BlockData> prohibitCraft = Sets.newHashSet();

	// transcript of every event in the match
	private List<TranscriptEvent> transcript;
	
	private boolean refereeReady = false;
	
	public boolean isRefereeReady()
	{ return getReferees().size() == 0 || refereeReady; }

	public void setRefereeReady(boolean r)
	{ refereeReady = r; }

	private boolean debugMode = false;

	public boolean isDebugMode()
	{ return debugMode; }
	
	public void setDebugMode(boolean d)
	{ broadcast(ChatColor.GREEN + "Debug mode is now " + ((debugMode = d) ? "on" : "off")); }
	
	// number of seconds for each phase
	public static final int READY_SECONDS = 15;
	public static final int COMPLETED_SECONDS = 180;

	public AutoRefMatch(World world, boolean tmp, MatchStatus state)
	{ this(world, tmp); setCurrentState(state); }

	public AutoRefMatch(World world, boolean tmp)
	{
		this.world = world;
		loadWorldConfiguration();
		
		// is this world a temporary world?
		this.tmp = tmp;
		
		// brand new match transcript
		transcript = Lists.newLinkedList();
		
		// fix vanish
		this.setupSpectators();
	}

	public Set<AutoRefPlayer> getPlayers()
	{
		Set<AutoRefPlayer> players = Sets.newHashSet();
		for (AutoRefTeam team : teams)
			players.addAll(team.getPlayers());
		return players;
	}

	public Set<Player> getReferees()
	{
		Set<Player> refs = Sets.newHashSet();
		for (Player p : world.getPlayers())
			if (p.hasPermission("autoreferee.referee")) refs.add(p);
		for (AutoRefPlayer apl : getPlayers())
			if (apl.getPlayer() != null) refs.remove(apl.getPlayer());
		return refs;
	}

	public boolean isReferee(Player p)
	{
		for (AutoRefPlayer apl : getPlayers())
			if (apl.getPlayerName() == p.getName()) return false;
		return p.hasPermission("autoreferee.referee");
	}
	
	public static boolean isCompatible(World w)
	{ return new File(w.getWorldFolder(), "autoreferee.yml").exists(); }
	
	public void reload()
	{ this.loadWorldConfiguration(); }
	
	@SuppressWarnings("unchecked")
	private void loadWorldConfiguration()
	{
		// file stream and configuration object (located in world folder)
		worldConfigFile = new File(world.getWorldFolder(), "autoreferee.yml");
		worldConfig = YamlConfiguration.loadConfiguration(worldConfigFile);

		// load up our default values file, so that we can have a base to work with
		InputStream defConfigStream = AutoReferee.getInstance().getResource("defaults/map.yml");
		if (defConfigStream != null) worldConfig.setDefaults(
			YamlConfiguration.loadConfiguration(defConfigStream));

		// make sure any defaults get copied into the map file
		worldConfig.options().copyDefaults(true);
		worldConfig.options().header(AutoReferee.getInstance().getDescription().getFullName());
		worldConfig.options().copyHeader(false);

		teams = Sets.newHashSet();
		messageReferees("match", getWorld().getName(), "init");

		for (Map<?, ?> map : worldConfig.getMapList("match.teams"))
			teams.add(AutoRefTeam.fromMap((Map<String, Object>) map, this));
		
		startMechanisms = Sets.newHashSet();
		for (String sm : worldConfig.getStringList("match.start-mechanisms"))
			startMechanisms.add(StartMechanism.unserialize(world, sm));
		
		protectedEntities = Sets.newHashSet();
		for (String uid : worldConfig.getStringList("match.protected-entities"))
			protectedEntities.add(UUID.fromString(uid));
		
		prohibitCraft = Sets.newHashSet();
		for (String b : worldConfig.getStringList("match.no-craft"))
			prohibitCraft.add(BlockData.fromString(b));
		
		// HELPER: ensure all protected entities are still present in world
		Set<UUID> uuidSearch = Sets.newHashSet(protectedEntities);
		for (Entity e : getWorld().getEntities()) uuidSearch.remove(e.getUniqueId());
		if (!uuidSearch.isEmpty()) this.broadcast(ChatColor.RED + "" + ChatColor.BOLD + "WARNING: " + 
			ChatColor.RESET + "One or more protected entities are missing!");
		
		// get the start region (safe for both teams, no pvp allowed)
		if (worldConfig.isString("match.start-region"))
			startRegion = CuboidRegion.fromCoords(worldConfig.getString("match.start-region"));
		
		// get the time the match is set to start
		if (worldConfig.isString("match.start-time"))
			startTime = AutoReferee.parseTimeString(worldConfig.getString("match.start-time"));
		
		// get the extra settings cached
		mapName = worldConfig.getString("map.name", "<Untitled>");
		versionString = worldConfig.getString("map.version", "1.0");
		mapAuthors = worldConfig.getStringList("map.creators");
		
		allowFriendlyFire = worldConfig.getBoolean("match.allow-ff", false);
		allowCraft = worldConfig.getBoolean("match.allow-craft", false);
	}

	public void saveWorldConfiguration() 
	{
		// if there is no configuration object or file, nothin' doin'...
		if (worldConfigFile == null || worldConfig == null) return;

		// create and save the team data list
		List<Map<String, Object>> teamData = Lists.newArrayList();
		for (AutoRefTeam t : teams) teamData.add(t.toMap());
		worldConfig.set("match.teams", teamData);
		
		// save the start mechanisms
		List<String> smList = Lists.newArrayList();
		for ( StartMechanism sm : startMechanisms ) smList.add(sm.serialize());
		worldConfig.set("match.start-mechanisms", smList);
		
		// save the protected entities
		List<String> peList = Lists.newArrayList();
		for ( UUID uid : protectedEntities ) peList.add(uid.toString());
		worldConfig.set("match.protected-entities", peList);
		
		// save the craft blacklist
		List<String> ncList = Lists.newArrayList();
		for ( BlockData bd : prohibitCraft ) ncList.add(bd.toString());
		worldConfig.set("match.no-craft", ncList);
		
		// save the start region
		if (startRegion != null)
			worldConfig.set("match.start-region", startRegion.toCoords());

		// save the configuration file back to the original filename
		try { worldConfig.save(worldConfigFile); }

		// log errors, report which world did not save
		catch (java.io.IOException e)
		{ AutoReferee.getInstance().getLogger().info("Could not save world config: " + world.getName()); }
	}

	public void messageReferees(String ...parts)
	{
		if (this.isDebugMode()) broadcast(ChatColor.DARK_GRAY + StringUtils.join(parts, ":"));
		for (Player ref : getReferees()) messageReferee(ref, parts);
	}	
	
	public void messageReferee(Player ref, String ...parts)
	{
		try
		{
			ref.sendPluginMessage(AutoReferee.getInstance(), AutoReferee.REFEREE_PLUGIN_CHANNEL, 
				StringUtils.join(parts, ":").getBytes(AutoReferee.PLUGIN_CHANNEL_ENC));
		}
		catch (UnsupportedEncodingException e)
		{ AutoReferee.getInstance().getLogger().info("Unsupported encoding: " + AutoReferee.PLUGIN_CHANNEL_ENC); }
	}

	public void updateReferee(Player ref)
	{
		messageReferee(ref, "match", getWorld().getName(), "init");
		messageReferee(ref, "match", getWorld().getName(), "map", getMapName());

		if (getCurrentState().inProgress())
			messageReferee(ref, "match", getWorld().getName(), "time", getTimestamp(","));
		
		for (AutoRefTeam team : getTeams())
		{
			messageReferee(ref, "team", team.getRawName(), "init");
			messageReferee(ref, "team", team.getRawName(), "color", team.getColor().toString());

			for (BlockData bd : team.getObjectives())
				messageReferee(ref, "team", team.getRawName(), "obj", "+" + bd.toString());

			for (AutoRefPlayer apl : team.getPlayers())
			{
				messageReferee(ref, "team", team.getRawName(), "player", "+" + apl.getPlayerName());
				updateReferee(ref, apl);
			}
		}
	}

	public void updateReferee(Player ref, AutoRefPlayer apl)
	{
		messageReferee(ref, "player", apl.getPlayerName(), "kills", Integer.toString(apl.totalKills));
		messageReferee(ref, "player", apl.getPlayerName(), "deaths", Integer.toString(apl.totalDeaths));
		messageReferee(ref, "player", apl.getPlayerName(), "streak", Integer.toString(apl.totalStreak));

		Player pl = apl.getPlayer();
		if (pl != null)
		{
			messageReferee(ref, "player", apl.getPlayerName(), "hp", Integer.toString(pl.getHealth()));
			messageReferee(ref, "player", apl.getPlayerName(), "armor", Integer.toString(ArmorPoints.fromPlayer(pl)));
		}

		for (AutoRefPlayer en : getPlayers()) if (apl.isDominating(en))
			messageReferee(ref, "player", apl.getPlayerName(), "dominate", en.getPlayerName());
	}

	public void broadcast(String msg)
	{ for (Player p : world.getPlayers()) p.sendMessage(msg); }

	public static String normalizeMapName(String m)
	{ return m == null ? null : m.toLowerCase().replaceAll("[^0-9a-z]+", ""); }

	public String getVersionString()
	{ return String.format("%s-v%s", this.getMapName().replaceAll("[^0-9a-zA-Z]+", ""), this.getVersion()); }
	
	public static File unzipMapFolder(File zip) throws IOException
	{
		ZipFile zfile = new ZipFile(zip);
		Enumeration<? extends ZipEntry> entries = zfile.entries();
		
		File f, basedir = null;
		
		File lib = AutoRefMatch.getMapLibrary();
		while (entries.hasMoreElements())
		{
			ZipEntry entry = entries.nextElement();			
			if (!entry.isDirectory()) FileUtils.copyInputStreamToFile(
				zfile.getInputStream(entry), f = new File(lib, entry.getName()));
			else (f = new File(lib, entry.getName())).mkdirs();
			
			if (entry.isDirectory() && (basedir == null || 
				basedir.getName().startsWith(f.getName()))) basedir = f;
		}
		
		zfile.close();
		return basedir;
	}

	public static File getMapFolder(String worldName, Long checksum) throws IOException
	{
		// assume worldName exists
		if (worldName == null) return null;
		worldName = AutoRefMatch.normalizeMapName(worldName);
		
		// if there is no map library, quit
		File mapLibrary = getMapLibrary();
		if (!mapLibrary.exists()) return null;
		
		// find the map being requested
		for (File f : mapLibrary.listFiles())
		{
			// if this is a zipfile containing the right world, unzip it
			if (f.getName().toLowerCase().startsWith(worldName) && 
				f.getName().toLowerCase().endsWith(".zip")) return unzipMapFolder(f);
			
			// skip non-directories
			if (!f.isDirectory()) continue;
			
			// if it doesn't have an autoreferee config file
			File cfgFile = new File(f, AutoReferee.CFG_FILENAME);
			if (!cfgFile.exists()) continue;
			
			// check the map name, if it matches, this is the one we want
			FileConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
			String cMapName = AutoRefMatch.normalizeMapName(cfg.getString("map.name"));
			if (!worldName.equals(cMapName)) continue;
			
			// compute the checksum of the directory, make sure it matches
			if (checksum != null &&	recursiveCRC32(f) != checksum) continue;
			
			// this is the map we want
			return f;
		}
		
		// look for map online
		InputStream rd = null;
		StringWriter mlist = new StringWriter();
		
		try
		{
			URL url = new URL(MAPLIST);
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
			
			// read the contents of the URL
			IOUtils.copy(rd = conn.getInputStream(), mlist);
		}
		
		// just drop out
		catch (Exception e) { return null; }
		
		finally
		{
			try
			{
				// close the stream pointer
				if (rd != null) rd.close();
			}
			// meh. don't bother, if something goes wrong here.
			catch (Exception e) {  }
		}
		
		for (String line : mlist.toString().split("[\\r\\n]+"))
		{
			// go through the file until we find the right map
			String[] parts = line.split(";", 4);
			if (!worldName.equalsIgnoreCase(normalizeMapName(parts[0]))) continue;
			
			// download the file
			URL url = new URL(MAPREPO + parts[2]);
			File zip = new File(AutoRefMatch.getMapLibrary(), parts[2]);
			FileUtils.copyURLToFile(url, zip);
			
			// if the md5s match, return the unzipped folder
			String md5sum = DigestUtils.md5Hex(new FileInputStream(zip));
			if (md5sum.equalsIgnoreCase(parts[3])) return unzipMapFolder(zip);
			
			// if the md5sum did not match, quit here
			zip.delete(); throw new IOException(
				"MD5 Mismatch: " + md5sum + " != " + parts[3]);
		}
		
		// no map matches
		return null;
	}

	public static long recursiveCRC32(File file) throws IOException
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

	public static File getMapLibrary()
	{
		// maps library is a folder called `maps/`
		File m = new File("maps");
		
		// if it doesn't exist, make the directory
		if (m.exists() && !m.isDirectory()) m.delete();
		if (!m.exists()) m.mkdir();
		
		// return the maps library
		return m;
	}

	public static void setupWorld(World w, boolean b)
	{
		// if this map isn't compatible with AutoReferee, quit...
		if (AutoReferee.getInstance().getMatch(w) != null || !isCompatible(w)) return;
		AutoReferee.getInstance().addMatch(new AutoRefMatch(w, b, MatchStatus.WAITING));
	}

	public File archiveMapData() throws IOException
	{
		// make sure the folder exists first
		File archiveFolder = new File(getMapLibrary(), this.getVersionString());
		if (!archiveFolder.exists()) archiveFolder.mkdir();
		
		// (1) copy the configuration file:
		FileUtils.copyFileToDirectory(
			new File(getWorld().getWorldFolder(), AutoReferee.CFG_FILENAME), archiveFolder);
		
		// (2) copy the level.dat:
		FileUtils.copyFileToDirectory(
			new File(getWorld().getWorldFolder(), "level.dat"), archiveFolder);
		
		// (3) copy the region folder (only the .mca files):
		FileUtils.copyDirectory(new File(getWorld().getWorldFolder(), "region"), 
			new File(archiveFolder, "region"), FileFilterUtils.suffixFileFilter(".mca"));
		
		// (4) make an empty data folder:
		new File(archiveFolder, "data").mkdir();
		return archiveFolder;
	}
	
	private static void addToZip(ZipOutputStream zip, File f, File base) throws IOException
	{
		zip.putNextEntry(new ZipEntry(base.toURI().relativize(f.toURI()).getPath()));
		if (f.isDirectory()) for (File c : f.listFiles()) addToZip(zip, c, base);
		else IOUtils.copy(new FileInputStream(f), zip);
	}
	
	public File distributeMap() throws IOException
	{
		File archiveFolder = this.archiveMapData();
		File outZipfile = new File(getMapLibrary(), this.getVersionString() + ".zip");
		
		ZipOutputStream zip = new ZipOutputStream(new 
			BufferedOutputStream(new FileOutputStream(outZipfile)));
		zip.setMethod(ZipOutputStream.DEFLATED);
		addToZip(zip, archiveFolder, getMapLibrary());
		
		zip.close();
		return archiveFolder;
	}

	public void destroy() throws IOException
	{
		this.setCurrentState(MatchStatus.NONE);
		AutoReferee.getInstance().clearMatch(this);
		
		// if everyone has been moved out of this world, clean it up
		if (world.getPlayers().size() == 0)
		{
			// if we are running in auto-mode and this is OUR world
			if (AutoReferee.getInstance().isAutoMode() || this.isTemporaryWorld())
			{
				AutoReferee.getInstance().getServer().unloadWorld(world, false);
				if (!AutoReferee.getInstance().getConfig().getBoolean("save-worlds", false))
					FileUtils.deleteDirectory(world.getWorldFolder());
			}
		}
	}
	
	public boolean canCraft(BlockData bd)
	{
		for (BlockData nc : prohibitCraft)
			if (nc.equals(bd)) return false;
		return true;
	}

	public void addIllegalCraft(BlockData bd)
	{
		this.prohibitCraft.add(bd);
		this.broadcast("Crafting " + bd.getName() + " is now prohibited");
	}

	public AutoRefTeam getArbitraryTeam()
	{
		// minimum size of any one team, and an array to hold valid teams
		int minsize = Integer.MAX_VALUE;
		List<AutoRefTeam> vteams = Lists.newArrayList();
		
		// determine the size of the smallest team
		for (AutoRefTeam team : getTeams())
			if (team.getPlayers().size() < minsize)
				minsize = team.getPlayers().size();
	
		// make a list of all teams with this size
		for (AutoRefTeam team : getTeams())
			if (team.getPlayers().size() == minsize) vteams.add(team);
	
		// return a random element from this list
		return vteams.get(new Random().nextInt(vteams.size()));
	}

	public static class StartMechanism
	{
		public Location loc = null;
		public BlockState blockState = null;
		public boolean state = true;
		
		public StartMechanism(Block block, boolean state)
		{
			this.state = state;
			this.loc = block.getLocation(); 
			this.blockState = block.getState();
		}
		
		public StartMechanism(Block block)
		{ this(block, true); }
		
		@Override public int hashCode()
		{ return loc.hashCode() ^ blockState.hashCode(); }
		
		@Override public boolean equals(Object o)
		{ return (o instanceof StartMechanism) && hashCode() == o.hashCode(); }
		
		public String serialize()
		{ return Vector3.fromLocation(loc).toCoords() + ":" + Boolean.toString(state); }
		
		public static StartMechanism unserialize(World w, String sm)
		{
			String[] p = sm.split(":");

			Block block = w.getBlockAt(Vector3.fromCoords(p[0]).toLocation(w));
			boolean state = Boolean.parseBoolean(p[1]);

			return new StartMechanism(block, state);
		}
		
		@Override public String toString()
		{ return blockState.getType().name() + "(" + Vector3.fromLocation(loc).toCoords() + 
			"):" + Boolean.toString(state); }
	}

	public StartMechanism addStartMech(Block block, boolean state)
	{
		if (block.getType() != Material.LEVER) state = true;
		StartMechanism sm = new StartMechanism(block, state);
		startMechanisms.add(sm);
		
		AutoReferee.getInstance().getLogger().info(
			sm.toString() + " is a start mechanism.");
		return sm;
	}

	public boolean isStartMechanism(Location loc)
	{
		if (loc == null) return false;
		for (StartMechanism sm : startMechanisms)
			if (loc.equals(sm.loc)) return true;
		return false;
	}
	
	// unserialized match initialization parameters
	static class MatchParams
	{
		public static class TeamInfo
		{
			private String name;
			
			public String getName()
			{ return name; }
			
			private List<String> players;
	
			public List<String> getPlayers()
			{ return Collections.unmodifiableList(players); }
		}
		
		// info about all the teams
		private List<TeamInfo> teams;
		
		public List<TeamInfo> getTeams()
		{ return Collections.unmodifiableList(teams); }
	
		// match tag for reporting
		private String tag;
		
		public String getTag()
		{ return tag; }
		
		// map name and checksum
		private String map;
		private Long checksum;
		
		public String getMap()
		{ return map; }
		
		public Long getChecksum()
		{ return checksum; }
	}

	public void start()
	{
		// set up the world time one last time
		world.setTime(startTime);
		startTicks = world.getFullTime();
		
		addEvent(new TranscriptEvent(this, TranscriptEvent.EventType.MATCH_START,
			"Match began.", null, null, null));

		// send referees the start event
		messageReferees("match", getWorld().getName(), "start");
		
		// remove all mobs, animals, and items (again)
		this.clearEntities();

		// loop through all the redstone mechanisms required to start / FIXME BUKKIT-1858
		if (AutoReferee.getInstance().isAutoMode())
			for (StartMechanism sm : startMechanisms)
		{
			MaterialData mdata = sm.blockState.getData();
			switch (sm.blockState.getType())
			{
				case LEVER:
					// flip the lever to the correct state
					((Lever) mdata).setPowered(sm.state);
					break;
					
				case STONE_BUTTON:
					// press (or depress) the button
					((Button) mdata).setPowered(sm.state);
					break;
					
				case WOOD_PLATE:
				case STONE_PLATE:
					// press (or depress) the pressure plate
					((PressurePlate) mdata).setData((byte)(sm.state ? 0x1 : 0x0));
					break;
			}
			
			// save the block state and fire an update
			sm.blockState.setData(mdata);
			sm.blockState.update(true);
		}
		
		// set teams as started
		for (AutoRefTeam team : getTeams())
			team.startMatch();
			
		// set the current state to playing
		setCurrentState(MatchStatus.PLAYING);
	}

	private int getVanishLevel(Player p)
	{
		// if this person is a player, lowest vanish level
		if (isPlayer(p)) return 0;

		// referees have the highest vanish level (see everything)
		if (p.hasPermission("autoreferee.referee")) return 200;
		
		// streamers are ONLY able to see streamers and players
		if (p.hasPermission("autoreferee.streamer")) return 1;
		
		// spectators can only be seen by referees
		return 100;
	}
	
	// either vanish or show the player `subj` from perspective of `view`
	private void setupVanish(Player view, Player subj)
	{
		if (getVanishLevel(view) >= getVanishLevel(subj) || 
			!this.getCurrentState().inProgress()) view.showPlayer(subj);
		else view.hidePlayer(subj);
	}

	public void setupSpectators(Player focus)
	{
		setSpectatorMode(focus, isPlayer(focus));
		for ( Player pl : getWorld().getPlayers() )
		{
			// setup vanish in both directions
			setupVanish(focus, pl);
			setupVanish(pl, focus);
		}
	}

	public void setSpectatorMode(Player p, boolean b)
	{
		p.setGameMode(b ? GameMode.SURVIVAL : GameMode.CREATIVE);
		AutoReferee.setAffectsSpawning(p, b);
		AutoReferee.setCollidesWithEntities(p, b);
	}

	public void setupSpectators()
	{ for ( Player pl : getWorld().getPlayers() ) setupSpectators(pl); }
	
	public void clearEntities()
	{
		for (Entity e : world.getEntitiesByClasses(Monster.class, 
			Animals.class, Item.class, ExperienceOrb.class, Arrow.class))
			if (!protectedEntities.contains(e.getUniqueId())) e.remove();
	}

	// helper class for starting match, synchronous task
	static class MatchStartTask implements Runnable
	{
		public static final ChatColor COLOR = ChatColor.GREEN;
		
		public int task = -1;
		private int secs = 3;
		
		private AutoRefMatch match = null;
		public MatchStartTask(AutoRefMatch m)
		{
			match = m;
		}
		
		public void run()
		{
			// if the countdown has ended...
			if (secs == 0)
			{
				// setup world to go!
				match.start();
				match.broadcast(">>> " + MatchStartTask.COLOR + "GO!");
				
				// cancel the task
				AutoReferee.getInstance().getServer().getScheduler().cancelTask(task);
			}
			
			// report number of seconds remaining
			else match.broadcast(">>> " + MatchStartTask.COLOR + 
				Integer.toString(secs--) + "...");
		}
	}

	// prepare this world to start
	private void prepareMatch()
	{
		BukkitScheduler scheduler = AutoReferee.getInstance().getServer().getScheduler();
		
		// set the current time to the start time
		world.setTime(this.startTime);
		
		// remove all mobs, animals, and items
		this.clearEntities();
		
		// turn off weather forever (or for a long time)
		world.setStorm(false);
		world.setWeatherDuration(Integer.MAX_VALUE);
		
		// prepare all players for the match
		for (AutoRefPlayer apl : getPlayers()) apl.heal();

		int readyDelay = AutoReferee.getInstance().getConfig().getInt(
			"delay-seconds.ready", AutoRefMatch.READY_SECONDS);
		
		// announce the match starting in X seconds
		this.broadcast(MatchStartTask.COLOR + "Match will begin in "
			+ ChatColor.WHITE + Integer.toString(readyDelay) + MatchStartTask.COLOR + " seconds.");

		// send referees countdown notification
		messageReferees("match", getWorld().getName(), "countdown", Integer.toString(readyDelay));
		
		// cancel any previous match-start task
		if (this.matchStarter != null && this.matchStarter.task != -1)
			scheduler.cancelTask(this.matchStarter.task);
		
		// schedule the task to announce and prepare the match
		this.matchStarter = new MatchStartTask(this);
		this.matchStarter.task = scheduler.scheduleSyncRepeatingTask(
				AutoReferee.getInstance(), this.matchStarter, readyDelay * 20L, 20L);
	}

	public void checkTeamsReady() 
	{
		// if there are no players on the server
		if (getPlayers().size() == 0)
		{
			// set all the teams to not ready and status as waiting
			for ( AutoRefTeam t : teams ) t.setReady(false);
			setCurrentState(MatchStatus.WAITING); return;
		}
		
		// this function is only useful if we are waiting
		if (getCurrentState() != MatchStatus.WAITING) return;
		
		// if we aren't in online mode, assume we are always ready
		if (!AutoReferee.getInstance().isAutoMode()) { setCurrentState(MatchStatus.READY); return; }
		
		// check if all the players are here
		boolean ready = true;
		for ( OfflinePlayer opl : getExpectedPlayers() )
			ready &= opl.isOnline() && isPlayer(opl.getPlayer()) &&
				getPlayer(opl.getPlayer()).isReady();
		
		// set status based on whether the players are online
		setCurrentState(ready ? MatchStatus.READY : MatchStatus.WAITING);
	}
	
	public void checkTeamsStart()
	{
		boolean teamsReady = true;
		for ( AutoRefTeam t : teams )
			teamsReady &= t.isReady();
		
		boolean ready = getReferees().size() == 0 ? teamsReady : isRefereeReady();
		if (teamsReady && !ready) for (Player p : getReferees())
			p.sendMessage(ChatColor.GRAY + "Teams are ready. Type /ready to begin the match.");
		
		// everyone is ready, let's go!
		if (ready) this.prepareMatch();
	}
	
	public void checkWinConditions()
	{
		Plugin plugin = AutoReferee.getInstance();
		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin,
			new Runnable(){ public void run(){ delayedCheckWinConditions(); } });
	}
	
	public void delayedCheckWinConditions()
	{
		// this code is only called in BlockPlaceEvent and BlockBreakEvent when
		// we have confirmed that the state is PLAYING, so we know we are definitely
		// in a match if this function is being called
		
		if (getCurrentState().inProgress()) for (AutoRefTeam team : this.teams)
		{
			// if there are no win conditions set, skip this team
			if (team.winConditions.size() == 0) continue;
			
			// check all win condition blocks (AND together)
			boolean win = true;
			for (Map.Entry<Location, BlockData> pair : team.winConditions.entrySet())
				win &= pair.getValue().matches(world.getBlockAt(pair.getKey()));
			if (win) matchComplete(team);
		}
	}

	// helper class for terminating world, synchronous task
	class MatchEndTask implements Runnable
	{
		public void run()
		{
			// first, handle all the players
			for (Player p : world.getPlayers()) AutoReferee.getInstance().playerDone(p);
			
			// then, cleanup the match object (swallow exceptions)
			try { destroy(); } catch (Exception e) {  };
		}
	}

	public void matchComplete(AutoRefTeam t)
	{
		// announce the victory and set the match to completed
		this.broadcast(t.getName() + " Wins!");
		
		// remove all mobs, animals, and items
		this.clearEntities();
		
		for (AutoRefPlayer apl : getPlayers())
		{
			Player pl = apl.getPlayer();
			if (pl == null) continue;
			
			pl.setGameMode(GameMode.CREATIVE);
			pl.getInventory().clear();
		}
		
		// send referees the end event
		messageReferees("match", getWorld().getName(), "end", t.getRawName());
		
		addEvent(new TranscriptEvent(this, TranscriptEvent.EventType.MATCH_END,
			"Match ended. " + t.getRawName() + " wins!", null, null, null));
		setCurrentState(MatchStatus.COMPLETED);
		
		setWinningTeam(t);
		logPlayerStats(null);
		
		AutoReferee plugin = AutoReferee.getInstance();
		int termDelay = plugin.getConfig().getInt(
			"delay-seconds.completed", COMPLETED_SECONDS);
		
		plugin.getServer().getScheduler().scheduleSyncDelayedTask(
			plugin, new MatchEndTask(), termDelay * 20L);
	}

	public AutoRefTeam teamNameLookup(String name)
	{
		AutoRefTeam mteam = null;
		
		// if there is no match on that world, forget it
		// is this team name a word?
		for (AutoRefTeam t : teams) if (t.matches(name))
		{ if (mteam == null) mteam = t; else return null; }
	
		// return the matched team (or null if no match)
		return mteam;
	}
	
	// get all expected players
	public Set<OfflinePlayer> getExpectedPlayers()
	{
		Set<OfflinePlayer> eps = Sets.newHashSet();
		for (AutoRefTeam team : teams)
			eps.addAll(team.getExpectedPlayers());
		return eps;
	}
	
	// returns the team for the expected player
	public AutoRefTeam expectedTeam(OfflinePlayer opl)
	{
		for (AutoRefTeam team : teams)
			if (team.getExpectedPlayers().contains(opl)) return team;
		return null;
	}
	
	// returns if the player is meant to join this match
	public boolean isPlayerExpected(OfflinePlayer opl)
	{ return expectedTeam(opl) != null; }
	
	public boolean joinTeam(Player pl, AutoRefTeam t)
	{
		AutoRefTeam pteam = getPlayerTeam(pl);
		if (t == pteam) return true;
		
		if (pteam != null) pteam.leave(pl);
		t.join(pl); return true;
	}
	
	public void leaveTeam(Player pl)
	{ for (AutoRefTeam team : teams) team.leave(pl); }
	
	public AutoRefPlayer getPlayer(Player pl)
	{
		for (AutoRefTeam team : teams)
		{
			AutoRefPlayer apl = team.getPlayer(pl);
			if (apl != null) return apl;
		}
		return null;
	}
	
	public boolean isPlayer(Player pl)
	{ return getPlayer(pl) != null; }
	
	public AutoRefPlayer getNearestPlayer(Location loc)
	{
		AutoRefPlayer apl = null;
		double distance = Double.POSITIVE_INFINITY;
		
		for (AutoRefPlayer a : getPlayers())
		{
			Player pl = a.getPlayer();
			if (pl == null) continue;
			
			double d = loc.distanceSquared(pl.getLocation());
			if (d < distance) { apl = a; distance = d; }
		}
		
		return apl;
	}
	
	public AutoRefTeam getPlayerTeam(Player pl)
	{
		for (AutoRefTeam team : teams)
			if (team.getPlayer(pl) != null) return team;
		return null;
	}
	
	public String getPlayerName(Player pl)
	{
		AutoRefPlayer apl = getPlayer(pl);
		return (apl == null) ? pl.getName() : apl.getName();
	}
	
	public Location getPlayerSpawn(Player pl)
	{
		AutoRefTeam team = getPlayerTeam(pl);
		if (team != null) return team.getSpawnLocation();
		return world.getSpawnLocation();
	}
	
	public boolean isSafeZone(Location loc)
	{
		if (this.inStartRegion(loc)) return true;
		for (AutoRefTeam team : getTeams()) for (AutoRefRegion reg : team.getRegions())
			if (reg.contains(Vector3.fromLocation(loc)) && reg.isSafeZone()) return true;
		return false;
	}

	public void logPlayerStats(String h)
	{
		String hdl = h != null ? h : 
			new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
		
		try
		{
			File sfile = new File(AutoReferee.getInstance().getLogDirectory(), hdl + ".log");
			PrintWriter fw = new PrintWriter(sfile);
			
			for (AutoRefPlayer apl : getPlayers()) apl.writeStats(fw);
			for (TranscriptEvent e : transcript) fw.println(e);
			
			fw.close();
		}
		catch (IOException e)
		{ AutoReferee.getInstance().getLogger().severe("Could not write player stat logfile."); }
		
		// upload WEBSTATS (do via an async query in case uploading the stats lags the main thread)
		Plugin plugin = AutoReferee.getInstance();
		plugin.getServer().getScheduler().scheduleAsyncDelayedTask(plugin, new Runnable()
		{
			public void run()
			{
				broadcast(ChatColor.RED + "Generating Match Summary...");
				String webstats = uploadReport(ReportGenerator.generate(AutoRefMatch.this));
				
				if (webstats != null)
				{
					AutoReferee.getInstance().getLogger().info("Match Summary - " + webstats);
					broadcast(ChatColor.RED + "Match Summary: " + ChatColor.RESET + webstats);
				}
				else broadcast(ChatColor.RED + AutoReferee.NO_WEBSTATS_MESSAGE);
			}
		});
	}
	
	private String uploadReport(String report)
	{
		OutputStreamWriter wr = null;
		InputStream rd = null;
		
		try
		{
			URL url = new URL("http://pastehtml.com/upload/create?input_type=html&result=address");
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
		
			wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write("txt=" + URLEncoder.encode(report, "UTF-8")); wr.flush();
			StringWriter writer = new StringWriter();
			
			IOUtils.copy(rd = conn.getInputStream(), writer);
			return writer.toString();
		}
		
		// just drop out
		catch (Exception e)
		{ return null; }
		
		finally
		{
			try
			{
				// close the stream pointers
				if (wr != null) wr.close();
				if (rd != null) rd.close();
			}
			// meh. don't bother, if something goes wrong here.
			catch (Exception e) {  }
		}
	}

	// distance from the closest owned region
	public double distanceToClosestRegion(Player p)
	{
		AutoRefTeam team = getPlayerTeam(p);
		if (team != null) return team.distanceToClosestRegion(p.getLocation());
		return Double.MAX_VALUE;
	}

	// is location in start region?
	public boolean inStartRegion(Location loc)
	{ return startRegion != null && startRegion.distanceToRegion(loc) < ZoneListener.SNEAK_DISTANCE; }

	public void updateCarrying(AutoRefPlayer apl, Set<BlockData> carrying, Set<BlockData> newCarrying)
	{
		Set<BlockData> add = Sets.newHashSet(newCarrying);
		add.removeAll(carrying);

		Set<BlockData> rem = Sets.newHashSet(carrying);
		rem.removeAll(newCarrying);
		
		Player player = apl.getPlayer();
		for (BlockData bd : add) messageReferees("player", player.getName(), "obj", "+" + bd.toString());
		for (BlockData bd : rem) messageReferees("player", player.getName(), "obj", "-" + bd.toString());
	}

	public void updateHealthArmor(AutoRefPlayer apl, int currentHealth,
			int currentArmor, int newHealth, int newArmor)
	{
		Player player = apl.getPlayer();
		
		if (currentHealth != newHealth) messageReferees("player", player.getName(), 
			"hp", Integer.toString(newHealth));
		
		if (currentArmor != newArmor) messageReferees("player", player.getName(), 
			"armor", Integer.toString(newArmor));
	}
	
	public static class TranscriptEvent
	{
		public enum EventVisibility
		{ NONE, REFEREES, ALL }
		
		public enum EventType
		{
			// generic match start and end events
			MATCH_START("match-start", EventVisibility.NONE),
			MATCH_END("match-end", EventVisibility.NONE),
			
			// player messages (except kill streak) should be broadcast to players
			PLAYER_DEATH("player-death", EventVisibility.ALL),
			PLAYER_STREAK("player-killstreak", EventVisibility.NONE, ChatColor.DARK_GRAY),
			PLAYER_DOMINATE("player-dominate", EventVisibility.ALL, ChatColor.DARK_GRAY),
			PLAYER_REVENGE("player-revenge", EventVisibility.ALL, ChatColor.DARK_GRAY),
			
			// objective events should not be broadcast to players
			OBJECTIVE_FOUND("objective-found", EventVisibility.REFEREES),
			OBJECTIVE_PLACED("objective-place", EventVisibility.REFEREES);
			
			private String eventClass;
			private EventVisibility visibility;
			private ChatColor color;
			
			private EventType(String eventClass, EventVisibility visibility)
			{ this(eventClass, visibility, null); }
			
			private EventType(String eventClass, EventVisibility visibility, ChatColor color)
			{
				this.eventClass = eventClass;
				this.visibility = visibility;
				this.color = color;
			}
			
			public String getEventClass()
			{ return eventClass; }
			
			public EventVisibility getVisibility()
			{ return visibility; }
			
			public ChatColor getColor()
			{ return color; }
		}
		
		public Object icon1;
		public Object icon2;
		
		private EventType type;

		public EventType getType()
		{ return type; }
		
		private String message;
		
		public String getMessage()
		{ return message; }
		
		public Location location;
		public long timestamp;
		
		public TranscriptEvent(AutoRefMatch match, EventType type, String message, 
			Location loc, Object icon1, Object icon2)
		{
			this.type = type;
			this.message = message;
			
			// if no location is given, use the spawn location
			this.location = (loc != null) ? loc :
				match.getWorld().getSpawnLocation();
			
			// these represent left- and right-side icons for a transcript
			this.icon1 = icon1;
			this.icon2 = icon2;
			
			this.timestamp = match.getMatchTime();
		}

		public String getTimestamp()
		{
			return String.format("%02d:%02d:%02d",
				timestamp/3600L, (timestamp/60L)%60L, timestamp%60L);
		}
		
		@Override
		public String toString()
		{ return String.format("[%s] %s", this.getTimestamp(), this.getMessage()); }
	}

	public void addEvent(TranscriptEvent event)
	{
		AutoReferee plugin = AutoReferee.getInstance();
		transcript.add(event);
		
		Collection<Player> recipients = null;
		switch (event.getType().getVisibility())
		{
			case REFEREES: recipients = getReferees(); break;
			case ALL: recipients = getWorld().getPlayers(); break;
		}
		
		ChatColor clr = event.getType().getColor();
		String message = event.getMessage();
		
		if (clr == null) message = colorMessage(message);
		else message = (clr + message + ChatColor.RESET);
		
		if (recipients != null) for (Player player : recipients)
			player.sendMessage(message);
		
		if (plugin.getConfig().getBoolean("console-log", false))
			plugin.getLogger().info(event.toString());
	}

	public List<TranscriptEvent> getTranscript()
	{ return Collections.unmodifiableList(transcript); }

	public String colorMessage(String message)
	{
		message = ChatColor.stripColor(message);
		for (AutoRefPlayer apl : getPlayers()) if (apl != null)
			message = message.replaceAll(apl.getPlayerName(), apl.getName());
		for (AutoRefTeam team : getTeams()) if (team.winConditions != null)
			for (BlockData bd : team.winConditions.values()) if (bd != null)
				message = message.replaceAll(bd.getRawName(), bd.getName());
		return ChatColor.RESET + message;
	}

	public void sendMatchInfo(Player player)
	{
		player.sendMessage(ChatColor.RESET + "Map: " + ChatColor.GRAY + getMapName() + 
			" v" + getVersion() + ChatColor.ITALIC + " by " + getMapAuthors());
		
		AutoRefPlayer apl = getPlayer(player);
		String tmpflag = tmp ? "*" : "";
		
		if (apl != null) player.sendMessage("You are on team: " + apl.getTeam().getName());
		else if (isReferee(player)) player.sendMessage(ChatColor.GRAY + "You are a referee! " + tmpflag);
		else player.sendMessage("You are not on a team! Type " + ChatColor.GRAY + "/jointeam");
		
		for (AutoRefTeam team : getSortedTeams())
			player.sendMessage(String.format("%s (%d) - %s", 
				team.getName(), team.getPlayers().size(), team.getPlayerList()));
		
		long timestamp = (getWorld().getFullTime() - getStartTicks()) / 20L;
		player.sendMessage("Match status is currently " + ChatColor.GRAY + getCurrentState().name());
		if (getCurrentState().inProgress())
			player.sendMessage(String.format(ChatColor.GRAY + "The current match time is: %02d:%02d:%02d", 
				timestamp/3600L, (timestamp/60L)%60L, timestamp%60L));
	}
}
