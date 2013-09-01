package org.mctourney.autoreferee.util.worldsearch;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Consumes: ObjectiveExhaustionMasterTask.entitychunks<br>
 * Output: ObjectiveExhaustionMasterTask.found<br>
 * Cancellation: Self, on depletion
 *
 * Searches each chunk for entities with the goal objects
 *
 * @author kane
 */
public class WorkerEntitySearch extends BukkitRunnable
{
	public static final int MAX_PER_RUN = 4;
	ObjectiveExhaustionMasterTask master;

	public WorkerEntitySearch(ObjectiveExhaustionMasterTask task)
	{
		master = task;
	}

	@Override
	public void run()
	{
		// TODO Auto-generated method stub

	}
}
