package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import org.mctourney.autoreferee.AutoRefTeam;

/**
 * Called when a player joins a team.
 *
 * @author authorblues
 */
public class PlayerTeamJoinEvent extends PlayerTeamEvent implements Cancellable
{
	public static enum Reason
	{
		MANUAL,
		AUTOMATIC,
		EXPECTED,
		UNKNOWN;
	}

	private static final HandlerList handlers = new HandlerList();
	private boolean cancelled = false;
	private Reason reason = Reason.UNKNOWN;

	@Deprecated
	public PlayerTeamJoinEvent(Player player, AutoRefTeam team)
	{ this(player, team, Reason.UNKNOWN); }

	public PlayerTeamJoinEvent(Player player, AutoRefTeam team, Reason reason)
	{
		super(player, team);
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
