package org.mctourney.AutoReferee;

import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.material.Colorable;
import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.ColorConverter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ReportGenerator
{
	public static String generate(AutoRefMatch match)
	{
		String htm, css, js;
		try
		{
			htm = getResourceString("webstats/report.htm");
			css = getResourceString("webstats/report.css");
			js  = getResourceString("webstats/report.js" );
		}
		catch (IOException e) { return null; }
		
		StringWriter transcript = new StringWriter();
		TranscriptEvent endEvent = null;
		for (TranscriptEvent e : match.getTranscript())
		{
			transcript.write(String.format("      %s\n", e.toHTML()));
			if (e.type != TranscriptEvent.EventType.MATCH_END) endEvent = e;
		}
		
		List<AutoRefTeam> sortedTeams = Lists.newArrayList(match.getTeams());
		Collections.sort(sortedTeams);
		
		AutoRefTeam win = match.getWinningTeam();
		String winmembers = "{none}";
		
		StringWriter teamlist = new StringWriter();
		for (AutoRefTeam team : sortedTeams)
		{
			Set<String> members = Sets.newHashSet();
			for (AutoRefPlayer apl : team.getPlayers())
				members.add(apl.getPlayerName());
			
			String memberlist = members.size() == 0 ? "{none}" : StringUtils.join(members, ", ");
			if (team == win) winmembers = memberlist;
			
			teamlist.write(String.format("<li><span class='team team-%s'>%s</span>: %s</li>", 
				team.getTag(), ChatColor.stripColor(team.getName()), memberlist));
		}
		
		String winningTeam = (win == null) ? "??" : 
			String.format("<span class='teamlist'><span class='team team-%s'>%s</span>: %s",
				win.getTag(), ChatColor.stripColor(win.getName()), winmembers);
		
		return (htm
			// base files get replaced first
			.replaceAll("#base-css#", css.replaceAll("\\s+", " "))
			.replaceAll("#base-js#", js)
			
			// followed by the team, player, and block styles
			.replaceAll("#team-css#", getTeamStyles(match).replaceAll("\\s+", " "))
			.replaceAll("#plyr-css#", getPlayerStyles(match).replaceAll("\\s+", " "))
			.replaceAll("#blok-css#", getBlockStyles(match).replaceAll("\\s+", " "))
			
			// then match and map names
			.replaceAll("#title#", match.getMatchName())
			.replaceAll("#map#", match.getMapName())
			
			// date and length of match
			.replaceAll("#date#", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.FULL).format(new Date()))
			.replaceAll("#length#", endEvent == null ? "??" : endEvent.getTimestamp())
			
			// team information (all teams, and winning team)
			.replaceAll("#teams#", "<ul>" + teamlist.toString() + "</ul>")
			.replaceAll("#winners#", winningTeam)
			
			// and last, throw in the transcript
			.replaceAll("#transcript#", transcript.toString()));
	}

	// helper method
	private static String getResourceString(String path) throws IOException
	{
		StringWriter buffer = new StringWriter();
		IOUtils.copy(AutoReferee.getInstance().getResource(path), buffer);
		return buffer.toString();
	}
	
	private static Map<BlockData, Integer> terrain_png = Maps.newHashMap();
	private static int terrain_png_size = 16;
	
	static
	{
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.WHITE.getData()), 4 * 16 + 0);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.BLACK.getData()), 7 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.GRAY.getData()), 7 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.RED.getData()), 8 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.PINK.getData()), 8 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.GREEN.getData()), 9 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.LIME.getData()), 9 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.BROWN.getData()), 10 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.YELLOW.getData()), 10 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.BLUE.getData()), 11 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.LIGHT_BLUE.getData()), 11 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.PURPLE.getData()), 12 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.MAGENTA.getData()), 12 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.CYAN.getData()), 13 * 16 + 1);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.ORANGE.getData()), 13 * 16 + 2);
		terrain_png.put(new BlockData(Material.WOOL, DyeColor.SILVER.getData()), 14 * 16 + 1);
	}
	
	// generate player.css
	private static String getPlayerStyles(AutoRefMatch match)
	{
		StringWriter css = new StringWriter();
		for (AutoRefPlayer apl : match.getPlayers())
			css.write(String.format(".player-%s { background-image: url(http://minotar.net/avatar/%s/16.png); }\n", 
				apl.getTag(), apl.getPlayerName()));
		return css.toString();
	}
	
	// generate team.css
	private static String getTeamStyles(AutoRefMatch match)
	{
		StringWriter css = new StringWriter();
		for (AutoRefTeam team : match.getTeams())
			css.write(String.format(".team-%s { color: %s; }\n", 
				team.getTag(), ColorConverter.chatToHex(team.getColor())));
		return css.toString();
	}
	
	// generate block.css
	private static String getBlockStyles(AutoRefMatch match)
	{
		Set<BlockData> blocks = Sets.newHashSet();
		for (AutoRefTeam team : match.getTeams())
			blocks.addAll(team.winConditions.values());
		
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
				DyeColor color = DyeColor.getByData(bd.getData());
				String hex = ColorConverter.dyeToHex(color);
				css.write(String.format("%s { color: %s; }\n", selector, hex));
			}
		}
			
		return css.toString();
	}
}
