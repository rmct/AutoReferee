package org.mctourney.autoreferee.event.team;

import org.mctourney.autoreferee.event.AutoRefereeEvent;
import org.mctourney.autoreferee.goals.AutoRefGoal;

public abstract class ObjectiveEvent extends AutoRefereeEvent
{
	private AutoRefGoal goal;

	public ObjectiveEvent(AutoRefGoal goal)
	{
		this.goal = goal;
	}

	public AutoRefGoal getGoal()
	{ return this.goal; }

	public Class<? extends AutoRefGoal> getGoalType()
	{ return this.goal.getClass(); }
}
