package org.mctourney.AutoReferee.goals;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.LocationUtil;

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
	 * @param range maximum allowed distance from target
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
	public boolean isSatisfied(AutoRefMatch match)
	{ return null != match.blockInRange(blockdata, loc, range); }

	@Override
	public void updateReferee(Player ref)
	{
		AutoRefMatch match = getOwner().getMatch();
		String bd = getItem().serialize();

		match.messageReferee(ref, "team", getOwner().getName(), "obj", "+" + bd);
		match.messageReferee(ref, "team", getOwner().getName(), "state", bd,
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
