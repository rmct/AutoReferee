package org.mctourney.autoreferee.regions;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.util.LocationUtil;

public class CuboidRegion extends AutoRefRegion
{
	public double x1, y1, z1;
	public double x2, y2, z2;

	public World world;

	public CuboidRegion(Location v1, Location v2)
	{
		// if the two locations are from different worlds, quit
		assert v1.getWorld() == v2.getWorld();

		world = v1.getWorld();
		x1 = Math.min(v1.getX(), v2.getX()); x2 = Math.max(v1.getX(), v2.getX());
		y1 = Math.min(v1.getY(), v2.getY()); y2 = Math.max(v1.getY(), v2.getY());
		z1 = Math.min(v1.getZ(), v2.getZ()); z2 = Math.max(v1.getZ(), v2.getZ());
	}

	public CuboidRegion(World world, double x1, double x2, double y1, double y2, double z1, double z2)
	{
		this.world = world;
		this.x1 = Math.min(x1, x2); this.x2 = Math.max(x1, x2);
		this.y1 = Math.min(y1, y2); this.y2 = Math.max(y1, y2);
		this.z1 = Math.min(z1, z2); this.z2 = Math.max(z1, z2);
	}

	public CuboidRegion(AutoRefMatch match, Element elt)
	{ this(match.getWorld(), elt); }

	public CuboidRegion(World world, Element elt)
	{
		this(
			LocationUtil.fromCoords(world, elt.getAttributeValue("min")),
			LocationUtil.fromCoords(world, elt.getAttributeValue("max"))
		);
	}

	public Element toElement()
	{
		return this.setRegionSettings(new Element("cuboid")
			.setAttribute("min", LocationUtil.toBlockCoords(this.getMinimumPoint()))
			.setAttribute("max", LocationUtil.toBlockCoords(this.getMaximumPoint())));
	}

	@Override
	public int hashCode()
	{ return getMinimumPoint().hashCode() ^ Integer.rotateLeft(getMaximumPoint().hashCode(), 16); }

	@Override
	public boolean equals(Object o)
	{ return (o instanceof CuboidRegion) && hashCode() == o.hashCode(); }

	@Override
	public String toString()
	{
		return String.format("CUBOID(%s:%s), A=%d",
			LocationUtil.toBlockCoords(this.getMinimumPoint()),
			LocationUtil.toBlockCoords(this.getMaximumPoint()),
			(int)((x2-x1+1) * (y2-y1+1) * (z2-z1+1)));
	}

	public Location getMinimumPoint()
	{ return new Location(world, x1, y1, z1); }

	public Location getMaximumPoint()
	{ return new Location(world, x2, y2, z2); }

	public static CuboidRegion combine(CuboidRegion a, CuboidRegion b)
	{
		assert a.world == b.world;
		return new CuboidRegion(a.world,
			Math.min(a.x1, b.x1), Math.max(a.x2, b.x2),
			Math.min(a.y1, b.y1), Math.max(a.y2, b.y2),
			Math.min(a.z1, b.z1), Math.max(a.z2, b.z2));
	}

	// distance from region, axially aligned (value less than actual distance, but
	// appropriate for measurements on cuboid regions)
	@Override
	public double distanceToRegion(Location v)
	{
		// garbage-in, garbage-out
		if (v == null || v.getWorld() != world)
			return Double.POSITIVE_INFINITY;

		double x = v.getX(), y = v.getY(), z = v.getZ();
		Location mx = getMaximumPoint(), mn = getMinimumPoint();

		// return maximum distance from this region
		// (max on all sides, axially-aligned)
		return CuboidRegion.multimax ( 0
		,	mn.getX() - x, x - mx.getX() - 1
		,	mn.getY() - y, y - mx.getY() - 1
		,	mn.getZ() - z, z - mx.getZ() - 1
		);
	}

	@Override
	public Location getRandomLocation(Random r)
	{
		return getMinimumPoint().add(
			(x2 - x1 + 1) * r.nextDouble(),
			(y2 - y1 + 1) * r.nextDouble(),
			(z2 - z1 + 1) * r.nextDouble());
	}

	@Override
	public CuboidRegion getBoundingCuboid() { return this; }
}
