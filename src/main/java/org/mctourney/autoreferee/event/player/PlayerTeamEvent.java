package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.event.Cancellable;

public abstract class PlayerTeamEvent extends PlayerEvent implements Cancellable
{
	private AutoRefTeam team;
	private boolean cancelled = false;

	public PlayerTeamEvent(Player player, AutoRefTeam team)
	{
		super(player);
		this.team = team;
	}

	public AutoRefTeam getTeam()
	{ return this.team; }

	@Override
	public boolean isCancelled()
	{ return this.cancelled; }

	@Override
	public void setCancelled(boolean cancel)
	{ this.cancelled = cancel; }
}
