package org.mctourney.AutoReferee;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoReferee.*;
import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;

public class PlayerVersusPlayerListener implements Listener
{
	AutoReferee plugin = null;

	// is friendly-fire allowed on this map?
	boolean allow_ff = false;

	public PlayerVersusPlayerListener(Plugin p)
	{
		plugin = (AutoReferee) p;
		allow_ff = plugin.getMapConfig().getBoolean("match.allow-ff");
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerDeath(EntityDeathEvent event)
	{
		if ((event instanceof PlayerDeathEvent))
		{
			PlayerDeathEvent pdeath = (PlayerDeathEvent) event;
			Player victim = (Player) pdeath.getEntity();

			// get the player who killed this player (might be null)
			Player killer = victim.getKiller();
			String dmsg = pdeath.getDeathMessage();

			// if the death was due to intervention by the plugin
			// let's change the death message to reflect this fact
			if (plugin.actionTaken.containsKey(victim.getName()))
			{
				switch (plugin.actionTaken.get(victim.getName()))
				{
					// killed because they entered the void lane
					case ENTERED_VOIDLANE:
						dmsg = victim.getName() + " entered the void lane.";
						break;
				}

				// remove this verdict once we have used it
				plugin.actionTaken.remove(victim.getName());
			}

			// color the player's name with his team color
			dmsg = dmsg.replace(victim.getName(), plugin.colorPlayer(victim));

			// if the killer was a player, color their name as well
			if (killer != null) 
			{
				if (plugin.getConfig().getBoolean("server-mode.console.log", false))
					plugin.log.info("[DEATH] " + killer.getDisplayName() 
						+ " killed " + victim.getDisplayName());
				dmsg = dmsg.replace(killer.getName(), plugin.colorPlayer(killer));
			}

			// update the death message with the changes
			pdeath.setDeathMessage(dmsg);
			
			// save information about this kill to the logs (register to victim/killer logs)
			if (plugin.playerData != null)
			{
				// register the death of the victim
				if (plugin.playerData.containsKey(victim))
					plugin.playerData.get(victim).registerDeath(pdeath);
				
				// register the kill for the killer
				if (plugin.playerData.containsKey(killer))
					plugin.playerData.get(killer).registerKill(pdeath);
			}
		}
	}

	public static Player entityToPlayer(Entity e)
	{
		// damaging entity is an actual player, easy!
		if ((e instanceof Player)) return (Player) e;

		// damaging entity is an arrow, then who was bow?
		if ((e instanceof Projectile))
		{
			LivingEntity shooter = ((Projectile) e).getShooter();
			if ((shooter instanceof Player)) return (Player) shooter;
		}
		return null;
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void damageDealt(EntityDamageEvent event)
	{
		if ((event instanceof EntityDamageByEntityEvent))
		{
			EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) event;
			Player damager = entityToPlayer(ed.getDamager());
			Player damaged = entityToPlayer(ed.getEntity());

			// if either of these aren't players, nothing to do here
			if (null == damager || null == damaged) return;

			// if the damaged entity was a player
			if (null != damaged)
			{
				// if the match is in progress and player is in start region
				// cancel any damage dealt to the player
				if (plugin.getState() == eMatchStatus.PLAYING && 
						plugin.inStartRegion(damaged.getLocation()))
				{ event.setCancelled(true); return; }
			}
			
			// get team affiliations of these players (maybe null)
			Team d1team = plugin.getTeam(damager);
			Team d2team = plugin.getTeam(damaged);
			if (d1team == null && d2team == null) return;

			// if the attacked isn't on a team, or same team (w/ no FF), cancel
			event.setCancelled(d2team == null || (d1team == d2team && allow_ff));

			if (event.isCancelled()) return;
		}
		
		if (plugin.playerData != null && plugin.playerData.containsKey(event.getEntity()))
			plugin.playerData.get(event.getEntity()).registerDamage(event);
	}
}