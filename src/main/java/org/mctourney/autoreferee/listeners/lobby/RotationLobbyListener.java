package org.mctourney.autoreferee.listeners.lobby;

import java.util.List;
import java.util.logging.Level;
import java.io.IOException;
import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.event.match.MatchCompleteEvent;
import org.mctourney.autoreferee.event.match.MatchLoadEvent;
import org.mctourney.autoreferee.event.match.MatchStartEvent;
import org.mctourney.autoreferee.event.match.MatchUnloadEvent;
import org.mctourney.autoreferee.event.player.PlayerMatchJoinEvent;
import org.mctourney.autoreferee.event.player.PlayerTeamJoinEvent;
import org.mctourney.autoreferee.util.commands.AutoRefCommand;
import org.mctourney.autoreferee.util.commands.AutoRefPermission;

import com.google.common.collect.Lists;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;

public class RotationLobbyListener extends LobbyListener
{
	public static final File ROTATION_FILE = new File("rotation.txt");

	private List<AutoRefMap> rotation = Lists.newArrayList();

	private int rotationCounter = 0;
	private AutoRefMatch currentMatch = null;
	private String currentMatchName = null;

	public RotationLobbyListener(AutoReferee plugin)
	{
		super(plugin);
		AutoRefMatch.giveMatchInfoBooks = false;

		try
		{
			// loop through the map names in the rotation file to add to the list
			for (String mapname : FileUtils.readLines(ROTATION_FILE, "UTF-8"))
			{
				AutoRefMap map = AutoRefMap.getMap(mapname);
				if (map != null) rotation.add(map);
			}
		}
		catch (IOException e) { e.printStackTrace(); }

		// print the rotation to the server log
		AutoReferee.log(String.format("AutoReferee Rotation (%d maps)", rotation.size()), Level.INFO);
		for (AutoRefMap map : rotation)
			AutoReferee.log(String.format(">>> %s", map.getVersionString()), Level.INFO);

		// defer the task of loading the next map to the first server tick
		new BukkitRunnable() { @Override public void run() { loadNextMap(); } }.runTask(plugin);
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

	public void playerJoinMatch(AutoRefMatch match, Player player)
	{
		if (match.getCurrentState().isBeforeMatch() && !player.hasPermission("autoreferee.referee"))
			match.joinTeam(player, match.getArbitraryTeam(), PlayerTeamJoinEvent.Reason.AUTOMATIC, false);
	}

	@EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
	public void playerLogin(PlayerJoinEvent event)
	{
		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
		{
			this.currentMatch.joinMatch(event.getPlayer());
			playerJoinMatch(this.currentMatch, event.getPlayer());
		}
	}

	@EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
	public void playerTeleport(PlayerTeleportEvent event)
	{
		if (event.getTo().getWorld() == plugin.getLobbyWorld())
		{
			event.setTo(this.currentMatch.getPlayerSpawn(event.getPlayer()));
			playerJoinMatch(this.currentMatch, event.getPlayer());
		}
	}

	@Override
	protected void lobbyLoadMap(Player player, AutoRefMap map)
	{
		if (currentMatch != null)
			currentMatch.joinMatch(player);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void matchJoin(PlayerMatchJoinEvent event)
	{
		playerJoinMatch(event.getMatch(), event.getPlayer());
	}

	private class MatchStarterTask extends BukkitRunnable
	{
		private final AutoRefMatch match;

		public MatchStarterTask(AutoRefMatch match)
		{ this.match = match; }

		@Override
		public void run()
		{
			// starting a match is based purely on teams being filled
			boolean ready = true;
			for (AutoRefTeam t : match.getTeams())
				ready &= t.getPlayers().size() >= t.getMaxSize();
			if (ready) match.startMatch(MatchStartEvent.Reason.AUTOMATIC);
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void teamJoin(PlayerTeamJoinEvent event)
	{
		AutoRefTeam team = event.getTeam();
		if (team.getPlayers().size() >= team.getMaxSize())
			event.setCancelled(true);

		// schedule a check to see if we need to start the match
		if (team.getMatch().getCurrentState().isBeforeMatch())
			new MatchStarterTask(team.getMatch()).runTask(plugin);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void matchStart(MatchStartEvent event)
	{
		// if the match is being manually started, let it happen(?)
		if (event.getReason() == MatchStartEvent.Reason.READY) return;

		boolean canStart = true;
		for (AutoRefTeam t : event.getMatch().getTeams())
			canStart &= t.getPlayers().size() >= t.getMinSize();
		if (!canStart) event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void matchComplete(MatchCompleteEvent event)
	{
		if (event.getMatch() == this.currentMatch)
			new BukkitRunnable()
			{
				@Override
				public void run()
				{
					currentMatch.broadcast(ChatColor.GREEN + "Coming next: " +
						ChatColor.RESET + getNextMap().getVersionString());
				}
			// announce the next match 10 seconds after the match ends
			}.runTaskLater(plugin, 10 * 20L);
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
