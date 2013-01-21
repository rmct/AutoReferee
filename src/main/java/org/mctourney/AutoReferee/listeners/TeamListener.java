package org.mctourney.AutoReferee.listeners;

import java.util.Iterator;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoRefMap;
import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefMatch.Role;
import org.mctourney.AutoReferee.AutoRefPlayer;
import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.AutoReferee;

import com.google.common.collect.Maps;

public class TeamListener implements Listener
{
	AutoReferee plugin = null;

	// mapping spectators to the matches they died in
	Map<String, AutoRefMatch> deadSpectators = Maps.newHashMap();

	public TeamListener(Plugin p)
	{ plugin = (AutoReferee) p; }

	@EventHandler(priority=EventPriority.HIGHEST)
	public void chatMessage(AsyncPlayerChatEvent event)
	{
		// typical chat message format, swap out with colored version
		Player speaker = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(speaker.getWorld());

		// restrict listeners to being in the same match (not world).
		// this should avoid messing up multi-world chat on multipurpose servers
		Iterator<Player> iter = event.getRecipients().iterator();
		while (iter.hasNext())
		{
			Player listener = iter.next();

			if (plugin.getMatch(listener.getWorld()) != match)
			{ iter.remove(); continue; }
		}

		// if the speaker isn't in a match, that's all we can do
		if (match == null) return;

		AutoRefTeam speakerTeam = match.getPlayerTeam(speaker);
		Role speakerRole = match.getRole(speaker);

		event.setFormat("<" + match.getDisplayName(speaker) + "> " + event.getMessage());

		iter = event.getRecipients().iterator();
		if (!match.getCurrentState().isBeforeMatch()) while (iter.hasNext())
		{
			Player listener = iter.next();

			// if listener is a streamer and the speaker is a non-streamer spectator, hide it
			if (match.isStreamer(listener) && speakerTeam == null && speakerRole != Role.STREAMER)
			{ iter.remove(); continue; }

			// if listener is on a team, and speaker is a spectator, hide message
			if (speakerTeam == null && match.getPlayerTeam(listener) != null)
			{ iter.remove(); continue; }
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void spectatorDeath(PlayerDeathEvent event)
	{
		World world = event.getEntity().getWorld();
		AutoRefMatch match = plugin.getMatch(world);

		if (match != null && !match.isPlayer(event.getEntity()))
		{
			deadSpectators.put(event.getEntity().getName(), match);
			event.getDrops().clear();
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void spectatorDeath(EntityDamageEvent event)
	{
		World world = event.getEntity().getWorld();
		AutoRefMatch match = plugin.getMatch(world);

		if (event.getEntityType() != EntityType.PLAYER) return;
		Player player = (Player) event.getEntity();

		if (match != null && match.getCurrentState().inProgress() &&
			!match.isPlayer(player))
		{
			event.setCancelled(true);
			if (player.getLocation().getY() < -64)
				player.teleport(match.getPlayerSpawn(player));
			player.setFallDistance(0);
		}
	}

	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event)
	{
		String name = event.getPlayer().getName();
		if (deadSpectators.containsKey(name))
		{
			AutoRefMatch match = deadSpectators.get(name);
			if (match != null) event.setRespawnLocation(match.getWorldSpawn());
			deadSpectators.remove(name); return;
		}

		World world = event.getPlayer().getWorld();
		AutoRefMatch match = plugin.getMatch(world);

		if (match != null && match.isPlayer(event.getPlayer()))
		{
			// does this player have a bed spawn?
			boolean hasBed = event.getPlayer().getBedSpawnLocation() != null;

			// if the player attempts to respawn in a different world, bring them back
			if (!hasBed || event.getRespawnLocation().getWorld() != match.getWorld())
				event.setRespawnLocation(match.getPlayerSpawn(event.getPlayer()));

			// setup respawn for the player
			match.getPlayer(event.getPlayer()).respawn();
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void playerLogin(PlayerLoginEvent event)
	{
		Player player = event.getPlayer();
		if (plugin.isAutoMode())
		{
			// if they should be whitelisted, let them in, otherwise, block them
			if (plugin.playerWhitelisted(player)) event.allow();
			else
			{
				event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, AutoReferee.NO_LOGIN_MESSAGE);
				return;
			}
		}

		// if this player needs to be in a specific world, put them there
		AutoRefTeam team = plugin.getExpectedTeam(player);
		AutoRefMatch match = plugin.getMatch(player.getWorld());

		if (team != null) { team.join(player); match = team.getMatch(); }
		if (match != null && match.isPlayer(player))
			match.messageReferees("player", player.getName(), "login");
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerQuit(PlayerQuitEvent event)
	{
		Player player = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null) return;

		// leave the team, if necessary
		AutoRefTeam team = plugin.getTeam(player);
		if (team != null) match.messageReferees("player", player.getName(), "logout");
		if (team != null && !match.getCurrentState().inProgress()) team.leave(player);

		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null && player.getLocation() != null)
			apl.setLastLogoutLocation(player.getLocation());

		// if this player was damaged recently (during the match), notify
		if (match.getCurrentState().inProgress() && apl != null && apl.wasDamagedRecently())
		{
			String message = apl.getDisplayName() + ChatColor.GRAY + " logged out during combat " +
				String.format("with %2.1f hearts remaining", apl.getPlayer().getHealth() / 2.0);
			for (Player ref : match.getReferees(true)) ref.sendMessage(message);
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void signCommand(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(player.getWorld());

		if (event.hasBlock() && event.getClickedBlock().getState() instanceof Sign)
		{
			String[] lines = ((Sign) event.getClickedBlock().getState()).getLines();
			if (lines[0] == null || !"[AutoReferee]".equals(lines[0])) return;

			if (match != null && match.getCurrentState().isBeforeMatch() &&
				match.inStartRegion(event.getClickedBlock().getLocation()))
			{
				// execute the command on the sign (and hope like hell that AutoReferee picks it up)
				if (event.getAction() == Action.RIGHT_CLICK_BLOCK)
					player.performCommand(ChatColor.stripColor(lines[1] + " " + lines[2]).trim());
				event.setCancelled(true);
			}

			else if (player.getWorld() == plugin.getLobbyWorld())
			{
				// load the world named on the sign
				if (event.getAction() == Action.RIGHT_CLICK_BLOCK)
				{
					// cancel the event only if its a right click
					event.setCancelled(true);

					player.sendMessage(ChatColor.GREEN + "Please wait...");
					String mapName = lines[1] + " " + lines[2] + " " + lines[3];
					AutoRefMap.loadMap(player, mapName.trim(), null);
				}
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void changeGamemode(PlayerGameModeChangeEvent event)
	{
		Player player = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(player.getWorld());

		// if there is a match currently in progress on this world...
		if (match != null && plugin.isAutoMode() &&
			match.getCurrentState().inProgress())
		{
			// cancel the gamemode change if the player is a participant
			if (event.getNewGameMode() == GameMode.CREATIVE &&
				match.isPlayer(player)) event.setCancelled(true);
		}
	}
}
