package org.mctourney.autoreferee.goals;

import java.lang.reflect.Constructor;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;

import com.google.common.collect.Maps;

/**
 * Represents a condition for victory.
 *
 * @author authorblues
 */
public abstract class AutoRefGoal
{
	public abstract boolean isSatisfied(AutoRefMatch match);
	public abstract void updateReferee(Player ref);

	public abstract BlockData getItem();
	public abstract Location getTarget();

	// for saving the data
	public abstract Element toElement();

	// make a copy of this goal
	public abstract AutoRefGoal copy();
	public abstract AutoRefGoal copy(AutoRefTeam team);

	/**
	 * Represents the status of a tracked item.
	 */
	public static enum ItemStatus
	{
		NONE("none", 0.0),

		SEEN("found", 0.1),

		CARRYING("carry", 0.1),

		TARGET("target", 1000.0);

		private String msg;
		public double value;

		private ItemStatus(String m, double v)
		{ msg = m; value = v; }

		@Override
		public String toString()
		{ return msg; }
	}

	protected AutoRefTeam owner;
	private ItemStatus itemStatus = ItemStatus.NONE;

	public AutoRefGoal(AutoRefTeam team)
	{ setOwner(team); }

	public void setOwner(AutoRefTeam team)
	{ owner = team; }

	public AutoRefTeam getOwner()
	{ return owner; }

	public boolean hasItem()
	{ return getItem() != null; }

	public boolean canCraftItem()
	{ return false; }

	public ItemStatus getItemStatus()
	{ return itemStatus; }

	public void setItemStatus(ItemStatus s)
	{ if (hasItem()) itemStatus = s; }

	public boolean hasTarget()
	{ return getTarget() != null; }

	// default definition
	public double getScore(AutoRefMatch match)
	{ return isSatisfied(match) ? 1000.0 : 0.0; }

	public boolean canBeCompleted(AutoRefMatch match)
	{ return true; }

	protected AutoRefGoal getGoalSettings(AutoRefTeam team, Element elt)
	{
		return this;
	}

	private static Map<String, Class<? extends AutoRefGoal>> goalNames = Maps.newHashMap();
	static
	{
		addGoalType("block", BlockGoal.class);
		addGoalType("core", CoreGoal.class);
		addGoalType("survive", SurvivalGoal.class);
		addGoalType("time", TimeGoal.class);
		addGoalType("deathmatch", ScoreDeathmatchGoal.class);
		addGoalType("dummy", ScoreDummyGoal.class);
		addGoalType("region", ScoreRegionGoal.class);
	}

	public static void addGoalType(String tag, Class<? extends AutoRefGoal> cls)
	{ goalNames.put(tag, cls); }

	public static AutoRefGoal fromElement(AutoRefTeam team, Element elt)
	{
		Class<? extends AutoRefGoal> cls = goalNames.get(elt.getName());
		if (cls == null) return null;

		try
		{
			Constructor<? extends AutoRefGoal> cons = cls.getConstructor(AutoRefTeam.class, Element.class);
			return cons.newInstance(team, elt).getGoalSettings(team, elt);
		}
		catch (Exception e) { e.printStackTrace(); return null; }
	}
}