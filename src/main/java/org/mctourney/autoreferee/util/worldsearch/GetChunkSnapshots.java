package org.mctourney.autoreferee.util.worldsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.mctourney.autoreferee.AutoRefMatch;

public class GetChunkSnapshots implements Callable<List<ChunkSnapshot>>
{
	List<Vector> coords;
	AutoRefMatch match;
	public GetChunkSnapshots(List<Vector> coords, AutoRefMatch match) {
		this.coords = coords;
		this.match = match;
	}

	@Override
	public List<ChunkSnapshot> call() throws Exception
	{
		World world = match.getWorld();
		List<ChunkSnapshot> ret = new ArrayList<ChunkSnapshot>();
		for (Vector v : coords) {
			ret.add(world.getChunkAt(v.getBlockX(), v.getBlockZ()).getChunkSnapshot());
		}
		return ret;
	}

}
