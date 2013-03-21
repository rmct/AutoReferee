package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.event.AutoRefereeEvent;

public abstract class PlayerEvent extends AutoRefereeEvent
{
	protected Player player;

	public PlayerEvent(Player player)
	{
		this.player = player;
	}

	public Player getPlayer()
	{ return player; }
}
