package org.mctourney.autoreferee.plugin;

import com.google.common.collect.Maps;

import org.bukkit.plugin.Plugin;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.event.AutoRefereeEvent;
import org.mctourney.autoreferee.event.AutoRefereeEventHandler;
import org.mctourney.autoreferee.event.AutoRefereeListener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AutoRefPluginManager
{
	Map<Class<? extends AutoRefereeEvent>, List<EventHandlerMethod>> eventMap = Maps.newHashMap();

	public AutoRefPluginManager()
	{
	}

	public void registerListener(AutoRefereeListener listener, Plugin plugin)
	{
		for (Method method : listener.getClass().getDeclaredMethods())
			if (method.getAnnotation(AutoRefereeEventHandler.class) != null)
		{
			AutoReferee.log("Found something: " + method.getName());
			Class<?> types[] = method.getParameterTypes();
			if (types.length == 1 && AutoRefereeEvent.class.isAssignableFrom(types[0]))
			{
				AutoReferee.log("Right types!");
				if ((types[0].getModifiers() & Modifier.ABSTRACT) == 0)
					registerListenerMethod(plugin, listener, method, (Class<? extends AutoRefereeEvent>) types[0]);
				else throw new IllegalArgumentException("May not apply AutoRefereeEventHandler to abstract event types.");
			}
			else throw new IllegalArgumentException("AutoRefereeEventHandler requires an event-type parameter.");
		}
	}

	private void registerListenerMethod(Plugin plugin, AutoRefereeListener listener,
		Method method, Class<? extends AutoRefereeEvent> eventType)
	{
		AutoRefereeEventHandler annotation = method.getAnnotation(AutoRefereeEventHandler.class);
		List<EventHandlerMethod> mlist = eventMap.get(eventType);
		if (mlist == null) eventMap.put(eventType, mlist = new ArrayList<EventHandlerMethod>());

		mlist.add(new EventHandlerMethod(plugin, listener, method));
		AutoReferee.log(String.format("%s registered %s (%d)",
			plugin.getName(), eventType.getSimpleName(), mlist.size()));
	}

	public void fireEvent(AutoRefereeEvent event)
	{
		List<EventHandlerMethod> methods = eventMap.get(event.getClass());
		if (methods != null) for (EventHandlerMethod handler : methods)
			handler.triggerEvent(event);
	}
}
