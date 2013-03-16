package org.mctourney.autoreferee.util.commands;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Sets;

public class CommandDocumentationGenerator
{
	@SuppressWarnings("unchecked")
	private static Set<Class<? extends CommandHandler>> commandHandlers = Sets.newHashSet
	(	org.mctourney.autoreferee.commands.PlayerCommands.class
	,	org.mctourney.autoreferee.commands.RefereeCommands.class
	,	org.mctourney.autoreferee.commands.AdminCommands.class
	,	org.mctourney.autoreferee.commands.ConfigurationCommands.class
	);

	public static void generateDocumentationFile(File file)
	{
		Set<String> commandLines = Sets.newHashSet();
		for (Class<? extends CommandHandler> handler : commandHandlers)
			for (Method method : handler.getDeclaredMethods())
		{
			AutoRefCommand cmd = method.getAnnotation(AutoRefCommand.class);
			AutoRefPermission perm = method.getAnnotation(AutoRefPermission.class);

			if (cmd != null)
				commandLines.add(String.format("%s;%s;%s;%s;%d;%d;%s",
					// command execution
					StringUtils.join(cmd.name(), ' '),

					// options list (to be parsed)
					cmd.options(),

					// options help info, if any
					cmd.optionsHelp(),

					// necessary permissions nodes
					StringUtils.join(perm.nodes(), ','),

					// permission level required
					perm.role().ordinal(),

					// can command be used from console?
					perm.console() ? 1 : 0,

					// text description (must be last)
					cmd.description()
				));
		}

		try { FileUtils.writeLines(file, commandLines); }
		catch (IOException e) { e.printStackTrace(); }
	}
}
