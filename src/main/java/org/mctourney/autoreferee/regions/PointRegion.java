package org.mctourney.autoreferee.regions;

import java.util.Random;

import org.bukkit.Location;

import org.bukkit.World;
import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.util.LocationUtil;
import org.mctourney.autoreferee.util.MathUtil;

public class PointRegion extends AutoRefRegion
{
	Location pos = null;
	double x = 0; double y = 0; double z = 0;
	
	public PointRegion(Location loc)
	{ this.pos = loc.getBlock().getLocation(); this.yaw = (int)loc.getYaw(); 
	  this.x = pos.getX(); this.y = pos.getY(); this.z = pos.getY(); }

	public PointRegion(AutoRefMatch match, Element e)
	{ this(match.getWorld(), e); }

	public PointRegion(World world, Element e)
	{ this(LocationUtil.fromCoords(world, e.getAttributeValue("pos"))); }

	public Element toElement()
	{
		return this.setRegionSettings(new Element("location")
			.setAttribute("pos", LocationUtil.toBlockCoords(pos)));
	}

	@Override
	public double distanceToRegion(double x0, double y0, double z0)
	{ return MathUtil.dist(x, y, z, x0, y0, z0); }

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

	@Override
	public String toString()
	{ return String.format("POINT(%s)", LocationUtil.toBlockCoords(pos)); }
}
