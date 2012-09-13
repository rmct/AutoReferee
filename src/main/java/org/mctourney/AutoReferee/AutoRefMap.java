package org.mctourney.AutoReferee;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class AutoRefMap implements Comparable<AutoRefMap>
{
	public String name;
	public String version;
	
	public File folder = null;
	
	public String filename;
	public String md5sum;
	
	public AutoRefMap(String name, String version, File folder)
	{ this.name = name; this.version = version; this.folder = folder; }

	public AutoRefMap(String csv)
	{
		String[] parts = csv.split(";", 5);
		
		// normalized name and version are first 2 columns
		this.name = AutoRefMatch.normalizeMapName(parts[0]);
		this.version = parts[1];
		
		// followed by the filename and an md5sum
		this.filename = parts[2];
		this.md5sum = parts[3];
	}

	public String getVersionString()
	{ return name + " v" + version; }

	public boolean isInstalled()
	{ return folder != null; }
	
	public File getFolder() throws IOException
	{
		if (!isInstalled()) download();
		return folder;
	}
	
	public void download() throws IOException
	{
		URL url = new URL(AutoRefMatch.getMapRepo() + filename);
		File zip = new File(AutoRefMap.getMapLibrary(), filename);
		FileUtils.copyURLToFile(url, zip);
		
		// if the md5s match, return the unzipped folder
		String md5comp = DigestUtils.md5Hex(new FileInputStream(zip));
		if (md5comp.equalsIgnoreCase(md5sum)) return;
		
		// if the md5sum did not match, quit here
		zip.delete(); throw new IOException(
			"MD5 Mismatch: " + md5comp + " != " + md5sum);
	}
	
	@Override
	public int hashCode()
	{ return name.toLowerCase().hashCode(); }
	
	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AutoRefMap)) return false;
		AutoRefMap map = (AutoRefMap) o;
		
		return name.equalsIgnoreCase(map.name) 
			&& version.equalsIgnoreCase(map.version);
	}

	@Override
	public int compareTo(AutoRefMap other)
	{ return name.compareTo(other.name); }
	
	public static AutoRefMatch createMatch(String map, String world) throws IOException
	{
		// add a match object now marked as temporary
		AutoReferee plugin = AutoReferee.getInstance();
		World w = createMatchWorld(map, world);
		
		if (w == null) return null;
		
		// return the added temporary match
		AutoRefMatch match = new AutoRefMatch(w, true);
		plugin.addMatch(match); return match;
	}
	
	public static AutoRefMatch createMatch(AutoRefMap map, String world) throws IOException
	{
		// add a match object now marked as temporary
		AutoReferee plugin = AutoReferee.getInstance();
		World w = createMatchWorld(map, world);
		
		if (w == null) return null;
		
		// return the added temporary match
		AutoRefMatch match = new AutoRefMatch(w, true);
		plugin.addMatch(match); return match;
	}
	
	public static World createMatchWorld(AutoRefMap map, String world) throws IOException
	{
		if (world == null)
			world = AutoReferee.WORLD_PREFIX + Long.toHexString(new Date().getTime());
		
		// get the folder associated with this world name
		File mapFolder = map.getFolder();
		if (mapFolder == null) return null;
		
		// create the temporary directory where this map will be
		File destWorld = new File(world);
		if (!destWorld.mkdir()) throw new IOException("Could not make temporary directory.");
		
		// copy the files over and return the loaded world
		FileUtils.copyDirectory(mapFolder, destWorld);
		return AutoReferee.getInstance().getServer().createWorld(WorldCreator.name(destWorld.getName()));
	}

	public static World createMatchWorld(String map, String world) throws IOException
	{ return createMatchWorld(AutoRefMap.getMap(map), world); }

	public static boolean parseMatchInitialization(String json) // TODO
	{
		Type type = new TypeToken<List<AutoRefMatch.MatchParams>>() {}.getType();
		List<AutoRefMatch.MatchParams> paramList = new Gson().fromJson(json, type);
		
		try
		{
			// for each match in the list, go ahead and create the match
			for (AutoRefMatch.MatchParams params : paramList) createMatch(params);
		}
		catch (IOException e) { return false; }
		return true;
	}

	public static AutoRefMatch createMatch(AutoRefMatch.MatchParams params) throws IOException
	{
		AutoRefMatch m = AutoRefMap.createMatch(params.getMap(), null);
		
		Iterator<AutoRefTeam> teamiter = m.getTeams().iterator();
		for (AutoRefMatch.MatchParams.TeamInfo teaminfo : params.getTeams())
		{
			if (!teamiter.hasNext()) break;
			AutoRefTeam team = teamiter.next();
			
			team.setName(teaminfo.getName());
			for (String name : teaminfo.getPlayers())
				team.addExpectedPlayer(name);
		}
	   	return m;
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

	private static final int MAX_NAME_DISTANCE = 5;
	
	public static AutoRefMap getMap(String name) throws IOException
	{
		// assume worldName exists
		if (name == null) return null;
		name = AutoRefMatch.normalizeMapName(name);
		
		// if there is no map library, quit
		File mapLibrary = AutoRefMap.getMapLibrary();
		if (!mapLibrary.exists()) return null;
		
		AutoRefMap bmap = null; int ldist = MAX_NAME_DISTANCE;
		for (AutoRefMap map : getAvailableMaps())
		{
			String mapName = AutoRefMatch.normalizeMapName(map.name);
			int namedist = StringUtils.getLevenshteinDistance(name, mapName);
			if (namedist <= ldist) { bmap = map; ldist = namedist; }
		}
		
		// get best match
		return bmap;
	}
	
	public static File getMapFolder(String name) throws IOException
	{
		AutoRefMap bmap = AutoRefMap.getMap(name);
		return bmap == null ? null : bmap.getFolder();
	}

	public static Set<AutoRefMap> getAvailableMaps()
	{
		Set<AutoRefMap> maps = Sets.newHashSet();
		maps.addAll(getInstalledMaps());
		maps.addAll(getRemoteMaps());
		return maps;
	}
	
	private static class MapUpdateTask implements Runnable
	{
		private CommandSender sender;
		private boolean force;
		
		public MapUpdateTask(CommandSender sender, boolean force)
		{ this.sender = sender; this.force = force; }
		
		@Override
		public void run()
		{
			// get remote map directory
			Map<String, AutoRefMap> remote = Maps.newHashMap();
			for (AutoRefMap map : getRemoteMaps()) remote.put(map.name, map);
			
			// check for updates on installed maps
			for (AutoRefMap map : getInstalledMaps()) try
			{
				// get the remote version and check if there is an update
				AutoRefMap rmap = remote.get(map.name);
				if (rmap != null && (force || !map.version.equalsIgnoreCase(rmap.version)))
				{
					sender.sendMessage(String.format("UPDATING %s (%s -> %s)...", 
						rmap.name, map.version, rmap.version));
					if (rmap.getFolder() == null) sender.sendMessage("Update FAILED");
					else
					{
						if (map.isInstalled()) FileUtils.deleteDirectory(map.folder);
						sender.sendMessage("Update SUCCESS: " + rmap.getVersionString());
					}
				}
			}
			catch (IOException e) {  }
		}
	}
	
	public static void getUpdates(CommandSender sender, boolean force)
	{
		AutoReferee instance = AutoReferee.getInstance();
		instance.getServer().getScheduler().scheduleAsyncDelayedTask(
			instance, new MapUpdateTask(sender, force));
	}

	public static Set<AutoRefMap> getRemoteMaps()
	{
		Set<AutoRefMap> maps = Sets.newHashSet();
		String mlist = QueryServer.syncQuery(AutoRefMatch.getMapRepo() + "list.csv", null, null);
	
		if (mlist != null) for (String line : mlist.split("[\\r\\n]+")) 
			maps.add(new AutoRefMap(line));
		return maps;
	}

	public static Set<AutoRefMap> getInstalledMaps()
	{
		Set<AutoRefMap> maps = Sets.newHashSet();
		
		// look through the zip files for what's already installed
		for (File f : AutoRefMap.getMapLibrary().listFiles())
		{
			// if this is a zipfile containing the right world, unzip it
			if (f.getName().toLowerCase().endsWith(".zip"))
			try { f = unzipMapFolder(f); } catch (IOException e) { continue; }
			
			AutoRefMap mapInfo = getMapInfo(f);
			if (mapInfo != null) maps.add(mapInfo);
		}
		
		return maps;
	}

	public static AutoRefMap getMapInfo(File folder)
	{
		// skip non-directories
		if (!folder.isDirectory()) return null;
		
		// if it doesn't have an autoreferee config file
		File cfgFile = new File(folder, AutoReferee.CFG_FILENAME);
		if (!cfgFile.exists()) return null;
		
		// check the map name, if it matches, this is the one we want
		FileConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
		return new AutoRefMap(AutoRefMatch.normalizeMapName(cfg.getString("map.name")),
			cfg.getString("map.version", "1.0"), folder);
	}

	public static File unzipMapFolder(File zip) throws IOException
	{
		ZipFile zfile = new ZipFile(zip);
		Enumeration<? extends ZipEntry> entries = zfile.entries();
		
		File f, basedir = null;
		
		File lib = AutoRefMap.getMapLibrary();
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
		zip.delete();
		return basedir;
	}
	
	private static class MapLoader implements Runnable
	{
		private CommandSender sender;
		private String map, custom;
		
		public MapLoader(CommandSender sender, String map, String custom)
		{ this.sender = sender; this.map = map; this.custom = custom; }

		@Override
		public void run()
		{
			AutoRefMatch match = null;
			try { match = AutoRefMap.createMatch(this.map, this.custom); }
			catch (IOException e) {  }
			
			if (match == null) sender.sendMessage("No such map: " + this.map);
			else AutoReferee.getInstance().getServer().getScheduler().scheduleSyncDelayedTask(
				AutoReferee.getInstance(), new MapPostLoader(sender, match));
		}
	}
	
	private static class MapPostLoader implements Runnable
	{
		private CommandSender sender;
		private AutoRefMatch match;
		
		public MapPostLoader(CommandSender sender, AutoRefMatch match)
		{ this.sender = sender; this.match = match; }

		@Override
		public void run()
		{
			sender.sendMessage(ChatColor.DARK_GRAY + match.getVersionString() + " setup!");
			if (sender instanceof Player) ((Player) sender).teleport(match.getWorldSpawn());
		}
	}

	public static void loadMap(CommandSender sender, String mapName, String customName)
	{
		AutoReferee instance = AutoReferee.getInstance();
		instance.getServer().getScheduler().scheduleAsyncDelayedTask(
			instance, new MapLoader(sender, mapName, customName));
	}
}