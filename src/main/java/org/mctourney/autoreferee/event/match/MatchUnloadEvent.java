package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.event.Cancellable;

public class MatchUnloadEvent extends MatchEvent implements Cancellable
{
	private boolean cancelled = false;

	public MatchUnloadEvent(AutoRefMatch match)
	{
		super(match);
	}

	@Override
	public boolean isCancelled()
	{ return this.cancelled; }

	@Override
	public void setCancelled(boolean cancel)
	{ this.cancelled = cancel; }
}
