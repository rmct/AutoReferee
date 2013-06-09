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

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import org.mctourney.autoreferee.event.match.MatchLoadEvent;
import org.mctourney.autoreferee.util.NullChunkGenerator;
import org.mctourney.autoreferee.util.QueryServer;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

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
	private static final File DOWNLOADING = new File("");

	private String name;
	private String version;

	private File zip = null;

	private String filename;
	private String md5sum;

	protected AutoRefMap(String name, String version, File zip)
		throws IOException
	{
		this.name = name; this.version = version; this.zip = zip;
		this.md5sum = DigestUtils.md5Hex(new FileInputStream(zip));
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
	{ return this.zip != null; }

	/**
	 * Gets root zip for this map, downloading if necessary.
	 *
	 * @return root zip for map
	 * @throws IOException if map download fails
	 */
	public File getZip() throws IOException
	{
		// if the map is already downloading, just be patient
		while (this.zip == DOWNLOADING)
			try { Thread.sleep(500); }
			catch (InterruptedException e) {  }

		if (!isInstalled()) download();
		return this.zip;
	}

	private void download() throws IOException
	{
		// mark the file as downloading
		this.zip = AutoRefMap.DOWNLOADING;

		URL url = new URL(AutoRefMatch.getMapRepo() + filename);
		File zip = new File(AutoRefMap.getMapLibrary(), filename);
		FileUtils.copyURLToFile(url, zip);

		// if the md5s match, return the zip
		String md5comp = DigestUtils.md5Hex(new FileInputStream(zip));
		if (md5comp.equalsIgnoreCase(md5sum)) { this.zip = zip; return; }

		// if the md5sum did not match, quit here
		zip.delete(); throw new IOException(
			"MD5 Mismatch: " + md5comp + " != " + md5sum);
	}

	/**
	 * Installs the map if it is not already installed.
	 */
	public void install()
	{
		// runnable downloads the map if it isn't installed
		BukkitRunnable runnable = new BukkitRunnable()
		{
			@Override public void run()
			{ try { getZip(); } catch (IOException e) { e.printStackTrace(); } }
		};

		// run the task asynchronously to avoid locking up the main thread on a download
		runnable.runTaskAsynchronously(AutoReferee.getInstance());
	}

	@Override
	public int hashCode()
	{ return name.toLowerCase().hashCode(); }

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AutoRefMap)) return false;
		AutoRefMap map = (AutoRefMap) o;
		return name.equalsIgnoreCase(map.name);
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
		World w = createMatchWorld(map, world);
		return AutoReferee.getInstance().getMatch(w).temporary();
	}

	private static World createMatchWorld(AutoRefMap map, String world) throws IOException
	{
		if (world == null)
			world = AutoReferee.WORLD_PREFIX + Long.toHexString(new Date().getTime());

		// copy the files over and return the loaded world
		map.unpack(new File(world));
		return AutoReferee.getInstance().getServer().createWorld(
			WorldCreator.name(world).generateStructures(false)
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
		return AutoRefMap.getMapInfo(zip);
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

	private static class MapUpdateTask extends BukkitRunnable
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

			for (File folder : AutoRefMap.getMapLibrary().listFiles())
				if (folder.isDirectory()) try
			{
				File arxml = new File(folder, AutoReferee.CFG_FILENAME);
				if (!arxml.exists()) continue;

				Element arcfg = new SAXBuilder().build(arxml).getRootElement();
				Element meta = arcfg.getChild("meta");
				if (meta != null)
				{
					AutoRefMap rmap = remote.get(AutoRefMatch.normalizeMapName(meta.getChildText("name")));
					if (rmap != null && rmap.getZip() != null)
					{
						FileUtils.deleteQuietly(folder);
						AutoReferee.getInstance().sendMessageSync(sender, String.format(
							"Updated %s to new format (%s)", rmap.name, rmap.getVersionString()));
					}
				}
			}
			catch (IOException e) { e.printStackTrace(); }
			catch (JDOMException e) { e.printStackTrace(); }

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
						if (rmap.getZip() == null) AutoReferee.getInstance()
							.sendMessageSync(sender, "Update " + ChatColor.RED + "FAILED");
						else
						{
							AutoReferee.getInstance().sendMessageSync(sender,
								"Update " + ChatColor.GREEN + "SUCCESS: " +
									ChatColor.RESET + rmap.getVersionString());
							FileUtils.deleteQuietly(map.getZip());
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
	{ new MapUpdateTask(sender, force).runTaskAsynchronously(AutoReferee.getInstance()); }

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
		for (File zip : AutoRefMap.getMapLibrary().listFiles())
		{
			AutoRefMap mapInfo = getMapInfo(zip);
			if (mapInfo != null) maps.add(mapInfo);
		}

		return maps;
	}

	public static Element getConfigFileData(File zip) throws IOException, JDOMException
	{
		ZipFile zfile = new ZipFile(zip);
		Enumeration<? extends ZipEntry> entries = zfile.entries();

		// if it doesn't have an autoreferee config file
		while (entries.hasMoreElements())
		{
			ZipEntry entry = entries.nextElement();
			if (entry.getName().endsWith(AutoReferee.CFG_FILENAME))
				return new SAXBuilder().build(zfile.getInputStream(entry)).getRootElement();
		}
		return null;
	}

	/**
	 * Get map info object associated with a zip
	 *
	 * @param zip zip file containing a configuration file
	 * @return map info object if zip contains a map, otherwise null
	 */
	public static AutoRefMap getMapInfo(File zip)
	{
		// skip non-directories
		if (zip.isDirectory()) return null;

		Element worldConfig = null;
		try { worldConfig = getConfigFileData(zip); }
		catch (IOException e) { e.printStackTrace(); return null; }
		catch (JDOMException e) { e.printStackTrace(); return null; }

		String mapName = "??", version = "1.0";
		Element meta = worldConfig.getChild("meta");
		if (meta != null)
		{
			mapName = AutoRefMatch.normalizeMapName(meta.getChildText("name"));
			version = meta.getChildText("version");
		}

		 try { return new AutoRefMap(mapName, version, zip); }
		 catch (IOException e) { e.printStackTrace(); return null; }
	}

	private File unpack(File dest) throws IOException
	{
		ZipFile zfile = new ZipFile(this.getZip());
		Enumeration<? extends ZipEntry> entries = zfile.entries();

		File f, basedir = null;
		File tmp = FileUtils.getTempDirectory();
		while (entries.hasMoreElements())
		{
			ZipEntry entry = entries.nextElement();
			f = new File(tmp, entry.getName());
			if (f.exists()) FileUtils.deleteQuietly(f);

			if (entry.isDirectory()) FileUtils.forceMkdir(f);
			else FileUtils.copyInputStreamToFile(zfile.getInputStream(entry), f);

			if (entry.isDirectory() && (basedir == null ||
				basedir.getName().startsWith(f.getName()))) basedir = f;
		}

		zfile.close();
		if (dest.exists()) FileUtils.deleteDirectory(dest);
		FileUtils.moveDirectory(basedir, dest);

		return dest;
	}

	private static abstract class MapDownloader extends BukkitRunnable
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
				new MapLoader(sender, map, custom).runTask(AutoReferee.getInstance());
			}
			catch (IOException e) {  }
		}
	}

	private static class MapRepoDownloader extends MapDownloader
	{
		private AutoRefMap map;
		private String name = null;

		public MapRepoDownloader(CommandSender sender, String mapName, String custom)
		{ this(sender, AutoRefMap.getMap(mapName), custom); this.name = mapName; }

		public MapRepoDownloader(CommandSender sender, AutoRefMap map, String custom)
		{ super(sender, custom); this.map = map; }

		@Override
		public void run()
		{
			if (this.map == null)
			{
				AutoReferee plugin = AutoReferee.getInstance();
				plugin.sendMessageSync(sender, "No such map: " + this.name);
			}
			else this.loadMap(this.map);
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

	private static class MapLoader extends BukkitRunnable
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
			catch (IOException e) { e.printStackTrace(); return; }

			MatchLoadEvent event = new MatchLoadEvent(match);
			AutoReferee.callEvent(event);

			AutoReferee plugin = AutoReferee.getInstance();
			plugin.getLogger().info(String.format("%s loaded %s (%s)", sender.getName(),
				match.getVersionString(), match.getWorld().getName()));

			sender.sendMessage(ChatColor.DARK_GRAY + match.getVersionString() + " setup!");
			if (sender instanceof Player) match.joinMatch((Player) sender);
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
		sender.sendMessage(ChatColor.GREEN + "Loading map: " + name);
		new MapRepoDownloader(sender, name, worldname)
			.runTaskAsynchronously(AutoReferee.getInstance());
	}

	/**
	 * Loads a map by name.
	 *
	 * @param sender user receiving progress updates
	 * @param map map to be loaded
	 * @param worldname name of custom world folder, possibly null
	 */
	public static void loadMap(CommandSender sender, AutoRefMap map, String worldname)
	{
		sender.sendMessage(ChatColor.GREEN + "Loading map: " + map.getVersionString());
		new MapRepoDownloader(sender, map, worldname)
			.runTaskAsynchronously(AutoReferee.getInstance());
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
		sender.sendMessage(ChatColor.GREEN + "Loading map from URL...");
		new MapURLDownloader(sender, url, worldname)
			.runTaskAsynchronously(AutoReferee.getInstance());
	}
}
