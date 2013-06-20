package org.mctourney.autoreferee.goals;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;

/**
 * Represents a block placement goal.
 *
 * @author authorblues
 */
public class BlockGoal extends AutoRefGoal
{
	private Location loc;
	private BlockData blockdata;
	private int range;

	private String customName = null;
	private boolean canCraft = false;

	/**
	 * Constructs a team's win condition.
	 *
	 * @param team owner of this win condition
	 * @param loc target location for objective
	 * @param blockdata objective block type
	 * @param range maximum allowed distance from target
	 */
	public BlockGoal(AutoRefTeam team, Location loc, BlockData blockdata, int range)
	{ super(team); this.loc = loc; this.blockdata = blockdata; this.range = range; }

	private BlockGoal(AutoRefTeam team, BlockGoal goal)
	{ this(team, goal.loc, goal.blockdata, goal.range); }

	/**
	 * Constructs a team's win condition.
	 *
	 * @param team owner of this win condition
	 * @param loc target location for objective
	 * @param blockdata objective block type
	 */
	public BlockGoal(AutoRefTeam team, Location loc, BlockData blockdata)
	{ this(team, loc, blockdata, team.getMatch().getInexactRange()); }

	/**
	 * Constructs a team's win condition.
	 *
	 * @param team owner of this win condition
	 * @param block block to construct win condition from
	 * @param range maximum allowed distance from target
	 */
	public BlockGoal(AutoRefTeam team, Block block, int range)
	{ super(team); this.loc = block.getLocation(); this.blockdata = BlockData.fromBlock(block); this.range = range; }

	/**
	 * Constructs a team's win condition.
	 *
	 * @param team owner of this win condition
	 * @param block block to construct win condition from
	 */
	public BlockGoal(AutoRefTeam team, Block block)
	{ this(team, block, team.getMatch().getInexactRange()); }

	public BlockGoal(AutoRefTeam team, Element elt)
	{
		super(team);

		this.loc = LocationUtil.fromCoords(team.getMatch().getWorld(),
			elt.getAttributeValue("pos"));
		this.blockdata = BlockData.unserialize(elt.getAttributeValue("id"));

		String arange = elt.getAttributeValue("range");
		if (arange != null && !arange.isEmpty())
			range = Integer.parseInt(arange);
		else range = team.getMatch().getInexactRange();

		String acraft = elt.getAttributeValue("craftable");
		this.canCraft = (acraft != null && Boolean.parseBoolean(acraft));

		String text = elt.getTextTrim();
		if (text != null && !text.isEmpty())
			customName = text;
	}

	@Override
	public BlockGoal copy()
	{ return this.copy(this.owner); }

	@Override
	public BlockGoal copy(AutoRefTeam team)
	{ return new BlockGoal(team, this); }

	@Override
	public Element toElement()
	{
		Element elt = new Element("block");

		if (this.customName != null)
			elt.setText(this.customName);

		elt.setAttribute("pos", LocationUtil.toBlockCoords(this.loc));
		elt.setAttribute("id", this.blockdata.serialize());

		if (this.range != getOwner().getMatch().getInexactRange())
			elt.setAttribute("range", Integer.toString(this.range));

		if (this.canCraft)
			elt.setAttribute("craftable", Boolean.toString(this.canCraft));

		return elt;
	}

	@Override
	public boolean isSatisfied(AutoRefMatch match)
	{ return null != blockInRange(blockdata, loc, range); }

	@Override
	public double getScore(AutoRefMatch match)
	{ return getItemStatus().value; }

	/**
	 * Checks if a given block type exists within a cube centered around a location.
	 *
	 * @param blockdata block type being searched for
	 * @param loc center point of searchable cube
	 * @param radius radius of searchable cube
	 * @return location of a matching block within the region if one exists, otherwise null
	 */
	public static Location blockInRange(BlockData blockdata, Location loc, int radius)
	{
		Block b = loc.getBlock();
		int h = loc.getWorld().getMaxHeight();
		int by = loc.getBlockY();

		for (int y = -radius; y <= radius; ++y) if (by + y >= 0 && by + y < h)
		for (int x = -radius; x <= radius; ++x)
		for (int z = -radius; z <= radius; ++z)
		{
			Block rel = b.getRelative(x, y, z);
			if (blockdata.matchesBlock(rel)) return rel.getLocation();
		}

		return null;
	}

	/**
	 * Checks if a given block type exists within a cube centered around a location.
	 *
	 * @param goal win condition object
	 * @return location of a matching block within the region if one exists, otherwise null
	 */
	public static Location blockInRange(BlockGoal goal)
	{ return blockInRange(goal.getItem(), goal.getTarget(), goal.getInexactRange()); }

	@Override
	public void updateReferee(Player ref)
	{
		String bd = getItem().serialize();
		AutoRefMatch.messageReferee(ref, "team", getOwner().getName(), "goal", "+" + bd);
		AutoRefMatch.messageReferee(ref, "team", getOwner().getName(), "state", bd,
				getItemStatus().toString());
	}

	/**
	 * Gets the target location for this objective.
	 */
	@Override
	public Location getTarget()
	{ return loc; }

	/**
	 * Gets the block type for this objective.
	 */
	@Override
	public BlockData getItem()
	{ return blockdata; }

	@Override
	public boolean canCraftItem()
	{ return canCraft; }

	@Override
	public String toString()
	{
		String nm = customName == null ? blockdata.getDisplayName() : customName;
		return String.format("%s @ %s", nm, LocationUtil.toBlockCoords(loc));
	}

	/**
	 * Gets the maximum range this objective may be placed from its target.
	 */
	public int getInexactRange()
	{ return range; }
}
