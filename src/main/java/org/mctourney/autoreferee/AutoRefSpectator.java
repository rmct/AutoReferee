package org.mctourney.autoreferee;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.mctourney.autoreferee.util.TeleportationUtil;

public class AutoRefSpectator extends AutoRefPlayer
{
	private AutoRefMatch match = null;
	private boolean nightVision = false;
	private String cyclePlayer = null;
	private boolean viewInventory = true;

	public AutoRefSpectator(String name, AutoRefMatch match)
	{ super(name, null); this.match = match; }

	public AutoRefSpectator(Player player, AutoRefMatch match)
	{ this(player.getName(), match); }

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
		this.cyclePlayer = getMatch().getCycleNextPlayer(this.cyclePlayer);
		AutoRefPlayer apl = getMatch().getPlayer(this.cyclePlayer);
		if (apl != null) getPlayer().teleport(TeleportationUtil.playerTeleport(apl));
	}

	public void cyclePrevPlayer()
	{
		this.cyclePlayer = getMatch().getCyclePrevPlayer(this.cyclePlayer);
		AutoRefPlayer apl = getMatch().getPlayer(this.cyclePlayer);
		if (apl != null) getPlayer().teleport(TeleportationUtil.playerTeleport(apl));
	}

	public boolean canViewInventory()
	{ return viewInventory; }

	public void setViewInventory(boolean vi)
	{ this.viewInventory = vi; }
}
