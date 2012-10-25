package org.mctourney.AutoReferee.util;

import java.lang.Math;

import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import org.mctourney.AutoReferee.AutoRefPlayer;

public class TeleportationUtil
{
	private TeleportationUtil()
	{  }

	private static Location locationLookingAt(Location base, Location target)
	{
		if (base == null || target == null) return base;

		double dx = base.getX() - target.getX(),
			   dy = base.getY() - target.getY(),
			   dz = base.getZ() - target.getZ();

		double dist = Math.sqrt(dx*dx + dz*dz);
		
		Location res = base.clone();
		res.setPitch((float) Math.atan2(dist, dy));
		res.setYaw((float) Math.atan2(dz, dx));

		return res;
	}

	private static boolean isBlockPassable(Block b)
	{ return net.minecraft.server.Block.byId[b.getTypeId()].material.isSolid(); }

	private static Location checkDirection(Location loc, Vector v)
	{
		v = v.normalize();
		int d = -1;

		Location best = loc;
		for (int h = 0; h <= 5; ++h)
		{
			// furthest we can stray in one direction
			// based on the rows preceeding
			int m = 5;

			// if going up above the target is impassable, stop
			Location c = loc.add(0, h, 0);
			if (!isBlockPassable(c.getBlock())) break;

			// attempt up to M blocks away from the center
			int k; for (k = 1; k <= m; ++k)
			{
				// the next location we are checking
				Location nc = c.add(v);

				// if this block is impassable, dec k before quitting
				if (!isBlockPassable(nc.getBlock())) { --k; break; }
				
				// update c if the block is passable
				else c = nc;
			}

			// we only got to row k, don't exceed this in future passes
			m = k;

			// if this is farther away than any previously
			// save it as our best match
			if (h > 0 && k + h > d) { d = k + h; best = c; }
		}

		return best;
	}

	public static Location locationTeleport(Location loc)
	{
		Location x, best = loc;
		double sqd, bsqd = -1.0;
	
		x = checkDirection(loc, new Vector( 0,  0,  1));
		sqd = x.distanceSquared(best); 
		if (sqd > bsqd) { bsqd = sqd; best = x; }

		x = checkDirection(loc, new Vector( 0,  0, -1));
		sqd = x.distanceSquared(best); 
		if (sqd > bsqd) { bsqd = sqd; best = x; }

		x = checkDirection(loc, new Vector( 1,  0,  0));
		sqd = x.distanceSquared(best); 
		if (sqd > bsqd) { bsqd = sqd; best = x; }

		x = checkDirection(loc, new Vector(-1,  0,  0));
		sqd = x.distanceSquared(best); 
		if (sqd > bsqd) { bsqd = sqd; best = x; }

		// return a location that is looking at the target
		return locationLookingAt(best, loc);
	}
}
