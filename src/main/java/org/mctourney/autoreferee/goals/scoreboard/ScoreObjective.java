package org.mctourney.autoreferee.goals.scoreboard;

import java.util.Set;

import org.bukkit.scoreboard.Objective;

import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.goals.AutoRefGoal;
import org.mctourney.autoreferee.goals.ScoreGoal;

import com.google.common.collect.Sets;

public class ScoreObjective extends AutoRefObjective
{
	public ScoreObjective(Objective objective, AutoRefTeam team, Set<ScoreGoal> scoregoals)
	{
		super(objective, team, team.getName(), 0);
		this.goals.addAll(scoregoals);

		// update these objectives
		this.update();
	}

	@Override
	public void update()
	{
		double totalscore = 0;
		for (AutoRefGoal goal : this.goals)
			totalscore += goal.getScore(this.team.getMatch());

		this.setName(String.format("%s", this.team.getName()));
		this.setValue((int) totalscore);
	}

	public static Set<AutoRefObjective> fromTeam(Objective objective, AutoRefTeam team)
	{
		Set<AutoRefObjective> objectives = Sets.newHashSet();

		Set<ScoreGoal> scoregoals = team.getTeamGoals(ScoreGoal.class);
		if (!scoregoals.isEmpty()) objectives.add(new ScoreObjective(objective, team, scoregoals));

		return objectives;
	}
}
