package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.event.Cancellable;

/**
 * Called when a player joins a match.
 *
 * @author authorblues
 */
public class PlayerMatchJoinEvent extends PlayerMatchEvent implements Cancellable
{
	private boolean cancelled = false;

	public PlayerMatchJoinEvent(Player player, AutoRefMatch match)
	{
		super(player, match);
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
