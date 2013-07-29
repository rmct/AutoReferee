package org.mctourney.autoreferee.goals;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;

import org.jdom2.Element;

import com.google.common.collect.Maps;

public class ScoreDummyGoal extends ScoreGoal
{
	private static Map<String, ScoreDummyGoal> dummygoals = Maps.newHashMap();
	private final String goalname;

	public ScoreDummyGoal(AutoRefTeam team, String name)
	{
		super(team); this.goalname = name;
		dummygoals.put(name, this);
	}

	public ScoreDummyGoal(AutoRefTeam team, Element elt)
	{ this(team, elt.getAttributeValue("name")); }

	public ScoreDummyGoal(AutoRefTeam team, ScoreDummyGoal scoreDummyGoal)
	{ this(team, scoreDummyGoal.getName()); }

	private String getName()
	{ return goalname; }

	@Override
	public ScoreDummyGoal copy()
	{ return this.copy(this.owner); }

	@Override
	public ScoreDummyGoal copy(AutoRefTeam team)
	{ return new ScoreDummyGoal(team, this); }

	@Override
	public void updateReferee(Player ref)
	{
	}

	@Override
	public BlockData getItem()
	{ return null; }

	@Override
	public Location getTarget()
	{ return null; }

	@Override
	public String toString()
	{ return "DUMMY[" + this.goalname + "=" + this.getTargetScore() + "]"; }

	@Override
	public Element toElement()
	{
		Element elt = new Element("dummy");
		elt.setAttribute("name", this.goalname);
		return elt;
	}

	public static ScoreDummyGoal getDummyGoal(String name)
	{ return dummygoals.get(name); }
}
