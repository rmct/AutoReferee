package org.mctourney.AutoReferee;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class RefereeChannelListener implements PluginMessageListener
{
	AutoReferee plugin = null;

	public RefereeChannelListener(Plugin p)
	{ plugin = (AutoReferee) p; }
	
	public void onPluginMessageReceived(String channel, Player player, byte[] message)
	{
	}
}
