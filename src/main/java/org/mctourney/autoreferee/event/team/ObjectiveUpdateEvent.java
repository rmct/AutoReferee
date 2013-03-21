package org.mctourney.autoreferee.event.team;

import org.mctourney.autoreferee.goals.AutoRefGoal;

public class ObjectiveUpdateEvent extends ObjectiveEvent
{
	public ObjectiveUpdateEvent(AutoRefGoal goal)
	{
		super(goal);
	}
}
