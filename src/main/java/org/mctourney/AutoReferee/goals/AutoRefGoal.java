package org.mctourney.AutoReferee.goals;

import java.lang.reflect.Constructor;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.util.BlockData;

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

	/**
	 * Represents the status of a tracked item.
	 */
	public static enum ItemStatus
	{
		NONE("none"),

		SEEN("found"),

		CARRYING("carry"),

		TARGET("target");

		private String msg;

		private ItemStatus(String m)
		{ msg = m; }

		@Override
		public String toString()
		{ return msg; }
	}

	private AutoRefTeam owner;
	private ItemStatus itemStatus = ItemStatus.NONE;

	public AutoRefGoal(AutoRefTeam team)
	{ setOwner(team); }

	public void setOwner(AutoRefTeam team)
	{ owner = team; }

	public AutoRefTeam getOwner()
	{ return owner; }

	public boolean hasItem()
	{ return getItem() != null; }

	public ItemStatus getItemStatus()
	{ return itemStatus; }

	public void setItemStatus(ItemStatus s)
	{ if (hasItem()) itemStatus = s; }

	public boolean hasTarget()
	{ return getTarget() != null; }

	private AutoRefGoal getGoalSettings(AutoRefTeam team, Element elt)
	{
		return this;
	}

	private static Map<String, Class<? extends AutoRefGoal>> goalNames = Maps.newHashMap();
	static
	{
		goalNames.put("block", BlockGoal.class);
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