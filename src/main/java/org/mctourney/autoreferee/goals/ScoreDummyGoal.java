package org.mctourney.autoreferee.goals;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;

import org.jdom2.Element;

public class ScoreDummyGoal extends ScoreGoal
{
	private final String goalname;
	private double score = 0.0;

	public ScoreDummyGoal(AutoRefTeam team, String name)
	{
		super(team); this.goalname = name;
	}

	public ScoreDummyGoal(AutoRefTeam team, Element elt)
	{ this(team, elt.getAttributeValue("name")); }

	public ScoreDummyGoal(AutoRefTeam team, ScoreDummyGoal scoreDummyGoal)
	{ this(team, scoreDummyGoal.getName()); }

	public String getName()
	{ return goalname; }

	public double getScore(AutoRefMatch match)
	{ return this.score; }

	public void setScore(double score)
	{ this.score = score; }

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
}
