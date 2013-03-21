package org.mctourney.autoreferee.plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.plugin.Plugin;

import org.mctourney.autoreferee.event.AutoRefereeEvent;

public class EventHandlerMethod
{
	private final Object self;
	private final Method method;
	private final Plugin plugin;

	public EventHandlerMethod(Plugin plugin, Object self, Method method)
	{
		this.self = self;
		this.method = method;
		this.plugin = plugin;
	}

	public Plugin getPlugin()
	{ return plugin; }

	public void triggerEvent(AutoRefereeEvent event)
	{
		try
		{
			this.method.invoke(this.self, event);
		}
		catch (IllegalAccessException e)
		{ e.printStackTrace(); }
		catch (InvocationTargetException e)
		{ e.printStackTrace(); }
	}

	public int hashCode()
	{
		int hash = 1;
		hash = 17 * hash + this.self.hashCode();
		hash = 17 * hash + this.method.hashCode();
		hash = 17 * hash + this.plugin.hashCode();
		return hash;
	}

	public boolean equals(Object o)
	{ return o instanceof EventHandlerMethod && o.hashCode() == this.hashCode(); }
}
