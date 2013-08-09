package org.mctourney.autoreferee;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.mctourney.autoreferee.util.TeleportationUtil;

public class AutoRefSpectator extends AutoRefPlayer
{
	private AutoRefMatch match = null;
	private String cyclePlayer = null;

	private boolean nightVision = false;
	private boolean viewInventory = true;
	private boolean invisible = true;
	private boolean streamer;
	
	private Location prevLocation = null;

	public AutoRefSpectator(String name, AutoRefMatch match)
	{
		super(name, null); this.match = match;
		Player player = this.getPlayer();

		this.streamer = player == null ? false
			: player.hasPermission("autoreferee.streamer");
	}

	public AutoRefSpectator(Player player, AutoRefMatch match)
	{ this(player.getName(), match); }

	public boolean isInvisible()
	{ return invisible; }

	public void setInvisible(boolean vis)
	{ this.invisible = vis; }

	public boolean isStreamer()
	{ return streamer; }

	public void setStreamer(boolean b)
	{
		this.streamer = b; this.match.setupSpectators(this.getPlayer());
		this.getPlayer().sendMessage(ChatColor.GREEN + "You are " +
			(this.streamer ? "now" : "no longer") + " in streamer mode!");
	}

	@Override
	public AutoRefMatch getMatch()
	{ return this.match; }

	public boolean hasNightVision()
	{ return nightVision; }

	public void setNightVision(boolean b)
	{
		this.nightVision = b;
		if (this.hasClientMod()) AutoRefMatch.messageReferee(getPlayer(),
			"match", getMatch().getWorld().getName(), "nightvis", this.nightVision ? "1" : "0");
		else this.applyNightVision();
	}

	public void applyNightVision()
	{
		if (!isOnline() || this.hasClientMod()) return;
		getPlayer().removePotionEffect(PotionEffectType.NIGHT_VISION);

		PotionEffect nightvis = new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0);
		if (this.nightVision) getPlayer().addPotionEffect(nightvis);
	}

	public void cycleNextPlayer()
	{
		AutoRefMatch match = getMatch();
		for (int i = 0; i < match.getPlayers().size(); ++i)
		{
			this.cyclePlayer = match.getCycleNextPlayer(this.cyclePlayer);
			AutoRefPlayer apl = match.getPlayer(this.cyclePlayer);

			if (apl != null)
			{
				Location tploc = TeleportationUtil.playerTeleport(apl);
				if (tploc != null) { getPlayer().teleport(tploc); break; }
			}
		}
	}

	public void cyclePrevPlayer()
	{
		AutoRefMatch match = getMatch();
		for (int i = 0; i < match.getPlayers().size(); ++i)
		{
			this.cyclePlayer = match.getCyclePrevPlayer(this.cyclePlayer);
			AutoRefPlayer apl = match.getPlayer(this.cyclePlayer);

			if (apl != null)
			{
				Location tploc = TeleportationUtil.playerTeleport(apl);
				if (tploc != null) { getPlayer().teleport(tploc); break; }
			}
		}
	}

	public boolean canViewInventory()
	{ return viewInventory; }

	public void setViewInventory(boolean vi)
	{ this.viewInventory = vi; }
	
	public Location prevLocation()
	{ return prevLocation;}
	
	public void setPrevLocation(Location l)
	{ this.prevLocation = l;}
}
