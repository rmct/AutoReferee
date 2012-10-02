package org.mctourney.AutoReferee.util;

import org.bukkit.Location;

public class BlockVector3 extends Vector3
{
	private int bx, by, bz;

	public BlockVector3(int x, int y, int z)
	{ super(x, y, z); bx = x; by = y; bz = z; }

	public BlockVector3(Vector3 v)
	{ this((int)v.x, (int)v.y, (int)v.z); }

	@Override
	public String toCoords()
	{ return bx + "," + by + "," + bz; }

	public static BlockVector3 fromCoords(String coords)
	{
		Vector3 v3 = Vector3.fromCoords(coords);
		return v3 == null ? null : new BlockVector3(v3);
	}

	public static BlockVector3 fromLocation(Location loc)
	{ return new BlockVector3(Vector3.fromLocation(loc)); }
}
