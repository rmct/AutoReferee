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
	private boolean broken;
	private AutoRefRegion region;
	private long range = 0L;

	public CoreGoal(AutoRefTeam team, AutoRefRegion region)
	{
		super(team);
		this.region = region;
	}

	public CoreGoal(AutoRefTeam team, Element elt)
	{
		super(team);
		this.region = AutoRefRegion.fromElement(team.getMatch(), elt.getChildren().get(0));

		if (elt.getAttribute("range") != null)
			try { this.range = Long.parseLong(elt.getAttributeValue("range").trim()); }
			catch (NumberFormatException e) { e.printStackTrace(); }
	}

	public long getRange()
	{ return this.range; }

	public void setRange(long range)
	{ this.range = range; }

	public void checkSatisfied(BlockFromToEvent event)
	{
		final Location fm = event.getBlock().getLocation();
		final Location to = event.getToBlock().getLocation();

		if (region != null && region.distanceToRegion(fm) <= range &&
			region.distanceToRegion(to) > range) broken = true;
	}

	@Override
	public boolean isSatisfied(AutoRefMatch match)
	{ return broken; }

	@Override
	public String toString()
	{
		return "CORE";
	}

	@Override
	public void updateReferee(Player ref)
	{
		AutoRefMatch.messageReferee(ref, "team", getOwner().getName(),
			"goal", "core", region.toString(), Boolean.toString(broken));
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
		if (range > 0L) elt.setAttribute("range", Long.toString(range));
		elt.addContent(region.toElement());
		return elt;
	}
}
