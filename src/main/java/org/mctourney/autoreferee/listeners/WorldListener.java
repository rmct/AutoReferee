package org.mctourney.autoreferee.listeners;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.Plugin;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.util.PlayerUtil;
import org.mctourney.autoreferee.util.SportBukkitUtil;

public class WorldListener implements Listener
{
	AutoReferee plugin = null;

	public WorldListener(Plugin p)
	{
		plugin = (AutoReferee) p;
	}

	@EventHandler
	public void worldInit(WorldInitEvent event)
	{ checkLoadedWorld(event); }

	@EventHandler
	public void worldLoad(WorldLoadEvent event)
	{ checkLoadedWorld(event); }

	private void checkLoadedWorld(WorldEvent event)
	{
		AutoRefMatch.setupWorld(event.getWorld(), false);
	}

	@EventHandler(priority=EventPriority.LOWEST)
	public void commandBlockCommandEvent(ServerCommandEvent event)
	{
		if (!(event.getSender() instanceof BlockCommandSender)) return;
		Block commandBlock = ((BlockCommandSender) event.getSender()).getBlock();

		AutoRefMatch match = plugin.getMatch(commandBlock.getWorld());
		if (match == null) return;

		if (event.getCommand().startsWith("say"))
		{
			match.broadcast(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "[@] " +
				ChatColor.RESET + event.getCommand().substring(3).trim());
			event.setCommand(""); return;
		}
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
			if (player.getWorld() != match.getWorld()) match.joinMatch(player);
			else match.checkTeamsReady();

			SportBukkitUtil.setOverheadName(player, match.getDisplayName(player));
			if (!match.getCurrentState().inProgress() || match.isPlayer(player))
				match.broadcast(match.colorMessage(event.getJoinMessage()));
			event.setJoinMessage(null);

			match.sendMatchInfo(player);
			match.setupSpectators(player);

			// only clear inventories and give books if before match or not a player
			if (match.getCurrentState().isBeforeMatch() || !match.isPlayer(player))
			{
				// give them a book with info about the match
				PlayerUtil.clearInventory(player);
				player.getInventory().addItem(match.getMatchInfoBook());
			}

			if (match.isReferee(player))
				match.updateReferee(player);

			if (!player.hasPlayedBefore())
				player.teleport(match.getPlayerSpawn(player));
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

	@EventHandler
	public void worldJoin(PlayerChangedWorldEvent event)
	{
		// update team ready information for both worlds
		AutoRefMatch matchFm = plugin.getMatch(event.getFrom());
		if (matchFm != null) matchFm.checkTeamsReady();

		Player player = event.getPlayer();
		AutoRefMatch matchTo = plugin.getMatch(player.getWorld());

		if (matchTo != null)
		{
			SportBukkitUtil.setOverheadName(player, matchTo.getDisplayName(player));

			matchTo.checkTeamsReady();
			matchTo.sendMatchInfo(player);
			matchTo.setupSpectators(player);

			if (matchTo.isReferee(player))
				matchTo.updateReferee(player);

			// give them a book with info about the match
			PlayerUtil.clearInventory(player);
			player.getInventory().addItem(matchTo.getMatchInfoBook());
		}
		else SportBukkitUtil.setOverheadName(player, player.getName());

		// if this is leaving a match, leave its team
		if (matchFm != null)
			matchFm.leaveTeam(player, false);

		// if they are leaving AutoReferee-managed worlds
		if (matchFm != null && matchTo == null)
		{
			matchFm.setSpectatorMode(player, false);
			PlayerUtil.setGameMode(player, GameMode.SURVIVAL);
		}
	}
}
