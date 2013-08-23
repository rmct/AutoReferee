package org.mctourney.autoreferee.listeners;

import java.util.HashMap;
import java.util.Set;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mctourney.autoreferee.util.BlockData;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

@SuppressWarnings("serial")
public class GoalsInventorySnapshot extends HashMap<BlockData, Integer>
{
	public GoalsInventorySnapshot()
	{
		super();
	}

	public GoalsInventorySnapshot(Inventory inv, Set<BlockData> goals)
	{
		super();
		for (BlockData goal : goals) {
			int count = itemSum(goal, inv.getContents());
			if (count != 0) this.put(goal, count);
		}
	}

	/**
	 * This state is left in the return, the argument is right.
	 */
	public MapDifference<BlockData, Integer> subtract(GoalsInventorySnapshot other)
	{
		return Maps.difference(this, other);
	}

	private static int itemSum(BlockData data, ItemStack[] items) {
		int count = 0;
		for (ItemStack it : items) {
			if (it == null) continue;
			if (data.equals(BlockData.fromItemStack(it)))
				count += it.getAmount();
		}
		return count;
	}
}