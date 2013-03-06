package org.mctourney.AutoReferee.commands;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.BooleanPrompt;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.util.commands.AutoRefCommand;
import org.mctourney.AutoReferee.util.commands.AutoRefPermission;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;

public class PlayerCommands
{
	AutoReferee plugin;

	public PlayerCommands(Plugin plugin)
	{
		this.plugin = (AutoReferee) plugin;
	}

	@AutoRefCommand(name={"matchinfo"}, argmax=0)
	@AutoRefPermission(console=true)

	public boolean matchInfo(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != null) match.sendMatchInfo(sender);
		else sender.sendMessage(ChatColor.GRAY +
			plugin.getName() + " is not running for this world!");

		return true;
	}

	@AutoRefCommand(name={"jointeam"})
	@AutoRefPermission(console=false)

	public boolean joinTeam(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if there is no match, or the plugin is running in auto-mode, quit
		if (match == null || plugin.isAutoMode()) return false;
		boolean isref = sender.hasPermission("autoreferee.referee");

		// get the target team
		AutoRefTeam team = args.length > 0 ? match.teamNameLookup(args[0]) :
			match.getArbitraryTeam();

		if (team == null)
		{
			// team name is invalid. let the player know
			if (args.length > 0)
			{
				sender.sendMessage(ChatColor.DARK_GRAY + args[0] +
					ChatColor.RESET + " is not a valid team.");
				sender.sendMessage("Teams are " + match.getTeamList());
			}
			return true;
		}

		// if there are players specified on the command line, add them
		if (args.length > 1 && isref) for (int i = 1; i < args.length; ++i)
		{
			Player target = plugin.getServer().getPlayer(args[i]);
			if (target != null) match.joinTeam(target, team, true);
		}

		// otherwise, add yourself
		else if (sender instanceof Player)
			match.joinTeam((Player) sender, team, isref);
		return true;
	}

	@AutoRefCommand(name={"leaveteam"})
	@AutoRefPermission(console=false)

	public boolean leaveTeam(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if there is no match, or the plugin is running in auto-mode, quit
		if (match == null || plugin.isAutoMode()) return false;
		boolean isref = sender.hasPermission("autoreferee.referee");

		// if there are players specified on the command line, remove them
		if (args.length > 0 && isref) for (int i = 0; i < args.length; ++i)
		{
			Player target = plugin.getServer().getPlayer(args[i]);
			if (target != null) match.leaveTeam(target, true);
		}

		// otherwise, remove yourself
		else if (sender instanceof Player)
			match.leaveTeam((Player) sender, isref);
		return true;
	}

	@AutoRefCommand(name={"joinmatch"}, argmin=0, argmax=1)
	@AutoRefPermission(console=false)

	public boolean joinMatch(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if the plugin is running in auto-mode, quit
		if (plugin.isAutoMode()) return false;

		if (args.length == 0)
		{
			sender.sendMessage(ChatColor.DARK_GRAY + "Available matches:");
			List<String> lines = Lists.newLinkedList();

			for (AutoRefMatch m : plugin.getMatches())
			{
				List<Player> players = m.getWorld().getPlayers();
				if (m.access == AutoRefMatch.AccessType.PUBLIC && players.size() > 0)
					lines.add("* " + ChatColor.GRAY + m.getMapName() + " v" + m.getMapVersion() +
						ChatColor.RESET + " with " + ChatColor.RED + players.get(0).getName());
			}

			if (lines.size() == 0) sender.sendMessage(ChatColor.GRAY + "None available. Create one now!");
			else for (String line : lines) sender.sendMessage(line);

			return true;
		}

		// if the player is preoccupied, don't let this happen
		if (match != null && match.isPlayer((OfflinePlayer) sender) &&
			match.getCurrentState().inProgress()) return false;

		// get the player this command is targeting
		Player player = plugin.getServer().getPlayer(args[0]);
		if (player == null) return true;

		// the match we are interested in is the match they are trying to join
		match = plugin.getMatch(player.getWorld());

		if (match != null)
		{
			if (!sender.hasPermission("autoreferee.referee") && match.access != AutoRefMatch.AccessType.PUBLIC)
				sender.sendMessage(ChatColor.RED + "You do not have permission to join this match.");
			else match.joinMatch((Player) sender);
		}
		return true;
	}

	@AutoRefCommand(name={"leavematch"}, argmax=1)
	@AutoRefPermission(console=true)

	public boolean leaveMatch(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if the plugin is running in auto-mode, quit
		if (plugin.isAutoMode()) return false;
		Player player = sender instanceof Player ? (Player) sender : null;

		if (sender.hasPermission("autoreferee.referee") && args.length > 0)
			match = plugin.getMatch((player = plugin.getServer().getPlayer(args[0])).getWorld());

		if (match != null) match.ejectPlayer(player);
		return true;
	}

	@AutoRefCommand(name={"setaccess"}, argmin=1, argmax=1, options="q")
	@AutoRefPermission(console=true)

	public boolean setAccess(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if the plugin is running in auto-mode, quit
		if (match == null || plugin.isAutoMode()) return false;
		AutoRefMatch.AccessType access = match.access;

		try { access = AutoRefMatch.AccessType.valueOf(args[0].toUpperCase()); }
		catch (Exception e) { return true; }

		if (access != null) match.access = access;
		sender.sendMessage(ChatColor.DARK_GRAY + "This match is now " + ChatColor.RED + match.access.name());

		if (access == AutoRefMatch.AccessType.PUBLIC && sender instanceof Player && !options.hasOption('q') &&
			plugin.getLobbyWorld() != null) for (Player p : plugin.getLobbyWorld().getPlayers())
		{
			p.sendMessage(ChatColor.DARK_GRAY + sender.getName() + "'s " + match.getMapName() + " is now public");
			p.sendMessage(ChatColor.DARK_GRAY + "Type " + ChatColor.RED + "/joinmatch " + sender.getName() +
				ChatColor.DARK_GRAY + " to join!");
		}

		return true;
	}

	@AutoRefCommand(name={"ready"}, argmax=1, options="tfyns+")
	@AutoRefPermission(console=true)

	public boolean ready(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if there is no match in progress, or the match has started, quit
		if (match == null || !match.getCurrentState().isBeforeMatch()) return false;

		boolean rstate = !options.hasOption('f') && !options.hasOption('n');
		Player player = sender instanceof Player ? (Player) sender : null;

		// if console or referee sends this message
		if (player == null || match.isReferee(player))
		{
			// attempt to set the ready delay if one is specified
			try { if (options.hasOption('s')) match.setReadyDelay(Integer.parseInt(options.getOptionValue('s'))); }
			catch (NumberFormatException e) {  };

			match.setRefereeReady(rstate);
		}
		else
		{
			AutoRefTeam team = match.getPlayerTeam(player);
			if (team != null) team.setReady(rstate);
		}

		match.checkTeamsStart();
		return true;
	}

	@AutoRefCommand(name={"autoref", "version"}, argmax=0)
	@AutoRefPermission(console=true)

	public boolean getVersion(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		sender.sendMessage(ChatColor.DARK_GRAY + "This server is running " +
			ChatColor.BLUE + plugin.getDescription().getFullName() +
			ChatColor.GRAY + " (" + plugin.getMD5sum().substring(0, 8) + ")");
		return true;
	}

	@AutoRefCommand(name={"notify"})
	@AutoRefPermission(console=false)

	public boolean notify(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		match.notify(((Player) sender).getLocation(), StringUtils.join(args, ' '));
		return true;
	}

	@AutoRefCommand(name={"autoref", "invite"}, argmin=1)
	@AutoRefPermission(console=true)

	public boolean invitePlayers(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null || !match.getCurrentState().isBeforeMatch()) return false;

		// who is doing the inviting
		String from = (sender instanceof Player)
			? match.getDisplayName((Player) sender) : "This server";

		for (int i = 0; i < args.length; ++i)
		{
			// if this player cannot be found, skip
			Player invited = plugin.getServer().getPlayer(args[i]);
			if (invited == null) continue;

			// if this player is already busy competing in a match, skip
			AutoRefMatch m = plugin.getMatch(invited.getWorld());
			if (m != null && m.isPlayer(invited) && m.getCurrentState().inProgress()) continue;

			// otherwise, invite them
			if (invited.getWorld() != match.getWorld())
				new Conversation(plugin, invited, new InvitationPrompt(match, from)).begin();
		}
		return true;
	}
}

class InvitationPrompt extends BooleanPrompt
{
	public InvitationPrompt(AutoRefMatch match, String from)
	{ this.match = match; this.from = from; }

	private AutoRefMatch match;
	private String from;

	@Override
	public String getPromptText(ConversationContext context)
	{
		return ChatColor.GREEN + String.format(">>> %s is inviting you to %s.",
			from, match.getMapName()) + " Do you accept?";
	}

	@Override
	protected Prompt acceptValidatedInput(final ConversationContext context, boolean response)
	{
		if (response && context.getForWhom() instanceof Player)
			new BukkitRunnable()
			{
				@Override public void run()
				{ match.joinMatch((Player) context.getForWhom()); }

			}.runTask(AutoReferee.getInstance());
		return Prompt.END_OF_CONVERSATION;
	}
}
