package org.mctourney.autoreferee.util.worldsearch;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.mctourney.autoreferee.util.BlockData;

/**
 * Consumes: ObjectiveExhaustionMasterTask.foundContainers<br>
 * Output: ObjectiveExhaustionMasterTask.found<br>
 * Cancellation: Handled by master
 *
 * Checks containers for the goals, and adds the results to the found field.
 *
 * @author kane
 */
public class WorkerContainerSearch extends BukkitRunnable
{
	private ObjectiveExhaustionMasterTask master;

	public WorkerContainerSearch(ObjectiveExhaustionMasterTask task)
	{
		master = task;
	}

	@Override
	public void run()
	{
		if (master.contchunks.isEmpty()) return;

		World world = master.team.getMatch().getWorld();
		Location loc = new Location(world, 0, 0, 0);
		Set<BlockData> goals = master.searching;
		for (int i = 0; i < 15; i++) // 15 chunks per run
		{
			Vector vec = master.entitychunks.poll();
			if (vec == null) { this.cancel(); return; }
			BlockState[] containers = world.getChunkAt(vec.getBlockX(), vec.getBlockZ()).getTileEntities(); // actually chunk-coords

			for (BlockState state : containers)
			{
				if (state instanceof InventoryHolder)
				{
					Inventory inv = ((InventoryHolder) state).getInventory();
					if (state instanceof Chest)
						inv = ((Chest) state).getBlockInventory();
					checkInventory(inv, goals, state.getLocation(loc));
				}
				// TODO: spawners
			}
		}
	}

	private void checkInventory(Inventory inv, Set<BlockData> goals, Location loc)
	{
		for (ItemStack item : inv.getContents())
		{
			BlockData bd = BlockData.fromItemStack(item);
			for (BlockData data : goals)
				if (data.equals(bd))
					master.found.add(new _Entry<BlockData, Vector>(data, loc.toVector()));
		}
	}
}
