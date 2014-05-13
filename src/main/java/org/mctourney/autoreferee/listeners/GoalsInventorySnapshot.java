package org.mctourney.autoreferee.listeners;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mctourney.autoreferee.util.BlockData;

@SuppressWarnings("serial")
public class GoalsInventorySnapshot extends HashMap<BlockData, Integer>
{
	public GoalsInventorySnapshot()
	{
		super();
	}

	public GoalsInventorySnapshot(ItemStack item, Set<BlockData> goals)
	{
		super();
		BlockData target = BlockData.fromItemStack(item);
		for (BlockData goal : goals)
			if (goal.equals(target))
				this.put(goal, item.getAmount());
	}

	public GoalsInventorySnapshot(ItemStack item, BlockData goal)
	{
		super();
		if (goal.equals(BlockData.fromItemStack(item)))
			this.put(goal, item.getAmount());
	}

	public static GoalsInventorySnapshot fromItemsAndGoals(Collection<ItemStack> items, Set<BlockData> goals)
	{
		GoalsInventorySnapshot ret = new GoalsInventorySnapshot();
		for (BlockData goal : goals)
		{
			int count = itemSum(goal, items);
			ret.put(goal, count);
		}
		return ret;
	}

	public GoalsInventorySnapshot(Inventory inv, Set<BlockData> goals)
	{
		super();
		ItemStack[] contents = inv.getContents();
		for (BlockData goal : goals)
		{
			int count = itemSum(goal, contents);
			this.put(goal, count);
		}
	}

	public GoalsInventorySnapshot(Collection<Block> blocks, Set<BlockData> goals)
	{
		super();
		for (BlockData goal : goals)
		{
			int count = blockCount(goal, blocks);
			this.put(goal, count);
		}
	}

	public GoalsInventorySnapshot(MapDifference<BlockData, Integer> diff)
	{
		super();
		this.putAll(diff.entriesOnlyOnLeft());
		for (Map.Entry<BlockData, Integer> entry : diff.entriesOnlyOnRight().entrySet())
		{
			this.put(entry.getKey(), -entry.getValue());
		}
		for (Map.Entry<BlockData, ValueDifference<Integer>> entry : diff.entriesDiffering().entrySet())
		{
			this.put(entry.getKey(), entry.getValue().leftValue() - entry.getValue().rightValue());
		}
	}

	@Override
	public Integer put(BlockData key, Integer value)
	{
		Validate.notNull(value, "Null integers are not permitted in GoalsInventorySnapshot");
		// Convert all zeros to not-present
		if (value == 0)
			return this.remove(key);

		return super.put(key, value);
	}

	/**
	 * This object is left in the return, the provided argument is the right.
	 */
	public MapDifference<BlockData, Integer> getDiff(GoalsInventorySnapshot other)
	{
		return Maps.difference(this, other);
	}
	
	public GoalsInventorySnapshot getSubtracted(GoalsInventorySnapshot other)
	{
		return new GoalsInventorySnapshot(getDiff(other));
	}

	private static int itemSum(BlockData data, ItemStack[] items)
	{
		int count = 0;
		for (ItemStack it : items)
		{
			if (it == null) continue;
			if (data.equals(BlockData.fromItemStack(it)))
				count += it.getAmount();
		}
		return count;
	}

	private static int itemSum(BlockData data, Collection<ItemStack> items)
	{
		int count = 0;
		for (ItemStack it : items)
		{
			if (it == null) continue;
			if (data.equals(BlockData.fromItemStack(it)))
				count += it.getAmount();
		}
		return count;
	}
	
	private static int blockCount(BlockData data, Collection<Block> blocks)
	{
		int count = 0;
		for (Block block : blocks)
		{
			if (data.matchesBlock(block))
				count++;
		}
		return count;
	}
	
	public String toString()
	{
		if (this.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		// Desired output: RED WOOL, GREEN WOOL, YELLOW WOOL x5, BLUE WOOL x20
		for (Map.Entry<BlockData, Integer> entry : this.entrySet())
		{
			if (!first) sb.append(", ");
			sb.append(entry.getKey().getDisplayName());
			sb.append(ChatColor.RESET);
			int count = entry.getValue();
			if (count != 1)
				sb.append(ChatColor.YELLOW).append("x").append(Integer.toString(count)).append(ChatColor.RESET);
		}
		return sb.toString();
	}
}
