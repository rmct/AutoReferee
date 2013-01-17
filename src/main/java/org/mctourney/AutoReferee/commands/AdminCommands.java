package org.mctourney.AutoReferee.commands;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoRefMap;
import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.util.commands.AutoRefCommand;
import org.mctourney.AutoReferee.util.commands.AutoRefPermission;

import org.apache.commons.cli.CommandLine;

import com.google.common.collect.Lists;

public class AdminCommands
{
	AutoReferee plugin;

	public AdminCommands(Plugin plugin)
	{
		this.plugin = (AutoReferee) plugin;
	}

	@AutoRefCommand(name={"autoref", "world"}, argmin=1, argmax=1)
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean setConsoleWorld(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		plugin.setConsoleWorld(args[0]);
		World world = plugin.getConsoleWorld();

		sender.sendMessage("Selected world: " + world.getName());
		return world != null;
	}

	@AutoRefCommand(name={"autoref", "load"}, argmin=1, argmax=2)
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean loadMap(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// get generate a map name from the args
		String mapName = args[0];

		// may specify a custom world name as the 3rd argument
		String customName = args.length < 2 ? null : args[1];

		// get world setup for match
		sender.sendMessage(ChatColor.GREEN + "Please wait...");
		AutoRefMap.loadMap(sender, mapName, customName);

		return true;
	}

	@AutoRefCommand(name={"autoref", "loadurl"}, argmin=1, argmax=2)
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean loadMapFromURL(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// may specify a custom world name
		String customName = args.length < 2 ? null : args[1];

		// get world setup for match
		sender.sendMessage(ChatColor.GREEN + "Please wait...");
		AutoRefMap.loadMapFromURL(sender, args[0], customName);

		return true;
	}

	@AutoRefCommand(name={"autoref", "unload"}, argmin=0, argmax=0)
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean unloadMap(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != null) match.destroy();
		return true;
	}

	@AutoRefCommand(name={"autoref", "reload"}, argmin=0, argmax=0)
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean reloadMap(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
		throws IOException
	{
		if (match == null) return false;

		AutoRefMap map = AutoRefMap.getMap(match.getMapName());
		if (map == null || !map.isInstalled())
		{
			sender.sendMessage(ChatColor.DARK_GRAY +
				"No archive of this map exists " + match.getMapName());
			return true;
		}

		sender.sendMessage(ChatColor.DARK_GRAY +
			"Preparing a new copy of " + map.getVersionString());

		AutoRefMatch newmatch = AutoRefMap.createMatch(map, null);
		for (Player p : match.getWorld().getPlayers())
			p.teleport(newmatch.getWorldSpawn());

		match.destroy();
		return true;
	}

	@AutoRefCommand(name={"autoref", "maplist"}, argmin=0, argmax=0)
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean getMapList(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		List<AutoRefMap> maps = Lists.newArrayList(AutoRefMap.getAvailableMaps());
		Collections.sort(maps);

		sender.sendMessage(ChatColor.GOLD + String.format("Available Maps (%d):", maps.size()));
		for (AutoRefMap mapInfo : maps)
		{
			ChatColor color = mapInfo.isInstalled() ? ChatColor.WHITE : ChatColor.DARK_GRAY;
			sender.sendMessage("* " + color + mapInfo.getVersionString());
		}

		return true;
	}

	@AutoRefCommand(name={"autoref", "update"}, argmin=0, argmax=0, options="f")
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean updateMaps(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		AutoRefMap.getUpdates(sender, options.hasOption('f'));
		return true;
	}

	@AutoRefCommand(name={"autoref", "autoinvite"}, argmin=1)
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean autoInvite(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != null) for (int i = 0; i < args.length; ++i)
		{
			// first, remove this player from all expected player lists
			OfflinePlayer opl = plugin.getServer().getOfflinePlayer(args[i]);
			for (AutoRefMatch m : plugin.getMatches()) m.removeExpectedPlayer(opl);

			// add them to the expected players list
			match.addExpectedPlayer(opl);

			// if this player cannot be found, skip
			Player invited = plugin.getServer().getPlayer(args[i]);
			if (invited == null) continue;

			// if this player is already busy competing in a match, skip
			AutoRefMatch m = plugin.getMatch(invited.getWorld());
			if (m != null && m.isPlayer(invited) && m.getCurrentState().inProgress()) continue;

			// otherwise, let's drag them in (no asking)
			match.joinMatch(invited);
		}

		return true;
	}
}
