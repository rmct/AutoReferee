package org.mctourney.autoreferee.goals.scoreboard;

import java.util.Set;

import org.bukkit.scoreboard.Objective;

import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.goals.AutoRefGoal;
import org.mctourney.autoreferee.goals.BlockGoal;

import com.google.common.collect.Sets;

public class BlockObjective extends AutoRefObjective
{
	public BlockObjective(Objective objective, AutoRefTeam team, Set<BlockGoal> blockgoals)
	{
		super(objective, team, team.getName(), 0);
		this.goals.addAll(blockgoals);

		// update these objectives
		this.update();
	}

	@Override
	public void update()
	{
		int found = 0, placed = 0;
		for (AutoRefGoal goal : this.goals) switch (goal.getItemStatus())
		{
			case TARGET:
				placed++;

			case CARRYING:
			case SEEN:
				found++;

			default: break;
		}

		this.setName(String.format("%s", this.team.getName()));
	//	this.setName(String.format("%s (%d)", this.team.getName(), found));
		this.setValue(placed);
	}

	public static Set<AutoRefObjective> fromTeam(Objective objective, AutoRefTeam team)
	{
		Set<AutoRefObjective> objectives = Sets.newHashSet();

		Set<BlockGoal> blockgoals = team.getTeamGoals(BlockGoal.class);
		if (!blockgoals.isEmpty()) objectives.add(new BlockObjective(objective, team, blockgoals));

		return objectives;
	}
}
