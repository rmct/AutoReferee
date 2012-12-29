package org.mctourney.AutoReferee.util.commands;

import org.apache.commons.lang.StringUtils;

public class CommandPermissionException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public CommandPermissionException(AutoRefCommand command, String reason)
	{ super("Could not execute command '/" + StringUtils.join(command.name(), ' ') + "': " + reason); }
}