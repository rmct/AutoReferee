package org.mctourney.AutoReferee;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public class ObjectiveTracker implements Listener 
{
	AutoReferee plugin = null;
	
	public ObjectiveTracker(Plugin p)
	{
		plugin = (AutoReferee) p;
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void inventoryClick(InventoryClickEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getWhoClicked().getWorld());
		if (match == null) return;
		
		Inventory inv = event.getInventory();
	}
}
