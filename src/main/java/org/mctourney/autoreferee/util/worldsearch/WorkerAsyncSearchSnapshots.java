package org.mctourney.autoreferee.util.worldsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.mctourney.autoreferee.util.BlockData;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Consumes: ObjectiveExhaustionMasterTask.snapshots<br>
 * Output: ObjectiveExhaustionMasterTask.found<br>
 * Output: ObjectiveExhaustionMasterTask.foundContainers<br>
 * Cancellation: Self-cancels upon master.all_snapshots_added and queue empty
 *
 * Searches the ChunkSnapshots for the goal blocks.
 *
 * @author kane
 *
 */
public class WorkerAsyncSearchSnapshots extends BukkitRunnable
{
	public volatile boolean finished = false;
	private ObjectiveExhaustionMasterTask master;

	public static final int[] CONTAINER_BLOCKS = new int[]
			{
		Material.BEACON.getId(),
		Material.BREWING_STAND.getId(),
		Material.CHEST.getId(),
		Material.TRAPPED_CHEST.getId(),
		Material.DISPENSER.getId(),
		Material.DROPPER.getId(),
		Material.FURNACE.getId(),
		Material.HOPPER.getId(),
		Material.JUKEBOX.getId(),
			};

	public WorkerAsyncSearchSnapshots(ObjectiveExhaustionMasterTask task)
	{
		master = task;
	}

	@Override
	public void run()
	{
		// pre-clear, pass it on later
		boolean wasInterrupted = Thread.interrupted();

		try
		{
			// Blocking run until everything's available
			while (!master.all_snapshots_added)
			{
				consume(master.snapshots.take());
			}

			// Finish off by listening to special value
			ChunkSnapshot snap;
			while ((snap = master.snapshots.poll()) != null)
			{
				consume(snap);
			}
		}
		catch (IllegalArgumentException e)
		{
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
			wasInterrupted = true;
		}
		master.snapshots.offer(null);

		finished = true;

		if (wasInterrupted) // pass it on
			Thread.currentThread().interrupt();
	}

	private void consume(ChunkSnapshot snap)
	{
		Validate.notNull(snap); // send stop signal if poison-value

		Set<BlockData> goals = master.searching; // safe due to COW
		int[] interesting = buildInteresting(goals);

		int baseX = snap.getX() << 4;
		int baseZ = snap.getZ() << 4;

		for (int sy = 0; sy < 16; sy++)
		{
			if (snap.isSectionEmpty(sy))
				continue;
			for (int py = sy << 4; py < (sy << 4) + 16; py++)
				for (int iz = 0; iz < 16; iz++)
					for (int ix = 0; ix < 16; ix++)
					{
						int block = snap.getBlockTypeId(ix, py, iz);
						if (ArrayUtils.contains(interesting, block))
						{
							Vector pos = new Vector(baseX + ix, py, baseZ + iz);
							if (ArrayUtils.contains(CONTAINER_BLOCKS, block))
								master.foundContainers.add(pos);
							else
							{
								BlockData bd = new BlockData(Material.getMaterial(block), (byte) snap.getBlockData(ix, py, iz));
								for (BlockData data : goals)
									if (data.equals(bd))
										master.found.add(new _Entry<BlockData, Vector>(data, pos));
							}
						}
					}
		}
	}

	private static int[] buildInteresting(Set<BlockData> goals)
	{
		Set<BlockData> filterGoals = Sets.newHashSet();
		for (BlockData data : goals)
		{
			filterGoals.add(new BlockData(data.getMaterial()));
		}
		int[] tmp = new int[filterGoals.size()];
		int i = 0;
		for (BlockData data : filterGoals)
		{
			tmp[i++] = data.getMaterial().getId();
		}
		return ArrayUtils.addAll(CONTAINER_BLOCKS, tmp);
	}
}
