package org.mctourney.AutoReferee.source;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;

import org.mctourney.AutoReferee.AutoRefPlayer;
import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.BlockVector3;

public class SourceInventory
{
	public Inventory inventory;
	public BlockData blockdata;
	
	// has this team seen this chest recently?
	public long lastSeen = 0;

	public static final long SEEN_COOLDOWN = 40 * 20;

	protected SourceInventory(Inventory inv)
	{ this.blockdata = BlockData.fromInventory(this.inventory = inv); }
	
	protected SourceInventory(BlockData bd)
	{ this.blockdata = bd; this.inventory = null; }

	@Override
	public String toString()
	{ throw new UnsupportedOperationException("Abstract method."); }

	public String getType()
	{ throw new UnsupportedOperationException("Abstract method."); }

	public String getName()
	{
		return ChatColor.GRAY + String.format("%s(%s)", this.getType(),
			BlockVector3.fromLocation(this.getLocation()).toCoords()) + ChatColor.RESET;
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

	public Location getLocation()
	{ throw new UnsupportedOperationException("Abstract method."); }
	
	public boolean matchesBlock(Block b)
	{ throw new UnsupportedOperationException("Abstract method."); }
	
	public boolean matchesEntity(Entity e)
	{ throw new UnsupportedOperationException("Abstract method."); }
}
