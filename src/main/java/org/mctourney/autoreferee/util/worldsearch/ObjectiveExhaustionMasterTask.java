package org.mctourney.autoreferee.util.worldsearch;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
import com.google.common.collect.Sets;

public class ObjectiveExhaustionMasterTask implements Runnable
{
	AutoReferee plugin = AutoReferee.getInstance();
	final AutoRefTeam team;
	final Set<BlockData> originalSearch;
	private Set<BlockData> searching;
	private Map<BlockData, Vector> results = Maps.newHashMap();

	public ObjectiveExhaustionMasterTask(AutoRefTeam team, Set<BlockData> goals)
	{
		this.team = team;
		this.searching = goals;
		originalSearch = ImmutableSet.copyOf(goals);
	}

	@Override
	// REMINDER: This is run async!
	// There are some thread-safety breaks, however, in the interest of efficency.
	// For example, team.getRegions() isn't factually thread-safe, but we treat it that way (and it really is close enough).
	public void run()
	{
		List<Vector> chunks = Lists.newArrayList(getChunkVectors());
		List<SearchChunkSnapshot> searches = Lists.newLinkedList();
		Set<Vector> chests = Sets.newHashSet();

		final int _chunks_size = chunks.size();
		for (int i = 0; i < _chunks_size; i += 10)
		{
			checkSearches(searches, chests);
			if (checkComplete())
			{
				for (SearchChunkSnapshot s : searches)
					s.cancel(true);
				return;
			}

			int max = Math.max(_chunks_size, i + 10);
			List<Vector> sublist = chunks.subList(i, max);
			Future<List<ChunkSnapshot>> future = Bukkit.getScheduler().callSyncMethod(plugin, new GetChunkSnapshots(sublist, team.getMatch()));
			List<ChunkSnapshot> value;
			try
			{
				// Doing the get inside the loop rate-limits our search.
				value = future.get();
			}
			catch (ExecutionException e)
			{ e.printStackTrace(); quit(e.toString()); return; }
			catch (InterruptedException e)
			{ e.printStackTrace(); quit("thread interrupted"); return; }

			SearchChunkSnapshot search = new SearchChunkSnapshot(value, searching, team);
			searches.add(search);
			Bukkit.getScheduler().runTaskAsynchronously(plugin, search);
		}
		chunks = null; // drop

		// Wait for async searches to complete
		int i = 1;
		while (!searches.isEmpty())
		{
			try
			{ Thread.sleep(++i);}
			catch (InterruptedException e)
			{ e.printStackTrace(); quit("thread interrupted"); return; }

			checkSearches(searches, chests);
		}

		if (checkComplete())
			return;

		searches = null;
		// Start searching chests.
		SearchChests chestSearch = new SearchChests(chests, team, searching);
		Bukkit.getScheduler().runTaskTimer(plugin, chestSearch, 0, 1);

		while (!chestSearch.isDone())
		{
			try
			{ Thread.sleep(49);}
			catch (InterruptedException e)
			{ e.printStackTrace(); quit("thread interrupted"); return; }
		}
		Map<BlockData, Vector> blocks = chestSearch.getResults();
		results.putAll(blocks);
		searching.removeAll(blocks.keySet());
	}

	// unsafe
	private void quit() {
		for (Player p : team.getMatch().getReferees())
			p.sendMessage(ChatColor.RED + "The exhaustion search for " + team.getDisplayName() + " was " + ChatColor.DARK_RED + "aborted.");
	}
	// unsafe
	private void quit(String message) {
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

	private void checkSearches(List<SearchChunkSnapshot> searches, Set<Vector> chests)
	{
		Iterator<SearchChunkSnapshot> iter = searches.iterator();
		while (iter.hasNext())
		{
			SearchChunkSnapshot search = iter.next();
			// note: isReady() is synchronized, so the results should be updated after this
			if (!search.isReady()) continue;

			if (search.foundBlocks())
			{
				Map<BlockData, Vector> blocks = search.getBlockResults();
				results.putAll(blocks);
				searching.removeAll(blocks.keySet());
			}
			if (search.foundContainers())
			{
				chests.addAll(search.getContainerResults());
			}
			iter.remove();
		}
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
