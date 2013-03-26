package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

import org.mctourney.autoreferee.AutoRefMatch;

/**
 * Represents an event related to an {@link org.mctourney.autoreferee.AutoRefMatch}
 *
 * @author authorblues
 */
public abstract class PlayerMatchEvent extends PlayerEvent
{
	private AutoRefMatch match;

	public PlayerMatchEvent(Player player, AutoRefMatch match)
	{
		super(player);
		this.match = match;
	}

	/**
	 * Gets the match for this event.
	 * @return match
	 */
	public AutoRefMatch getMatch()
	{ return this.match; }
}
