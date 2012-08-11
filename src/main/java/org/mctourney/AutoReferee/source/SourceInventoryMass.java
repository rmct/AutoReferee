package org.mctourney.AutoReferee.source;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;

import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.BlockVector3;

import com.google.common.collect.Sets;

public class SourceInventoryMass extends SourceInventory
{
	private Set<Block> blocks = Sets.newHashSet();
	
	private SourceInventoryMass(Block b) 
	{
		super(BlockData.fromBlock(b));
		blocks.add(b); this.buildMass(b);
	}
	
	private static final int MASS_RANGE = 3;
	private void buildMass(Block b)
	{
		Location loc = b.getLocation();
		World w = loc.getWorld();
		
		for (int x = -MASS_RANGE; x <= MASS_RANGE; ++x)
		for (int y = -MASS_RANGE; y <= MASS_RANGE; ++y)
		for (int z = -MASS_RANGE; z <= MASS_RANGE; ++z)
		{
			Block nb = w.getBlockAt(loc.add(x, y, z));
			if (blockdata.matches(nb) && blocks.add(nb)) this.buildMass(nb);
		}
	}

	@Override
	public int hashCode()
	{ return blocks.hashCode(); }
	
	@Override
	public boolean equals(Object o)
	{ return blocks.equals(((SourceInventoryMass) o).blocks); }

	@Override
	public String toString()
	{ return BlockVector3.fromLocation(getLocation()).toCoords(); }

	@Override
	public String getType()
	{ return "MASS"; }

	@Override
	public Location getLocation()
	{ return (Location) blocks.toArray()[0]; }

	@Override
	public boolean matchesBlock(Block b)
	{ return blocks.contains(b); }

	@Override
	public boolean matchesEntity(Entity e)
	{ return false; }
	
	public static SourceInventoryMass fromBlock(Block block)
	{ return new SourceInventoryMass(block); }
	
	public static boolean isMassSource(Block b)
	{
		return false;
	}
}
