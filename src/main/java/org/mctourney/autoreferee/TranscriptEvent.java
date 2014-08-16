package org.mctourney.autoreferee;

import com.google.common.collect.Sets;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.Set;

/**
 * An event to be later reported in match statistics. Events are announced when they happen,
 * and each type has its own visibility level to denote who will see the even happen live.
 *
 * @author authorblues
 */
public class TranscriptEvent
{
	public enum EventVisibility
	{ NONE, REFEREES, TEAM, ALL }

	private Set<Object> actors;
	public Set<Object> getActors()
	{ return actors; }

	private Set<AutoRefPlayer> playerActors;

	public Set<AutoRefPlayer> getPlayerActors()
	{ return playerActors; }

	private TranscriptEventType type;

	public TranscriptEventType getType()
	{ return type; }

	private String message;

	public String getMessage()
	{ return ChatColor.stripColor(message); }

	public String getColoredMessage()
	{ return message; }

	private Location location;
	private long timestamp;

	/**
	 *
	 * Supported Actor types: AutoRefPlayer, BlockData
	 *
	 * @param match
	 * @param type
	 * @param message
	 * @param loc
	 * @param actors
	 */
	public TranscriptEvent(AutoRefMatch match, TranscriptEventType type, String message,
			Location loc, Object... actors)
	{
		this.type = type;
		this.message = type.getColor() != null ? type.getColor() + message + ChatColor.RESET :
			message.contains("" + ChatColor.COLOR_CHAR) ? message : match.colorMessage(message);

		// if no location is given, use the spawn location
		this.location = (loc != null) ? loc :
			match.getWorld().getSpawnLocation();

		this.timestamp = match.getElapsedSeconds();

		this.actors = Sets.newHashSet(actors);
		this.playerActors = Sets.newHashSet();
		for (Object o : actors)
			if (o instanceof AutoRefPlayer)
				playerActors.add((AutoRefPlayer) o);
	}

	public String getTimestamp()
	{
		long t = getSeconds();
		return String.format("%02d:%02d:%02d",
			t/3600L, (t/60L)%60L, t%60L);
	}

	@Override
	public String toString()
	{ return String.format("[%s] %s", this.getTimestamp(), this.getColoredMessage()); }

	public Location getLocation()
	{ return location; }

	public long getSeconds()
	{ return timestamp; }
}
