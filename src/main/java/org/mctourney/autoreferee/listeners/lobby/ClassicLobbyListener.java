package org.mctourney.autoreferee.listeners.lobby;

import org.bukkit.entity.Player;

import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoReferee;

public class ClassicLobbyListener extends LobbyListener
{
	public ClassicLobbyListener(AutoReferee plugin)
	{ super(plugin); }

	@Override
	protected void lobbyLoadMap(Player player, AutoRefMap map)
	{ AutoRefMap.loadMap(player, map, null); }
}
