package org.mctourney.autoreferee.commands;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import org.apache.commons.lang.StringUtils;

import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.event.match.MatchUnloadEvent;
import org.mctourney.autoreferee.util.commands.AutoRefCommand;
import org.mctourney.autoreferee.util.commands.AutoRefPermission;
import org.mctourney.autoreferee.util.commands.CommandHandler;

import org.apache.commons.cli.CommandLine;

import com.google.common.collect.Lists;

public class AdminCommands implements CommandHandler
{
	AutoReferee plugin;

	public AdminCommands(Plugin plugin)
	{
		this.plugin = (AutoReferee) plugin;
	}

	@AutoRefCommand(name={"autoref", "world"}, argmin=1, argmax=1,
		description="Specifies the name of the world for console commands to modify.")
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean setConsoleWorld(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		plugin.setConsoleWorld(args[0]);
		World world = plugin.getConsoleWorld();

		sender.sendMessage("Selected world: " + (world == null ? "<none>" : world.getName()));
		return world != null;
	}

	@AutoRefCommand(name={"autoref", "setlobby"}, argmax=0,
		description="Sets the current world to be the AutoReferee lobby world.")
	@AutoRefPermission(console=false, nodes={"autoreferee.admin"})

	public boolean setLobbyWorld(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != null) return false;
		World lobby = ((Player) sender).getWorld();

		plugin.setLobbyWorld(lobby);
		sender.sendMessage(ChatColor.GREEN + lobby.getName() +
			" is the new AutoReferee lobby world.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "load"}, argmin=1, options="c+x",
		description="Loads a map by name, case insensitive.")
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean loadMap(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// generate a map name from the args
		String mapName = StringUtils.join(args, " ");

		// may specify a custom world name
		String customName = options.getOptionValue('c');

		// get world setup for match
		sender.sendMessage(ChatColor.GREEN + "Please wait...");
		AutoRefMap.loadMap(sender, mapName, customName);

		return true;
	}

	@AutoRefCommand(name={"autoref", "loadurl"}, argmin=1, argmax=1, options="c+x",
		description="Loads a map from a remote zip file, taking the URL as a parameter.")
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean loadMapFromURL(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// may specify a custom world name
		String customName = options.getOptionValue('c');

		// get world setup for match
		sender.sendMessage(ChatColor.GREEN + "Please wait...");
		AutoRefMap.loadMapFromURL(sender, args[0], customName);

		return true;
	}

	@AutoRefCommand(name={"autoref", "unload"}, argmin=0, argmax=0,
		description="Unloads the current map. Connected players are either moved to the lobby or kicked.")
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean unloadMap(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != null) match.destroy(MatchUnloadEvent.Reason.COMMAND);
		else sender.sendMessage(ChatColor.GRAY + "No world to unload.");

		return true;
	}

	@AutoRefCommand(name={"autoref", "reload"}, argmin=0, argmax=0,
		description="Reloads the current map to its original, unmodified state. Players are migrated to the new copy.")
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

		match.destroy(MatchUnloadEvent.Reason.COMMAND);
		return true;
	}

	@AutoRefCommand(name={"autoref", "maplist"}, argmin=0, argmax=0,
		description="List all maps available, both on this server and in the repository.")
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

	@AutoRefCommand(name={"autoref", "update"}, argmin=0, argmax=0, options="f",
		description="Updates maps installed on server. Use -f to force an update.")
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean updateMaps(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		AutoRefMap.getUpdates(sender, options.hasOption('f'));
		return true;
	}

	@AutoRefCommand(name={"autoref", "autoinvite"}, argmin=1,
		description="Invite player(s) to participate in match. Works for offline players as well.")
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

	@AutoRefCommand(name={"autoref", "pmsend"}, argmin=1,
		description="Send plugin message.")
	@AutoRefPermission(console=false, nodes={"autoreferee.admin"})

	public boolean sendPluginMessage(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (!(sender instanceof Player) || !sender.isOp()) return false;
		AutoRefMatch.messageReferee((Player) sender, StringUtils.join(args, " "));

		return true;
	}
}
