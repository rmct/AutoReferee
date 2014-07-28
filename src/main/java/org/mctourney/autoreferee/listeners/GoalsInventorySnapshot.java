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

	public static GoalsInventorySnapshot fromDiff(MapDifference<BlockData, Integer> diff, boolean leftSideOnly)
	{
		GoalsInventorySnapshot snap = new GoalsInventorySnapshot();
		if (leftSideOnly)
			snap.putAll(diff.entriesOnlyOnLeft());
		else
			snap.putAll(diff.entriesOnlyOnRight());

		// Only positive differences
		for (Map.Entry<BlockData, ValueDifference<Integer>> entry : diff.entriesDiffering().entrySet())
		{
			int count;
			if (leftSideOnly)
				count = entry.getValue().leftValue() - entry.getValue().rightValue();
			else
				count = entry.getValue().rightValue() - entry.getValue().leftValue();

			if (count > 0)
				snap.put(entry.getKey(), count);
		}
		return snap;
	}

	public int getInt(BlockData key)
	{
		Integer val = get(key);
		if (val == null) return 0;

		return val;
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

	public void subtractInPlace(GoalsInventorySnapshot other)
	{
		for (Map.Entry<BlockData, Integer> entry : other.entrySet())
			subtractInPlace(entry.getKey(), entry.getValue());
	}

	public void subtractInPlace(BlockData key, Integer value)
	{
		Validate.notNull(value);
		put(key, getInt(key) - value);
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
		// Desired output: RED WOOL, GREEN WOOL, 5 x YELLOW WOOL, 20 x BLUE WOOL
		for (Map.Entry<BlockData, Integer> entry : this.entrySet())
		{
			if (!first)
			{ sb.append(", "); }
			else
			{ first = false; }

			int count = entry.getValue();
			if (count != 1)
				sb.append(ChatColor.YELLOW).append(Integer.toString(count)).append(" x ").append(ChatColor.RESET);
			sb.append(entry.getKey().getDisplayName());
			sb.append(ChatColor.RESET);
		}
		return sb.toString();
	}
}
