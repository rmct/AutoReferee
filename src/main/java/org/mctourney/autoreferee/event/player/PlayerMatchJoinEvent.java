package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.AutoRefMatch;

public class PlayerMatchJoinEvent extends PlayerMatchEvent
{
	private boolean cancelled = false;

	public PlayerMatchJoinEvent(Player player, AutoRefMatch match)
	{
		super(player, match);
	}

	@Override
	public boolean isCancelled()
	{ return this.cancelled; }

	@Override
	public void setCancelled(boolean cancel)
	{ this.cancelled = cancel; }
}
