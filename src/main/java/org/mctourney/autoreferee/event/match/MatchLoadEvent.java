package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;

public class MatchLoadEvent extends MatchEvent
{
	public MatchLoadEvent(AutoRefMatch match)
	{
		super(match);
	}
}
