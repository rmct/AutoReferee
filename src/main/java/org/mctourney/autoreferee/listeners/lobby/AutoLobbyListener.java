package org.mctourney.autoreferee.listeners.lobby;

import org.bukkit.entity.Player;

import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoReferee;

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
}
