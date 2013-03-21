package org.mctourney.autoreferee.event.match;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.event.Cancellable;

public class MatchUploadStatsEvent extends MatchEvent implements Cancellable
{
	private boolean cancelled = false;
	private String webstats = null;

	public MatchUploadStatsEvent(AutoRefMatch match, String webstats)
	{
		super(match);
		this.webstats = webstats;
	}

	public String getWebstats()
	{ return this.webstats; }

	public void setWebstats(String webstats)
	{ this.webstats = webstats; }

	@Override
	public boolean isCancelled()
	{ return this.cancelled; }

	@Override
	public void setCancelled(boolean cancel)
	{ this.cancelled = cancel; }
}
