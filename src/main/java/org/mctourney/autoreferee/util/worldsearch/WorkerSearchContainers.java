package org.mctourney.autoreferee.util.worldsearch;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Consumes: ObjectiveExhaustionMasterTask.foundContainers<br>
 * Output: ObjectiveExhaustionMasterTask.found<br>
 * Cancellation: Handled by master
 *
 * Checks containers for the goals, and adds the results to the found field.
 *
 * @author kane
 */
public class WorkerSearchContainers extends BukkitRunnable
{
	private ObjectiveExhaustionMasterTask master;
	private List<Vector> locations = new ArrayList<Vector>(20);

	public WorkerSearchContainers(ObjectiveExhaustionMasterTask task)
	{
		master = task;
	}

	@Override
	public void run()
	{
		// TODO Auto-generated method stub

	}

}
