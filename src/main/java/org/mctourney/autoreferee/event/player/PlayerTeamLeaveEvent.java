package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.event.Cancellable;

/**
 * Called when a player leaves a team.
 *
 * @author authorblues
 */
public class PlayerTeamLeaveEvent extends PlayerTeamEvent implements Cancellable
{
	private boolean cancelled = false;

	public PlayerTeamLeaveEvent(Player player, AutoRefTeam team)
	{
		super(player, team);
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
