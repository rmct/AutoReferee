package org.mctourney.AutoReferee.source;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;

public class SourceInventoryEntity extends SourceInventory
{
	private Entity entity = null;
	
	private SourceInventoryEntity(Entity e) 
	{
		super(((InventoryHolder) e).getInventory());
		this.entity = e;
	}

	@Override
	public String toString()
	{ return this.entity.getUniqueId().toString(); }

	@Override
	public String getType()
	{ return entity.getType().name(); }

	@Override
	public Location getLocation()
	{ return entity.getLocation(); }

	@Override
	public boolean matchesBlock(Block b)
	{ return false; }

	@Override
	public boolean matchesEntity(Entity e)
	{ return entity.equals(e); }
	
	public static SourceInventoryEntity fromEntity(Entity e)
	{
		if (e instanceof InventoryHolder)
			return new SourceInventoryEntity(e);
		else return null;
	}
	
	public static SourceInventoryEntity fromUUID(World w, UUID uid)
	{
		for (Entity e : w.getEntitiesByClasses(InventoryHolder.class))
			if (uid.equals(e.getUniqueId())) return fromEntity(e);
		return null;
	}
}
