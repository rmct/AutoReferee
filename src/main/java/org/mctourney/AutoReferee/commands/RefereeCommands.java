package org.mctourney.AutoReferee.commands;

import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefPlayer;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.util.TeleportationUtil;
import org.mctourney.AutoReferee.util.commands.AutoRefCommand;
import org.mctourney.AutoReferee.util.commands.AutoRefPermission;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Maps;

public class RefereeCommands
{
	AutoReferee plugin;

	// save previous location before teleport
	private Map<String, Location> prevLocation;

	public RefereeCommands(Plugin plugin)
	{
		this.plugin = (AutoReferee) plugin;
		prevLocation = Maps.newHashMap();
	}

	@AutoRefCommand(name={"announce"})
	@AutoRefPermission(console=true)

	public boolean announce(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		String n = sender instanceof Player ? match.getDisplayName((Player) sender) : sender.getName();
		match.broadcast(String.format("<%s> %s", n, StringUtils.join(args, ' ')));
		return true;
	}

	@AutoRefCommand(name={"broadcast"})
	@AutoRefPermission(console=true)

	public boolean broadcast(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		match.broadcast(ChatColor.DARK_GRAY + "[B] " +
			ChatColor.GRAY + StringUtils.join(args, ' '));
		return true;
	}

	@AutoRefCommand(name={"viewinventory"}, argmax=1, options="p")
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.REFEREE)

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

	@AutoRefCommand(name={"artp"}, argmax=1, options="b*d*l*t*s*o*v*r")
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.REFEREE)

	public boolean teleport(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if there is no match, quit
		if (match == null) return false;
		Player player = (Player) sender;

		// just dump location into this for teleporting later
		Location tplocation = null;

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
			AutoRefTeam team = match.teamNameLookup(options.getOptionValue('s'));
			if (team != null) tplocation = team.getSpawnLocation();
			else tplocation = match.getWorld().getSpawnLocation();
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('o'))
		{
			AutoRefTeam team = match.teamNameLookup(options.getOptionValue('o'));
			if (team != null) tplocation = team.getLastObjectiveLocation();
			else tplocation = match.getLastObjectiveLocation();
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('v'))
		{
			AutoRefTeam team = match.teamNameLookup(options.getOptionValue('v'));
			if (team != null) tplocation = team.getVictoryMonumentLocation();
			tplocation = TeleportationUtil.locationTeleport(tplocation);
		}
		else if (options.hasOption('r'))
		{
			// get location in lookup table, or null
			tplocation = prevLocation.get(player.getName());
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
		if (tplocation != null)
		{
			prevLocation.put(player.getName(), player.getLocation());
			player.setFlying(true); player.teleport(tplocation);
		}
		else player.sendMessage(ChatColor.DARK_GRAY + "You cannot teleport to this location: invalid or unsafe.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "teamname"}, argmin=2, argmax=2)
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean setTeamName(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		// get the team with the specified name
		AutoRefTeam team = match.teamNameLookup(args[0]);
		if (team == null)
		{
			sender.sendMessage(ChatColor.DARK_GRAY + args[0] +
				ChatColor.RESET + " is not a valid team.");
			sender.sendMessage("Teams are " + match.getTeamList());
		}
		else team.setName(args[1]);
		return true;
	}

	@AutoRefCommand(name={"autoref", "swapteams"}, argmin=2, argmax=2)
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean swapTeams(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		AutoRefTeam team1 = match.teamNameLookup(args[0]);
		AutoRefTeam team2 = match.teamNameLookup(args[1]);

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

	@AutoRefCommand(name={"autoref", "countdown"}, argmax=0)
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

	@AutoRefCommand(name={"autoref", "timelimit"}, argmin=1, argmax=1)
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

	@AutoRefCommand(name={"autoref", "endmatch"}, argmax=1)
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.REFEREE)

	public boolean endMatch(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (args.length >= 1)
		{
			if ("none".equalsIgnoreCase(args[0]) || "tie".equalsIgnoreCase(args[0]))
				match.matchComplete(null);
			else match.matchComplete(match.teamNameLookup(args[0]));
		}
		else match.matchComplete();
		return true;
	}

	@AutoRefCommand(name={"autoref", "hud", "swap"}, argmax=0)
	@AutoRefPermission(console=false, role=AutoRefMatch.Role.REFEREE)

	public boolean swapHUD(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != null && sender instanceof Player)
			match.messageReferee((Player) sender, "match", match.getWorld().getName(), "swap");
		return true;
	}
}
