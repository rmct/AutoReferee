package org.mctourney.autoreferee.event.team;

import org.bukkit.event.HandlerList;

import org.mctourney.autoreferee.goals.AutoRefGoal;

public class ObjectiveUpdateEvent extends ObjectiveEvent
{
	private static final HandlerList handlers = new HandlerList();

	public ObjectiveUpdateEvent(AutoRefGoal goal)
	{
		super(goal);
	}

	@Override
	public HandlerList getHandlers()
	{ return handlers; }

	public static HandlerList getHandlerList()
	{ return handlers; }
}
