package org.mctourney.autoreferee.goals;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;

public abstract class ScoreGoal extends AutoRefGoal
{
	protected Double targetScore = null;

	public ScoreGoal(AutoRefTeam team)
	{
		super(team);
	}

	public void setTargetScore(Double target)
	{ this.targetScore = target; }

	public Double getTargetScore()
	{ return this.targetScore; }

	public boolean isSatisfied(AutoRefMatch match)
	{ return targetScore != null && this.getScore(match) >= this.getTargetScore(); }

	@Override
	protected AutoRefGoal getGoalSettings(AutoRefTeam team, Element elt)
	{
		if (elt.getAttribute("target") != null)
			try { this.setTargetScore(Double.parseDouble(elt.getAttributeValue("target"))); }
			catch (NumberFormatException e) { e.printStackTrace(); }

		return super.getGoalSettings(team, elt);
	}
}
