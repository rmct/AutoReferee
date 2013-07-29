package org.mctourney.autoreferee.goals;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.regions.CuboidRegion;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;

import org.jdom2.Element;

import com.google.common.collect.Sets;

public class ScoreRegionGoal extends ScoreGoal
{
	protected final Set<AutoRefRegion> regions = Sets.newHashSet();
	protected Location tpto = null;

	protected double score = 0;

	public ScoreRegionGoal(AutoRefTeam team, AutoRefRegion ...regions)
	{
		super(team);
		this.regions.addAll(Sets.newHashSet(regions));
	}

	public ScoreRegionGoal(AutoRefTeam team, Element elt)
	{
		this(team);
		for (Element child : elt.getChildren())
		{
			AutoRefRegion reg = AutoRefRegion.fromElement(team.getMatch(), child);
			if (reg != null) this.regions.add(reg);
		}
	}

	public ScoreRegionGoal(AutoRefTeam team, ScoreRegionGoal scoreRegionGoal)
	{ this(team, scoreRegionGoal.regions.toArray(new AutoRefRegion[]{})); }

	@Override
	protected AutoRefGoal getGoalSettings(AutoRefTeam team, Element elt)
	{
		World world = team.getMatch().getWorld();
		if (elt.getAttribute("tp") != null)
			tpto = LocationUtil.fromCoords(world, elt.getAttributeValue("tp"));

		return super.getGoalSettings(team, elt);
	}

	@Override
	public ScoreRegionGoal copy()
	{ return this.copy(this.owner); }

	@Override
	public ScoreRegionGoal copy(AutoRefTeam team)
	{ return new ScoreRegionGoal(team, this); }

	@Override
	public void updateReferee(Player ref)
	{
	}

	public double getScore(AutoRefMatch match)
	{ return this.score; }

	public void addScore(double v)
	{ this.score += v; }

	@Override
	public BlockData getItem()
	{ return null; }

	@Override
	public Location getTarget()
	{
		CuboidRegion cube = null;
		for (AutoRefRegion reg : this.regions)
			cube = AutoRefRegion.combine(cube, reg);
		return cube == null ? null : cube.getCenter();
	}

	@Override
	public String toString()
	{ return "REGIONGOAL"; }

	@Override
	public Element toElement()
	{
		Element elt = new Element("scorezone");
		for (AutoRefRegion reg : regions)
			elt.addContent(reg.toElement());
		return elt;
	}
}
