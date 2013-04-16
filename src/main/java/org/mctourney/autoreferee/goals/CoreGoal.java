package org.mctourney.autoreferee.goals;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFromToEvent;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.util.BlockData;

public class CoreGoal extends AutoRefGoal
{
	private boolean satisfied;
	private AutoRefRegion region;

	public CoreGoal(AutoRefTeam team, AutoRefRegion region)
	{
		super(team);
		this.region = region;
	}

	public CoreGoal(AutoRefTeam team, Element elt)
	{
		super(team);
		this.region = AutoRefRegion.fromElement(team.getMatch(), elt.getChildren().get(0));
	}

	public void checkSatisfied(BlockFromToEvent event)
	{
		if (region != null && region.contains(event.getBlock().getLocation()) &&
			!region.contains(event.getToBlock().getLocation())) satisfied = true;
	}

	@Override
	public boolean isSatisfied(AutoRefMatch match)
	{ return satisfied; }

	@Override
	public void updateReferee(Player ref)
	{
		AutoRefMatch match = getOwner().getMatch();
		match.messageReferee(ref, "team", getOwner().getName(),
			"goal", "core", region.toString(), Boolean.toString(satisfied));
	}

	@Override
	public BlockData getItem() { return null; }

	@Override
	public Location getTarget() { return null; }

	@Override
	public Element toElement()
	{
		if (region == null)
			throw new IllegalStateException("Not a valid CoreGoal: Requires a valid region.");

		Element elt = new Element("core");
		elt.addContent(region.toElement());
		return elt;
	}
}
