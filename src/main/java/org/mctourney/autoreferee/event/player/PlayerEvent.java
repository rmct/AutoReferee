package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.event.AutoRefereeEvent;

/**
 * Represents an event related to a Bukkit player.
 *
 * @author authorblues
 */
public abstract class PlayerEvent extends AutoRefereeEvent
{
	protected Player player;

	public PlayerEvent(Player player)
	{
		this.player = player;
	}

	/**
	 * Gets the player for this event.
	 * @return player
	 */
	public Player getPlayer()
	{ return player; }
}
