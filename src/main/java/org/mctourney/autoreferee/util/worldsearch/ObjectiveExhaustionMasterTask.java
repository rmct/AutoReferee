package org.mctourney.autoreferee.util.worldsearch;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.regions.CuboidRegion;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;

public class ObjectiveExhaustionMasterTask implements Runnable
{
	public final AutoReferee plugin = AutoReferee.getInstance();
	public final AutoRefTeam team;

	// Safety strategy: Immutable set
	public final Set<BlockData> originalSearch;
	// Safety strategy: Copy on write
	public Set<BlockData> searching;
	// Safety strategy: Only read once
	public Map<BlockData, Vector> results = Maps.newHashMap();

	/**
	 * A stop-flag for the chunk snapshot worker threads.
	 */
	public volatile boolean all_snapshots_added;
	public ConcurrentLinkedQueue<Vector> entitychunks = Queues.newConcurrentLinkedQueue();
	public ConcurrentLinkedQueue<Vector> contchunks = Queues.newConcurrentLinkedQueue();
	public LinkedBlockingQueue<ChunkSnapshot> snapshots = Queues.newLinkedBlockingQueue();
	public ConcurrentLinkedQueue<_Entry<BlockData, Vector>> found = Queues.newConcurrentLinkedQueue();

	private WorkerEntitySearch entSearcher;
	private WorkerContainerSearch containerSearcher;
	private List<WorkerAsyncSearchSnapshots> searchers;
	private WorkerValidateResults resultChecker;

	public ObjectiveExhaustionMasterTask(AutoRefTeam team, Set<BlockData> goals)
	{
		this.team = team;
		this.searching = goals;
		originalSearch = ImmutableSet.copyOf(goals);
	}

	@Override
	// REMINDER: This is run async!
	public void run()
	{
		List<Vector> chunks = Lists.newArrayList(getChunkVectors());

		// Start entity searcher
		entitychunks.addAll(chunks);
		entSearcher = new WorkerEntitySearch(this);
		entSearcher.runTaskTimer(plugin, 0, 3);

		// Start container searcher
		contchunks.addAll(chunks);
		containerSearcher = new WorkerContainerSearch(this);
		containerSearcher.runTaskTimer(plugin, 1, 3);

		// Start up chunk snapshot searchers
		// TODO pick a count
		searchers = Lists.newLinkedList();
		WorkerAsyncSearchSnapshots tmp_searcher = new WorkerAsyncSearchSnapshots(this);
		tmp_searcher.runTaskAsynchronously(plugin);
		searchers.add(tmp_searcher);
		tmp_searcher = new WorkerAsyncSearchSnapshots(this);
		tmp_searcher.runTaskAsynchronously(plugin);
		searchers.add(tmp_searcher);
		tmp_searcher = new WorkerAsyncSearchSnapshots(this);
		tmp_searcher.runTaskAsynchronously(plugin);
		searchers.add(tmp_searcher);

		// Start result checker
		resultChecker = new WorkerValidateResults(this);
		resultChecker.runTaskTimer(plugin, 2, 3);

		final int _chunks_size = chunks.size();
		for (int i = 0; i < _chunks_size; i += 10)
		{
			if (checkComplete())
			{ cleanup(); return; }

			int max = Math.max(_chunks_size, i + 10);
			List<Vector> sublist = chunks.subList(i, max);
			Future<List<ChunkSnapshot>> future = Bukkit.getScheduler().callSyncMethod(plugin, new CallableGetSnapshots(sublist, team.getMatch().getWorld()));
			List<ChunkSnapshot> value;
			try
			{
				// Do the get inside the loop to rate-limit.
				value = future.get();
			}
			catch (ExecutionException e)
			{ e.printStackTrace(); quit(e.toString()); return; }
			catch (InterruptedException e)
			{ e.printStackTrace(); quit("thread interrupted"); return; }

			// Memory consistency: variable set happens-before adding of final snapshots
			if (i + 10 >= _chunks_size)
				all_snapshots_added = true;
			snapshots.addAll(value);
		}
		chunks = null; // drop
		snapshots.add(null); // poison-value, signal to stop trying to take

		while (true)
		{
			try
			{
				Thread.sleep(50);
			}
			catch (InterruptedException e) { }

			if (checkComplete())
			{
				cleanup();
				return;
			}
		}

	}

	private void cleanup()
	{
		// Cancel timer tasks first
		tryCancel(entSearcher);
		tryCancel(containerSearcher);
		tryCancel(resultChecker);

		// Prepare all the "done" signals
		all_snapshots_added = true;
		snapshots.clear();
		snapshots.add(null);
		for (WorkerAsyncSearchSnapshots searcher : searchers)
		{
			tryCancel(searcher);
		}
	}

	private void tryCancel(BukkitRunnable r)
	{
		try { r.cancel(); }
		catch (IllegalStateException e) { }
	}

	// unsafe
	private void quit(String message)
	{
		for (Player p : team.getMatch().getReferees())
			p.sendMessage(ChatColor.RED + "The exhaustion search for " + team.getDisplayName() + " was stopped: " + ChatColor.DARK_RED + message);
	}

	private Set<Vector> getChunkVectors()
	{
		Set<Vector> chunks = Sets.newHashSet();
		Set<AutoRefRegion> regions = team.getRegions();
		for (AutoRefRegion region : regions)
		{
			addChunks(chunks, region.getBoundingCuboid());
		}
		return chunks;
	}

	private void addChunks(Set<Vector> chunks, CuboidRegion bound)
	{
		int czmax = (int) Math.floor(bound.z2) >> 4;
		int czmin = (int) Math.floor(bound.z1) >> 4;
		int cxmax = (int) Math.floor(bound.x2) >> 4;
		for (int cx = (int) Math.floor(bound.x1) >> 4; cx < cxmax; cx++)
			for (int cz = czmin; cz < czmax; cz++)
				chunks.add(new Vector(cx, 0, cz));
	}

	private boolean checkComplete()
	{
		if (searching.isEmpty())
		{
			// Schedule announce, return true
			Bukkit.getScheduler().runTask(plugin, new Runnable() { public void run()
			{
				AutoRefMatch match = team.getMatch();
				World world = match.getWorld();
				StringBuilder sb = new StringBuilder();
				sb.append(ChatColor.GREEN + "Objective search for " + team.getDisplayName() + ChatColor.GREEN + " complete.");
				sb.append('\n');
				for (BlockData bd : originalSearch)
				{
					Location loc = results.get(bd).toLocation(world);
					sb.append(bd.getDisplayName() + ChatColor.GRAY + " is at " + ChatColor.RED + LocationUtil.toBlockCoords(loc));
					sb.append('\n');
				}
				String[] message = sb.toString().split("\n");
				for (Player p : match.getSpectators())
					p.sendMessage(message);
			} });
			return true;
		}
		return false;
	}
}
