package org.mctourney.autoreferee;

import java.util.Set;

import org.bukkit.entity.Player;

import org.mctourney.autoreferee.regions.AutoRefRegion;

public class FreeForAllTeam extends AutoRefTeam
{
	public FreeForAllTeam()
	{
		super();

		this.maxsize = 1;
		this.minsize = 1;
	}

	public FreeForAllTeam(int maxsize)
	{ this(); this.maxsize = maxsize; }

	@Override
	public Set<AutoRefRegion> getRegions()
	{ return match.getRegions(); }

	@Override
	public boolean leaveQuietly(Player player)
	{
		// remove the player as usual
		if (!super.leaveQuietly(player)) return false;

		// if this team is now empty, and the match hasn't started, destroy the team
		if (this.isEmptyTeam() && getMatch().getCurrentState().isBeforeMatch())
			getMatch().teams.remove(this);

		return true;
	}
}
