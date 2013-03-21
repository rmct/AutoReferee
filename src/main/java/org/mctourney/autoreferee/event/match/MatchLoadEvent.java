package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;

/**
 * Called when a new map is loaded.
 *
 * @author authorblues
 */
public class MatchLoadEvent extends MatchEvent
{
	public MatchLoadEvent(AutoRefMatch match)
	{
		super(match);
	}
}
