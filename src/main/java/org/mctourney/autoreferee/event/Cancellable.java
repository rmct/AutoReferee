package org.mctourney.autoreferee.event;

/**
 * Represents an event whose callback may preclude additional actions from being taken.
 */
public abstract interface Cancellable
{
	/**
	 * Checks the cancelled state of the event.
	 * @return true if the event has been cancelled, false otherwise
	 */
	public boolean isCancelled();

	/**
	 * Sets the cancelled state of the event.
	 * @param cancel true to cancel the event, false to un-cancel the event
	 */
	public void setCancelled(boolean cancel);
}
