package org.mctourney.AutoReferee.util;

import java.util.Map;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;

import org.apache.commons.collections.map.DefaultedMap;

public class BlockData
{
	private Material mat;

	public Material getMaterial()
	{ return mat; }

	public void setMaterial(Material mat)
	{ this.mat = mat; }
	
	private byte data;

	public byte getData()
	{ return data; }

	public void setData(byte data)
	{ this.data = data; }

	// material value and metadata (-1 = no metadata)
	public BlockData(Material m, byte d) { setMaterial(m); setData(d); }

	@Override public int hashCode()
	{ return getMaterial().hashCode() ^ new Byte(getData()).hashCode(); }

	@Override public boolean equals(Object o)
	{
		// if the object is a mismatched type, its not equal
		if (o == null || !(o instanceof BlockData)) return false;
		
		// otherwise, check that the data is all equivalent
		BlockData ob = (BlockData) o; 
		return ob.getMaterial().equals(getMaterial()) && ob.getData() == getData();
	}

	// does this block data match the given block?
	public boolean matches(Block b)
	{
		// matches if materials and metadata are same
		return (b != null && b.getType().equals(getMaterial())
			&& (getData() == -1 || getData() == b.getData()));
	}

	@Override public String toString()
	{
		String s = Integer.toString(getMaterial().ordinal());
		return getData() == -1 ? s : (s + "," + Integer.toString(getData()));
	}
	
	public String getName()
	{
		String bname = getMaterial().name().replaceAll("_+", " ");
		if ((getMaterial().getNewData((byte) 0) instanceof Colorable))
		{
			DyeColor color = DyeColor.getByData(getData());
			bname = color.name() + " " + bname;
		}
		return bname;
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
	
	// generate block data object from an ItemStack
	public static BlockData fromItemStack(ItemStack item)
	{ return new BlockData(item.getType(), item.getData().getData()); }
	
	// get primary BlockData type from Inventory
	@SuppressWarnings("unchecked")
	public static BlockData fromInventory(Inventory inv)
	{
		Map<BlockData, Integer> count = new DefaultedMap(0);
		for (ItemStack item : inv)
		{
			BlockData bd = BlockData.fromItemStack(item);
			count.put(bd, item.getAmount() + count.get(bd));
		}
		
		Map.Entry<BlockData, Integer> best = null;
		for (Map.Entry<BlockData, Integer> entry : count.entrySet())
			if (best == null || entry.getValue() > best.getValue()) best = entry;
		
		return best == null ? null : best.getKey();
	}
}