package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.event.Event;

public abstract class MatchEvent extends Event
{
	protected AutoRefMatch match;

	public MatchEvent(AutoRefMatch match)
	{
		this.match = match;
	}

	public AutoRefMatch getMatch()
	{ return match; }
}
