package org.mctourney.autoreferee.util;

public interface Metadatable
{
	public void addMetadata(String key, Object metadata);

	public Object getMetadata(String key);

	public boolean hasMetadata(String key);

	public Object removeMetadata(String key);

	public void clearMetadata();
}
