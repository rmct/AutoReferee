package org.mctourney.AutoReferee.listeners;

import java.util.Set;
import java.io.UnsupportedEncodingException;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import org.mctourney.AutoReferee.AutoReferee;
import org.mctourney.AutoReferee.AutoRefMatch;

import com.google.common.collect.Sets;

public class RefereeChannelListener implements PluginMessageListener
{
	AutoReferee plugin = null;

	public RefereeChannelListener(Plugin p)
	{ plugin = (AutoReferee) p; }
	
	public void onPluginMessageReceived(String channel, Player player, byte[] mbytes)
	{
		try
		{
			String message = new String(mbytes, "UTF-8");
			AutoRefMatch match = plugin.getMatch(player.getWorld());

			if (match != null && "REGISTER".equalsIgnoreCase(channel))
			{
				Set<String> reg = Sets.newHashSet(message.split("0"));
				if (reg.contains(AutoReferee.REFEREE_PLUGIN_CHANNEL))
					match.updateReferee(player);
			}
		}
		catch (UnsupportedEncodingException e) {  }
	}
}
