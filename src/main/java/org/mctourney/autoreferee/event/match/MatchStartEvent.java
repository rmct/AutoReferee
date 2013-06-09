package org.mctourney.autoreferee.event.match;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.mctourney.autoreferee.AutoRefMatch;

/**
 * Called when a match is starting.
 *
 * @author authorblues
 */
public class MatchStartEvent extends MatchEvent implements Cancellable
{
	public static enum Reason
	{
		READY,
		AUTOMATIC,
		UNKNOWN;
	}

	private static final HandlerList handlers = new HandlerList();
	private boolean cancelled = false;
	private Reason reason = Reason.UNKNOWN;

	@Deprecated
	public MatchStartEvent(AutoRefMatch match)
	{ this(match, Reason.UNKNOWN); }

	public MatchStartEvent(AutoRefMatch match, Reason reason)
	{
		super(match);
		this.reason = reason;
	}

	public Reason getReason()
	{ return this.reason; }

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

	@Override
	public HandlerList getHandlers()
	{ return handlers; }

	public static HandlerList getHandlerList()
	{ return handlers; }
}
