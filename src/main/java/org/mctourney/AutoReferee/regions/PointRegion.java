package org.mctourney.AutoReferee.regions;

import java.util.Random;

import org.bukkit.Location;

import org.jdom2.Element;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.util.LocationUtil;

public class PointRegion extends AutoRefRegion
{
	Location pos = null;

	public PointRegion(Location loc)
	{ this.pos = loc; }

	public PointRegion(AutoRefMatch match, Element e)
	{ this(LocationUtil.fromCoords(match.getWorld(), e.getAttributeValue("pos"))); }

	@Override
	public double distanceToRegion(Location loc)
	{ return pos.distance(loc); }

	@Override
	public Location getRandomLocation(Random r)
	{ return pos.clone().add(0.5, 0.0, 0.5); }

	@Override
	public CuboidRegion getBoundingCuboid()
	{ return new CuboidRegion(pos, pos); }

	@Override
	public int hashCode()
	{ return pos.hashCode(); }

	@Override
	public boolean equals(Object o)
	{ return (o instanceof PointRegion) && hashCode() == o.hashCode(); }
}
