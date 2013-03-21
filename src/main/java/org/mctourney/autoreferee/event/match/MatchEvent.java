package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.event.AutoRefereeEvent;

public abstract class MatchEvent extends AutoRefereeEvent
{
	protected AutoRefMatch match;

	public MatchEvent(AutoRefMatch match)
	{
		this.match = match;
	}

	public AutoRefMatch getMatch()
	{ return match; }
}
