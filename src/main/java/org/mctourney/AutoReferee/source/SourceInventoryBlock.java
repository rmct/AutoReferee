package org.mctourney.AutoReferee.source;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;

import org.mctourney.AutoReferee.util.BlockVector3;

public class SourceInventoryBlock extends SourceInventory
{
	private Block block = null;
	
	private SourceInventoryBlock(Block b) 
	{
		super(((InventoryHolder) b.getState()).getInventory());
		this.block = b;
	}

	@Override
	public String toString()
	{ return BlockVector3.fromLocation(getLocation()).toCoords(); }

	@Override
	public String getType()
	{ return block.getType().name(); }

	@Override
	public Location getLocation()
	{ return block.getLocation(); }

	@Override
	public boolean matchesBlock(Block b)
	{ return block.equals(b); }

	@Override
	public boolean matchesEntity(Entity e)
	{ return false; }
	
	public static SourceInventoryBlock fromBlock(Block block)
	{
		if (block.getState() instanceof InventoryHolder)
			return new SourceInventoryBlock(block);
		else return null;
	}
	
	public static SourceInventoryBlock fromLocation(Location loc)
	{ return SourceInventoryBlock.fromBlock(loc.getBlock()); }
}
