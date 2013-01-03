package org.mctourney.AutoReferee.util.metrics;

import org.mcstats.Metrics;
import org.mctourney.AutoReferee.AutoReferee;

/**
 * Represents an incremental tracker for discrete events.
 *
 * @author mbaxter
 */
public class IncrementPlotter extends Metrics.Plotter
{
	private final String name;
	private int value, last;

	public IncrementPlotter(String name)
	{
		this.name = name;
		this.value = this.last = 0;
	}

	@Override
	public String getColumnName()
	{ return this.name; }

	@Override
	public int getValue()
	{ AutoReferee.getInstance().getLogger().info("Sending " + this.value + " for " + this.name); return this.last = this.value; }

	public void increment()
	{ ++this.value; }

	@Override
	public void reset()
	{ this.value -= this.last; }
}