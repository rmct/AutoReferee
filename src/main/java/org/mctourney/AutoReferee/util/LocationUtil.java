package org.mctourney.AutoReferee.util;

import org.bukkit.Location;
import org.bukkit.World;

public abstract class LocationUtil
{
	public static String toBlockCoords(Location loc)
	{ return String.format("%d,%d,%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()); }

	public static String toCoords(Location loc)
	{ return String.format("%f,%f,%f", loc.getX(), loc.getY(), loc.getZ()); }

	public static Location fromCoords(World world, String coords)
	{
		try
		{
			String[] values = coords.split(",");
			return new Location(world,
				Double.parseDouble(values[0]),
				Double.parseDouble(values[1]),
				Double.parseDouble(values[2]));
		}
		catch (Exception e) { return null; }
	}
}
