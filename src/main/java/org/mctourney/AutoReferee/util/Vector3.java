package org.mctourney.AutoReferee.util;

import org.bukkit.Location;

public class Vector3
{
	public double x, y, z;
	
	public Vector3(double x, double y, double z)
	{ this.x = x; this.y = y; this.z = z; }
	
	public Vector3(BlockVector3 v)
	{ this.x = v.x; this.y = v.y; this.z = v.z; }
	
	public Vector3(com.sk89q.worldedit.Vector v)
	{
		this.x = v.getX();
		this.y = v.getY();
		this.z = v.getZ();
	}

	public String toCoords()
	{ return new BlockVector3(this).toCoords(); }

	public static Vector3 fromCoords(String coords)
	{
		try
		{
			String[] values = coords.split(",");
			return new Vector3( // vector 1
				Integer.parseInt(values[0]),
				Integer.parseInt(values[1]),
				Integer.parseInt(values[2]));
		}
		catch (Exception e) { return null; }
	}

	public static Vector3 fromLocation(Location loc)
	{ return new Vector3(loc.getX(), loc.getY(), loc.getZ()); }
}
