package org.mctourney.autoreferee.goals;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;

public class ScoreDeathmatchGoal extends ScoreGoal
{
	public ScoreDeathmatchGoal(AutoRefTeam team)
	{
		super(team);
	}

	public ScoreDeathmatchGoal(AutoRefTeam team, Element elt)
	{
		super(team);
	}

	public double getScore(AutoRefMatch match)
	{
		double score = 0.0;
		for (AutoRefPlayer apl : this.getOwner().getPlayers())
			score += apl.getKills();
		return score;
	}

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
	public Element toElement()
	{
		Element elt = new Element("deathmatch");
		return elt;
	}
}
