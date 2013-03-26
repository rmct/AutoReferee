package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.mctourney.autoreferee.AutoRefMatch;

/**
 * Called when a player leaves a match.
 *
 * @author authorblues
 */
public class PlayerMatchLeaveEvent extends PlayerMatchEvent implements Cancellable
{
	private static final HandlerList handlers = new HandlerList();
	private boolean cancelled = false;

	public PlayerMatchLeaveEvent(Player player, AutoRefMatch match)
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

	@Override
	public HandlerList getHandlers()
	{ return handlers; }
}
