package org.mctourney.autoreferee.util.commands;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrMatcher;
import org.apache.commons.lang.text.StrTokenizer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpTopic;
import org.bukkit.help.IndexHelpTopic;
import org.bukkit.plugin.java.JavaPlugin;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefMatch.Role;
import org.mctourney.autoreferee.util.commands.CommandManager.AutoRefCommandHelpTopic;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class CommandManager implements CommandExecutor, TabCompleter
{
	Map<String, HandlerNode> cmap = Maps.newHashMap();

	protected class CommandDelegator
	{
		public Object handler;
		public Method method;

		Options commandOptions;
		AutoRefCommandHelpTopic helpTopic = null;

		public CommandDelegator(Object handler, Method method, PluginCommand command)
		{
			this.method = method; this.handler = handler;

			AutoRefCommand cAnnotation = method.getAnnotation(AutoRefCommand.class);
			AutoRefPermission pAnnotation = method.getAnnotation(AutoRefPermission.class);

			if (!cAnnotation.description().isEmpty())
				helpTopic = new AutoRefCommandHelpTopic(cAnnotation, pAnnotation);

			commandOptions = new Options();
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

		public boolean execute(CommandSender sender, AutoRefMatch match, String[] args)
			throws CommandPermissionException, UnrecognizedOptionException
		{
			CommandLine cli = null;

			try { cli = new GnuParser().parse(commandOptions, args); args = cli.getArgs(); }
			catch (UnrecognizedOptionException e) { throw e; }
			catch (ParseException e) { e.printStackTrace(); }

			AutoRefCommand command = method.getAnnotation(AutoRefCommand.class);
			AutoRefPermission permissions = method.getAnnotation(AutoRefPermission.class);

			// perform the args cut at this point
			args = (String[]) ArrayUtils.subarray(args, command.name().length - 1, args.length);

			// check command permissions
			checkPermissions(command, permissions, sender);

			// if the number of arguments is incorrect, just return false
			if (command.argmin() > args.length || command.argmax() < args.length) return false;

			try { return ((Boolean) method.invoke(handler, sender, match, args, cli)).booleanValue(); }
			catch (Exception e) { e.printStackTrace(); return false; }
		}
	}

	public class AutoRefCommandHelpTopic extends HelpTopic
	{
		private AutoRefPermission permissions;
		private AutoRefCommand command;
		private boolean alias = false;

		public AutoRefCommandHelpTopic(AutoRefCommand command, AutoRefPermission permissions)
		{
			this.command = command;
			this.permissions = permissions;

			this.name = "/" + StringUtils.join(command.name(), " ");
			this.shortText = command.description();
			this.setupFullText();
		}

		protected void setupFullText()
		{
			String usage = command.usage();
			if ("".equals(usage)) usage = command.argmax() == 0
				? "<command>" : "<command> [args?]";

			this.fullText = command.description() + "\n"
				+ "Usage: " + usage.replace("<command>", this.name);

			String[] opts = command.opthelp();
			if (opts.length > 0)
			{
				String opt = "Options:";
				for (int i = 0; i < opts.length; i += 2) opt += String.format(
					"\n%s   -%s, %s", ChatColor.RESET.toString(), opts[i], opts[i+1]);
				this.fullText += "\n" + opt;
			}
		}

		@Override
		public boolean canSee(CommandSender sender)
		{
			try { checkPermissions(this.command, this.permissions, sender); return true; }
			catch (CommandPermissionException e) { return false; }
		}

		public boolean isAlias()
		{ return alias; }

		public AutoRefCommandHelpTopic copyAlias(String alias)
		{
			AutoRefCommandHelpTopic topic = new AutoRefCommandHelpTopic(this.command, this.permissions);

			// modify the command to insert the Bukkit alias
			String[] cmd = command.name().clone(); cmd[0] = alias;
			topic.name = "/" + StringUtils.join(cmd, " ");

			topic.alias = true;
			topic.setupFullText();
			return topic;
		}
	}

	public void checkPermissions(AutoRefCommand command, AutoRefPermission permissions, CommandSender sender)
		throws CommandPermissionException
	{
		if (sender instanceof ConsoleCommandSender && permissions != null && !permissions.console())
			throw new CommandPermissionException(command, "Command not available from console");

		if (sender instanceof Player)
		{
			Player player = (Player) sender;
			AutoRefMatch match = AutoReferee.getInstance().getMatch(player.getWorld());
			Role role = match == null ? AutoRefMatch.Role.NONE : match.getRole(player);

			if (!role.atLeast(permissions.role()))
				throw new CommandPermissionException(command, match == null
					? "Command available only within an AutoReferee match"
					: ("Command not available to " + role.toString().toLowerCase()));

			for (String node : permissions.nodes()) if (!player.hasPermission(node))
				throw new CommandPermissionException(command, "Insufficient permissions");
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
			if (pcommand.getTabCompleter() != this) pcommand.setTabCompleter(this);

			CommandDelegator delegator = new CommandDelegator(commands, method, pcommand);
			this.setHandler(delegator, command.name());
		}
	}

	public void generateHelp(JavaPlugin plugin)
	{
		List<HelpTopic> topics = Lists.newArrayList();
		for (Map.Entry<String, HandlerNode> e : cmap.entrySet())
		{
			PluginCommand pcommand = Bukkit.getPluginCommand(e.getKey());
			if (pcommand != null && plugin.equals(pcommand.getPlugin()))
			{
				HandlerNode handler = e.getValue();
				if (handler != null) topics.addAll(handler.getHelpTopics(pcommand));
			}
		}

		Iterator<HelpTopic> iter = topics.iterator();
		for (iter = topics.iterator(); iter.hasNext(); )
		{
			HelpTopic topic = iter.next();
			Bukkit.getHelpMap().addTopic(topic);

			// remove the aliases from the list before we populate the index
			if (topic instanceof AutoRefCommandHelpTopic &&
				((AutoRefCommandHelpTopic) topic).isAlias()) iter.remove();
		}

		Collections.sort(topics, new Comparator<HelpTopic>()
		{
			@Override
			public int compare(HelpTopic a, HelpTopic b)
			{ return a.getName().compareToIgnoreCase(b.getName()); }
		});

		String stext = String.format("Below is a list of all %s commands:", plugin.getName());
		Bukkit.getHelpMap().addTopic(new IndexHelpTopic(plugin.getName(), stext, "", topics));
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
			HandlerNode node = cmap.get(command.getName());
			List<String> ccmd = Lists.newArrayList(command.getName());

			if (node == null) return false;

			// attempt to narrow down the method using the args
			for (String arg : args)
			{
				// lowercase to maintain case insensitivity
				arg = arg.toLowerCase();

				HandlerNode next = node.cmap.get(arg);
				if (next == null) break;

				// move on to the next node, increment the cut length
				ccmd.add(arg); node = next;
			}

			String partialcmd = "/" + StringUtils.join(ccmd, " ");
			if (node.handler != null)
			{
				// attempt to execute the handler, if false, print usage
				if (node.handler.execute(sender, match, args)) return true;
				AutoRefCommand cAnnotation = node.handler.method.getAnnotation(AutoRefCommand.class);

				String usage = cAnnotation.usage();
				if ("".equals(usage)) usage = cAnnotation.argmax() == 0
					? "<command>" : "<command> [args?]";

				// show the usage string with the partial command
				sender.sendMessage(ChatColor.DARK_RED + usage.replace("<command>", partialcmd));
				return true;
			}

			// show possible branches in the command tree
			String options = node.cmap.size() < 5 ? StringUtils.join(node.cmap.keySet(), '|') : "[args]";
			sender.sendMessage(ChatColor.DARK_RED + partialcmd + " " + options);

			return true;
		}
		catch (CommandPermissionException e)
		{ sender.sendMessage(ChatColor.DARK_GRAY + e.getMessage()); return true; }
		catch (UnrecognizedOptionException e)
		{ sender.sendMessage(ChatColor.DARK_GRAY + e.getMessage()); return true; }
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
	{
		try
		{
			HandlerNode node = cmap.get(command.getName());
			if (node == null) return null;

			// attempt to narrow down the method using the args
			for (int i = 0; i < args.length - 1; ++i)
			{
				// lowercase to maintain case insensitivity
				String arg = args[i].toLowerCase();

				HandlerNode next = node.cmap.get(arg);
				if (next == null) return null;

				// move on to the next node, increment the cut length
				node = next;
			}

			if (node.handler != null) return null;
			String partial = args[args.length - 1].toLowerCase();

			// show possible branches in the command tree
			List<String> opts = Lists.newArrayList();
			for (String c : node.cmap.keySet())
				if (c.toLowerCase().startsWith(partial)) opts.add(c);
			return opts;
		}
		catch (CommandPermissionException e)
		{ sender.sendMessage(ChatColor.DARK_GRAY + e.getMessage()); }

		return null;
	}

	private void setHandler(CommandDelegator handler, String[] cmd)
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
	protected CommandManager.CommandDelegator handler;

	public HandlerNode(CommandManager.CommandDelegator handler)
	{
		this.handler = handler;
		this.cmap = Maps.newHashMap();
	}

	protected List<AutoRefCommandHelpTopic> getHelpTopics(PluginCommand pcmd)
	{
		List<AutoRefCommandHelpTopic> topics = Lists.newArrayList();
		if (handler != null && handler.helpTopic != null)
		{
			for (String alias : pcmd.getAliases())
				topics.add(handler.helpTopic.copyAlias(alias));
			topics.add(handler.helpTopic);
		}

		for (HandlerNode child : cmap.values())
			topics.addAll(child.getHelpTopics(pcmd));
		return topics;
	}
}
