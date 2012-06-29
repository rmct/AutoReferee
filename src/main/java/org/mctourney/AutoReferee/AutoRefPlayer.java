package org.mctourney.AutoReferee;

import java.util.Map;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.google.common.collect.Maps;

class AutoRefPlayer
{
	static class DamageCause
	{
		// cause of damage, primary value for damage cause
		public EntityDamageEvent.DamageCause damageCause;
		
		// extra information to accompany damage cause
		public Object payload = null;
		
		// generate a hashcode
		@Override public int hashCode()
		{ return (payload == null ? 0 : payload.hashCode()) ^ 
			damageCause.hashCode(); }
	
		@Override public boolean equals(Object o)
		{ return hashCode() == o.hashCode(); }
		
		public DamageCause(EntityDamageEvent.DamageCause c, Object p)
		{ damageCause = c; payload = p; }
		
		public static DamageCause fromDamageEvent(EntityDamageEvent e)
		{
			EntityDamageEvent.DamageCause c = e.getCause();
			Object p = null;
			
			EntityDamageByEntityEvent edEvent = null;
			if ((e instanceof EntityDamageByEntityEvent))
				edEvent = (EntityDamageByEntityEvent) e;
			
			switch (c)
			{
				case ENTITY_ATTACK:
				case ENTITY_EXPLOSION:
					// get the entity that did the killing
					if (edEvent != null)
						p = edEvent.getDamager();
					break;
	
				case PROJECTILE:
				case MAGIC:
					// get the shooter from the projectile
					if (edEvent != null && edEvent.getDamager() != null)
						p = ((Projectile) edEvent.getDamager()).getShooter();
					
					// change damage cause to ENTITY_ATTACK
					//c = EntityDamageEvent.DamageCause.ENTITY_ATTACK;
					break;
			}
			
			if ((p instanceof Monster))
				p = ((Monster) p).getType();
			return new DamageCause(c, p);
		}
		
		@Override public String toString()
		{
			String damager = null;
			
			// generate a 'damager' string for more information
			if ((payload instanceof Player))
				damager = ((Player) payload).getName();
			if ((payload instanceof EntityType))
				damager = ((EntityType) payload).name();
			
			// return a string representing this damage cause
			return (damager == null ? "" : (damager + "'s "))
				+ damageCause.name();
		}
	}

	// stored player reference
	protected Player player;
	
	// number of times this player has killed other players
	public Map<String, Integer> kills;
	public int totalKills = 0;

	// number of times player has died and damage taken
	public Map<AutoRefPlayer.DamageCause, Integer> deaths;
	public Map<AutoRefPlayer.DamageCause, Integer> damage;
	public int totalDeaths = 0;
	
	// constructor for simply setting up the variables
	public AutoRefPlayer(Player p)
	{
		kills = Maps.newHashMap();
		deaths = Maps.newHashMap();
		damage = Maps.newHashMap();
		player = p;
	}
	
	// register that we just received this damage
	public void registerDamage(EntityDamageEvent e)
	{
		// get the last damage cause, and mark that as the cause of the damage
		AutoRefPlayer.DamageCause dc = AutoRefPlayer.DamageCause.fromDamageEvent(e);
		damage.put(dc, e.getDamage() + (damage.containsKey(dc) ? damage.get(dc) : 0));
	}
	
	// register that we just died
	public void registerDeath(PlayerDeathEvent e)
	{
		// get the last damage cause, and mark that as the cause of one death
		AutoRefPlayer.DamageCause dc = AutoRefPlayer.DamageCause.fromDamageEvent(e.getEntity().getLastDamageCause());
		deaths.put(dc, 1 + (deaths.containsKey(dc) ? deaths.get(dc) : 0));
		++totalDeaths;
	}
	
	// register that we killed the Player who fired this event
	public void registerKill(PlayerDeathEvent e)
	{
		String pname = e.getEntity().getName();
		kills.put(pname, 1 + (kills.containsKey(pname) ? kills.get(pname) : 0));
		++totalKills;
	}
}