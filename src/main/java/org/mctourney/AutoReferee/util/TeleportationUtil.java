package org.mctourney.AutoReferee.util;

import java.util.Set;
import java.lang.Math;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.google.common.collect.Sets;

import org.mctourney.AutoReferee.AutoRefPlayer;

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
		return locationTeleport(loc.add(0.0, 1.7, 0.0));
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

	private static Set<Material> passableBlocks;
	static
	{
		passableBlocks = Sets.newHashSet();
		passableBlocks.add(Material.AIR);
		passableBlocks.add(Material.DEAD_BUSH);
		passableBlocks.add(Material.DIODE_BLOCK_ON);
		passableBlocks.add(Material.DIODE_BLOCK_OFF);
		passableBlocks.add(Material.LADDER);
		passableBlocks.add(Material.LEVER);
		passableBlocks.add(Material.LONG_GRASS);
		passableBlocks.add(Material.MELON_STEM);
		passableBlocks.add(Material.NETHER_STALK);
		passableBlocks.add(Material.NETHER_WARTS);
		passableBlocks.add(Material.POWERED_RAIL);
		passableBlocks.add(Material.PUMPKIN_STEM);
		passableBlocks.add(Material.RAILS);
		passableBlocks.add(Material.RED_ROSE);
		passableBlocks.add(Material.REDSTONE_TORCH_ON);
		passableBlocks.add(Material.REDSTONE_TORCH_OFF);
		passableBlocks.add(Material.REDSTONE_WIRE);
		passableBlocks.add(Material.SAPLING);
		passableBlocks.add(Material.SIGN);
		passableBlocks.add(Material.SIGN_POST);
		passableBlocks.add(Material.SNOW);
		passableBlocks.add(Material.STATIONARY_WATER);
		passableBlocks.add(Material.STONE_BUTTON);
		passableBlocks.add(Material.STONE_PLATE);
		passableBlocks.add(Material.TORCH);
		passableBlocks.add(Material.TRIPWIRE);
		passableBlocks.add(Material.TRIPWIRE_HOOK);
		passableBlocks.add(Material.VINE);
		passableBlocks.add(Material.WALL_SIGN);
		passableBlocks.add(Material.WATER);
		passableBlocks.add(Material.WATER_LILY);
		passableBlocks.add(Material.WOOD_PLATE);
		passableBlocks.add(Material.YELLOW_FLOWER);
	}

	private static boolean isBlockPassable(Block b)
	{ return passableBlocks.contains(b.getType()); }

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

	private static Set<Vector> directions;
	static
	{
		directions = Sets.newHashSet();
		directions.add(new Vector( 0,  0,  1));
		directions.add(new Vector( 0,  0, -1));
		directions.add(new Vector( 1,  0,  0));
		directions.add(new Vector(-1,  0,  0));
	}
}
