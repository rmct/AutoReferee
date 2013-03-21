package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.event.Cancellable;

public abstract class PlayerMatchEvent extends PlayerEvent implements Cancellable
{
	private AutoRefMatch match;
	private boolean cancelled = false;

	public PlayerMatchEvent(Player player, AutoRefMatch match)
	{
		super(player);
		this.match = match;
	}

	public AutoRefMatch getMatch()
	{ return this.match; }

	@Override
	public boolean isCancelled()
	{ return this.cancelled; }

	@Override
	public void setCancelled(boolean cancel)
	{ this.cancelled = cancel; }
}
