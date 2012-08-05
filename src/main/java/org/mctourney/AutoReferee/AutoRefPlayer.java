package org.mctourney.AutoReferee;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.AutoReferee.util.ArmorPoints;
import org.mctourney.AutoReferee.util.BlockData;

import org.apache.commons.collections.map.DefaultedMap;

import com.google.common.collect.Sets;

public class AutoRefPlayer
{
	public static final EntityDamageEvent VOID_DEATH = 
		new EntityDamageEvent(null, EntityDamageEvent.DamageCause.VOID, 0);

	static class DamageCause
	{
		// cause of damage, primary value for damage cause
		public EntityDamageEvent.DamageCause damageCause;
		
		// extra information to accompany damage cause
		public Object p = null, x = null;
		
		// generate a hashcode
		@Override public int hashCode()
		{ return damageCause.hashCode()
			^ (p == null ? 0 : p.hashCode())
			^ (x == null ? 0 : x.hashCode()); }
	
		@Override public boolean equals(Object o)
		{ return hashCode() == o.hashCode(); }
		
		protected DamageCause(EntityDamageEvent.DamageCause c, Object p, Object x)
		{ damageCause = c; this.p = p; this.x = x; }
		
		public static DamageCause fromDamageEvent(EntityDamageEvent e)
		{
			EntityDamageEvent.DamageCause c = e.getCause();
			Object p = null, x = null;
			
			EntityDamageByEntityEvent edEvent = null;
			if ((e instanceof EntityDamageByEntityEvent))
				edEvent = (EntityDamageByEntityEvent) e;
			
			switch (c)
			{
				case ENTITY_ATTACK:
				case ENTITY_EXPLOSION:
					// get the entity that did the killing
					if (edEvent != null)
					{
						p = edEvent.getDamager();
						if (p instanceof Player)
							x = ((Player) p).getItemInHand().getType();
					}
					break;
	
				case PROJECTILE:
				case MAGIC:
					// get the shooter from the projectile
					if (edEvent != null && edEvent.getDamager() != null)
					{
						p = ((Projectile) edEvent.getDamager()).getShooter();
						x = edEvent.getDamager().getType();
					}
					
					// change damage cause to ENTITY_ATTACK
					//c = EntityDamageEvent.DamageCause.ENTITY_ATTACK;
					break;
			}
			
			if ((p instanceof Player))
				p = AutoReferee.getInstance().getMatch(e.getEntity().getWorld()).getPlayer((Player) p);
			else if ((p instanceof Entity))
				p = ((Entity) p).getType();
			return new DamageCause(c, p, x);
		}
		
		private String cleanEnum(String e)
		{ return e.toLowerCase().replace("_+", " "); }
		
		public String materialToWeapon(Material mat)
		{
			if (mat == null) return null;
			if (mat == Material.AIR) return "fists";
			return cleanEnum(mat.name());
		}
		
		@Override public String toString()
		{
			String weapon = null, damager = "";
			
			// if there is a payload, convert it to a string
			if (p != null)
			{
				// generate a 'damager' string for more information
				if ((p instanceof EntityType))
					damager = "a " + ((EntityType) p).name();
				else if ((p instanceof AutoRefPlayer))
					damager = ((AutoRefPlayer) p).getPlayerName();
				
				// default: .toString()
				else damager = p.toString();
				
				// cleanup string if not a player's name
				if (!(p instanceof AutoRefPlayer))
					damager = cleanEnum(damager);
			}
			else return cleanEnum(damageCause.name());
			
			// if this was an entity attack, figure out the weapon
			switch (damageCause)
			{
				case ENTITY_ATTACK:
					weapon = materialToWeapon((Material) x);
					return damager + (weapon == null ? "" : ("'s " + weapon));
					
				case PROJECTILE:
				case MAGIC:
					weapon = cleanEnum(((EntityType) x).name());
					return damager + (weapon == null ? "" : ("'s " + weapon));
			}
					
			return damager;
		}
	}

