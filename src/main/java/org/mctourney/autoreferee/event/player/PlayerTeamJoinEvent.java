package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.AutoRefTeam;

public class PlayerTeamJoinEvent extends PlayerTeamEvent
{
	private boolean cancelled = false;

	public PlayerTeamJoinEvent(Player player, AutoRefTeam team)
	{
		super(player, team);
	}

	@Override
	public boolean isCancelled()
	{ return this.cancelled; }

	@Override
	public void setCancelled(boolean cancel)
	{ this.cancelled = cancel; }
}
