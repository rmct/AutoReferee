package org.mctourney.autoreferee.listeners.lobby;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.scheduler.BukkitRunnable;
import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.event.match.MatchStartEvent;
import org.mctourney.autoreferee.event.player.PlayerMatchJoinEvent;
import org.mctourney.autoreferee.event.player.PlayerTeamJoinEvent;

public class AutoLobbyListener extends LobbyListener
{
	public AutoLobbyListener(AutoReferee plugin)
	{
		super(plugin);
		AutoRefMatch.giveMatchInfoBooks = false;
	}

	@Override
	protected void lobbyLoadMap(Player player, AutoRefMap map)
	{
		AutoRefMatch match = findSuitableMatch(map);
		if (match != null) match.joinMatch(player);
		else AutoRefMap.loadMap(player, map, null);
	}

	private AutoRefMatch findSuitableMatch(AutoRefMap map)
	{
		for (AutoRefMatch match : plugin.getMatches())
			if (match.getCurrentState().isBeforeMatch() && map.equals(match.getMap()))
				return match;
		return null;
	}

	protected class PlayerJoinTask extends BukkitRunnable
	{
		private AutoRefMatch match;
		private Player player;

		public PlayerJoinTask(AutoRefMatch match, Player player)
		{ this.match = match; this.player = player; }

		@Override
		public void run()
		{
			if (match.getCurrentState().isBeforeMatch() && match.getPlayerTeam(player) == null
				&& !player.hasPermission("autoreferee.referee"))
			{
				match.joinTeam(player, match.getArbitraryTeam(),
					PlayerTeamJoinEvent.Reason.AUTOMATIC, false);
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void matchJoin(PlayerMatchJoinEvent event)
	{ new PlayerJoinTask(event.getMatch(), event.getPlayer()).runTask(plugin); }

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
}
