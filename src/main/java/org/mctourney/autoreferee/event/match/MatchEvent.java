package org.mctourney.autoreferee.event.match;

import org.bukkit.event.world.WorldEvent;
import org.mctourney.autoreferee.AutoRefMatch;

/**
 * Represents an event related to an {@link org.mctourney.autoreferee.AutoRefMatch}
 *
 * @author authorblues
 */
public abstract class MatchEvent extends WorldEvent
{
	protected AutoRefMatch match;

	public MatchEvent(AutoRefMatch match)
	{
		super(match.getWorld());
		this.match = match;
	}

	/**
	 * Gets the match for this event.
	 * @return match
	 */
	public AutoRefMatch getMatch()
	{ return match; }
}
