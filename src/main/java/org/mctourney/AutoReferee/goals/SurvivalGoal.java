package org.mctourney.AutoReferee.goals;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.util.BlockData;

public class SurvivalGoal extends AutoRefGoal
{
	public SurvivalGoal(AutoRefTeam team, Element elt)
	{ super(team); }

	public Element toElement()
	{ return new Element("survive"); }

	@Override
	public String toString()
	{ return "SURVIVE"; }

	@Override
	public boolean isSatisfied(AutoRefMatch match)
	{
		for (AutoRefTeam team : match.getTeams())
			if (team != this.getOwner())
				if (team.getPlayers().size() > 0) return false;
		return true;
	}

	@Override
	public void updateReferee(Player ref) {  }

	@Override
	public BlockData getItem() { return null; }

	@Override
	public Location getTarget() { return null; }
}
