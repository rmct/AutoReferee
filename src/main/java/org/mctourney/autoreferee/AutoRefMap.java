package org.mctourney.autoreferee;

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
import org.apache.commons.lang3.StringUtils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import org.mctourney.autoreferee.util.NullChunkGenerator;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Represents a map object, available to be loaded by AutoReferee.
 *
 * @author authorblues
 */
public class AutoRefMap implements Comparable<AutoRefMap>
{
	private String name;
	private String version;

	private File folder = null;

	private String filename;
	private String md5sum;

	protected AutoRefMap(String name, String version, File folder)
	{
		this.name = name; this.version = version;
		File checksum = new File(this.folder = folder, "checksum");

		this.md5sum = null;
		if (checksum.exists())
			try { this.md5sum = FileUtils.readFileToString(checksum); }
			catch (IOException e) {  }
	}

	protected AutoRefMap(String csv)
	{
		String[] parts = csv.split(";", 5);
		if (parts.length < 4) return;

		// normalized name and version are first 2 columns
		this.name = AutoRefMatch.normalizeMapName(parts[0]);
		this.version = parts[1];

		// followed by the filename and an md5sum
		this.filename = parts[2];
		this.md5sum = parts[3];
	}

	/**
	 * Gets the name of the map
	 *
	 * @return map name
	 */
	public String getName()
	{ return name; }

	/**
	 * Gets the version of the map
	 *
	 * @return map version
	 */
	public String getVersion()
	{ return version; }

	/**
	 * Gets human-readable name of map, including version number.
	 *
	 * @return map version string
	 */
	public String getVersionString()
	{ return name + " v" + version; }

	/**
	 * Gets whether the map has been installed.
	 *
	 * @return true if map is installed, otherwise false
	 */
	public boolean isInstalled()
	{ return folder != null; }

	/**
	 * Gets root folder for this map, downloading if necessary.
	 *
	 * @return root folder for map
	 * @throws IOException if map download fails
	 */
	public File getFolder() throws IOException
	{
		if (!isInstalled()) download();
		return folder;
	}

	private void download() throws IOException
	{
		URL url = new URL(AutoRefMatch.getMapRepo() + filename);
		File zip = new File(AutoRefMap.getMapLibrary(), filename);
		FileUtils.copyURLToFile(url, zip);

		// if the md5s match, return the unzipped folder
		String md5comp = DigestUtils.md5Hex(new FileInputStream(zip));
		if (md5comp.equalsIgnoreCase(md5sum))
		{ this.folder = AutoRefMap.unzipMapFolder(zip); return; }

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

	/**
	 * Creates match object given map name and an optional custom world name.
	 *
	 * @param map name of map, to be downloaded if necessary
	 * @param world custom name for world folder, or null
	 *
	 * @return match object for the loaded world
	 * @throws IOException if map download fails
	 */
	public static AutoRefMatch createMatch(String map, String world) throws IOException
	{ return createMatch(AutoRefMap.getMap(map), world); }

	/**
	 * Creates match object given map name and an optional custom world name.
	 *
	 * @param map map object, to be downloaded if necessary
	 * @param world custom name for world folder, or null
	 *
	 * @return match object for the loaded world
	 * @throws IOException if map download fails
	 */
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

	private static World createMatchWorld(AutoRefMap map, String world) throws IOException
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
		return AutoReferee.getInstance().getServer().createWorld(
			WorldCreator.name(destWorld.getName()).generateStructures(false)
				.generator(new NullChunkGenerator()));
	}

	/**
	 * Handles JSON object to initialize matches.
	 * @param json match parameters to be loaded
	 *
	 * @return true if matches were loaded, otherwise false
	 * @see AutoRefMatch.MatchParams
	 */
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

	/**
	 * Generates a match from the given match parameters.
	 *
	 * @param params match parameters object
	 * @return generated match object
	 * @throws IOException if map download fails
	 */
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

	/**
	 * Gets root folder of map library, generating folder if necessary.
	 *
	 * @return root folder of map library
	 */
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

