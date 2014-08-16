package org.mctourney.autoreferee;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;

/**
* Created by kane on 8/16/14.
*/
public enum TranscriptEventType
{
	// generic match start and end events
	MATCH_START("match-start", false, TranscriptEvent.EventVisibility.NONE),
	MATCH_END("match-end", false, TranscriptEvent.EventVisibility.NONE),

	// player messages (except kill streak) should be broadcast to players
	PLAYER_DEATH("player-death", true, TranscriptEvent.EventVisibility.NONE),
	PLAYER_STREAK("player-killstreak", false, TranscriptEvent.EventVisibility.NONE, ChatColor.DARK_GRAY),
	PLAYER_DOMINATE("player-dominate", true, TranscriptEvent.EventVisibility.ALL, ChatColor.DARK_GRAY),
	PLAYER_REVENGE("player-revenge", true, TranscriptEvent.EventVisibility.ALL, ChatColor.DARK_GRAY),

	// objective events should not be broadcast to the other team
	OBJECTIVE_FOUND("objective-found", true, TranscriptEvent.EventVisibility.TEAM),
	OBJECTIVE_PLACED("objective-place", true, TranscriptEvent.EventVisibility.TEAM),
	OBJECTIVE_MAJOR("objective-major", true, TranscriptEvent.EventVisibility.REFEREES),
	OBJECTIVE_DETAIL("objective-detail", true, TranscriptEvent.EventVisibility.REFEREES),
	;

	private String eventClass;
	private TranscriptEvent.EventVisibility visibility;
	private ChatColor color;
	private boolean supportsFiltering;

	TranscriptEventType(String eventClass, boolean hasFilter,
			TranscriptEvent.EventVisibility visibility)
	{ this(eventClass, hasFilter, visibility, null); }

	TranscriptEventType(String eventClass, boolean hasFilter,
			TranscriptEvent.EventVisibility visibility, ChatColor color)
	{
		this.eventClass = eventClass;
		this.visibility = visibility;
		this.color = color;
		this.supportsFiltering = hasFilter;
	}

	public String getEventClass()
	{ return eventClass; }

	public String getEventName()
	{ return StringUtils.capitalize(name().toLowerCase().replaceAll("_", " ")); }

	public TranscriptEvent.EventVisibility getVisibility()
	{ return visibility; }

	public ChatColor getColor()
	{ return color; }

	public boolean hasFilter()
	{ return supportsFiltering; }
}
