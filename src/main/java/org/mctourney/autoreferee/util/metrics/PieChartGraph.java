package org.mctourney.autoreferee.util.metrics;

import java.util.Map;
import java.util.Set;

import org.mcstats.Metrics;

import com.google.common.collect.Maps;

public class PieChartGraph
{
	private Map<String, IncrementPlotter> items;

	public PieChartGraph(Metrics.Graph graph, Set<String> choices)
	{
		this.items = Maps.newHashMap();
		for (String choice : choices)
		{
			IncrementPlotter plotter = new IncrementPlotter(choice);
			items.put(choice, plotter); graph.addPlotter(plotter);
		}

		IncrementPlotter other = new IncrementPlotter("Other");
		items.put(null, other); graph.addPlotter(other);
	}

	public void increment(String choice)
	{ items.get(items.containsKey(choice) ? choice : null).increment(); }
}
