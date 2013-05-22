package org.mctourney.autoreferee.regions;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.util.LocationUtil;

public class CylinderRegion extends AutoRefRegion
{
	public double x, y, z, h;
	public double r;

	public World world;

	public CylinderRegion(Location base, double height, double radius)
	{
		this.world = base.getWorld();
		this.x = base.getBlockX() + 0.5;
		this.y = base.getBlockY();
		this.z = base.getBlockZ() + 0.5;

		this.h = height;
		this.r = radius;
	}

	public CylinderRegion(AutoRefMatch match, Element elt)
	{ this(match.getWorld(), elt); }

	public CylinderRegion(World world, Element elt)
	{
		this(LocationUtil.fromCoords(world, elt.getAttributeValue("base")),
			Double.parseDouble(elt.getAttributeValue("height")),
			Double.parseDouble(elt.getAttributeValue("radius")));
	}

	@Override
	public double distanceToRegion(Location loc)
	{
		return multimax(0, y - loc.getY(), loc.getY() - (y + h),
			new Location(world, x, loc.getY(), z).distance(loc) - r);
	}

	public Location getBase()
	{ return new Location(world, x, y, z); }

	@Override
	public Location getRandomLocation(Random r)
	{
		double t = 2 * Math.PI * r.nextDouble();
		double u = r.nextDouble() * r.nextDouble();
		double d = (u > 1.0 ? 2.0 - u : u) * this.r;
		double y = this.y + r.nextDouble() * h;

		// for explanation: http://stackoverflow.com/a/5838055
		return new Location(world, x + d * Math.cos(t), y, z + d * Math.sin(t));
	}

	@Override
	public CuboidRegion getBoundingCuboid()
	{ return new CuboidRegion(world, x-r, x+r, y, y+h, z-r, z+r); }

	@Override
	public Element toElement()
	{
		return this.setRegionSettings(new Element("cylinder")
			.setAttribute("base", LocationUtil.toBlockCoords(this.getBase()))
			.setAttribute("height", Double.toString(h))
			.setAttribute("radius", Double.toString(r)));
	}
}
