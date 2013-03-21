package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.event.AutoRefereeEvent;

/**
 * Represents an event related to an {@link org.mctourney.autoreferee.AutoRefMatch}
 *
 * @author authorblues
 */
public abstract class MatchEvent extends AutoRefereeEvent
{
	protected AutoRefMatch match;

	public MatchEvent(AutoRefMatch match)
	{
		this.match = match;
	}

	/**
	 * Gets the match for this event.
	 * @return match
	 */
	public AutoRefMatch getMatch()
	{ return match; }
}
