package org.mctourney.autoreferee.event.match;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.mctourney.autoreferee.AutoRefMatch;

/**
 * Called when a map is unloaded from the server.
 *
 * @author authorblues
 */
public class MatchUnloadEvent extends MatchEvent implements Cancellable
{
	public static enum Reason
	{
		COMMAND,
		COMPLETE,
		EMPTY,
		UNKNOWN;
	}

	private static final HandlerList handlers = new HandlerList();
	private boolean cancelled = false;
	private Reason reason = Reason.UNKNOWN;

	@Deprecated
	public MatchUnloadEvent(AutoRefMatch match)
	{ this(match, Reason.UNKNOWN); }

	public MatchUnloadEvent(AutoRefMatch match, Reason reason)
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
