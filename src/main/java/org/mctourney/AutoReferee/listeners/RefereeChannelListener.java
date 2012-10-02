package org.mctourney.AutoReferee.listeners;

import java.io.UnsupportedEncodingException;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.AutoRefMatch;

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
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (AutoReferee.REFEREE_PLUGIN_CHANNEL.equals(event.getChannel()) && match != null
			&& match.isReferee(event.getPlayer())) match.updateReferee(event.getPlayer());
	}
}
