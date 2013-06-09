package org.mctourney.autoreferee.goals.scoreboard;

import java.util.Collections;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.scoreboard.Objective;

import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.goals.AutoRefGoal;

import com.google.common.collect.Sets;

public abstract class AutoRefObjective
{
	protected Objective objective;
	protected OfflinePlayer title;

	protected AutoRefTeam team;
	protected Set<AutoRefGoal> goals;

	protected ChatColor color = null;
	protected String name;
	protected int value = 0;

	public AutoRefObjective(Objective objective, AutoRefTeam team, String name, int value, ChatColor color)
	{
		// reference to the actual scoreboard objective where we drop our entries
		assert objective != null : "Objective cannot be null";
		this.objective = objective;

		// save the owning team
		this.team = team;
		this.color = color;
		this.goals = Sets.newHashSet();

		// objective name and value
		this.setName(name);
		this.setValue(value);
	}

	public AutoRefObjective(Objective objective, AutoRefTeam team, String name, int value)
	{ this(objective, team, name, value, team.getColor()); }

	public abstract void update();

	public void setName(String name)
	{
		if (this.title != null)
			this.objective.getScoreboard().resetScores(this.title);
		this.name = name;

		String clr = this.color == null || name.length() > 14
			? "" : this.color.toString();
		this.title = Bukkit.getOfflinePlayer(clr + name);
		this.objective.getScore(this.title).setScore(this.value);
	}

	public String getName()
	{ return this.name; }

	public void setValue(int value)
	{
		this.value = value;
		this.objective.getScore(this.title).setScore(this.value);
	}

	public int getValue()
	{ return this.value; }

	public void setColor(ChatColor color)
	{
		if (color == ChatColor.RESET) color = null;
		this.color = color; this.setName(this.getName());
	}

	public ChatColor getColor()
	{ return this.color; }

	public Set<AutoRefGoal> getGoals()
	{ return Collections.unmodifiableSet(goals); }

	@Override
	public String toString()
	{ return String.format("%s[%s=%d]", this.getClass().getSimpleName(), this.getName(), this.getValue()); }

	public static Set<AutoRefObjective> fromTeam(Objective objective, AutoRefTeam team)
	{
		Set<AutoRefObjective> objectives = Sets.newHashSet();

		objectives.addAll(BlockObjective.fromTeam(objective, team));
		objectives.addAll(SurvivalObjective.fromTeam(objective, team));

		return objectives;
	}
}
