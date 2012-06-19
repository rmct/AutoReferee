package org.mctourney.AutoReferee.util;

import org.bukkit.Material;
import org.bukkit.block.Block;

public class BlockData
{
	public Material mat;
	public byte data;

	// material value and metadata (-1 = no metadata)
	public BlockData(Material m, byte d) { mat = m; data = d; }

	@Override public int hashCode()
	{ return mat.hashCode() ^ new Byte(data).hashCode(); }

	@Override public boolean equals(Object o)
	{
		// if the object is a mismatched type, its not equal
		if (o == null || !(o instanceof BlockData)) return false;
		
		// otherwise, check that the data is all equivalent
		BlockData ob = (BlockData) o; 
		return ob.mat.equals(mat) && ob.data == data;
	}

	// does this block data match the given block?
	public boolean matches(Block b)
	{
		// matches if materials and metadata are same
		return (b != null && b.getType().equals(mat)
			&& (data == -1 || data == b.getData()));
	}

	@Override public String toString()
	{
		String s = Integer.toString(mat.ordinal());
		return data == -1 ? s : (s + "," + Integer.toString(data));
	}
	
	public static BlockData fromString(String s)
	{
		// format: mat[,data]
		String[] units = s.split(",", 2);
		
		try 
		{
			// parse out the material (and potentially meta-data)
			Material mat = Material.getMaterial(Integer.parseInt(units[0]));
			byte data = units.length < 2 ? -1 : Byte.parseByte(units[1]);
			return new BlockData(mat, data);
		}
		
		// if there is a problem with parsing a material, assume the worst
		catch (NumberFormatException e) { return null; }
	}
	
	// generate block data object from a CraftBlock
	public static BlockData fromBlock(Block b)
	{ return new BlockData(b.getType(), b.getData()); }
}