package org.mctourney.AutoReferee.util;

import org.bukkit.Location;

public class BlockVector3 
{
	public int x, y, z;
	
	public BlockVector3(int x, int y, int z)
	{ this.x = x; this.y = y; this.z = z; }
	
	public BlockVector3(Vector3 v)
	{
		this.x = (int) v.x;
		this.y = (int) v.y;
		this.z = (int) v.z;
	}

	public String toCoords()
	{ return x + "," + y + "," + z; }

	public static BlockVector3 fromLocation(Location loc)
	{ return new BlockVector3(Vector3.fromLocation(loc)); }
}
