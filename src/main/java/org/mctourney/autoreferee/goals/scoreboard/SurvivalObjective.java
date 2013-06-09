package org.mctourney.autoreferee.goals.scoreboard;

import java.util.Set;

import org.bukkit.scoreboard.Objective;
import org.mctourney.autoreferee.AutoRefMatch.RespawnMode;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.goals.SurvivalGoal;

import com.google.common.collect.Sets;

public class SurvivalObjective extends AutoRefObjective
{
	public SurvivalObjective(Objective objective, AutoRefTeam team)
	{
		super(objective, team, team.getName(), 0);
		this.update();
	}

	@Override
	public void update()
	{
		this.setValue(this.team.getPlayers().size());
	}

	public static Set<AutoRefObjective> fromTeam(Objective objective, AutoRefTeam team)
	{
		Set<AutoRefObjective> objectives = Sets.newHashSet();

		if (team.getMatch().getRespawnMode() != RespawnMode.ALLOW &&
			!team.getTeamGoals(SurvivalGoal.class).isEmpty())
			objectives.add(new SurvivalObjective(objective, team));

		return objectives;
	}
}
