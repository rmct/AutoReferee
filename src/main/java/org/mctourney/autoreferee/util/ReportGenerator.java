package org.mctourney.autoreferee.util;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.material.Colorable;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.autoreferee.goals.AutoRefGoal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 * Formats HTML match reports.
 *
 * @author authorblues
 */
public class ReportGenerator
{
	public ReportGenerator()
	{  }

	private Map<String, String> customDetails = Maps.newLinkedHashMap();

	/**
	 * Adds (or modifies) a custom detail for this match report generator. Custom details will
	 * appear in the order that they are defined.
	 *
	 * @param key text for left column (descriptor)
	 * @param value text for right column (content)
	 */
	public void setCustomDetail(String key, String value)
	{ customDetails.put(key, value); }

	/**
	 * Generates a match report web page from a match object.
	 *
	 * @param match match object
	 * @return HTML for a match report web page, or null if an error occurred
	 */
	public String generate(AutoRefMatch match)
	{
		String htm, css, js, images = "";
		try
		{
			htm = getExternalResourceString("webstats/report.htm");
			css = getExternalResourceString("webstats/report.css");
			js  = getExternalResourceString("webstats/report.js" );

			images += getExternalResourceString("webstats/image-block.css");
			images += getExternalResourceString("webstats/image-header.css");
		//	images += getResourceString("webstats/image-items.css");
		}
		catch (IOException e) { return null; }

		StringWriter transcript = new StringWriter();
		TranscriptEvent endEvent = null;
		for (TranscriptEvent e : match.getTranscript())
		{
			transcript.write(transcriptEventHTML(match, e));
			if (e.getType() != TranscriptEvent.EventType.MATCH_END) endEvent = e;
		}

		AutoRefTeam win = match.getWinningTeam();
		String winningTeam = (win == null) ? "??" :
			String.format("<span class='team team-%s'>%s</span>",
				getTag(win), ChatColor.stripColor(win.getDisplayName()));

		Set<String> refList = Sets.newHashSet();
		for (Player pl : match.getReferees())
			refList.add(String.format("<span class='referee'>%s</span>", pl.getName()));

		Set<String> streamerList = Sets.newHashSet();
		for (Player pl : match.getStreamers())
			streamerList.add(String.format("<span class='streamer'>%s</span>", pl.getName()));

		List<String> extraRows = Lists.newLinkedList();
		for (Map.Entry<String, String> e : this.customDetails.entrySet())
			extraRows.add(String.format("<tr><th>%s</th><td>%s</td></tr>", e.getKey(), e.getValue()));

		File mapImage = new File(match.getWorld().getWorldFolder(), "map.png");
		if (!mapImage.exists()) match.saveMapImage();
		Location ptMin = match.getMapCuboid().getMinimumPoint();

		return (htm
			// base files get replaced first
			.replaceAll("#base-css#", css.replaceAll("\\s+", " ") + images)
			.replaceAll("#base-js#", Matcher.quoteReplacement(js))

			// followed by the team, player, and block styles
			.replaceAll("#team-css#", getTeamStyles(match).replaceAll("\\s+", " "))
			.replaceAll("#plyr-css#", getPlayerStyles(match).replaceAll("\\s+", " "))
			.replaceAll("#blok-css#", getBlockStyles(match).replaceAll("\\s+", " "))
			.replaceAll("#map-data#", String.format("{image:'%s', x:%d, z:%d}",
				MapImageGenerator.imageToDataURI(mapImage, "image/png"), ptMin.getBlockX(), ptMin.getBlockZ()))

			// then match and map names
			.replaceAll("#title#", match.getMatchName())
			.replaceAll("#map#", match.getMapName())

			// date and length of match
			.replaceAll("#date#", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.FULL).format(new Date()))
			.replaceAll("#length#", endEvent == null ? "??" : endEvent.getTimestamp())

			// team information (all teams, and winning team)
			.replaceAll("#teams#", getTeamList(match))
			.replaceAll("#winners#", winningTeam)

			// staff information
			.replaceAll("#referees#", StringUtils.join(refList, ", "))
			.replaceAll("#streamers#", StringUtils.join(streamerList, ", "))

			// filter settings
			.replaceAll("#filter-options", getFilterOptions())

			// addition details (custom?)
			.replaceAll("#xtra-details#", StringUtils.join(extraRows, "\n"))

			// and last, throw in the transcript and stats
			.replaceAll("#transcript#", transcript.toString())
			.replaceAll("#plyr-stats#", getPlayerStats(match))
		);
	}

	private static String getFilterOptions()
	{
		List<String> options = Lists.newLinkedList();
		for (AutoRefMatch.TranscriptEvent.EventType etype : AutoRefMatch.TranscriptEvent.EventType.values())
			if (etype.hasFilter())
				options.add("<option value='" + etype.getEventClass() + "'>" + etype.getEventName() + "</option>");
		return StringUtils.join(options, "");
	}

	// helper method
	private static String getExternalResourceString(String path) throws IOException
	{
		File dataFolder = AutoReferee.getInstance().getDataFolder();
		File resourceFile = new File(dataFolder, path);
		return IOUtils.toString(new FileInputStream(resourceFile));
	}

	// generate player.css
	private static String getPlayerStyles(AutoRefMatch match)
	{
		StringWriter css = new StringWriter();
		for (AutoRefPlayer apl : match.getPlayers())
			css.write(String.format(".player-%s:before { background-image: url(http://minotar.net/avatar/%s/16.png); }\n",
				getTag(apl), apl.getName()));
		return css.toString();
	}

	// generate team.css
	private static String getTeamStyles(AutoRefMatch match)
	{
		StringWriter css = new StringWriter();
		for (AutoRefTeam team : match.getTeams())
			css.write(String.format(".team-%s { color: %s; }\n",
				getTag(team), ColorConverter.chatToHex(team.getColor())));
		return css.toString();
	}

	private static Map<BlockData, Integer> terrain_png = Maps.newHashMap();
	private static int terrain_png_size = 16;

	static
	{
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.WHITE.getWoolData()), 4 * 16 + 0);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.BLACK.getWoolData()), 7 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.GRAY.getWoolData()), 7 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.RED.getWoolData()), 8 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.PINK.getWoolData()), 8 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.GREEN.getWoolData()), 9 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.LIME.getWoolData()), 9 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.BROWN.getWoolData()), 10 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.YELLOW.getWoolData()), 10 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.BLUE.getWoolData()), 11 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.LIGHT_BLUE.getWoolData()), 11 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.PURPLE.getWoolData()), 12 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.MAGENTA.getWoolData()), 12 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.CYAN.getWoolData()), 13 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.ORANGE.getWoolData()), 13 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.SILVER.getWoolData()), 14 * 16 + 1);
	}

	private static Map<Material, Integer> items_png = Maps.newHashMap();
	private static int items_png_size = 16;

	static
	{
		items_png.put(Material.LEATHER_HELMET, 0 * 16 + 0);
		items_png.put(Material.LEATHER_CHESTPLATE, 1 * 16 + 0);
		items_png.put(Material.LEATHER_LEGGINGS, 2 * 16 + 0);
		items_png.put(Material.LEATHER_BOOTS, 3 * 16 + 0);
		items_png.put(Material.CHAINMAIL_HELMET, 0 * 16 + 1);
		items_png.put(Material.CHAINMAIL_CHESTPLATE, 1 * 16 + 1);
		items_png.put(Material.CHAINMAIL_LEGGINGS, 2 * 16 + 1);
		items_png.put(Material.CHAINMAIL_BOOTS, 3 * 16 + 1);
		items_png.put(Material.IRON_HELMET, 0 * 16 + 2);
		items_png.put(Material.IRON_CHESTPLATE, 1 * 16 + 2);
		items_png.put(Material.IRON_LEGGINGS, 2 * 16 + 2);
		items_png.put(Material.IRON_BOOTS, 3 * 16 + 2);
		items_png.put(Material.DIAMOND_HELMET, 0 * 16 + 3);
		items_png.put(Material.DIAMOND_CHESTPLATE, 1 * 16 + 3);
		items_png.put(Material.DIAMOND_LEGGINGS, 2 * 16 + 3);
		items_png.put(Material.DIAMOND_BOOTS, 3 * 16 + 3);
		items_png.put(Material.GOLD_HELMET, 0 * 16 + 4);
		items_png.put(Material.GOLD_CHESTPLATE, 1 * 16 + 4);
		items_png.put(Material.GOLD_LEGGINGS, 2 * 16 + 4);
		items_png.put(Material.GOLD_BOOTS, 3 * 16 + 4);

		items_png.put(Material.WOOD_SWORD, 4 * 16 + 0);
		items_png.put(Material.WOOD_SPADE, 5 * 16 + 0);
		items_png.put(Material.WOOD_PICKAXE, 6 * 16 + 0);
		items_png.put(Material.WOOD_AXE, 7 * 16 + 0);
		items_png.put(Material.WOOD_HOE, 8 * 16 + 0);
		items_png.put(Material.STONE_SWORD, 4 * 16 + 1);
		items_png.put(Material.STONE_SPADE, 5 * 16 + 1);
		items_png.put(Material.STONE_PICKAXE, 6 * 16 + 1);
		items_png.put(Material.STONE_AXE, 7 * 16 + 1);
		items_png.put(Material.STONE_HOE, 8 * 16 + 1);
		items_png.put(Material.IRON_SWORD, 4 * 16 + 2);
		items_png.put(Material.IRON_SPADE, 5 * 16 + 2);
		items_png.put(Material.IRON_PICKAXE, 6 * 16 + 2);
		items_png.put(Material.IRON_AXE, 7 * 16 + 2);
		items_png.put(Material.IRON_HOE, 8 * 16 + 2);
		items_png.put(Material.DIAMOND_SWORD, 4 * 16 + 3);
		items_png.put(Material.DIAMOND_SPADE, 5 * 16 + 3);
		items_png.put(Material.DIAMOND_PICKAXE, 6 * 16 + 3);
		items_png.put(Material.DIAMOND_AXE, 7 * 16 + 3);
		items_png.put(Material.DIAMOND_HOE, 8 * 16 + 3);
		items_png.put(Material.GOLD_SWORD, 4 * 16 + 4);
		items_png.put(Material.GOLD_SPADE, 5 * 16 + 4);
		items_png.put(Material.GOLD_PICKAXE, 6 * 16 + 4);
		items_png.put(Material.GOLD_AXE, 7 * 16 + 4);
		items_png.put(Material.GOLD_HOE, 8 * 16 + 4);

		items_png.put(Material.BOW, 6 * 16 + 5);
		items_png.put(Material.POTION, 9 * 16 + 10);
	}

	// generate block.css
	private static String getBlockStyles(AutoRefMatch match)
	{
		Set<BlockData> blocks = Sets.newHashSet();
		for (AutoRefTeam team : match.getTeams())
			for (AutoRefGoal goal : team.getTeamGoals())
				if (goal.hasItem()) blocks.add(goal.getItem());

		StringWriter css = new StringWriter();
		for (BlockData bd : blocks)
		{
			Integer x = terrain_png.get(bd);

			String selector = String.format(".block.mat-%d.data-%d",
				bd.getMaterial().getId(), (int) bd.getData());
			css.write(selector + ":before ");

			if (x == null) css.write("{ display: none; }\n");
			else css.write(String.format("{ background-position: -%dpx -%dpx; }\n",
				terrain_png_size * (x % 16), terrain_png_size * (x / 16)));

			if ((bd.getMaterial().getNewData((byte) 0) instanceof Colorable))
			{
				DyeColor color = DyeColor.getByWoolData(bd.getData());
				String hex = ColorConverter.dyeToHex(color);
				css.write(String.format("%s { color: %s; }\n", selector, hex));
			}
		}

		return css.toString();
	}

	private static String getTeamList(AutoRefMatch match)
	{
		StringWriter teamlist = new StringWriter();
		for (AutoRefTeam team : match.getTeams())
		{
			Set<String> members = Sets.newHashSet();
			for (AutoRefPlayer apl : team.getCachedPlayers())
				members.add("<li><input type='checkbox' class='player-toggle' data-player='" +
					getTag(apl) + "'>" + playerHTML(apl) + "</li>\n");

			String memberlist = members.size() == 0
				? "<li>{none}</li>" : StringUtils.join(members, "");

			teamlist.write("<div class='span3'>");
			teamlist.write(String.format("<h4 class='team team-%s'>%s</h4>",
				getTag(team), ChatColor.stripColor(team.getDisplayName())));
			teamlist.write(String.format("<ul class='teammembers unstyled'>%s</ul></div>\n", memberlist));
		}

		return teamlist.toString();
	}

	private static class NemesisComparator implements Comparator<AutoRefPlayer>
	{
		private AutoRefPlayer target = null;

		public NemesisComparator(AutoRefPlayer target)
		{ this.target = target; }

		public int compare(AutoRefPlayer apl1, AutoRefPlayer apl2)
		{
			if (apl1.getTeam() == target.getTeam()) return -1;
			if (apl2.getTeam() == target.getTeam()) return +1;

			// get the number of kills on this player total
			int k = apl1.getKills(target) - apl2.getKills(target);
			if (k != 0) return k;

			// get the relative difference in "focus"
			return apl1.getKills(target)*apl2.getKills() -
				apl2.getKills(target)*apl1.getKills();
		}
	};

	private static String getPlayerStats(AutoRefMatch match)
	{
		List<AutoRefPlayer> players = Lists.newArrayList(match.getCachedPlayers());
		Collections.sort(players, new Comparator<AutoRefPlayer>()
		{
			public int compare(AutoRefPlayer apl1, AutoRefPlayer apl2)
			{ return apl2.getKDD() - apl1.getKDD(); }
		});

		int rank = 0;
		StringWriter playerstats = new StringWriter();
		for (AutoRefPlayer apl : players)
		{
			// get nemesis of this player
			AutoRefPlayer nms = Collections.max(players, new NemesisComparator(apl));
			if (nms != null && nms.getTeam() != null && nms.getTeam() == apl.getTeam()) nms = null;

			playerstats.write(String.format("<tr><td>%d</td><td>%s</td>",
				++rank, playerHTML(apl)));
			playerstats.write(String.format("<td>%d (%d)</td><td>%d</td><td>%s</td>",
				apl.getKills(), apl.getAssists(), apl.getDeaths(), apl.getExtendedAccuracyInfo()));
			playerstats.write(String.format("<td>%s</td></tr>\n",
				nms == null ? "none" : playerHTML(nms)));
		}

		return playerstats.toString();
	}

	private static String getTag(String s)
	{ return s.toLowerCase().replaceAll("[^a-z0-9]+", ""); }

	private static String getTag(AutoRefPlayer apl)
	{ return apl == null ? "none" : getTag(apl.getName()); }

	private static String getTag(AutoRefTeam team)
	{ return team == null ? "none" : getTag(team.getName()); }

	private static String playerHTML(AutoRefPlayer apl)
	{
		return String.format("<span class='player player-%s team-%s'>%s</span>",
			getTag(apl), getTag(apl.getTeam()), apl.getName());
	}

	private static String transcriptEventHTML(AutoRefMatch match, TranscriptEvent e)
	{
		String m = e.getMessage(), tagdata = "";
		Set<String> rowClasses = Sets.newHashSet("type-" + e.getType().getEventClass());

		Set<AutoRefPlayer> players = Sets.newHashSet();
		for (Object actor : e.actors)
		{
			if (actor instanceof AutoRefPlayer)
			{
				AutoRefPlayer apl = (AutoRefPlayer) actor; players.add(apl);
				tagdata += String.format(" data-player='%s'", apl.getName());
			}

			if (actor instanceof BlockData)
			{
				BlockData bd = (BlockData) actor;
				int mat = bd.getMaterial().getId();
				int data = bd.getData();

				m = m.replaceAll(bd.getName(), String.format(
					"<span class='block mat-%d data-%d'>%s</span>",
						mat, data, bd.getName()));
			}
		}

		for (AutoRefPlayer apl : match.getPlayers())
			if (m.contains(apl.getName()))
		{
			m = m.replaceAll(apl.getName(), playerHTML(apl));
			rowClasses.add("type-player-event");
			rowClasses.add("player-" + getTag(apl));
		}

		String coords = LocationUtil.toBlockCoords(e.getLocation());
		String fmt = "<tr class='transcript-event %s' data-location='%s'%s>" +
			"<td class='message'>%s</td><td class='timestamp'>%s</td></tr>\n";
		return String.format(fmt, StringUtils.join(rowClasses, " "), coords, tagdata, m, e.getTimestamp());
	}
	
	public static void extractResources()
	{
		AutoReferee plugin = AutoReferee.getInstance();
		File dataFolder = plugin.getDataFolder();
		File webstatsFolder = new File(dataFolder, "webstats");
		
		webstatsFolder.mkdir();
		
		writeStreamToFile(plugin.getResource("webstats/report.htm"), new File(webstatsFolder, "report.htm"));
		writeStreamToFile(plugin.getResource("webstats/report.css"), new File(webstatsFolder, "report.css"));
		writeStreamToFile(plugin.getResource("webstats/report.js"), new File(webstatsFolder, "report.js"));
		writeStreamToFile(plugin.getResource("webstats/image-block.css"), new File(webstatsFolder, "image-block.css"));
		writeStreamToFile(plugin.getResource("webstats/image-header.css"), new File(webstatsFolder, "image-header.css"));
		writeStreamToFile(plugin.getResource("webstats/image-items.css"), new File(webstatsFolder, "image-items.css"));
	}
	
	private static void writeStreamToFile(InputStream stream, File file) 
	{
		if(file.exists()) {
			return;
		}
		try {
			FileUtils.copyInputStreamToFile(stream, file);
		} catch (IOException ex) {
			Logger.getLogger(ReportGenerator.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
	
}
