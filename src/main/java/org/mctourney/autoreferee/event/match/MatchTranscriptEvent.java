package org.mctourney.autoreferee.event.match;

import org.bukkit.event.HandlerList;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.TranscriptEventType;
import org.mctourney.autoreferee.TranscriptEvent;

public class MatchTranscriptEvent extends MatchEvent
{
	private static final HandlerList handlers = new HandlerList();
	private TranscriptEvent transcriptEntry;

	public MatchTranscriptEvent(AutoRefMatch match, TranscriptEvent transcriptEntry)
	{ super(match); this.transcriptEntry = transcriptEntry; }

	public TranscriptEvent getEntry()
	{ return this.transcriptEntry; }

	public TranscriptEventType getEntryType()
	{ return this.transcriptEntry == null ? null : this.transcriptEntry.getType(); }

	@Override
	public HandlerList getHandlers()
	{ return handlers; }

	public static HandlerList getHandlerList()
	{ return handlers; }
}
