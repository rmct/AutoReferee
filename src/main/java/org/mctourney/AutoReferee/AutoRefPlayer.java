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
import org.bukkit.inventory.PlayerInventory;

import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.AutoReferee.AutoRefTeam.GoalStatus;
import org.mctourney.AutoReferee.util.ArmorPoints;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.BlockVector3;

import org.apache.commons.collections.map.DefaultedMap;

import com.google.common.collect.Sets;

public class AutoRefPlayer
{
	public static final EntityDamageEvent VOID_DEATH = 
		new EntityDamageEvent(null, EntityDamageEvent.DamageCause.VOID, 0);
	
	private static final int MIN_KILLSTREAK = 5;
	private static final int MIN_DOMINATE = 3;
	
	private static final long DAMAGE_COOLDOWN_TICKS = 3 * 20L;
	
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

	public void setTeam(AutoRefTeam t)
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
	
	// streak information - kill streak, domination, revenge
	public int totalStreak = 0;
	private Map<AutoRefPlayer, Integer> playerStreak;
	
	// last damage tick
	private long lastDamageTick;
	
	public long damageCooldownLength()
	{ return this.getTeam().getMatch().getWorld().getFullTime() - lastDamageTick; }
	
	public boolean wasDamagedRecently()
	{ return damageCooldownLength() < DAMAGE_COOLDOWN_TICKS; }
	
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
		
		// streak information
		totalStreak = 0;
		playerStreak = new DefaultedMap(0);
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
		
		// if the inventory needs to be cleared, clear it
		if (clearDrops) player.getInventory().clear();
		
		// "die" when the match isn't in progress just means a teleport
		if (!getTeam().getMatch().getCurrentState().inProgress())
		{
			player.teleport(getTeam().getSpawnLocation());
			player.setVelocity(new org.bukkit.util.Vector());
			player.setFallDistance(0.0f);
		}
		
