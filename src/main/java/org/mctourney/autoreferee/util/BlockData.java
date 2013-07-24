package org.mctourney.autoreferee.util;

import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;

import org.apache.commons.collections.map.DefaultedMap;

/**
 * Represents a type of block, combined with any identifiable metadata.
 *
 * @author authorblues
 */
public class BlockData
{
	// placeholder for a few common block data values
	public static final BlockData BEDROCK = new BlockData(Material.BEDROCK);
	public static final BlockData AIR     = new BlockData(Material.AIR);

	private Material mat;

	/**
	 * Gets the material type.
	 */
	public Material getMaterial()
	{ return mat; }

	/**
	 * Sets the material type.
	 */
	public void setMaterial(Material mat)
	{ this.mat = mat; }

	private byte data;

	/**
	 * Gets the metadata value.
	 */
	public byte getData()
	{ return data; }

	/**
	 * Sets the metadata value.
	 */
	public void setData(byte data)
	{ this.data = data; }

	/**
	 * Constructs a block data object with a material and metadata.
	 *
	 * @param material material type
	 * @param data metadata value, or -1 if any metadata
	 */
	public BlockData(Material material, byte data) { setMaterial(material); setData(data); }

	/**
	 * Constructs a block data object from an itemstack.
	 */
	public BlockData(ItemStack item) { this(item.getType(), item.getData().getData()); }

	/**
	 * Constructs a block data object with a material and any metadata.
	 *
	 * @param material material type
	 */
	public BlockData(Material material) { this(material, (byte)-1); }

	@Override public int hashCode()
	{ return getMaterial().hashCode(); }

	@Override public boolean equals(Object o)
	{
		// if the object is a mismatched type, its not equal
		if (o == null || !(o instanceof BlockData)) return false;

		// otherwise, check that the data is all equivalent
		BlockData ob = (BlockData) o;
		return ob.getMaterial().equals(getMaterial()) && this.dataMatches(ob);
	}

	private boolean dataMatches(BlockData ob)
	{ return ob.getData() == getData() || ob.getData() == -1 || getData() == -1; }

	/**
	 * Checks if the specified block matches this block data, taking metadata into account.
	 *
	 * @return true if block matches, otherwise false
	 */
	public boolean matchesBlock(Block block)
	{ return block != null && this.equals(BlockData.fromBlock(block)); }

	public String serialize()
	{
		String s = Integer.toString(getMaterial().getId());
		return getData() == -1 ? s : (s + "," + Integer.toString(getData()));
	}

	/**
	 * Gets a human-readable name for this block data, without color.
	 *
	 * @return block data name
	 */
	public String getName()
	{ return ChatColor.stripColor(getDisplayName()); }

	/**
	 * Gets a human-readable name for this block data.
	 *
	 * @return colored block data name
	 */
	public String getDisplayName()
	{
		String bname = getMaterial().name().replaceAll("_+", " ");
		if ((getMaterial().getNewData((byte) 0) instanceof Colorable))
		{
			DyeColor color = DyeColor.getByWoolData(getData());
			ChatColor chatColor = ColorConverter.dyeToChat(color);

			String colorName = color.name().replaceAll("_+", " ");
			bname = chatColor + colorName + " " + bname + ChatColor.RESET;

		}
		return bname;
	}

	/**
	 * Unserializes a block data object from a comma-seperated string.
	 *
	 * @param string serialized block data object
	 * @return block data object
	 */
	public static BlockData unserialize(String string)
	{
		// format: mat[,data]
		String[] units = string.split(",", 2);

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

	/**
	 * Generates a block data object from a block.
	 *
	 * @param block block object
	 * @return block data object
	 */
	public static BlockData fromBlock(Block block)
	{ return new BlockData(block.getType(), block.getData()); }

	/**
	 * Generates a block data object from an item stack.
	 *
	 * @param item item stack object
	 * @return block data object
	 */
	public static BlockData fromItemStack(ItemStack item)
	{
		byte b = item.getData().getData();
		return new BlockData(item.getType(), b);
	}

	/**
	 * Generates a block data object from an inventory. If an inventory contains more than
	 * one type of block, the block of the most quantity is returned.
	 *
	 * @param inv inventory object
	 * @return block data object
	 */
	@SuppressWarnings("unchecked")
	public static BlockData fromInventory(Inventory inv)
	{
		Map<BlockData, Integer> count = new DefaultedMap(0);
		for (ItemStack item : inv) if (item != null)
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
