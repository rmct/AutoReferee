package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.event.Cancellable;

/**
 * Called when a match is starting.
 *
 * @author authorblues
 */
public class MatchStartEvent extends MatchEvent implements Cancellable
{
	private boolean cancelled = false;

	public MatchStartEvent(AutoRefMatch match)
	{
		super(match);
	}

	/**
	 * Checks the cancelled state of the event.
	 * @return true if the event has been cancelled, false otherwise
	 */
	public boolean isCancelled()
	{ return this.cancelled; }

	/**
	 * Sets the cancelled state of the event.
	 * @param cancel true to cancel the event, false to uncancel the event
	 */
	public void setCancelled(boolean cancel)
	{ this.cancelled = cancel; }
}
