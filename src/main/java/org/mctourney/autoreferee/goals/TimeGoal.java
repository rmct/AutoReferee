package org.mctourney.autoreferee.goals;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.util.BlockData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeGoal extends AutoRefGoal
{
	private long seconds = Long.MAX_VALUE;

	public TimeGoal(AutoRefTeam team, long seconds)
	{ super(team); this.seconds = seconds; }

	private TimeGoal(AutoRefTeam team, TimeGoal goal)
	{ this(team, goal.seconds); }

	public TimeGoal(AutoRefTeam team, Element elt)
	{ this(team, parseTime(elt.getTextTrim())); }

	@Override
	public TimeGoal copy()
	{ return this.copy(this.owner); }

	@Override
	public TimeGoal copy(AutoRefTeam team)
	{ return new TimeGoal(team, this); }

	@Override
	public boolean isSatisfied(AutoRefMatch match)
	{ return match.getElapsedSeconds() > this.seconds; }

	@Override
	public void updateReferee(Player ref)
	{
		AutoRefMatch.messageReferee(ref, "team", getOwner().getName(),
			"goal", "time", Long.toString(seconds));
	}

	@Override
	public BlockData getItem() { return null; }

	@Override
	public Location getTarget() { return null; }

	@Override
	public Element toElement()
	{ return new Element("time").setText(printTime(seconds)); }

	public static String printTime(long sec)
	{
		long min = sec / 60L, hrs = min / 60L;
		return String.format("%d:%02d:%02d", hrs, min%60L, sec%60L);
	}

	public static long parseTime(String time)
	{
		Pattern pattern = Pattern.compile("(((\\d*):)?(\\d{1,2}):)?(\\d{1,2})", Pattern.CASE_INSENSITIVE);
		Matcher match = pattern.matcher(time);

		// if the time matches the format
		if (match.matches()) try
		{
			int hrs = match.group(3) == null ? 0 : Integer.parseInt(match.group(3));
			int min = match.group(4) == null ? 0 : Integer.parseInt(match.group(4));
			int sec = match.group(5) == null ? 0 : Integer.parseInt(match.group(5));
			return sec + 60L*(min + 60L*hrs);
		}
		catch (NumberFormatException e) {  }

		// fallback: return an impossibly large time value
		return Long.MAX_VALUE;
	}
}
