package org.mctourney.AutoReferee.util;

import java.util.Random;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

public class NullChunkGenerator extends ChunkGenerator
{
	private static final int WORLD_HEIGHT = 256;

	@Override
	public byte[][] generateBlockSections(World world, Random r,
		int x, int z, ChunkGenerator.BiomeGrid biomes)
	{ return new byte[WORLD_HEIGHT / 16][]; }
}
