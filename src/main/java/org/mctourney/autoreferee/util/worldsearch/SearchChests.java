package org.mctourney.autoreferee.util.worldsearch;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class SearchChests extends BukkitRunnable
{
	public static int MAX_PER_TICK = 30;

	private boolean finished = false;
	private Map<BlockData, Vector> ret = Maps.newHashMap();
	private Iterator<Vector> locationIter;
	private Set<BlockData> searching;
	private World world;

	public SearchChests(Set<Vector> chests, AutoRefTeam team, Set<BlockData> searching)
	{
		locationIter = chests.iterator();
		this.searching = Sets.newHashSet(searching);
		world = team.getMatch().getWorld();
	}

	@Override
	public void run()
	{
		int count = 0;
		while (locationIter.hasNext() && count < MAX_PER_TICK)
		{
			count++;
			Vector vector = locationIter.next();
			Location loc = vector.toLocation(world);
			BlockState state = world.getBlockAt(loc).getState();
			if (state instanceof InventoryHolder)
			{
				Inventory inv = ((InventoryHolder) state).getInventory();
				for (BlockData bd : searching)
				{
					if (bd.getData() == -1)
					{
						if (inv.contains(bd.getMaterial()))
						{
							// success
							ret.put(bd, vector);
							// TODO remove from searching
						}
					}
					else
					{
						Collection<? extends ItemStack> items = inv.all(bd.getMaterial()).values();
						if (items.isEmpty()) continue;
						for (ItemStack i : items)
							if (bd.equals(BlockData.fromItemStack(i)))
							{
								// success
								ret.put(bd, vector);
							}
					}
				}
			}
			else // TODO remove after testing
				System.err.println("[AutoReferee] [Debug] block at " + LocationUtil.toBlockCoords(loc) + " was not a container");
		}
	}

	public boolean isDone() {
		return finished;
	}

	public Map<BlockData, Vector> getResults()
	{
		return ret;
	}
}
