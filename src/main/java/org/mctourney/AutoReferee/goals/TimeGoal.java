package org.mctourney.AutoReferee.goals;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.util.BlockData;

public class TimeGoal extends AutoRefGoal
{
	private long ticks = Long.MAX_VALUE;

	public TimeGoal(AutoRefTeam team, Element elt)
	{
		super(team);
	}

	@Override
	public boolean isSatisfied(AutoRefMatch match)
	{ return match.getMatchTime() > this.ticks; }

	@Override
	public void updateReferee(Player ref)
	{
		AutoRefMatch match = getOwner().getMatch();
		match.messageReferee(ref, "team", getOwner().getName(),
			"goal", "time," + Long.toString(ticks));
	}

	@Override
	public BlockData getItem() { return null; }

	@Override
	public Location getTarget() { return null; }

	@Override
	public Element toElement()
	{ return new Element("time").setText(printTime(ticks)); }

	public static String printTime(long ticks)
	{
		long sec = ticks / 20L, min = sec / 60L, hrs = min / 60L;
		return String.format("%d:%02d:%02d", hrs, min%60L, sec%60L);
	}

	public static long parseTime(String time)
	{
		return 0L;
	}
}