	// stored references
	private String pname = null;
	private AutoRefTeam team;
	
	public Player getPlayer()
	{ return AutoReferee.getInstance().getServer().getPlayer(pname); }

	public String getPlayerName()
	{ return pname; }

	private void setPlayer(Player p)
	{ pname = p.getName(); }

	public AutoRefTeam getTeam()
	{ return team; }

	private void setTeam(AutoRefTeam t)
	{ team = t; }
	
	// number of times this player has killed other players
	public Map<AutoRefPlayer, Integer> kills;
	public int totalKills = 0;
	public int shotsFired, shotsHit;

	// number of times player has died and damage taken
	public Map<AutoRefPlayer.DamageCause, Integer> deaths;
	public Map<AutoRefPlayer.DamageCause, Integer> damage;
	public int totalDeaths = 0;
	
	// tracking objective items
	private Set<BlockData> carrying;

	private int currentHealth = 20;
	private int currentArmor = 0;
	
	public Set<BlockData> getCarrying()
	{ return carrying; }

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
		this.setPlayer(p);
		this.setTeam(t);
		
		// setup the carrying list
		this.carrying = Sets.newHashSet();
		
		// setup base health and armor level
		this.currentHealth = p.getHealth();
		this.currentArmor = ArmorPoints.fromPlayerInventory(p.getInventory());
	}
	
	public AutoRefPlayer(Player p)
	{ this(p, AutoReferee.getInstance().getTeam(p)); }

	@Override
	public int hashCode()
	{ return getPlayerName().hashCode(); }

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AutoRefPlayer)) return false;
		return getPlayerName().equals(((AutoRefPlayer) o).getPlayerName());
	}
	
	public String getName()
	{
		if (getTeam() == null) return getPlayerName();
		return getTeam().getColor() + getPlayerName() + ChatColor.RESET;
	}

	public String getTag()
	{ return getPlayerName().toLowerCase().replaceAll("[^a-z0-9]+", ""); }

	public void die(EntityDamageEvent deathCause, boolean clearDrops)
	{
		Player player = getPlayer();
		if (player == null || player.isDead()) return;
		
		// if a cause of death is specified, set it
		if (deathCause != null) player.setLastDamageCause(deathCause);
		
		// if the inventory needs to be cleared, clear it
		if (clearDrops) player.getInventory().clear();
		
		// kill the player
		player.setHealth(0);
	}

	public void respawn()
	{
		this.setExitLocation(null);
	}
	
	public void heal()
	{
		getPlayer().setHealth    ( 20 ); // 10 hearts
		getPlayer().setFoodLevel ( 20 ); // full food
		getPlayer().setSaturation(  5 ); // saturation depletes hunger
		getPlayer().setExhaustion(  0 ); // exhaustion depletes saturation
	}

	// register that we just received this damage
	public void registerDamage(EntityDamageEvent e)
	{
		// sanity check...
		if (e.getEntity() != getPlayer()) return;
		
		// get the last damage cause, and mark that as the cause of the damage
		AutoRefPlayer.DamageCause dc = AutoRefPlayer.DamageCause.fromDamageEvent(e);
		damage.put(dc, e.getDamage() + damage.get(dc));
	}
	
	// register that we just died
	public void registerDeath(PlayerDeathEvent e)
	{
		// sanity check...
		if (e.getEntity() != getPlayer()) return;
		
		// get the last damage cause, and mark that as the cause of one death
		AutoRefPlayer.DamageCause dc = AutoRefPlayer.DamageCause.fromDamageEvent(e.getEntity().getLastDamageCause());
		deaths.put(dc, 1 + deaths.get(dc)); ++totalDeaths;
		
		AutoRefMatch match = getTeam().getMatch(); Location loc = e.getEntity().getLocation();
		match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_DEATH,
			e.getDeathMessage(), loc, this, dc.p));
	}
	
	// register that we killed the Player who fired this event
	public void registerKill(PlayerDeathEvent e)
	{
		// sanity check...
		if (e.getEntity().getKiller() != getPlayer()) return;
		
		// get the name of the player who died, record one kill against them
		AutoRefPlayer apl = getTeam().getMatch().getPlayer(e.getEntity());
		kills.put(apl, 1 + kills.get(apl)); ++totalKills;
	}

	public boolean isReady()
	{ return getPlayer().getWorld() == getTeam().getMatch().getWorld(); }

	public int getScore()
	{ return totalKills - totalDeaths; }

	public String getAccuracy()
	{ return shotsFired == 0 ? "N/A" : (Integer.toString(100 * shotsHit / shotsFired) + "%"); }

	public String getExtendedAccuracyInfo()
	{
		return String.format("%s (%d/%d)", (shotsFired == 0 ? "N/A" : 
			(Integer.toString(100 * shotsHit / shotsFired) + "%")), shotsHit, shotsFired);
	}
	
	public void writeStats(PrintWriter fw)
	{
		String pname = this.getPlayerName();
		fw.println(String.format("Stats for %s: (%d:%d KDR) (%s accuracy)",
			pname, totalKills, totalDeaths, getAccuracy()));
		
		for (Map.Entry<AutoRefPlayer, Integer> kill : this.kills.entrySet())
			fw.println(String.format("\t%s killed %s %d time(s).",
				pname, kill.getKey().getPlayerName(), kill.getValue()));
		
		for (Map.Entry<DamageCause, Integer> death : this.deaths.entrySet())
			fw.println(String.format("\t%s killed %s %d time(s).",
				death.getKey().toString(), pname, death.getValue()));
		
		for (Map.Entry<DamageCause, Integer> damage : this.damage.entrySet())
			fw.println(String.format("\t%s caused %s %d damage.",
				damage.getKey().toString(), pname, damage.getValue()));
	}
	
	private Location exitLocation = null;
	
	public Location getExitLocation()
	{ return exitLocation; }
	
	public void setExitLocation(Location loc)
	{ this.exitLocation = loc; }

	public void updateCarrying()
	{
		Player player = getPlayer();
		if (player != null)
			updateCarrying(player.getInventory());
	}

	public void updateCarrying(Inventory inv)
	{
		Set<BlockData> newCarrying = Sets.newHashSet();
		if (getTeam() != null)
		{
			if (inv != null) for (ItemStack itm : inv)
				if (itm != null) newCarrying.add(BlockData.fromItemStack(itm));
			newCarrying.retainAll(getTeam().winConditions.values());
			
			if (newCarrying != carrying)
				getTeam().updateCarrying(this, carrying, newCarrying);
		}
		carrying = newCarrying;
	}

	public void updateHealthArmor()
	{
		Player player = this.getPlayer();
		if (player == null) return;
		
		int newHealth = player.getHealth();
		int newArmor = ArmorPoints.fromPlayerInventory(player.getInventory());
		
		getTeam().updateHealthArmor(this, 
			currentHealth, currentArmor, newHealth, newArmor);
		
		currentHealth = newHealth;
		currentArmor = newArmor;
	}

	public Inventory getInventoryView()
	{
		Player player = this.getPlayer();
		if (player == null) return null;
		
		Inventory pInventory = player.getInventory();
		Inventory inventoryView = Bukkit.getServer().createInventory(null,
			pInventory.getSize(), this.getName() + "'s Inventory");
		
		ItemStack[] contents = pInventory.getContents();
		for (int i = 0; i < contents.length; ++i)
			if (contents[i] != null) contents[i] = contents[i].clone();
		inventoryView.setContents(contents);
		
		return inventoryView;
	}

	public void showInventory(Player pl)
	{
		Inventory v = this.getInventoryView();
		if (v != null) pl.openInventory(v);
	}
}
