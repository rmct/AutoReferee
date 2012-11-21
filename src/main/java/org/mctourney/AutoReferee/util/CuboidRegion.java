package org.mctourney.AutoReferee.util;

import org.bukkit.Location;

public class CuboidRegion
{
	public double x1, y1, z1;
	public double x2, y2, z2;

	public CuboidRegion(Vector3 v1, Vector3 v2)
	{
		x1 = Math.min(v1.x, v2.x); x2 = Math.max(v1.x, v2.x);
		y1 = Math.min(v1.y, v2.y); y2 = Math.max(v1.y, v2.y);
		z1 = Math.min(v1.z, v2.z); z2 = Math.max(v1.z, v2.z);
	}

	public CuboidRegion(double x1, double x2, double y1, double y2, double z1, double z2)
	{
		this.x1 = x1; this.x2 = x2;
		this.y1 = y1; this.y2 = y2;
		this.z1 = z1; this.z2 = z2;
	}

	public Vector3 getMinimumPoint()
	{ return new Vector3(x1, y1, z1); }

	public Vector3 getMaximumPoint()
	{ return new Vector3(x2, y2, z2); }

	public boolean contains(Vector3 v)
	{
		return v.x >= x1 && v.y >= y1 && v.z >= z1
		    && v.x <= x2 && v.y <= y2 && v.z <= z2;
	}

	public static CuboidRegion fromCoords(String coords)
	{
		// split the region coordinates into two corners
		String[] values = coords.split(":");

		// generate the region by the two vectors
		Vector3 v1 = Vector3.fromCoords(values[0]), v2 = Vector3.fromCoords(values[1]);
		return (v1 == null || v2 == null ? null : new CuboidRegion(v1, v2));
	}

	public String toCoords()
	{
		// save region as "minX minY minZ maxX maxY maxZ"
		return getMinimumPoint().toCoords() + ":" +
			getMaximumPoint().toCoords();
	}

	// wrote this dumb helper function because `distanceToRegion` was looking ugly...
	public static double multimax( double base, double ... more )
	{ for ( double x : more ) base = Math.max(base, x); return base; }

	// distance from region, axially aligned (value less than actual distance, but
	// appropriate for measurements on cuboid regions)
	public double distanceToRegion(Location v)
	{
		// garbage-in, garbage-out
		if (v == null) return Double.POSITIVE_INFINITY;

		double x = v.getX(), y = v.getY(), z = v.getZ();
		Vector3 mx = getMaximumPoint(), mn = getMinimumPoint();

		// return maximum distance from this region
		// (max on all sides, axially-aligned)
		return CuboidRegion.multimax ( 0
		,	mn.x - x, x - mx.x - 1
		,	mn.y - y, y - mx.y - 1
		,	mn.z - z, z - mx.z - 1
		);
	}
}
