package org.mctourney.AutoReferee;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import com.google.gson.Gson;

public class QueryServer
{
	private static final String encoding = "UTF-8";
	
	private String qurl = null;
	private String key = null;
	
	public QueryServer(String qurl, String key)
	{ this.qurl = qurl; this.key = key; }
	
	public boolean ack()
	{
		try
		{
			String params = String.format("key=%s", URLEncoder.encode(key, encoding));
			String json = syncPostQuery("ack.php", params);
			return json != null && new Gson().fromJson(json, boolean.class);
		}
		catch (Exception e) { return false; }
	}
	
	public MatchParams getNextMatch()
	{
		try
		{
			String params = String.format("key=%s", URLEncoder.encode(key, encoding));
			String json = syncPostQuery("match.php", params);
			return json == null ? null : new Gson().fromJson(json, MatchParams.class);
		}
		catch (Exception e) { return null; }
	}
	
	private String syncGetQuery(String path, String params)
	{ return syncQuery(path, params, ""); }
	
	private String syncPostQuery(String path, String params)
	{ return syncQuery(path, "", params); }

	private String syncQuery(String path, String getParams, String postParams)
	{
		OutputStreamWriter wr = null;
		InputStream rd = null;
		
		try
		{
			URL url = new URL(String.format("%s/%s?%s", qurl, path, getParams));
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
		    
			wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(postParams); wr.flush();
			StringWriter writer = new StringWriter();
			
			IOUtils.copy(rd = conn.getInputStream(), writer);
			return writer.toString();
		}
		
		// just drop out
		catch (Exception e)
		{ return null; }
		
		finally
		{
			try
			{
				// close the stream pointers
				if (wr != null) wr.close();
				if (rd != null) rd.close();
			}
			// meh. don't bother, if something goes wrong here.
			catch (Exception e) {  }
		}
	}
	
	// unserialized match initialization parameters
	static class MatchParams
	{
		public static class TeamInfo
		{
			private String name;
			
			public String getName()
			{ return name; }
			
			private List<String> players;
	
			public List<String> getPlayers()
			{ return Collections.unmodifiableList(players); }
		}
		
		// info about all the teams
		private List<TeamInfo> teams;
		
		public List<TeamInfo> getTeams()
		{ return Collections.unmodifiableList(teams); }
	
		// match tag for reporting
		private String tag;
		
		public String getTag()
		{ return tag; }
		
		// map name and checksum
		private String map;
		private Long checksum;
		
		public String getMap()
		{ return map; }
		
		public Long getChecksum()
		{ return checksum; }
	}
}
