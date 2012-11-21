package org.mctourney.AutoReferee.util;

import org.bukkit.Location;
import org.bukkit.World;

public class Vector3
{
	protected double x, y, z;

	public Vector3(double x, double y, double z)
	{ this.x = x; this.y = y; this.z = z; }

	public Vector3(com.sk89q.worldedit.Vector v)
	{
		this.x = v.getX();
		this.y = v.getY();
		this.z = v.getZ();
	}

	public double getX() { return x; }
	public double getY() { return y; }
	public double getZ() { return z; }

	public int getBlockX() { return (int)x; }
	public int getBlockY() { return (int)y; }
	public int getBlockZ() { return (int)z; }

	public String toCoords()
	{ return String.format("%f,%f,%f", x, y, z); }

	public String toBlockCoords()
	{ return String.format("%d,%d,%d", getBlockX(), getBlockY(), getBlockZ()); }

	public static Vector3 fromCoords(String coords)
	{
		try
		{
			String[] values = coords.split(",");
			return new Vector3( // vector 1
				Double.parseDouble(values[0]),
				Double.parseDouble(values[1]),
				Double.parseDouble(values[2]));
		}
		catch (Exception e) { return null; }
	}

	public static Vector3 fromLocation(Location loc)
	{ return new Vector3(loc.getX(), loc.getY(), loc.getZ()); }

	public Location toLocation(World world)
	{ return new Location(world, x, y, z); }
}
