package org.mctourney.autoreferee.util;

import java.util.Set;
import java.lang.Math;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.google.common.collect.Sets;

import org.mctourney.autoreferee.AutoRefPlayer;

public class TeleportationUtil
{
	private TeleportationUtil()
	{  }

	public static Location locationTeleport(Location loc)
	{
		if (loc == null) return null;

		Location x, c = loc.clone(), best = c;
		double sqd, bsqd = -1.0;

		for (Vector vec : directions)
		{
			x = TeleportationUtil.checkDirection(c, vec);
			sqd = x.distanceSquared(best);
			if (sqd > bsqd) { bsqd = sqd; best = x; }
		}

		// return a location that is looking at the target
		Location dest = locationLookingAt(best, loc);
		return dest.distance(loc) < 1.0 ? null : dest;
	}

	public static Location blockTeleport(Block b)
	{ return blockTeleport(b.getLocation()); }

	public static Location blockTeleport(Location loc)
	{ return locationTeleport(loc.clone().add(0.5, 0.5, 0.5)); }

	public static Location entityTeleport(Entity e)
	{ return locationTeleport(e.getLocation()); }

	public static Location playerTeleport(AutoRefPlayer apl)
	{
		if (apl == null) return null;

		Location loc = apl.getLocation().clone();
		return locationTeleport(loc);
	}

	private static Location locationLookingAt(Location base, Location target)
	{
		if (base == null || target == null) return base;

		double dx = base.getX() - target.getX(),
		       dy = base.getY() - target.getY(),
		       dz = base.getZ() - target.getZ();

		double dist = Math.sqrt(dx*dx + dz*dz);

		Location res = base.clone();
		res.setPitch((float)(Math.atan2(dy, dist)*180/Math.PI));
		res.setYaw((float)(Math.atan2(dz, dx)*180/Math.PI) + 90.0f);

		return res;
	}

	private static Set<Material> passableBlocks = Sets.newHashSet
	(	Material.AIR
	,	Material.DEAD_BUSH
	,	Material.DIODE_BLOCK_ON
	,	Material.DIODE_BLOCK_OFF
	,	Material.LADDER
	,	Material.LEVER
	,	Material.LONG_GRASS
	,	Material.MELON_STEM
	,	Material.NETHER_STALK
	,	Material.NETHER_WARTS
	,	Material.POWERED_RAIL
	,	Material.PUMPKIN_STEM
	,	Material.RAILS
	,	Material.RED_ROSE
	,	Material.REDSTONE_TORCH_ON
	,	Material.REDSTONE_TORCH_OFF
	,	Material.REDSTONE_WIRE
	,	Material.SAPLING
	,	Material.SIGN
	,	Material.SIGN_POST
	,	Material.SNOW
	,	Material.STATIONARY_WATER
	,	Material.STONE_BUTTON
	,	Material.STONE_PLATE
	,	Material.TORCH
	,	Material.TRIPWIRE
	,	Material.TRIPWIRE_HOOK
	,	Material.VINE
	,	Material.WALL_SIGN
	,	Material.WATER
	,	Material.WATER_LILY
	,	Material.WOOD_PLATE
	,	Material.YELLOW_FLOWER
	);

	public static boolean isBlockPassable(Block b)
	{ return passableBlocks.contains(b.getType()); }

	public static boolean safeLocation(Location loc)
	{ return isBlockPassable(loc.getBlock()) &&
		isBlockPassable(loc.getBlock().getRelative(0, 1, 0)); }

	private static final int teleportDistance = 4;
	private static Location checkDirection(Location loc, Vector v)
	{
		v = v.normalize();
		int d = -1;

		// furthest we can stray in one direction
		// based on the rows preceeding
		int m = teleportDistance;

		Location best = loc.clone();
		for (int h = 0; h <= teleportDistance && m > h; ++h)
		{
			// attempt up to M blocks away from the center
			Location c = loc.clone().add(0, h, 0);
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
			if (k < m) m = k;

			// if this is farther away than any previously, save it
			// only allow locations below a grade of 45deg
			if (h > 0 && k >= h && k + h > d) { d = k + h; best = c; }
		}

		// shift down if we ever found a spot that works
		// location should be for feet-level, not head-level
		return d < 0 ? best : best.subtract(0, 1, 0);
	}

	private static Set<Vector> directions = Sets.newHashSet
	(	new Vector( 0,  0,  1)
	,	new Vector( 0,  0, -1)
	,	new Vector( 1,  0,  0)
	,	new Vector(-1,  0,  0)
	);
}
