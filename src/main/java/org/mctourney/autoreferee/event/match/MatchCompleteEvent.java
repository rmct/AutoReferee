package org.mctourney.autoreferee.event.match;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;

/**
 * Called when a match is ending.
 *
 * @author authorblues
 */
public class MatchCompleteEvent extends MatchEvent implements Cancellable
{
	private static final HandlerList handlers = new HandlerList();
	private boolean cancelled = false;
	private AutoRefTeam winner = null;

	public MatchCompleteEvent(AutoRefMatch match, AutoRefTeam winner)
	{
		super(match);
		this.winner = winner;
	}

	/**
	 * Returns the winning team for this match.
	 * @return winning team, null if a tie or no winner
	 */
	public AutoRefTeam getWinner()
	{ return this.winner; }

	/**
	 * Sets the winning team for this match.
	 * @param winner winning team
	 */
	public void setWinner(AutoRefTeam winner)
	{ this.winner = winner; }

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
