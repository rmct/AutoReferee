package org.mctourney.autoreferee.goals;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;

public class SurvivalGoal extends AutoRefGoal
{
	public SurvivalGoal(AutoRefTeam team)
	{ super(team); }

	private SurvivalGoal(AutoRefTeam team, SurvivalGoal goal)
	{ this(team); }

	public SurvivalGoal(AutoRefTeam team, Element elt)
	{ super(team); }

	@Override
	public SurvivalGoal copy()
	{ return this.copy(this.owner); }

	@Override
	public SurvivalGoal copy(AutoRefTeam team)
	{ return new SurvivalGoal(team, this); }

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
				if (hasSurvived(team)) return false;
		return true;
	}

	public static boolean hasSurvived(AutoRefTeam team)
	{
		boolean practice = team.getMatch().isPracticeMode();
		return !team.getPlayers().isEmpty()
			|| (practice && team.getCachedPlayers().isEmpty());
	}

	@Override
	public boolean canBeCompleted(AutoRefMatch match)
	{ return SurvivalGoal.hasSurvived(this.getOwner()); }

	@Override
	public double getScore(AutoRefMatch match)
	{ return this.getOwner().getPlayers().size() * 250.0; }

	@Override
	public void updateReferee(Player ref)
	{
		AutoRefMatch.messageReferee(ref, "team", getOwner().getName(),
			"goal", "survive", Integer.toString(getOwner().getPlayers().size()));
	}

	@Override
	public BlockData getItem() { return null; }

	@Override
	public Location getTarget() { return null; }
}
