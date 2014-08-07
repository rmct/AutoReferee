package org.mctourney.autoreferee.util.worldsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.util.Vector;

public class CallableGetSnapshots implements Callable<List<ChunkSnapshot>>
{
	private List<Vector> coords;
	private World world;

	public CallableGetSnapshots(List<Vector> coords, World world)
	{
		this.coords = coords;
		this.world = world;
	}

	@Override
	public List<ChunkSnapshot> call() throws Exception
	{
		List<ChunkSnapshot> ret = new ArrayList<ChunkSnapshot>();
		for (Vector v : coords)
			ret.add(world.getChunkAt(v.getBlockX(), v.getBlockZ()).getChunkSnapshot());
		return ret;
	}
}
