package org.mctourney.autoreferee;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.bukkit.World;

import org.mctourney.autoreferee.goals.AutoRefGoal;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class FreeForAllMatch extends AutoRefMatch
{
	Set<AutoRefGoal> allgoals = Sets.newHashSet();

	protected int maxsize = 1;

	public FreeForAllMatch(World world, boolean tmp, MatchStatus state)
	{
		super(world, tmp, state);
	}

	public FreeForAllMatch(World world, boolean tmp)
	{
		super(world, tmp);
	}

	@Override
	public Set<AutoRefTeam> getTeams()
	{
		// TODO Auto-generated method stub
		return super.getTeams();
	}

	@Override
	public String getTeamList()
	{
		List<AutoRefTeam> orderedTeams = Lists.newArrayList(getTeams());
		Collections.sort(orderedTeams, new Comparator<AutoRefTeam>()
		{
			@Override public int compare(AutoRefTeam a, AutoRefTeam b)
			{ return a.getName().compareToIgnoreCase(b.getName()); }
		});

		List<String> tlist = Lists.newArrayListWithCapacity(orderedTeams.size());
		for (AutoRefTeam team : orderedTeams) tlist.add(team.getDisplayName());
		return StringUtils.join(tlist, ", ");
	}

	@Override
	public AutoRefTeam getArbitraryTeam()
	{
		// attempt to make use of a team that has aleady been created first
		AutoRefTeam team = super.getArbitraryTeam();
		if (team != null && team.getPlayers().size() < team.getMaxSize()) return team;

		// no free team? let's make our own
		team = new FreeForAllTeam();
		for (AutoRefGoal goal : allgoals) team.addGoal(goal);
		return team;
	}

	@Override
	public AutoRefTeam getTeam(String name)
	{
		// try to get the team with the given name
		AutoRefTeam team = super.getTeam(name);
		if (team != null) return team;

		// maybe try treating the parameter as a player's name
		AutoRefPlayer apl = this.getPlayer(name);
		return apl.getTeam();
	}
}
