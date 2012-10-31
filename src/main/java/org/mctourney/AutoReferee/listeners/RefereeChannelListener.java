package org.mctourney.AutoReferee.listeners;

import java.io.UnsupportedEncodingException;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.ChatColor;

import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.AutoRefMatch;
import org.mctourney.AutoReferee.AutoRefPlayer;

public class RefereeChannelListener implements PluginMessageListener, Listener
{
	public static final char DELIMITER = '|';
	AutoReferee plugin = null;

	public RefereeChannelListener(Plugin p)
	{ plugin = (AutoReferee) p; }

	public void onPluginMessageReceived(String channel, Player player, byte[] mbytes)
	{
		try
		{
			String message = new String(mbytes, AutoReferee.PLUGIN_CHANNEL_ENC);
			AutoRefMatch match = plugin.getMatch(player.getWorld());
		}
		catch (UnsupportedEncodingException e)
		{ plugin.getLogger().info("Unsupported encoding: " + AutoReferee.PLUGIN_CHANNEL_ENC); }
	}

	@EventHandler
	public void channelRegistration(PlayerRegisterChannelEvent event)
	{
		Player pl = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(pl.getWorld());

		if (AutoReferee.REFEREE_PLUGIN_CHANNEL.equals(event.getChannel()) && match != null)
		{
			// if this is a player, complain and force them to quit their team!
			if (match.isPlayer(pl))
			{
				AutoRefPlayer apl = match.getPlayer(pl);
				for (Player ref : match.getReferees(true)) ref.sendMessage(apl.getName() +
					ChatColor.DARK_GRAY + " attempted to log in with a modified client!");
				match.leaveTeam(pl, true);
			}

			// update a referee with the latest information regarding the match
			if (match.isReferee(pl)) match.updateReferee(pl);
		}
	}
}
