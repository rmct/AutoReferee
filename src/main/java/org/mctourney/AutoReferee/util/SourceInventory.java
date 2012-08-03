package org.mctourney.AutoReferee.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;

import org.mctourney.AutoReferee.AutoRefPlayer;
import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;

public class SourceInventory
{
	public Object target;
	
	public Inventory inventory;
	public BlockData blockdata;
	
	// has this team seen this chest recently?
	public long lastSeen = 0;

	public static final long SEEN_COOLDOWN = 40 * 20;

	public SourceInventory(Inventory inv)
	{
		this.blockdata = BlockData.fromInventory(this.inventory = inv);
	}

	@Override public String toString()
	{
		// if the source is a block (Location), print coords
		if (target instanceof Location)
			return BlockVector3.fromLocation((Location) target).toCoords();
		
		// if the source is an entity, print UUID
		else if (target instanceof Entity)
			return ((Entity) target).getUniqueId().toString();
		
		// profit ??
		return null;
	}

	public String getName()
	{
		Location loc = null;
		String type = "??";
		
		if (target instanceof Location)
		{
			loc = (Location) target;
			type = loc.getWorld().getBlockAt(loc).getType().name();
		}
		
		else if (target instanceof Entity)
		{
			Entity entity = (Entity) target;
			loc = entity.getLocation();
			type = entity.getType().name();
		}
		
		return String.format("%s(%s)", type, 
			BlockVector3.fromLocation(loc).toCoords());
	}

	public void hasSeen(AutoRefPlayer apl)
	{
		// if this team has seen this box before, nevermind
		long ctime = this.getLocation().getWorld().getFullTime();
		if (lastSeen > 0 && ctime - lastSeen < SourceInventory.SEEN_COOLDOWN) return;
		
		if (apl != null)
		{
			// generate a transcript event for seeing the box
			String m = String.format("%s is carrying %s", apl.getPlayerName(), blockdata.getRawName());
			apl.getTeam().getMatch().addEvent(new TranscriptEvent(apl.getTeam().getMatch(),
				TranscriptEvent.EventType.OBJECTIVE_FOUND, m, this.getLocation(), apl, blockdata));
		}
		
		// mark this box as seen
		lastSeen = ctime;
	}

	private Location getLocation()
	{
		// if the source is a block (Location), return the location
		if (target instanceof Location) return ((Location) target);
		
		// if the source is an entity, return its position
		if (target instanceof Entity) return ((Entity) target).getLocation();
		
		// wat do?
		return null;
	}
}