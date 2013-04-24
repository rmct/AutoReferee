package org.mctourney.autoreferee.event.match;

import org.bukkit.event.HandlerList;

import org.mctourney.autoreferee.AutoRefMatch;

/**
 * Called when a new map is loaded.
 *
 * @author authorblues
 */
public class MatchLoadEvent extends MatchEvent
{
	private static final HandlerList handlers = new HandlerList();

	public MatchLoadEvent(AutoRefMatch match)
	{
		super(match);
	}

	@Override
	public HandlerList getHandlers()
	{ return handlers; }

	public static HandlerList getHandlerList()
	{ return handlers; }
}
