package org.mctourney.autoreferee.util.worldsearch;

import java.util.Set;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.mctourney.autoreferee.util.BlockData;

import com.google.common.collect.Sets;

/**
 * Consumes: ObjectiveExhaustionMasterTask.found<br>
 * Output: ObjectiveExhaustionMasterTask.searching<br>
 * Output: ObjectiveExhaustionMasterTask.results<br>
 * Cancellation: Handled by master
 *
 * Check whether the team would be able to access each result. If the result is
 * accessible, it is removed from the search goals and announced.
 *
 * @author riking
 */
public class WorkerValidateResults extends BukkitRunnable
{
	private final ObjectiveExhaustionMasterTask master;

	public WorkerValidateResults(ObjectiveExhaustionMasterTask task)
	{
		master = task;
	}

	@Override
	public void run()
	{
		if (master.found.isEmpty()) return;

		_Entry<BlockData, Vector> entry;
		Set<BlockData> newSearch = Sets.newHashSet(master.searching);
		while ((entry = master.found.poll()) != null)
		{
			Vector vec = entry.getValue();
			if (master.team.canEnter(vec.toLocation(master.team.getMatch().getWorld())))
			{
				BlockData data = entry.getKey();
				newSearch.remove(data);
				// No safety - read-once
				master.results.put(data, vec);
			}
		}

		if (!newSearch.equals(master.searching))
		{
			// Safety: Copy-on-write
			master.searching = newSearch;
		}
	}
}
