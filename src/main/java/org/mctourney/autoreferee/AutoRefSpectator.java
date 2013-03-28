package org.mctourney.autoreferee;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class AutoRefSpectator extends AutoRefPlayer
{
	private AutoRefMatch match = null;
	private boolean nightVision = false;

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
		if (this.hasClientMod()) getMatch().messageReferee(getPlayer(),
			"match", getMatch().getWorld().getName(), "nightvis", this.nightVision ? "1" : "0");
		else this.applyNightVision();
	}

	public void applyNightVision()
	{
		if (!isOnline() || this.hasClientMod()) return;
		getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 90 * 20, 0));
	}
}
