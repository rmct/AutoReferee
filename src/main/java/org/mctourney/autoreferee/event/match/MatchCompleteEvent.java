package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.event.Cancellable;

public class MatchCompleteEvent extends MatchEvent implements Cancellable
{
	private boolean cancelled = false;
	private AutoRefTeam winner = null;

	public MatchCompleteEvent(AutoRefMatch match, AutoRefTeam winner)
	{
		super(match);
		this.winner = winner;
	}

	public AutoRefTeam getWinner()
	{ return this.winner; }

	public void setWinner(AutoRefTeam winner)
	{ this.winner = winner; }

	@Override
	public boolean isCancelled()
	{ return this.cancelled; }

	@Override
	public void setCancelled(boolean cancel)
	{ this.cancelled = cancel; }
}
