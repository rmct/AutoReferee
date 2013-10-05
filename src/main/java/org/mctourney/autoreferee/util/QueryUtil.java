package org.mctourney.autoreferee.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;

import org.apache.commons.lang.StringUtils;
import org.mctourney.autoreferee.AutoReferee;

public class QueryUtil
{
	private static final String ENCODING = "UTF-8";

	public static String getUserAgent()
	{
		AutoReferee instance = AutoReferee.getInstance();
		String pluginName = instance.getDescription().getFullName();
		return String.format("%s (%s)", pluginName, instance.getCommit());
	}

	public static String syncGetQuery(String path, String params) throws IOException
	{ return syncQuery(path, params, null); }

	public static String syncPostQuery(String path, String params) throws IOException
	{ return syncQuery(path, null, params); }

	public static String syncPutQuery(String path, String params) throws IOException
	{ return syncQuery(path, "PUT", null, params); }

	public static String syncQuery(String path, String getParams, String postParams) throws IOException
	{ return syncQuery(path, null, getParams, postParams); }

	public static String syncQuery(String path, String method, String getParams, String postParams) throws IOException
	{
		OutputStreamWriter wr = null;
		InputStream rd = null;

		try
		{
			URL url = new URL(getParams == null ? path : String.format("%s?%s", path, getParams));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);

			AutoReferee instance = AutoReferee.getInstance();
			String pluginName = instance.getDescription().getFullName();
			conn.setRequestProperty("User-Agent", String.format("%s (%s)", pluginName, instance.getCommit()));

			if (method != null)
			{
				conn.setRequestMethod(method);
				conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			}

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
		catch (IOException e) { throw e; }

		finally
		{
			try
			{
				// close the stream pointers
				if (wr != null) wr.close();
				if (rd != null) rd.close();
			}
			// meh. don't bother, if something goes wrong here.
			catch (Exception ignored) {  }
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
