package org.mctourney.AutoReferee.util;

import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;

import com.google.common.collect.Maps;

public abstract class ChatColorConverter 
{
	private static Map<DyeColor, ChatColor> dyeColorMap;
	static
	{
		dyeColorMap = Maps.newHashMap();
		dyeColorMap.put(DyeColor.BLACK, ChatColor.BLACK);
		dyeColorMap.put(DyeColor.BLUE, ChatColor.DARK_BLUE);
		dyeColorMap.put(DyeColor.BROWN, ChatColor.GOLD);
		dyeColorMap.put(DyeColor.CYAN, ChatColor.BLUE);
		dyeColorMap.put(DyeColor.GRAY, ChatColor.GRAY);
		dyeColorMap.put(DyeColor.GREEN, ChatColor.DARK_GREEN);
		dyeColorMap.put(DyeColor.LIGHT_BLUE, ChatColor.BLUE);
		dyeColorMap.put(DyeColor.LIME, ChatColor.GREEN);
		dyeColorMap.put(DyeColor.MAGENTA, ChatColor.LIGHT_PURPLE);
		dyeColorMap.put(DyeColor.ORANGE, ChatColor.GOLD);
		dyeColorMap.put(DyeColor.PINK, ChatColor.LIGHT_PURPLE);
		dyeColorMap.put(DyeColor.PURPLE, ChatColor.DARK_PURPLE);
		dyeColorMap.put(DyeColor.RED, ChatColor.DARK_RED);
		dyeColorMap.put(DyeColor.SILVER, ChatColor.GRAY);
		dyeColorMap.put(DyeColor.WHITE, ChatColor.WHITE);
		dyeColorMap.put(DyeColor.YELLOW, ChatColor.YELLOW);
	}
	
	public static ChatColor fromDyeColor(DyeColor dclr)
	{
		if (dyeColorMap.containsKey(dclr))
			return dyeColorMap.get(dclr);
		return ChatColor.MAGIC;
	}
}
