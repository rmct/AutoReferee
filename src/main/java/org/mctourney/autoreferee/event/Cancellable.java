package org.mctourney.autoreferee.event;

public abstract interface Cancellable
{
	public boolean isCancelled();

	public void setCancelled(boolean cancel);
}
