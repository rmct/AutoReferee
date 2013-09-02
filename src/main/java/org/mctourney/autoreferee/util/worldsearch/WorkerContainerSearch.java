package org.mctourney.autoreferee.util.worldsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
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
		Set<BlockData> goals = master.searching;
		for (int i = 0; i < 15; i++) // 15 chunks per run
		{
			Vector vec = master.entitychunks.poll();
			if (vec == null) { this.cancel(); return; }
			BlockState[] entities = world.getChunkAt(vec.getBlockX(), vec.getBlockZ()).getTileEntities(); // actually chunk-coords

			for (Entity ent : entities)
			{
d
			}
		}


	}

}
