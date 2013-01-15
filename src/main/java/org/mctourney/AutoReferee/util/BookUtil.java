package org.mctourney.AutoReferee.util;

import java.util.Map;

import org.bukkit.ChatColor;

import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility for writing books using BookMeta.
 *
 * @author authorblues
 */
public class BookUtil
{
	private static final int LINE_WIDTH = 115;

	private static final int NUM_LINES = 13;

	private static Map<Character, Integer> charWidth = Maps.newHashMap();
	static
	{
		charWidth.put(' ', 3);
		charWidth.put('i', 1);
		charWidth.put('I', 3);
		charWidth.put('l', 2);

		charWidth.put('.', 1);
		charWidth.put(',', 1);
		charWidth.put('<', 4);
		charWidth.put('>', 4);
		charWidth.put(':', 1);
		charWidth.put(';', 1);
		charWidth.put('\'', 2);
		charWidth.put('"', 4);
		charWidth.put('[', 4);
		charWidth.put(']', 4);
		charWidth.put('{', 4);
		charWidth.put('}', 4);
		charWidth.put('|', 4);

		charWidth.put('`', 1);
		charWidth.put('~', 6);
		charWidth.put('!', 1);
		charWidth.put('@', 6);
		charWidth.put('*', 4);
		charWidth.put('(', 4);
		charWidth.put(')', 4);
	}

	private static int getCharWidth(char c, boolean bold)
	{
		int w = charWidth.containsKey(c) ? charWidth.get(c) : 5;
		if (bold) w += 1;

		return w;
	}

	public static String makePage(String ...lines)
	{ return StringUtils.join(lines, "\n" + ChatColor.BLACK + ChatColor.RESET); }

	public static String center(String text)
	{
		int w = 0;

		boolean isBold = false;
		for (int i = 0; i < text.length(); ++i)
		{
			char c = text.charAt(i);
			if (c == ChatColor.COLOR_CHAR)
				isBold = text.charAt(++i) == ChatColor.BOLD.getChar();
			else w += 1 + getCharWidth(c, isBold);
		}

		int repeat = (LINE_WIDTH - w) / (2 * (charWidth.get(' ') + 1));
		return StringUtils.repeat(' ', repeat < 0 ? 0 : repeat) + text;
	}
}
