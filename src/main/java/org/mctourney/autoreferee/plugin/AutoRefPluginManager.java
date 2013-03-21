package org.mctourney.autoreferee.plugin;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.bukkit.plugin.Plugin;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.event.Event;
import org.mctourney.autoreferee.event.EventHandler;
import org.mctourney.autoreferee.event.Listener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AutoRefPluginManager
{
	Map<Class<? extends Event>, List<EventHandlerMethod>> eventMap = Maps.newHashMap();

	public AutoRefPluginManager()
	{
	}

	public void registerListener(Listener listener, Plugin plugin)
	{
		for (Method method : listener.getClass().getDeclaredMethods())
			if (method.getAnnotation(EventHandler.class) != null)
		{
			AutoReferee.log("Found something: " + method.getName());
			Class<?> types[] = method.getParameterTypes();
			if (types.length == 1 && Event.class.isAssignableFrom(types[0]))
			{
				AutoReferee.log("Right types!");
				if ((types[0].getModifiers() & Modifier.ABSTRACT) == 0)
					registerListenerMethod(plugin, listener, method, (Class<? extends Event>) types[0]);
				else throw new IllegalArgumentException("May not apply EventHandler to abstract event types.");
			}
			else throw new IllegalArgumentException("EventHandler requires an event-type parameter.");
		}
	}

	private void registerListenerMethod(Plugin plugin, Listener listener,
		Method method, Class<? extends Event> eventType)
	{
		EventHandler annotation = method.getAnnotation(EventHandler.class);
		List<EventHandlerMethod> mlist = eventMap.get(eventType);
		if (mlist == null) eventMap.put(eventType, mlist = new ArrayList<EventHandlerMethod>());

		mlist.add(new EventHandlerMethod(plugin, listener, method));
		AutoReferee.log(String.format("%s registered %s (%d)",
			plugin.getName(), eventType.getSimpleName(), mlist.size()));
	}

	public void fireEvent(Event event)
	{
		List<EventHandlerMethod> methods = eventMap.get(event.getClass());
		if (methods != null) for (EventHandlerMethod handler : methods)
			handler.triggerEvent(event);
	}
}
