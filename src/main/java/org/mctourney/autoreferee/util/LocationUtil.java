package org.mctourney.autoreferee.util;

import org.bukkit.Location;
import org.bukkit.World;

public abstract class LocationUtil
{
	public static String toBlockCoords(Location loc)
	{ return String.format("%d,%d,%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()); }

	public static String toBlockCoordsWithYaw(Location loc)
	{ return String.format("%s,%d", toBlockCoords(loc), (int)loc.getYaw()); }

	public static String toCoords(Location loc)
	{ return String.format("%.3f,%.3f,%.3f", loc.getX(), loc.getY(), loc.getZ()); }

	public static Location fromCoords(World world, String coords)
	{
		try
		{
			String[] values = coords.split(",");
			Location ret = new Location(world, parseDouble(values[0]),
				parseDouble(values[1]), parseDouble(values[2]));

			if (values.length > 3) ret.setYaw(Float.parseFloat(values[3]));
			if (values.length > 4) ret.setPitch(Float.parseFloat(values[4]));
			return ret;
		}
		catch (Exception e) { return null; }
	}

	// simply here for utility
	private static double parseDouble(String v)
	{
		if (v.endsWith("oo") || v.endsWith("inf"))
			return v.startsWith("-")
				? Double.NEGATIVE_INFINITY
				: Double.POSITIVE_INFINITY;
		return Double.parseDouble(v);
	}
}
