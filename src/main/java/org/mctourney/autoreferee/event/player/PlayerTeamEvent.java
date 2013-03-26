package org.mctourney.autoreferee.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import org.mctourney.autoreferee.AutoRefTeam;

/**
 * Represents an event related to an {@link org.mctourney.autoreferee.AutoRefTeam}
 *
 * @author authorblues
 */
public abstract class PlayerTeamEvent extends PlayerEvent
{
	private static final HandlerList handlers = new HandlerList();
	private AutoRefTeam team;

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

	@Override
	public HandlerList getHandlers()
	{ return handlers; }
}
