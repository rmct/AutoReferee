package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.event.Event;

public abstract class PlayerEvent extends Event
{
	protected Player player;

	public PlayerEvent(Player player)
	{
		this.player = player;
	}

	public Player getPlayer()
	{ return player; }
}