		else
		{
			// if a cause of death is specified, set it
			if (deathCause != null) player.setLastDamageCause(deathCause);
			
			// kill the player
			player.setHealth(0);
		}
	}

	public void clearInventory()
	{
		Player p = getPlayer();
		if (p == null) return;
		
		// clear the player's inventory
		p.getInventory().clear();
		
		// clear the armor slots seperately
		p.getInventory().setHelmet(null);
		p.getInventory().setChestplate(null);
		p.getInventory().setLeggings(null);
		p.getInventory().setBoots(null);
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
		
		// reset the damage tick
		lastDamageTick = this.getTeam().getMatch().getWorld().getFullTime();
	}
	
	// register that we just died
	public void registerDeath(PlayerDeathEvent e)
	{
		// sanity check...
		if (e.getEntity() != getPlayer()) return;
		
		// get the last damage cause, and mark that as the cause of one death
		AutoRefPlayer.DamageCause dc = AutoRefPlayer.DamageCause.fromDamageEvent(e.getEntity().getLastDamageCause());
		deaths.put(dc, 1 + deaths.get(dc)); ++totalDeaths;
	
		AutoRefMatch match = getTeam().getMatch();
		Location loc = e.getEntity().getLocation();
		if (getExitLocation() != null) loc = getExitLocation();
		
		match.messageReferees("player", getPlayerName(), "deathpos", BlockVector3.fromLocation(loc).toCoords());
		match.messageReferees("player", getPlayerName(), "deaths", Integer.toString(totalDeaths));
		
		match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_DEATH,
			e.getDeathMessage(), loc, this, dc.p));
		
		// reset the streak after reporting (if it was good)
		if (totalStreak >= MIN_KILLSTREAK)
			match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_STREAK, 
				String.format("%s had a %d-kill streak!", this.getPlayerName(), totalStreak), loc, this, null));
		
		// reset total killstreak
		totalStreak = 0;
		match.messageReferees("player", getPlayerName(), "streak", Integer.toString(totalStreak));
	}
	
	// register that we killed the Player who fired this event
	public void registerKill(PlayerDeathEvent e)
	{
		// sanity check...
		if (e.getEntity().getKiller() != getPlayer()) return;
		
		// get the name of the player who died, record one kill against them
		AutoRefPlayer apl = getTeam().getMatch().getPlayer(e.getEntity());
		kills.put(apl, 1 + kills.get(apl)); ++totalKills;
		
		AutoRefMatch match = getTeam().getMatch();
		Location loc = e.getEntity().getLocation();
		
		// one more kill for kill streak
		++totalStreak;

		match.messageReferees("player", getPlayerName(), "kills", Integer.toString(totalKills));
		match.messageReferees("player", getPlayerName(), "streak", Integer.toString(totalStreak));
		
		if (playerStreak.get(apl) + 1 == MIN_DOMINATE)
		{
			match.messageReferees("player", getPlayerName(), "dominate", apl.getPlayerName());
			match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_DOMINATE, 
				String.format("%s is dominating %s", this.getPlayerName(), apl.getPlayerName()), loc, this, apl));
		}

		if (apl.playerStreak.get(this) >= MIN_DOMINATE)
		{
			match.messageReferees("player", getPlayerName(), "revenge", apl.getPlayerName());
			match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_REVENGE, 
				String.format("%s got revenge on %s", this.getPlayerName(), apl.getPlayerName()), loc, this, apl));
		}		

		// reset player streaks
		playerStreak.put(apl, playerStreak.get(apl) + 1);
		apl.playerStreak.put(this, 0);
	}

	public boolean isReady()
	{ return getPlayer().getWorld() == getTeam().getMatch().getWorld(); }

	public boolean isDominating(AutoRefPlayer apl)
	{ return playerStreak.get(apl) >= MIN_DOMINATE; }

	public int getScore()
	{ return totalKills - totalDeaths; }

	public String getAccuracy()
	{ return shotsFired == 0 ? "N/A" : (Integer.toString(100 * shotsHit / shotsFired) + "%"); }

	public void sendAccuracyUpdate()
	{
		String acc = Integer.toString(shotsFired == 0 ? 0 : (100 * shotsHit / shotsFired));
		getTeam().getMatch().messageReferees("player", getPlayerName(), "accuracy", acc);
	}

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
			newCarrying.retainAll(getTeam().getObjectives());
			
			Set<BlockData> oldCarrying = carrying;
			carrying = newCarrying;
			
			if (newCarrying != oldCarrying)
			{
				Map<BlockData, GoalStatus> oldStatus = getTeam().getObjectiveStatuses();
				getTeam().updateCarrying(this, oldCarrying, newCarrying);
				
				for (Map.Entry<BlockData, GoalStatus> e : getTeam().getObjectiveStatuses().entrySet())
				if (oldStatus.get(e.getKey()) == GoalStatus.NONE && e.getValue() != GoalStatus.NONE)
				{
					// generate a transcript event for seeing the box
					String m = String.format("%s is carrying %s", getPlayerName(), e.getKey().getRawName());
					getTeam().getMatch().addEvent(new TranscriptEvent(getTeam().getMatch(),
						TranscriptEvent.EventType.OBJECTIVE_FOUND, m, getPlayer().getLocation(), this, e.getKey()));
				}
			}
		}
	}

	public void updateHealthArmor()
	{
		Player player = this.getPlayer();
		if (player == null) return;
		
		int newHealth = Math.max(0, player.getHealth());
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
		
		PlayerInventory pInventory = player.getInventory();
		Inventory inventoryView = Bukkit.getServer().createInventory(null,
			pInventory.getSize() + 9, this.getName() + "'s Inventory");

		ItemStack[] oldContents = pInventory.getContents();
		ItemStack[] newContents = inventoryView.getContents();
		
		for (int i = 0; i < oldContents.length; ++i) 
			if (oldContents[i] != null) newContents[i] = oldContents[i];

		newContents[oldContents.length + 0] = pInventory.getHelmet();
		newContents[oldContents.length + 1] = pInventory.getChestplate();
		newContents[oldContents.length + 2] = pInventory.getLeggings();
		newContents[oldContents.length + 3] = pInventory.getBoots();

		newContents[oldContents.length + 7] = new ItemStack(Material.APPLE, player.getHealth());
		newContents[oldContents.length + 8] = new ItemStack(Material.COOKED_BEEF, player.getFoodLevel());
		
		for (int i = 0; i < oldContents.length; ++i)
			if (newContents[i] != null) newContents[i] = newContents[i].clone();
		
		inventoryView.setContents(newContents);
		return inventoryView;
	}

	public void showInventory(Player pl)
	{
		AutoRefMatch match = AutoReferee.getInstance().getMatch(pl.getWorld());
		if (match == null || !match.isReferee(pl)) return;

		Inventory v = this.getInventoryView();
		if (v != null) pl.openInventory(v);
	}
}
