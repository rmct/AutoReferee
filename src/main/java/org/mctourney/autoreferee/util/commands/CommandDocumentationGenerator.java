package org.mctourney.autoreferee.util.commands;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class CommandDocumentationGenerator
{
	@SuppressWarnings("unchecked")
	private static Set<Class<? extends CommandHandler>> commandHandlers = Sets.newHashSet
	(	org.mctourney.autoreferee.commands.PlayerCommands.class
	,	org.mctourney.autoreferee.commands.SpectatorCommands.class
	,	org.mctourney.autoreferee.commands.AdminCommands.class
	,	org.mctourney.autoreferee.commands.ConfigurationCommands.class
	,	org.mctourney.autoreferee.commands.PracticeCommands.class
	);

	public static void generateDocumentationFile(File file)
	{
		List<String> commandLines = Lists.newArrayList();
		for (Class<? extends CommandHandler> handler : commandHandlers)
			for (Method method : handler.getDeclaredMethods())
		{
			AutoRefCommand cmd = method.getAnnotation(AutoRefCommand.class);
			AutoRefPermission perm = method.getAnnotation(AutoRefPermission.class);

			if (cmd != null && !cmd.description().isEmpty())
			{
				String usage = cmd.argmax() > 0 ? "<command> <args...>" : "<command>";
				if (!cmd.usage().isEmpty()) usage = cmd.usage();

				String c = StringUtils.join(cmd.name(), ' ');
				usage = usage.replace("<command>", "/" + c);

				if (usage.contains("|")) System.err.println(String.format(
					"Usage string for '/%s' contains a '|'", StringUtils.join(cmd.name(), ' ')));

				commandLines.add(String.format("%s|%s|%s|%s|%d|%d|%s|%s",
					// command execution
					c,

					// options list (to be parsed)
					cmd.options(),

					// options help info, if any
					StringUtils.join(cmd.opthelp(), '#'),

					// necessary permissions nodes
					StringUtils.join(perm.nodes(), ','),

					// permission level required
					perm.role().ordinal(),

					// can command be used from console?
					perm.console() ? 1 : 0,

					// text description (must be last)
					cmd.description(),

					// usage string
					usage
				));
			}
		}

		Collections.sort(commandLines);

		try { FileUtils.writeLines(file, commandLines); }
		catch (IOException e) { e.printStackTrace(); }
	}
}
