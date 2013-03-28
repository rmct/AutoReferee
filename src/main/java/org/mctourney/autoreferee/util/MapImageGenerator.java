package org.mctourney.autoreferee.util;

import java.awt.image.RenderedImage;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import com.google.common.collect.Maps;
import com.google.common.io.LineReader;

import org.mctourney.autoreferee.AutoReferee;

public class MapImageGenerator
{
	private static Map<BlockData, int[]> blockColors;
	static
	{
		blockColors = Maps.newHashMap();
		LineReader blocks = new LineReader(new InputStreamReader(
			AutoReferee.getInstance().getResource("colors.csv")));

		try
		{
			String line; while ((line = blocks.readLine()) != null)
			{
				String[] parts = line.split("\\s+");
				if (parts.length == 5) try
				{
					BlockData blockdata = new BlockData(Material.getMaterial(Integer.parseInt(parts[0])),
						Integer.valueOf(parts[1]).byteValue());
					blockColors.put(blockdata, new int[]{ Integer.parseInt(parts[2]),
						Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), 255 });
				}
				catch (NumberFormatException e)
				{ e.printStackTrace(); }
			}
		} catch (IOException e)
		{ e.printStackTrace(); }
	}

	public static RenderedImage generateFromWorld(World world, int min_x, int max_x, int min_z, int max_z)
	{
		int w = max_x - min_x + 1;
		int h = max_z - min_z + 1;

		BufferedImage buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		WritableRaster raster = buffer.getRaster();

		for (int x = 0; x < w; ++x)
		for (int z = 0; z < h; ++z)
			raster.setPixel(x, z, getPixelAt(world, min_x + x, min_z + z));

		return buffer;
	}

	private static int[] getPixelAt(World world, int x, int z)
	{
		Block block = getHighestBlockAt(world, x, z);
		int[] color = blockColors.get(BlockData.fromBlock(block));
		return color == null ? new int[]{ 0, 0, 0, 0 } : color;
	}

	private static Block getHighestBlockAt(World world, int x, int z)
	{ return world.getHighestBlockAt(x, z).getRelative(0, -1, 0); }

	public static String imageToDataURI(File file, String type)
	{
		try
		{
			// convert image to a data URI using base64
			return String.format("data:%s;base64,%s", type,
				Base64.encodeBase64String(FileUtils.readFileToByteArray(file)).replaceAll("\\s+", ""));
		}
		catch (IOException e) { e.printStackTrace(); return null; }
	}
}
