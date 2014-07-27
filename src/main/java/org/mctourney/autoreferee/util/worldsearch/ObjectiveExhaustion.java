package org.mctourney.autoreferee.util.worldsearch;

import java.util.Set;

import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.goals.AutoRefGoal;
import org.mctourney.autoreferee.goals.AutoRefGoal.ItemStatus;
import org.mctourney.autoreferee.util.BlockData;

import com.google.common.collect.Sets;

public class ObjectiveExhaustion
{
	public static Set<BlockData> startSearch(AutoRefTeam team, AutoReferee plugin)
	{
		Set<BlockData> goals = Sets.newHashSet();

		for (AutoRefGoal goal : team.getTeamGoals())
		{
			if (!goal.hasItem()) continue;
			ItemStatus is = goal.getItemStatus();
			if (is == ItemStatus.TARGET || is == ItemStatus.CARRYING) continue;
			goals.add(goal.getItem());
		}
		if (goals.isEmpty()) return null;

		for (AutoRefPlayer player : team.getPlayers())
			goals.removeAll(player.getCarrying().keySet());

		if (goals.isEmpty()) return null;

		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new ObjectiveExhaustionMasterTask(team, goals));
		return goals;
	}
}
