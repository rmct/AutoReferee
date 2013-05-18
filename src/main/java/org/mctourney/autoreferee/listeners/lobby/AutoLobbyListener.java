package org.mctourney.autoreferee.listeners.lobby;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
	{ super(plugin); }

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

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void matchJoin(PlayerMatchJoinEvent event)
	{
		AutoRefMatch match = event.getMatch();
		if (!event.getPlayer().hasPermission("autoreferee.referee"))
			match.joinTeam(event.getPlayer(), match.getArbitraryTeam(), false);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void teamJoin(PlayerTeamJoinEvent event)
	{
		AutoRefTeam team = event.getTeam();
		if (team.getPlayers().size() >= team.getMaxSize())
			event.setCancelled(true);

		// starting a match is based purely on teams being filled
		boolean ready = true;
		for (AutoRefTeam t : team.getMatch().getTeams())
			ready &= t.getPlayers().size() >= t.getMaxSize();
		if (ready) team.getMatch().startMatch();
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void matchStart(MatchStartEvent event)
	{
		boolean canStart = true;
		for (AutoRefTeam t : event.getMatch().getTeams())
			canStart &= t.getPlayers().size() >= t.getMinSize();
		if (!canStart) event.setCancelled(true);
	}
}
