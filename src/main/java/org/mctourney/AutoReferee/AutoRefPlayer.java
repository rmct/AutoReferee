package org.mctourney.AutoReferee;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mctourney.AutoReferee.util.BlockData;

import org.apache.commons.collections.map.DefaultedMap;

import com.google.common.collect.Sets;

class AutoRefPlayer
{
	public static AutoReferee plugin = null;
	
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

	// stored references
	protected Player player;
	protected AutoRefTeam team;
	
	// number of times this player has killed other players
	public Map<String, Integer> kills;
	public int totalKills = 0;
	public int shotsFired, shotsHit;

	// number of times player has died and damage taken
	public Map<AutoRefPlayer.DamageCause, Integer> deaths;
	public Map<AutoRefPlayer.DamageCause, Integer> damage;
	public int totalDeaths = 0;
	
	// tracking objective items
	private Set<BlockData> carrying;
	
	// constructor for simply setting up the variables
	@SuppressWarnings("unchecked")
	public AutoRefPlayer(Player p, AutoRefTeam t)
	{
		// detailed statistics
		kills  = new DefaultedMap(0);
		deaths = new DefaultedMap(0);
		damage = new DefaultedMap(0);
		
		// accuracy information
		shotsFired = shotsHit = 0;
		
		// save the player and team as references
		this.player = p;
		this.team = t;
	}
	
	public AutoRefPlayer(Player p)
	{ this(p, plugin.getTeam(p)); }

	@Override
	public int hashCode()
	{ return player.hashCode(); }

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AutoRefPlayer)) return false;
		return player.equals(((AutoRefPlayer) o).player);
	}
	
	public String getName()
	{
		if (team == null) return player.getName();
		return team.getColor() + player.getName() + ChatColor.RESET;
	}
	
	public void heal()
	{
		player.setHealth    ( 20 ); // 10 hearts
		player.setFoodLevel ( 20 ); // full food
		player.setSaturation(  5 ); // saturation depletes hunger
		player.setExhaustion(  0 ); // exhaustion depletes saturation
	}

	// register that we just received this damage
	public void registerDamage(EntityDamageEvent e)
	{
		// sanity check...
		if (e.getEntity() != player) return;
		
		// get the last damage cause, and mark that as the cause of the damage
		AutoRefPlayer.DamageCause dc = AutoRefPlayer.DamageCause.fromDamageEvent(e);
		damage.put(dc, e.getDamage() + damage.get(dc));
	}
	
	// register that we just died
	public void registerDeath(PlayerDeathEvent e)
	{
		// sanity check...
		if (e.getEntity() != player) return;
		
		// get the last damage cause, and mark that as the cause of one death
		AutoRefPlayer.DamageCause dc = AutoRefPlayer.DamageCause.fromDamageEvent(e.getEntity().getLastDamageCause());
		deaths.put(dc, 1 + deaths.get(dc));
		++totalDeaths;
	}
	
	// register that we killed the Player who fired this event
	public void registerKill(PlayerDeathEvent e)
	{
		// sanity check...
		if (e.getEntity().getKiller() != player) return;
		
		// get the name of the player who died, record one kill against them
		String pname = e.getEntity().getName();
		kills.put(pname, 1 + kills.get(pname));
		++totalKills;
	}

	public boolean isReady()
	{
		return team.getMatch().getWorld() == player.getWorld();
	}
	
	public void writeStats(PrintWriter fw)
	{
		String pname = this.player.getName();
		String accuracyInfo = "";
		
		if (shotsFired > 0) accuracyInfo = " (" + 
			Integer.toString(100 * shotsHit / shotsFired) + "% accuracy)";
		
		fw.println("Stats for " + pname + ": (" + Integer.toString(this.totalKills)
			+ ":" + Integer.toString(this.totalDeaths) + " KDR)" + accuracyInfo);
		
		for (Map.Entry<String, Integer> kill : this.kills.entrySet())
			fw.println("\t" + pname + " killed " + kill.getKey() + " " 
				+ kill.getValue().toString() + " time(s).");
		
		for (Map.Entry<DamageCause, Integer> death : this.deaths.entrySet())
			fw.println("\t" + death.getKey().toString() + " killed " + pname 
				+ " " + death.getValue().toString() + " time(s).");
		
		for (Map.Entry<DamageCause, Integer> damage : this.damage.entrySet())
			fw.println("\t" + damage.getKey().toString() + " caused " + pname 
				+ " " + damage.getValue().toString() + " damage.");
	}

	public void updateCarrying(Inventory inv)
	{
		carrying = Sets.newHashSet();
		for (ItemStack item : inv)
			carrying.add(BlockData.fromItemStack(item));
		carrying.retainAll(team.winConditions.values());
	}
	
	public Set<BlockData> getCarrying()
	{ return carrying; }
}
