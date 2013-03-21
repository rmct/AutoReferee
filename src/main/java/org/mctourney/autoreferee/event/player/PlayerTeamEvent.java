package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.event.Cancellable;

/**
 * Represents an event related to an {@link org.mctourney.autoreferee.AutoRefTeam}
 *
 * @author authorblues
 */
public abstract class PlayerTeamEvent extends PlayerEvent
{
	private AutoRefTeam team;
	private boolean cancelled = false;

	public PlayerTeamEvent(Player player, AutoRefTeam team)
	{
		super(player);
		this.team = team;
	}

	/**
	 * Gets the team for this event.
	 * @return team
	 */
	public AutoRefTeam getTeam()
	{ return this.team; }
}
