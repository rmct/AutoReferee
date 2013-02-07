package org.mctourney.AutoReferee.util.commands;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrMatcher;
import org.apache.commons.lang.text.StrTokenizer;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import org.bukkit.plugin.java.JavaPlugin;
import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefMatch.Role;
import org.mctourney.AutoReferee.AutoReferee;

import com.google.common.collect.Maps;

public class CommandManager implements CommandExecutor
{
	Map<String, HandlerNode> cmap = Maps.newHashMap();

	protected class CommandHandler
	{
		public Object handler;
		public Method method;

		Options commandOptions;

		public CommandHandler(Object handler, Method method, PluginCommand command)
		{
			this.method = method; this.handler = handler;
			AutoRefCommand cAnnotation = method.getAnnotation(AutoRefCommand.class);

			commandOptions = new Options();
			if (!"".equals(cAnnotation.options()))
			{
				char options[] = cAnnotation.options().toCharArray();
				for (int i = 0; i < options.length; )
				{
					char arg = options[i++];
					if (i < options.length)
					{
						if (options[i] == '*') { OptionBuilder.hasOptionalArg(); ++i; }
						else if (options[i] == '+') { OptionBuilder.hasArg(); ++i; }
					}

					commandOptions.addOption(OptionBuilder.create(arg));
				}
			}
		}

		public boolean execute(CommandSender sender, AutoRefMatch match, String[] args)
			throws CommandPermissionException
		{
			CommandLineParser parser = new GnuParser();
			CommandLine cli = null;

			try { cli = parser.parse(commandOptions, args); args = cli.getArgs(); }
			catch (ParseException e) { e.printStackTrace(); }

			AutoRefCommand command = method.getAnnotation(AutoRefCommand.class);
			AutoRefPermission permissions = method.getAnnotation(AutoRefPermission.class);

			// perform the args cut at this point
			args = (String[]) ArrayUtils.subarray(args, command.name().length - 1, args.length);

			if (sender instanceof ConsoleCommandSender && permissions != null && !permissions.console())
				throw new CommandPermissionException(command, "Command not available from console");

			if (sender instanceof Player)
			{
				Player player = (Player) sender;
				Role role = match == null ? AutoRefMatch.Role.NONE : match.getRole(player);

				if (role.atLeast(permissions.role()))
					throw new CommandPermissionException(command, match == null
						? "Command available only within an AutoReferee match"
						: ("Command not available to " + role.toString().toLowerCase()));

				for (String node : permissions.nodes()) if (!player.hasPermission(node))
					throw new CommandPermissionException(command, "Insufficient permissions");
			}
			// if the number of arguments is incorrect, just return false
			if (command.argmin() > args.length || command.argmax() < args.length) return false;

			try { return ((Boolean) method.invoke(handler, sender, match, args, cli)).booleanValue(); }
			catch (Exception e) { e.printStackTrace(); return false; }
		}
	}

	public void registerCommands(Object commands, JavaPlugin plugin)
	{
		for (Method method : commands.getClass().getDeclaredMethods())
		{
			AutoRefCommand command = method.getAnnotation(AutoRefCommand.class);
			if (command == null || command.name().length == 0) continue;

			PluginCommand pcommand = plugin.getCommand(command.name()[0]);
			if (pcommand == null) throw new CommandRegistrationException(method, "Command not provided in plugin.yml");

			if (method.getReturnType() != boolean.class)
				throw new CommandRegistrationException(method, "Command method must return type boolean");

			if (pcommand.getExecutor() != this) pcommand.setExecutor(this);
			this.setHandler(new CommandHandler(commands, method, pcommand), command.name());
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		AutoReferee plugin = AutoReferee.getInstance();

		World world = plugin.getSenderWorld(sender);
		AutoRefMatch match = plugin.getMatch(world);

		// reparse the args properly using the string tokenizer from org.apache.commons
		args = new StrTokenizer(StringUtils.join(args, ' '), StrMatcher.splitMatcher(),
			StrMatcher.quoteMatcher()).setTrimmerMatcher(StrMatcher.trimMatcher()).getTokenArray();

		try
		{
			CommandHandler handler = this.getHandler(command.getName(), args);
			return handler == null ? false : handler.execute(sender, match, args);
		}
		catch (CommandPermissionException e)
		{ sender.sendMessage(ChatColor.DARK_GRAY + e.getMessage()); return true; }
	}

	private CommandHandler getHandler(String cmd, String[] args)
	{
		HandlerNode node = cmap.get(cmd);
		if (node == null) return null;

		// attempt to narrow down the method using the args
		for (String arg : args)
		{
			// lowercase to maintain case insensitivity
			arg = arg.toLowerCase();

			HandlerNode next = node.cmap.get(arg);
			if (next == null) break;

			// move on to the next node, increment the cut length
			node = next;
		}

		// return the appropriate handler
		return node.handler;
	}

	private void setHandler(CommandHandler handler, String[] cmd)
	{
		Map<String, HandlerNode> curr = cmap;
		HandlerNode node = null;

		// traverse handler tree, creating nodes if necessary
		for (String c : cmd)
		{
			// lowercase to maintain case insensitivity
			c = c.toLowerCase();

			if ((node = curr.get(c)) == null)
				curr.put(c, node = new HandlerNode(null));
			curr = node.cmap;
		}

		// set the appropriate handler at this node
		node.handler = handler;
	}
}

class HandlerNode
{
	protected Map<String, HandlerNode> cmap;
	protected CommandManager.CommandHandler handler;

	public HandlerNode(CommandManager.CommandHandler handler)
	{
		this.handler = handler;
		this.cmap = Maps.newHashMap();
	}
}
