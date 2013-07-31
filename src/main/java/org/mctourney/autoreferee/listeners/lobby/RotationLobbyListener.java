package org.mctourney.autoreferee.listeners.lobby;

import java.util.Collections;
import java.util.List;
import java.io.IOException;
import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.event.match.MatchCompleteEvent;
import org.mctourney.autoreferee.event.match.MatchLoadEvent;
import org.mctourney.autoreferee.event.match.MatchUnloadEvent;
import org.mctourney.autoreferee.util.commands.AutoRefCommand;
import org.mctourney.autoreferee.util.commands.AutoRefPermission;

import com.google.common.collect.Lists;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;

public class RotationLobbyListener extends AutoLobbyListener
{
	public static final File ROTATION_FILE = new File("rotation.txt");

	private List<AutoRefMap> rotation = Lists.newArrayList();

	private int rotationCounter = 0;
	private AutoRefMatch currentMatch = null;
	private String currentMatchName = null;

	public RotationLobbyListener(AutoReferee plugin)
	{
		super(plugin);
		try
		{
			// loop through the map names in the rotation file to add to the list
			for (String mapname : FileUtils.readLines(ROTATION_FILE, "UTF-8"))
			{
				AutoRefMap map = AutoRefMap.getMap(mapname);
				if (map != null) rotation.add(map);
			}

			// shuffle the rotation
			Collections.shuffle(rotation);
		}
		catch (IOException e) { e.printStackTrace(); }

		// defer the task of loading the next map to the first server tick
		new BukkitRunnable() { @Override public void run() { loadNextMap(); } }.runTask(plugin);
	}

	@Override
	protected void lobbyLoadMap(Player player, AutoRefMap map)
	{
		if (currentMatch != null)
			currentMatch.joinMatch(player);
	}

	protected AutoRefMap getNextMap()
	{ return rotation.get(rotationCounter % rotation.size()); }

	protected AutoRefMap loadNextMap()
	{
		// load up the map with a recognizable name
		this.currentMatchName = String.format("world-autoref-R%04d-%x",
			rotationCounter, System.currentTimeMillis());

		AutoRefMap map = getNextMap(); ++rotationCounter;
		AutoRefMap.loadMap(Bukkit.getConsoleSender(), map, this.currentMatchName);
		return map;
	}

	@AutoRefCommand(name={"autoref", "nextmap"}, argmax=0,
		description="Get the next map in the rotation.")
	@AutoRefPermission(console=true, role=AutoRefMatch.Role.PLAYER)

	public boolean nextMap(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match != this.currentMatch) return false;

		AutoRefMap map = getNextMap();
		sender.sendMessage(ChatColor.GREEN + "Next map is " + map.getVersionString());
		return true;
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void matchLoad(MatchLoadEvent event)
	{
		AutoRefMatch match = event.getMatch();
		if (match.getWorld().getName().equals(this.currentMatchName))
		{
			// update the current match BEFORE we unload
			AutoRefMatch prev = this.currentMatch;
			this.currentMatch = match;

			// if there was a match previously loaded, unload it
			if (prev != null)
			{
				for (Player player : prev.getWorld().getPlayers())
					player.teleport(match.getPlayerSpawn(player));
				prev.destroy(MatchUnloadEvent.Reason.COMPLETE);
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void playerLogin(PlayerJoinEvent event)
	{
		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
		{
			this.currentMatch.joinMatch(event.getPlayer());
			new PlayerJoinTask(this.currentMatch, event.getPlayer()).runTask(plugin);
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void playerTeleport(PlayerTeleportEvent event)
	{
		if (event.getTo().getWorld() == plugin.getLobbyWorld())
		{
			event.setTo(this.currentMatch.getPlayerSpawn(event.getPlayer()));
			new PlayerJoinTask(this.currentMatch, event.getPlayer()).runTask(plugin);
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void playerChangeWorld(PlayerChangedWorldEvent event)
	{
		if (event.getPlayer().getWorld() == this.currentMatch.getWorld())
			new PlayerJoinTask(this.currentMatch, event.getPlayer()).runTask(plugin);
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void matchComplete(MatchCompleteEvent event)
	{
		if (event.getMatch() == this.currentMatch && AutoRefMatch.COMPLETED_SECONDS >= 15)
			new BukkitRunnable()
			{
				@Override
				public void run()
				{
					currentMatch.broadcast(ChatColor.GREEN + "Coming next: " +
						ChatColor.RESET + getNextMap().getVersionString());
				}
			// announce the next match 5 seconds after the match ends
			}.runTaskLater(plugin, 5 * 20L);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void matchUnload(MatchUnloadEvent event)
	{
		// if this is the current match in the rotation, defer unloading
		// until we load the next match and move everyone there
		if (event.getMatch() == this.currentMatch)
		{
			AutoRefMap map = loadNextMap();
			currentMatch.broadcast(ChatColor.GREEN + "Now loading: " +
				ChatColor.RESET + map.getVersionString());
			event.setCancelled(true);
		}
	}
}
