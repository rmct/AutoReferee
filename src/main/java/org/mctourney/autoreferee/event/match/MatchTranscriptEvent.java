package org.mctourney.autoreferee.event.match;

import org.bukkit.event.HandlerList;
import org.mctourney.autoreferee.AutoRefMatch;

public class MatchTranscriptEvent extends MatchEvent
{
	private static final HandlerList handlers = new HandlerList();
	private AutoRefMatch.TranscriptEvent transcriptEntry;

	public MatchTranscriptEvent(AutoRefMatch match, AutoRefMatch.TranscriptEvent transcriptEntry)
	{ super(match); this.transcriptEntry = transcriptEntry; }

	public AutoRefMatch.TranscriptEvent getEntry()
	{ return this.transcriptEntry; }

	public AutoRefMatch.TranscriptEvent.EventType getEntryType()
	{ return this.transcriptEntry == null ? null : this.transcriptEntry.getType(); }

	@Override
	public HandlerList getHandlers()
	{ return handlers; }
}