	/**
	 * Gets map object associated with given map name.
	 *
	 * @param name name of map
	 * @return map object associated with the name
	 */
	public static AutoRefMap getMap(String name)
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

	/**
	 * Gets map object associated with the zip file at the provided URL.
	 *
	 * @param url URL of map zip to be downloaded.
	 * @return generated map object
	 * @throws IOException if map cannot be unpackaged
	 */
	public static AutoRefMap getMapFromURL(String url) throws IOException
	{
		String filename = url.substring(url.lastIndexOf('/') + 1, url.length());
		File zip = new File(AutoRefMap.getMapLibrary(), filename);

		FileUtils.copyURLToFile(new URL(url), zip);
		File folder = AutoRefMap.unzipMapFolder(zip);

		return AutoRefMap.getMapInfo(folder);
	}

	/**
	 * Gets root folder of map with the given name, downloading if necessary.
	 *
	 * @param name name of map to load
	 * @return root folder of map
	 * @throws IOException if map download fails
	 */
	public static File getMapFolder(String name) throws IOException
	{
		AutoRefMap bmap = AutoRefMap.getMap(name);
		return bmap == null ? null : bmap.getFolder();
	}

	/**
	 * Gets all maps available to be loaded.
	 *
	 * @return Set of all maps available to be loaded
	 */
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
				AutoRefMap rmap; if ((rmap = remote.get(map.name)) != null)
				{
					boolean needsUpdate = map.md5sum != null && !map.md5sum.equals(rmap.md5sum);
					if (force || needsUpdate)
					{
						AutoReferee.getInstance().sendMessageSync(sender, String.format(
							"UPDATING %s (%s -> %s)...", rmap.name, map.version, rmap.version));
						if (rmap.getFolder() == null) AutoReferee.getInstance()
							.sendMessageSync(sender, "Update FAILED");
						else
						{
							if (map.isInstalled()) FileUtils.deleteDirectory(map.folder);
							AutoReferee.getInstance().sendMessageSync(sender,
								"Update SUCCESS: " + rmap.getVersionString());
						}
					}
				}
			}
			catch (IOException e) { e.printStackTrace(); }
		}
	}

	/**
	 * Downloads updates to all maps installed on the server.
	 *
	 * @param sender user receiving progress updates
	 * @param force force re-download of maps, irrespective of version
	 */
	public static void getUpdates(CommandSender sender, boolean force)
	{
		AutoReferee instance = AutoReferee.getInstance();
		instance.getServer().getScheduler().runTaskAsynchronously(
			instance, new MapUpdateTask(sender, force));
	}

	/**
	 * Gets maps that are not installed, but may be downloaded.
	 *
	 * @return set of all maps available for download
	 */
	public static Set<AutoRefMap> getRemoteMaps()
	{
		Set<AutoRefMap> maps = Sets.newHashSet();
		String mlist = QueryServer.syncQuery(AutoRefMatch.getMapRepo() + "list.csv", null, null);

		if (mlist != null) for (String line : mlist.split("[\\r\\n]+"))
			maps.add(new AutoRefMap(line));
		return maps;
	}

	/**
	 * Gets maps installed locally on server.
	 *
	 * @return set of all maps available to load immediately
	 */
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

	/**
	 * Get map info object associated with a folder
	 *
	 * @param folder folder containing a configuration file
	 * @return map info object if folder contains a map, otherwise null
	 */
	public static AutoRefMap getMapInfo(File folder)
	{
		// skip non-directories
		if (!folder.isDirectory()) return null;

		// if it doesn't have an autoreferee config file
		File cfgFile = new File(folder, AutoReferee.CFG_FILENAME);
		if (!cfgFile.exists()) return null;

		// check the map name, if it matches, this is the one we want
		Element worldConfig = null;
		try { worldConfig = new SAXBuilder().build(cfgFile).getRootElement(); }
		catch (Exception e) { e.printStackTrace(); return null; }

		String mapName = "??", version = "1.0";
		Element meta = worldConfig.getChild("meta");
		if (meta != null)
		{
			mapName = meta.getChildText("name");
			version = meta.getChildText("version");
		}

		return new AutoRefMap(AutoRefMatch.normalizeMapName(mapName), version, folder);
	}

	private static File unzipMapFolder(File zip) throws IOException
	{
		String md5 = DigestUtils.md5Hex(new FileInputStream(zip));

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

		// add the checksum to the map directory to determine if an update is needed
		FileUtils.writeStringToFile(new File(basedir, "checksum"), md5);
		return basedir;
	}

	private static abstract class MapDownloader implements Runnable
	{
		protected CommandSender sender;
		private String custom;

		public MapDownloader(CommandSender sender, String custom)
		{ this.sender = sender; this.custom = custom; }

		public void loadMap(AutoRefMap map)
		{
			try
			{
				if (!map.isInstalled()) map.download();
				AutoReferee.getInstance().getServer().getScheduler().scheduleSyncDelayedTask(
					AutoReferee.getInstance(), new MapLoader(sender, map, custom));
			}
			catch (IOException e) {  }
		}
	}

	private static class MapRepoDownloader extends MapDownloader
	{
		private String mapName;

		public MapRepoDownloader(CommandSender sender, String mapName, String custom)
		{ super(sender, custom); this.mapName = mapName; }

		@Override
		public void run()
		{
			AutoRefMap map = AutoRefMap.getMap(this.mapName);
			if (map == null) AutoReferee.getInstance()
				.sendMessageSync(sender, "No such map: " + this.mapName);
			else this.loadMap(map);
		}
	}

	private static class MapURLDownloader extends MapDownloader
	{
		private String url;

		public MapURLDownloader(CommandSender sender, String url, String custom)
		{ super(sender, custom); this.url = url; }

		@Override
		public void run()
		{
			AutoRefMap map = null;
			try { map = AutoRefMap.getMapFromURL(this.url); } catch (IOException e) {  }

			if (map == null) AutoReferee.getInstance()
				.sendMessageSync(sender, "Could not load map from URL: " + this.url);
			else this.loadMap(map);
		}
	}

	private static class MapLoader implements Runnable
	{
		private CommandSender sender;
		private AutoRefMap map;
		private String custom;

		public MapLoader(CommandSender sender, AutoRefMap map, String custom)
		{ this.sender = sender; this.map = map; this.custom = custom; }

		@Override
		public void run()
		{
			AutoRefMatch match = null;
			try { match = AutoRefMap.createMatch(this.map, this.custom); }
			catch (IOException e) {  }

			AutoReferee plugin = AutoReferee.getInstance();
			plugin.getLogger().info(String.format("%s loaded %s (%s)", sender.getName(),
				match.getVersionString(), match.getWorld().getName()));

			sender.sendMessage(ChatColor.DARK_GRAY + match.getVersionString() + " setup!");
			if (sender instanceof Player) ((Player) sender).teleport(match.getWorldSpawn());
			if (sender == Bukkit.getConsoleSender()) plugin.setConsoleWorld(match.getWorld());
		}
	}

	/**
	 * Loads a map by name.
	 *
	 * @param sender user receiving progress updates
	 * @param name name of map to be loaded
	 * @param worldname name of custom world folder, possibly null
	 */
	public static void loadMap(CommandSender sender, String name, String worldname)
	{
		AutoReferee instance = AutoReferee.getInstance();
		instance.getServer().getScheduler().runTaskAsynchronously(
			instance, new MapRepoDownloader(sender, name, worldname));
	}

	/**
	 * Loads a map by URL.
	 *
	 * @param sender user receiving progress updates
	 * @param url URL of map zip to be downloaded
	 * @param worldname name of custom world folder, possibly null
	 */
	public static void loadMapFromURL(CommandSender sender, String url, String worldname)
	{
		AutoReferee instance = AutoReferee.getInstance();
		instance.getServer().getScheduler().runTaskAsynchronously(
			instance, new MapURLDownloader(sender, url, worldname));
	}
}
