package org.mctourney.AutoReferee;

import java.util.logging.Logger;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.Plugin;

public class PlayerVersusPlayerListener implements Listener
{
	AutoReferee refPlugin = null;
	public Logger log = Logger.getLogger("Minecraft");

	// is friendly-fire allowed on this map?
	boolean allow_ff = false;

	public PlayerVersusPlayerListener(Plugin plugin)
	{
		refPlugin = (AutoReferee) plugin;
		allow_ff = refPlugin.getMapConfig().getBoolean("match.allow-ff");
	}

	@EventHandler
	public void playerDeath(EntityDeathEvent event)
	{
		if ((event instanceof PlayerDeathEvent))
		{
			PlayerDeathEvent playerdeath = (PlayerDeathEvent) event;
			Player player = (Player) playerdeath.getEntity();

			// get the player who killed this player (might be null)
			Player killer = player.getKiller();
			String dmsg = playerdeath.getDeathMessage();

			// if the death was due to intervention by the plugin
			// let's change the death message to reflect this fact
			if (refPlugin.actionTaken.containsKey(player))
			{
				switch (refPlugin.actionTaken.get(player))
				{
					// killed because they entered the void lane
					case ENTERED_VOIDLANE:
						dmsg = player.getName() + " entered the void lane.";
						break;
				}

				// remove this verdict once we have used it
				refPlugin.actionTaken.remove(player);
			}

			// color the player's name with his team color
			dmsg = dmsg.replace(player.getName(), refPlugin.colorPlayer(player));

			// if the killer was a player, color their name as well
			if (killer != null) 
			{
				if (refPlugin.getConfig().getBoolean("server-mode.console.log", false))
					log.info("[DEATH] " + killer.getDisplayName() 
						+ " killed " + player.getDisplayName());
				dmsg = dmsg.replace(killer.getName(), refPlugin.colorPlayer(killer));
			}

			// update the death message with the changes
			playerdeath.setDeathMessage(dmsg);
		}
	}

	public Player entityToPlayer(Entity e)
	{
		// damaging entity is an actual player, easy!
		if ((e instanceof Player)) return (Player) e;

		// damaging entity is an arrow, then who was bow?
		if ((e instanceof Arrow))
		{
			LivingEntity shooter = ((Arrow) e).getShooter();
			if ((shooter instanceof Player)) return (Player) shooter;
		}
		return null;
	}

	@EventHandler
	public void damageDealt(EntityDamageEvent event)
	{
		if ((event instanceof EntityDamageByEntityEvent))
		{
			EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) event;
			Player damager = entityToPlayer(ed.getDamager());
			Player damaged = entityToPlayer(ed.getEntity());

			// if either of these aren't players, nothing to do here
			if (null == damager || null == damaged) return;

			// get team affiliations of these players (maybe null)
			Integer d1team = refPlugin.getTeam(damager);
			Integer d2team = refPlugin.getTeam(damaged);
			if (d1team == null && d2team == null) return;

			// if the attacked isn't on a team, or same team (w/ no FF), cancel
			if (d2team == null || (d1team == d2team && allow_ff))
			{ event.setCancelled(true); return; }

			log.info(damager.getName() + " dealt damage to " + damaged.getName() 
					+ " via " + ed.getCause().name());
		}
	}
}

