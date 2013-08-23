package org.mctourney.autoreferee.util.worldsearch;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChunkSnapshot;
import org.bukkit.util.Vector;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class SearchChunkSnapshot implements Runnable
{
	private boolean complete = false;
	private volatile boolean cancel = false;
	private Map<BlockData, Vector> foundBlocks = Maps.newHashMap();
	private Set<Vector> foundChests = Sets.newHashSet();

	private List<ChunkSnapshot> snapshots;
	private Set<BlockData> search;
	private AutoRefTeam team;

	public SearchChunkSnapshot(List<ChunkSnapshot> value, Set<BlockData> searching, AutoRefTeam team)
	{
		this.snapshots = value;
		// copy the set<blockdata> to guard against mods
		this.search = ImmutableSet.copyOf(searching);
		this.team = team;
	}

	public void run()
	{
		try {
			a;
		}
		catch (Throwable ignored)
		{}

		synchronized (this)
		{ complete = true; }
	}

	public synchronized boolean isReady()
	{
		return complete;
	}

	public void cancel(boolean cancelled)
	{ this.cancel = cancelled; }

	public boolean foundBlocks()
	{
		return foundBlocks != null && !foundBlocks.isEmpty();
	}
	public Map<BlockData, Vector> getBlockResults()
	{
		return foundBlocks;
	}

	public boolean foundContainers()
	{
		return foundChests != null && !foundChests.isEmpty();
	}
	public Set<Vector> getContainerResults() {
		return foundChests;
	}
}
