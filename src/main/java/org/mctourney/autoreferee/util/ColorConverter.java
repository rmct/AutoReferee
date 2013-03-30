package org.mctourney.autoreferee.util;

import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

import com.google.common.collect.Maps;

public abstract class ColorConverter
{
	private static Map<DyeColor, ChatColor> dyeChatMap;
	static
	{
		dyeChatMap = Maps.newHashMap();
		dyeChatMap.put(DyeColor.BLACK, ChatColor.DARK_GRAY);
		dyeChatMap.put(DyeColor.BLUE, ChatColor.DARK_BLUE);
		dyeChatMap.put(DyeColor.BROWN, ChatColor.GOLD);
		dyeChatMap.put(DyeColor.CYAN, ChatColor.AQUA);
		dyeChatMap.put(DyeColor.GRAY, ChatColor.GRAY);
		dyeChatMap.put(DyeColor.GREEN, ChatColor.DARK_GREEN);
		dyeChatMap.put(DyeColor.LIGHT_BLUE, ChatColor.BLUE);
		dyeChatMap.put(DyeColor.LIME, ChatColor.GREEN);
		dyeChatMap.put(DyeColor.MAGENTA, ChatColor.LIGHT_PURPLE);
		dyeChatMap.put(DyeColor.ORANGE, ChatColor.GOLD);
		dyeChatMap.put(DyeColor.PINK, ChatColor.LIGHT_PURPLE);
		dyeChatMap.put(DyeColor.PURPLE, ChatColor.DARK_PURPLE);
		dyeChatMap.put(DyeColor.RED, ChatColor.DARK_RED);
		dyeChatMap.put(DyeColor.SILVER, ChatColor.GRAY);
		dyeChatMap.put(DyeColor.WHITE, ChatColor.WHITE);
		dyeChatMap.put(DyeColor.YELLOW, ChatColor.YELLOW);
	}

	public static ChatColor dyeToChat(DyeColor dclr)
	{
		if (dyeChatMap.containsKey(dclr))
			return dyeChatMap.get(dclr);
		return ChatColor.MAGIC;
	}

	private static Map<ChatColor, String> chatHexMap;
	static
	{
		chatHexMap = Maps.newHashMap();
		chatHexMap.put(ChatColor.BLACK, "#000");
		chatHexMap.put(ChatColor.DARK_BLUE, "#00a");
		chatHexMap.put(ChatColor.DARK_GREEN, "#0a0");
		chatHexMap.put(ChatColor.DARK_AQUA, "#0aa");
		chatHexMap.put(ChatColor.DARK_RED, "#a00");
		chatHexMap.put(ChatColor.DARK_PURPLE, "#a0a");
		chatHexMap.put(ChatColor.GOLD, "#fa0");
		chatHexMap.put(ChatColor.GRAY, "#999");
		chatHexMap.put(ChatColor.DARK_GRAY, "#555");
		chatHexMap.put(ChatColor.BLUE, "#55f");
		chatHexMap.put(ChatColor.GREEN, "#5c5");
		chatHexMap.put(ChatColor.AQUA, "#5cc");
		chatHexMap.put(ChatColor.RED, "#f55");
		chatHexMap.put(ChatColor.LIGHT_PURPLE, "#f5f");
		chatHexMap.put(ChatColor.YELLOW, "#cc5");
		chatHexMap.put(ChatColor.WHITE, "#aaa");
	}

	public static String chatToHex(ChatColor clr)
	{
		if (chatHexMap.containsKey(clr))
			return chatHexMap.get(clr);
		return "#000";
	}

	private static Map<DyeColor, String> dyeHexMap;
	static
	{
		dyeHexMap = Maps.newHashMap();
		dyeHexMap.put(DyeColor.BLACK, "#181414");
		dyeHexMap.put(DyeColor.BLUE, "#253193");
		dyeHexMap.put(DyeColor.BROWN, "#56331c");
		dyeHexMap.put(DyeColor.CYAN, "#267191");
		dyeHexMap.put(DyeColor.GRAY, "#414141");
		dyeHexMap.put(DyeColor.GREEN, "#364b18");
		dyeHexMap.put(DyeColor.LIGHT_BLUE, "#6387d2");
		dyeHexMap.put(DyeColor.LIME, "#39ba2e");
		dyeHexMap.put(DyeColor.MAGENTA, "#be49c9");
		dyeHexMap.put(DyeColor.ORANGE, "#ea7e35");
		dyeHexMap.put(DyeColor.PINK, "#d98199");
		dyeHexMap.put(DyeColor.PURPLE, "#7e34bf");
		dyeHexMap.put(DyeColor.RED, "#9e2b27");
		dyeHexMap.put(DyeColor.SILVER, "#a0a7a7");
		dyeHexMap.put(DyeColor.WHITE, "#a4a4a4");
		dyeHexMap.put(DyeColor.YELLOW, "#c2b51c");
	}

	public static String dyeToHex(DyeColor clr)
	{
		if (dyeHexMap.containsKey(clr))
			return dyeHexMap.get(clr);
		return "#000";
	}

	public static Color hexToColor(String hex)
	{
		// get rid of typical hex color cruft
		if (hex.startsWith("#")) hex = hex.substring(1);
		if (hex.indexOf("x") != -1) hex = hex.substring(hex.indexOf("x"));

		// if the length isn't the standard 0xRRGGBB or 0xRGB, just quit
		if (hex.length() != 6 && hex.length() != 3) return null;

		// construct and return color object
		int sz = hex.length() / 3, mult = 1 << ((2 - sz)*4), x = 0;
		for (int i = 0, z = 0; z < hex.length(); ++i, z += sz)
			x |= (mult * Integer.parseInt(hex.substring(z, z+sz), 16)) << (i*8);
		return Color.fromBGR(x & 0xffffff);
	}

	public static Color rgbToColor(String rgb)
	{
		String parts[] = rgb.split("[^0-9]+");
		if (parts.length < 3) return null;

		int x = 0, i; for (i = 0; i < 3; ++i)
			x |= Integer.parseInt(parts[i]) << (i*8);
		return Color.fromBGR(x & 0xffffff);
	}

	public static String generateColorTable()
	{
		StringBuilder str = new StringBuilder();

		str.append("<table><tr><td>Chat Color</td><td>Color</td></tr>");
		for (Map.Entry<ChatColor, String> e : chatHexMap.entrySet())
			str.append(String.format("<tr><td style='color: %2$s;'>%1$s</td>" +
				"<td style='color: %2$s;'>Test String</td></tr>", e.getKey().name(), e.getValue()));
		str.append("</table>");

		str.append("<table><tr><td>Dye Color</td><td>Color</td></tr>");
		for (Map.Entry<DyeColor, String> e : dyeHexMap.entrySet())
			str.append(String.format("<tr><td style='color: %2$s;'>%1$s</td>" +
				"<td style='color: %2$s;'>Test String</td></tr>", e.getKey().name(), e.getValue()));
		str.append("</table>");

		return str.toString();
	}
}
