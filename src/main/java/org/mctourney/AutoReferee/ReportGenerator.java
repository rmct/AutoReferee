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
import org.mctourney.AutoReferee.util.BlockVector3;
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
			transcript.write(transcriptEventHTML(e));
			if (e.type != TranscriptEvent.EventType.MATCH_END) endEvent = e;
		}
		
		AutoRefTeam win = match.getWinningTeam();
		String winningTeam = (win == null) ? "??" : 
			String.format("<span class='team team-%s'>%s</span>",
				win.getTag(), ChatColor.stripColor(win.getName()));
		
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
			.replaceAll("#teams#", getTeamList(match))
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
	
	// generate player.css
	private static String getPlayerStyles(AutoRefMatch match)
	{
		StringWriter css = new StringWriter();
		for (AutoRefPlayer apl : match.getPlayers())
			css.write(String.format(".player-%s:before { background-image: url(http://minotar.net/avatar/%s/16.png); }\n", 
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
	
	private static String getTeamList(AutoRefMatch match)
	{
		StringWriter teamlist = new StringWriter();
		for (AutoRefTeam team : match.getSortedTeams())
		{
			Set<String> members = Sets.newHashSet();
			for (AutoRefPlayer apl : team.getPlayers())
				members.add("<li>" + playerHTML(apl) + "</li>\n");
			
			String memberlist = members.size() == 0 
				? "<li>{none}</li>" : StringUtils.join(members, "");
			
			teamlist.write("<div class='span3'>");
			teamlist.write(String.format("<h4 class='team team-%s'>%s</h4>",
				team.getTag(), ChatColor.stripColor(team.getName())));
			teamlist.write(String.format("<ul class='teammembers unstyled'>%s</ul></div>\n", memberlist));
		}
		
		return teamlist.toString();
	}
	
	private static String playerHTML(AutoRefPlayer apl)
	{
		return String.format("<span class='player player-%s team-%s'>%s</span>",
			apl.getTag(), apl.getTeam().getTag(), apl.getPlayerName());
	}
	
	private static String transcriptEventHTML(TranscriptEvent e)
	{
		String m = e.message;
		
		Set<AutoRefPlayer> players = Sets.newHashSet();
		if (e.icon1 instanceof AutoRefPlayer) players.add((AutoRefPlayer) e.icon1);
		if (e.icon2 instanceof AutoRefPlayer) players.add((AutoRefPlayer) e.icon2);
		
		for (AutoRefPlayer apl : players)
			m = m.replaceAll(apl.getPlayerName(), playerHTML(apl));
		
		if (e.icon2 instanceof BlockData)
		{
			BlockData bd = (BlockData) e.icon2;
			int mat = bd.getMaterial().getId();
			int data = bd.getData();
			
			m = m.replaceAll(bd.getRawName(), String.format(
				"<span class='block mat-%d data-%d'>%s</span>", 
					mat, data, bd.getRawName()));
		}
		
		String coords = BlockVector3.fromLocation(e.location).toCoords();
		String fmt = "<tr class='transcript-event %s' data-location='%s'>" + 
			"<td class='message'>%s</td><td class='timestamp'>%s</td></tr>";
		return String.format(fmt, e.type.getCssClass(), coords, m, e.getTimestamp());
	}
}
