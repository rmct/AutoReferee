package org.mctourney.autoreferee.commands;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefSpectator;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.util.PlayerKit;
import org.mctourney.autoreferee.util.TeleportationUtil;
import org.mctourney.autoreferee.util.LocationUtil;
import org.mctourney.autoreferee.util.commands.AutoRefCommand;
import org.mctourney.autoreferee.util.commands.AutoRefPermission;
import org.mctourney.autoreferee.util.commands.CommandHandler;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Maps;

public class SpectatorCommands implements CommandHandler
{
	AutoReferee plugin;

	public SpectatorCommands(Plugin plugin)
	{
		this.plugin = (AutoReferee) plugin;
	}

	@AutoRefCommand(name={"announce"},
		description="Announce a message to your current match. Your name will be shown.",
		usage="<command> <announcement message>")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean announce(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		String n = sender instanceof Player ? match.getDisplayName((Player) sender) : sender.getName();
		match.broadcast(String.format("<%s> %s", n, StringUtils.join(args, ' ')));
		return true;
	}

	@AutoRefCommand(name={"broadcast"},
		description="Broadcast a message to your current match. No name will be shown.",
		usage="<command> <broadcast message>")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean broadcast(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		match.broadcast(ChatColor.DARK_GRAY + "[B] " +
			ChatColor.GRAY + StringUtils.join(args, ' '));
		return true;
	}

