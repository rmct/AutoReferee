package org.mctourney.AutoReferee.listeners;

import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import org.apache.commons.collections.map.DefaultedMap;

import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefPlayer;
import org.mctourney.AutoReferee.AutoReferee;

public class WorldListener implements Listener
{
	AutoReferee plugin = null;

	public WorldListener(Plugin p)
	{
		plugin = (AutoReferee) p;
	}

	@EventHandler
	public void worldLoad(WorldLoadEvent event)
	{
		AutoRefMatch.setupWorld(event.getWorld(), false);
	}

	@EventHandler
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();

		// get the match for the world the player is logging into
		AutoRefMatch match = plugin.getMatch(player.getWorld());

		// if there is no match here, or they aren't meant to play in this world,
		// check if there is a world they are expected in
		if (match == null || !match.isPlayer(player))
			for (AutoRefMatch m : plugin.getMatches())
				if (m.isPlayerExpected(player)) match = m;

		if (match != null)
		{
			// if we are logging in to the wrong world, teleport to the correct world
			if (player.getWorld() != match.getWorld()) match.acceptInvitation(player);

			if (!match.getCurrentState().inProgress() || match.isPlayer(player))
				match.broadcast(match.colorMessage(event.getJoinMessage()));
			event.setJoinMessage(null);

			match.sendMatchInfo(player);
			match.setupSpectators(player);

			if (match.isReferee(player))
				match.updateReferee(player);
		}
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null)
		{
			if (!match.getCurrentState().inProgress() || match.isPlayer(event.getPlayer()))
				match.broadcast(match.colorMessage(event.getQuitMessage()));
			event.setQuitMessage(null);
		}
	}

	@SuppressWarnings("unchecked")
	public static GameMode getDefaultGamemode(World world)
	{
		Map<GameMode, Integer> cnt = new DefaultedMap(0); int x, max = 0;
		GameMode best = AutoReferee.getInstance().getServer().getDefaultGameMode();
		for (Player p : world.getPlayers()) if (!p.isOp())
		{
			cnt.put(p.getGameMode(), x = 1+cnt.get(p.getGameMode()));
			if (x > max) { max = x; best = p.getGameMode(); }
		}

		return best;
	}

	@EventHandler
	public void worldJoin(PlayerChangedWorldEvent event)
	{
		// update team ready information for both worlds
		AutoRefMatch matchFm = plugin.getMatch(event.getFrom());
		if (matchFm != null) matchFm.checkTeamsReady();

		AutoRefMatch matchTo = plugin.getMatch(event.getPlayer().getWorld());
		if (matchTo != null)
		{
			matchTo.checkTeamsReady();
			matchTo.sendMatchInfo(event.getPlayer());
			matchTo.setupSpectators(event.getPlayer());

			if (matchTo.isReferee(event.getPlayer()))
				matchTo.updateReferee(event.getPlayer());
		}

		// if they are leaving AutoReferee-managed worlds
		if (matchFm != null && matchTo == null)
		{
			GameMode dgm = getDefaultGamemode(event.getPlayer().getWorld());
			matchFm.setSpectatorMode(event.getPlayer(), false, dgm);
		}
	}
}
