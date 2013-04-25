package org.mctourney.autoreferee.util;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoReferee;

public class QueryServer
{
	private static final String ENCODING = "UTF-8";

	private String qurl = null;
	private String key = null;

	public QueryServer(String qurl, String key)
	{ this.qurl = qurl; this.key = key; }

	public boolean ack()
	{
		try
		{
			String params = String.format("key=%s", URLEncoder.encode(key, ENCODING));
			String json = syncPostQuery(qurl + "/ack.php", params);
			return json != null && new Gson().fromJson(json, boolean.class);
		}
		catch (Exception e) { return false; }
	}

	public AutoRefMatch.MatchParams getNextMatch()
	{
		try
		{
			String params = String.format("key=%s", URLEncoder.encode(key, ENCODING));
			String json = syncPostQuery(qurl + "/match.php", params);
			return json == null ? null : new Gson().fromJson(json, AutoRefMatch.MatchParams.class);
		}
		catch (Exception e) { return null; }
	}

	public static String syncGetQuery(String path, String params)
	{ return syncQuery(path, params, null); }

	public static String syncPostQuery(String path, String params)
	{ return syncQuery(path, null, params); }

	public static String syncQuery(String path, String getParams, String postParams)
	{
		OutputStreamWriter wr = null;
		InputStream rd = null;

		try
		{
			URL url = new URL(getParams == null ? path : String.format("%s?%s", path, getParams));
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);

			AutoReferee instance = AutoReferee.getInstance();
			String pluginName = instance.getDescription().getFullName();
			conn.setRequestProperty("User-Agent", String.format("%s (%s)", pluginName, instance.getCommit()));

			if (postParams != null)
			{
				wr = new OutputStreamWriter(conn.getOutputStream());
				wr.write(postParams); wr.flush();
			}

			StringWriter writer = new StringWriter();
			IOUtils.copy(rd = conn.getInputStream(), writer);
			return writer.toString();
		}

		// just drop out
		catch (Exception e)
		{ e.printStackTrace(); return null; }

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

	public static String prepareParams(Map<String, String> paramMap)
	{
		Set<String> params = Sets.newHashSet();
		for (Map.Entry<String, String> entry : paramMap.entrySet()) try
		{
			String val = URLEncoder.encode(entry.getValue(), ENCODING);
			params.add(String.format("%s=%s", entry.getKey(), val));
		}
		catch (UnsupportedEncodingException e)
		{ e.printStackTrace(); }

		return StringUtils.join(params, "&");
	}
}