	@AutoRefCommand(name={"viewinventory"}, argmax=1, options="p",
		description="View the inventory of the nearest player. Specify a name to show that player's inventory.",
		usage="<command> [<player name>]",
		opthelp=
		{
			"p", "show last inventory (pre-death)",
		})
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.SPECTATOR)

	public boolean viewInventory(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if there is no match, quit
		if (match == null) return false;

		Player player = (Player) sender;
		AutoRefPlayer target = args.length > 0 ? match.getPlayer(args[0])
			: match.getNearestPlayer(player.getLocation());

		// if there is a target, show an inventory
		if (target != null)
		{
			// if -p, show previous target's inventory
			if (options.hasOption('p')) target.showSavedInventory(player);
			else target.showInventory(player);
		}

		return true;
	}

	@AutoRefCommand(name={"artp"}, argmax=1, options="b*d*l*t*s*o*v*r",
		description="AutoReferee teleportation tools.",
		usage="<command> [<player name>]",
		opthelp=
		{
			"b", "teleport to player's bed location",
			"d", "teleport to player's death location",
			"l", "teleport to player's logout location",
			"t", "teleport to player's teleport location",
			"s", "teleport to player's spawn location",
			"o", "teleport to team's objective location",
			"v", "teleport to team's victory monument",
			"r", "teleport to previous location",
		})
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.SPECTATOR)

	public boolean teleport(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if there is no match, quit
		if (match == null) return false;
		Player player = (Player) sender;

		// just dump location into this for teleporting later
		Location tplocation = null;
		String targetcoords = "";

		if (options.hasOption('b'))
		{
			AutoRefPlayer apl = match.getPlayer(options.getOptionValue('b'));
			Location bedloc = apl == null ? null : apl.getBedLocation();
			if (bedloc == null)
			{
				player.sendMessage(apl.getDisplayName() + ChatColor.DARK_GRAY + " does not have a bed set.");
				return true;
			}
			else tplocation = TeleportationUtil.blockTeleport(bedloc);
		}
		else if (options.hasOption('d'))
		{
			AutoRefPlayer apl = match.getPlayer(options.getOptionValue('d'));
			if (apl != null) tplocation = apl.getLastDeathLocation();
			else tplocation = match.getLastDeathLocation();

			targetcoords = "(" + LocationUtil.toBlockCoords(tplocation) + ")";
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('l'))
		{
			AutoRefPlayer apl = match.getPlayer(options.getOptionValue('l'));
			if (apl != null) tplocation = apl.getLastLogoutLocation();
			else tplocation = match.getLastLogoutLocation();
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('t'))
		{
			AutoRefPlayer apl = match.getPlayer(options.getOptionValue('t'));
			if (apl != null) tplocation = apl.getLastTeleportLocation();
			else tplocation = match.getLastTeleportLocation();
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('s'))
		{
			AutoRefTeam team = match.getTeam(options.getOptionValue('s'));
			if (team != null) tplocation = team.getSpawnLocation();
			else tplocation = match.getWorld().getSpawnLocation();
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('o'))
		{
			AutoRefTeam team = match.getTeam(options.getOptionValue('o'));
			if (team != null) tplocation = team.getLastObjectiveLocation();
			else tplocation = match.getLastObjectiveLocation();
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('v'))
		{
			AutoRefTeam team = match.getTeam(options.getOptionValue('v'));
			if (team != null) tplocation = team.getVictoryMonumentLocation();
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('r'))
		{
			tplocation = plugin.getMatch(player.getWorld()).getSpectator(player).prevLocation();
		}
		else if (args.length > 0)
		{
			Player pl = plugin.getServer().getPlayer(args[0]);
			if (pl != null) tplocation = match.isPlayer(pl)
				? TeleportationUtil.entityTeleport(pl) : pl.getLocation();
		}
		// if no arguments were passed, teleport to last notification
		else tplocation = match.getLastNotificationLocation();

		// if we ever found a valid teleport, take it!
		if (tplocation != null && tplocation.getWorld() == player.getWorld())
		{
			plugin.getMatch(player.getWorld()).getSpectator(player).setPrevLocation(player.getLocation());
			player.setFlying(true); player.teleport(tplocation);
		}
		else player.sendMessage(ChatColor.DARK_GRAY + "You cannot teleport to this location: invalid or unsafe. " + ChatColor.GRAY + targetcoords);
		return true;
	}

	@AutoRefCommand(name={"autoref", "preview"}, argmax=0, options="yn",
		description="Cycle through players.",
		opthelp=
		{
			"y", "activate match preview mode",
			"n", "deactivate match preview mode",
		})
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.REFEREE)

	public boolean previewMode(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null || !match.getCurrentState().isBeforeMatch()) return false;
		if (!options.hasOption('y') && !options.hasOption('n'))
		{
			sender.sendMessage(ChatColor.GREEN + "You are attempting to put this match in preview mode");
			sender.sendMessage(ChatColor.GREEN + "Type '/ar preview -y' to confirm that you want to do this.");
			return true;
		}

		match.setPreviewMode(options.hasOption('y'));
		match.setupSpectators();

		for (Player player : match.getWorld().getPlayers())
		{
			player.sendMessage(ChatColor.GREEN + "This match is now in PREVIEW mode!");
			player.sendMessage(ChatColor.GREEN + "You may join a team and begin playing when you are ready.");
		}
		return true;
	}

	@AutoRefCommand(name={"autoref", "cycle"}, argmax=0, options="np",
		description="Cycle through players.",
		opthelp=
		{
			"n", "next player in cycle (default)",
			"p", "previous player in cycle",
		})
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.SPECTATOR)

	public boolean playerCycle(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		Player player = (Player) sender;

		if (options.hasOption('p'))
			match.getSpectator(player).cyclePrevPlayer();
		else match.getSpectator(player).cycleNextPlayer();

		return true;
	}

	@AutoRefCommand(name={"autoref", "teamname"}, argmin=2, argmax=2,
		description="Rename a team.",
		usage="<command> <old name> <new name>")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean setTeamName(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		// get the team with the specified name
		AutoRefTeam team = match.getTeam(args[0]);
		if (team == null)
		{
			sender.sendMessage(ChatColor.DARK_GRAY + args[0] +
				ChatColor.RESET + " is not a valid team.");
			sender.sendMessage("Teams are " + match.getTeamList());
		}
		else team.setName(args[1]);
		return true;
	}

	@AutoRefCommand(name={"autoref", "swapteams"}, argmin=2, argmax=2,
		description="Swaps the players and custom names of both teams.",
		usage="<command> <team1> <team2>")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.SPECTATOR)

	public boolean swapTeams(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		AutoRefTeam team1 = match.getTeam(args[0]);
		AutoRefTeam team2 = match.getTeam(args[1]);

		if (team1 == null)
		{
			sender.sendMessage(ChatColor.DARK_GRAY + args[0] +
				ChatColor.RESET + " is not a valid team.");
			sender.sendMessage("Teams are " + match.getTeamList());
		}
		else if (team2 == null)
		{
			sender.sendMessage(ChatColor.DARK_GRAY + args[1] +
				ChatColor.RESET + " is not a valid team.");
			sender.sendMessage("Teams are " + match.getTeamList());
		}
		else AutoRefTeam.switchTeams(team1, team2);
		return true;
	}

	@AutoRefCommand(name={"autoref", "countdown"}, argmax=1,
		description="Begin a generic countdown.",
		usage="<command> [<countdown length>]")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean countdown(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		int sec = 3;
		if (args.length > 0)
			try { sec = Integer.parseInt(args[0]); }
			catch (NumberFormatException e) {  }

		match.startCountdown(sec, false);
		return true;
	}

	@AutoRefCommand(name={"autoref", "givekit"}, argmin=2, argmax=2,
		description="Give a kit to the specified player.",
		usage="<command> <kit name> <player name>")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean giveKit(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		Player target = Bukkit.getPlayer(args[1]);
		if (target != null && match == null)
			match = AutoReferee.getInstance().getMatch(target.getWorld());

		// if there is no match, quit
		if (match == null) return false;

		// get the kit with this name (case sensitive)
		PlayerKit kit = match.getKit(args[0]);
		if (kit == null) { sender.sendMessage("Not a valid kit: " + args[0]); return true; }

		// get the receiver
		AutoRefPlayer apl = match.getPlayer(args[1]);
		if (apl == null) { sender.sendMessage("Not a valid player: " + args[1]); return true; }

		kit.giveTo(apl);
		sender.sendMessage("Gave kit " + ChatColor.GOLD + kit.getName() +
			ChatColor.RESET + " to player " + apl.getDisplayName());
		return true;
	}

	@AutoRefCommand(name={"autoref", "timelimit"}, argmin=1, argmax=1,
		description="Specify the total time allowed for this match, in minutes.",
		usage="<command> <time limit>")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean setTimeLimit(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		long time = -1L;
		if (!"none".equalsIgnoreCase(args[0]))
			try { time = Integer.parseInt(args[0]) * 60L; }
			catch (NumberFormatException e) { return true; }

		// set the time limit
		match.setTimeLimit(time);
		return true;
	}

	@AutoRefCommand(name={"autoref", "endmatch"}, argmax=1,
		description="Ends the current match by decision. Specify a team name to declare them " +
			"the winner, or 'tie' to announce a tie.",
		usage="<command> [<winning team>]")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean endMatch(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (args.length >= 1)
		{
			if ("none".equalsIgnoreCase(args[0]) || "tie".equalsIgnoreCase(args[0]))
				match.endMatch(null);
			else match.endMatch(match.getTeam(args[0]));
		}
		else match.endMatch();
		return true;
	}

	@AutoRefCommand(name={"autoref", "hud", "swap"}, argmax=0,
		description="If using the AutoReferee client mod, swaps sides for the two team listings.")
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.REFEREE)

	public boolean swapHUD(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != null && sender instanceof Player)
			AutoRefMatch.messageReferee((Player) sender, "match", match.getWorld().getName(), "swap");
		return true;
	}

	@AutoRefCommand(name={"autoref", "nightvis"}, argmax=0, options="x",
		description="Toggles permanent night vision for a spectator.",
		opthelp=
		{
			"x", "explicitly disable night vision",
		})
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.SPECTATOR)

	public boolean nightVision(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != null)
		{
			AutoRefSpectator spectator = match.getSpectator((Player) sender);

			if (spectator.hasNightVision() && !options.hasOption('x'))
				sender.sendMessage(ChatColor.GREEN + "Use '/ar nightvis -x' to disable night vision.");
			else spectator.setNightVision(!options.hasOption('x'));
		}
		return true;
	}

	@AutoRefCommand(name={"autoref", "streamer"}, argmax=1, options="x",
		description="Toggles streamer",
		usage="<command> [<player name>]",
		opthelp=
		{
			"x", "explicitly disable streamer mode",
		})
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.SPECTATOR)

	public boolean setStreamerMode(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		Player player = null;

		if (sender instanceof Player) player = (Player) sender;

		if (args.length > 0)
		{
			// only admins have permission to change someone else's streamer mode
			if (!sender.hasPermission("autoreferee.admin")) return false;

			Player target = Bukkit.getPlayer(args[0]);
			if (target == null)
			{
				sender.sendMessage(ChatColor.RED + "Not a valid player: " + args[0]);
				return true;
			}
			else player = target;
		}

		if (match != null && player != null)
		{
			AutoRefSpectator spectator = match.getSpectator(player);
			if (spectator == null) return false;

			if (spectator.isStreamer() && !options.hasOption('x'))
				sender.sendMessage(ChatColor.RED + "Use '/ar streamer -x' to disable streaming mode.");
			else spectator.setStreamer(!options.hasOption('x'));

			if (player != sender)
			{
				String enabled = spectator.isStreamer() ? "Enabled" : "Disabled";
				sender.sendMessage(ChatColor.GREEN + enabled + " streamer mode for " + player.getName());
			}
		}
		return true;
	}
}
