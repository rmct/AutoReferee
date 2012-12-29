package org.mctourney.AutoReferee.util.commands;

import java.lang.reflect.Method;

public class CommandRegistrationException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public CommandRegistrationException(Method method, String reason)
	{
		super("Could not register the command method " + method.getName() + " in the class " +
			method.getDeclaringClass().getName() + ": " + reason);
	}
}
