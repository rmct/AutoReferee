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
import org.bukkit.potion.PotionEffect;

import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.AutoReferee.AutoRefTeam.WinCondition;
import org.mctourney.AutoReferee.util.AchievementPoints;
import org.mctourney.AutoReferee.util.ArmorPoints;
import org.mctourney.AutoReferee.util.BlockData;
import org.mctourney.AutoReferee.util.BlockVector3;

import org.apache.commons.collections.map.DefaultedMap;

import com.google.common.collect.Sets;

/**
 * Represents a player participating in a match.
 *
 * @author authorblues
 */
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

				default:
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

		private String materialToWeapon(Material mat)
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
					damager = ((AutoRefPlayer) p).getName();

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

				default:
					break;
			}

			return damager;
		}
	}

	// stored references
	private String pname = null;
	private AutoRefTeam team;

	/**
	 * Retrieves a Player object corresponding to this player. Player objects are not
	 * cached, since they can log out and log in, so this method will always require
	 * a lookup.
	 *
	 * @return a Player object
	 */
	public Player getPlayer()
	{ return AutoReferee.getInstance().getServer().getPlayer(pname); }

	/**
	 * Checks if the player is online.
	 *
	 * @return true if player is online, otherwise false
	 */
	public boolean isOnline()
	{ return AutoReferee.getInstance().getServer().getOfflinePlayer(pname).isOnline(); }

	private void setPlayer(Player player)
	{ this.setName(player.getName()); }

	/**
	 * Gets raw player name.
	 *
	 * @return player name
	 */
	public String getName()
	{ return pname; }

	/**
	 * Sets raw player name.
	 */
	public void setName(String name)
	{ this.pname = name; }

	protected int nameSearch(String name)
	{
		if (!pname.toLowerCase().startsWith(name.toLowerCase())) return Integer.MAX_VALUE;
		return pname.length() - name.length();
	}

	/**
	 * Gets the team this player is on.
	 *
	 * @return team object
	 */
	public AutoRefTeam getTeam()
	{ return team; }

	/**
	 * Sets the team this player is on.
	 */
	public void setTeam(AutoRefTeam team)
	{ this.team = team; }

	private String capeURL = null;

	/**
	 * Sets the URL for this player's cape. This cape can be shown by a modified
	 * client only, as Minecraft does not permit custom capes.
	 *
	 * @param url URL of a 64x32 image for the player's cape
	 */
	public void setCape(String url)
	{
		capeURL = url;
		getTeam().getMatch().messageReferees("player", this.getName(), "cape", getCape());
	}

	/**
	 * Gets the URL for this player's cape.
	 *
	 * @return cape URL
	 */
	public String getCape()
	{
		if (capeURL == null) return "";
		return capeURL.replaceFirst("^https?://", "");
	}

	private int shotsFired = 0, shotsHit = 0;

	/**
	 * Resets arrow fire statistics.
	 */
	public void resetArrowFire()
	{ this.shotsFired = this.shotsHit = 0; }

	/**
	 * Increments the recorded number of arrows fired.
	 */
	public void incrementShotsFired()
	{ ++this.shotsFired; this.sendAccuracyUpdate(); }

	/**
	 * Increments the recorded number of this player's arrows that hit a target.
	 */
	public void incrementShotsHit()
	{
		++this.shotsHit; this.sendAccuracyUpdate();
		this.addPoints(AchievementPoints.ARROW_HIT);
	}

	/**
	 * Gets the number of arrows fired by this player.
	 *
	 * @return number of arrows fired
	 */
	public int getShotsFired()
	{ return this.shotsFired; }

	/**
	 * Gets the number of arrows this player's arrows that hit a target.
	 *
	 * @return number of arrows hit
	 */
	public int getShotsHit()
	{ return this.shotsHit; }

	// number of times this player has killed other players
	private Map<AutoRefPlayer, Integer> kills;
	private int totalKills = 0;

	/**
	 * Gets the number of times this player has killed a specific player.
	 *
	 * @param apl target player
	 * @return number of kills
	 */
	public int getKills(AutoRefPlayer apl)
	{ return this.kills.get(apl); }

	/**
	 * Gets the total number of players killed by this player.
	 *
	 * @return number of kills
	 */
	public int getKills()
	{ return totalKills; }

	// number of times player has died and damage taken
	private Map<AutoRefPlayer.DamageCause, Integer> deaths;
	private Map<AutoRefPlayer.DamageCause, Integer> damage;
	private int totalDeaths = 0;

	/**
	 * Gets the number of times this player has been killed by a specific player.
	 *
	 * @param apl target player
	 * @return number of deaths
	 */
	public int getDeaths(AutoRefPlayer apl)
	{ return this.deaths.get(apl); }

	/**
	 * Gets the number of times this player has died.
	 *
	 * @return number of deaths
	 */
	public int getDeaths()
	{ return totalDeaths; }

	// tracking objective items
	private Set<BlockData> carrying;

	private int currentHealth = 20;
	private int currentArmor = 0;

	/**
	 * Gets the objectives carried by this player.
	 *
	 * @return set of objectives
	 */
	public Set<BlockData> getCarrying()
	{ return carrying; }

	// streak information - kill streak, domination, revenge
	private int totalStreak = 0;
	private Map<AutoRefPlayer, Integer> playerStreak;

	/**
	 * Gets the number of times this player has consecutively killed another. This
	 * value will reset itself to zero once this player is killed by the target player.
	 *
	 * @param apl target player
	 * @return number of consecutive deaths
	 */
	public int getStreak(AutoRefPlayer apl)
	{ return this.playerStreak.get(apl); }

	/**
	 * Gets the number of times this player has died in this life.
	 *
	 * @return number of consecutive deaths
	 */
	public int getStreak()
	{ return totalStreak; }

	// last damage tick
	private long lastDamageTick;

	/**
	 * Gets the number of world ticks since this player was last damaged.
	 *
	 * @return ticks since last damage
	 */
	public long damageCooldownLength()
	{ return this.getTeam().getMatch().getWorld().getFullTime() - lastDamageTick; }

	/**
	 * Checks if the player has been damaged recently.
	 *
	 * @return true if player was recently damaged, otherwise false
	 */
	public boolean wasDamagedRecently()
	{ return damageCooldownLength() < DAMAGE_COOLDOWN_TICKS; }

	// amount of time the saved inventory is valid
	private long SAVED_INVENTORY_LIFESPAN = 20L * 60 * 3;

	private Inventory lastInventoryView = null;
	private long lastInventoryViewSavedTick = -1L;

	private boolean savedInventoryStale()
	{ return getPlayer().getWorld().getFullTime() > lastInventoryViewSavedTick + SAVED_INVENTORY_LIFESPAN; }

	private int points = 0;

	/**
	 * Adds achievement points for this player.
	 */
	public void addPoints(AchievementPoints ach)
	{
		if (ach == null) return;
		this.addPoints(ach.getValue());
	}

	/**
	 * Adds achievement points for this player. This method can be used to add
	 * custom point values, if necessary.
	 */
	public void addPoints(int points)
	{
		this.points += points;
	}

	/**
	 * Gets the number of achievement points this player has earned.
	 *
	 * @return achievement points
	 */
	public int getPoints()
	{ return points; }

	/**
	 * Gets the normalized number of achievement points this player has earned.
	 *
	 * @return normalized achievement points
	 */
	public int getDisplayPoints()
	{ return AchievementPoints.ticksToPoints(points); }

	/**
	 * Gets location of this player's bed.
	 */
	public Location getBedLocation()
	{
		Player pl = getPlayer();
		if (pl == null) return null;
		return pl.getBedSpawnLocation();
	}

	private Location lastDeathLocation = null;

	/**
	 * Gets location of this player's most recent death.
	 */
	public Location getLastDeathLocation()
	{ return lastDeathLocation; }

	public void setLastDeathLocation(Location loc)
	{
		lastDeathLocation = loc;
		this.getTeam().getMatch().setLastDeathLocation(loc);
	}

	private Location lastLogoutLocation = null;

	/**
	 * Gets location of this player's most recent logout.
	 */
	public Location getLastLogoutLocation()
	{ return lastLogoutLocation; }

	public void setLastLogoutLocation(Location loc)
	{
		lastLogoutLocation = loc;
		this.getTeam().getMatch().setLastLogoutLocation(loc);
	}

	private Location lastTeleportLocation = null;

	/**
	 * Gets location of this player's most recent teleport.
	 */
	public Location getLastTeleportLocation()
	{ return lastTeleportLocation; }

	public void setLastTeleportLocation(Location loc)
	{
		lastTeleportLocation = loc;
		this.getTeam().getMatch().setLastTeleportLocation(loc);
	}

	/**
	 * Gets this player's current location.
	 *
	 * @return location of player if logged in, otherwise location of last logout
	 */
	public Location getLocation()
	{
		Player pl = getPlayer();
		if (pl != null) return pl.getLocation();
		return lastLogoutLocation;
	}

	/**
	 * Creates a player object for the given player name.
	 *
	 * @param name player name
	 * @param team player team, or null if no team
	 */
	@SuppressWarnings("unchecked")
	public AutoRefPlayer(String name, AutoRefTeam team)
	{
		// detailed statistics
		kills  = new DefaultedMap(0);
		deaths = new DefaultedMap(0);
		damage = new DefaultedMap(0);

		// accuracy information
		this.resetArrowFire();

		// save the player and team as references
		this.setName(name);
		this.setTeam(team);

		// setup the carrying list
		this.carrying = Sets.newHashSet();

		// streak information
		playerStreak = new DefaultedMap(0);
		totalStreak = 0;
	}

	/**
	 * Creates a player object for the given player.
	 *
	 * @param player player
	 * @param team player team, or null if no team
	 */
	public AutoRefPlayer(Player player, AutoRefTeam team)
	{
		this(player.getName(), team);

		// setup base health and armor level
		this.currentHealth = player.getHealth();
		this.currentArmor = ArmorPoints.fromPlayerInventory(player.getInventory());
	}

	/**
	 * Creates a player object for the given player.
	 *
	 * @param player player
	 */
	public AutoRefPlayer(Player player)
	{ this(player, AutoReferee.getInstance().getTeam(player)); }

	@Override
	public int hashCode()
	{ return getName().hashCode(); }

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AutoRefPlayer)) return false;
		return getName().equals(((AutoRefPlayer) o).getName());
	}

	/**
	 * Gets name of this player, colored with team colors.
	 *
	 * @return colored name
	 */
	public String getDisplayName()
	{
		if (getTeam() == null) return getName();
		return getTeam().getColor() + getName() + ChatColor.RESET;
	}

	/**
	 * Kills the player. If the match has not yet started, the player
	 * will be teleported back to spawn.
	 *
	 * @param cause reported cause of death, or null if no cause
	 * @param cleardrops clear contents of inventory on death
	 */
	public void die(EntityDamageEvent cause, boolean cleardrops)
	{
		Player player = getPlayer();
		if (player == null || player.isDead()) return;

		// if the inventory needs to be cleared, clear it
		if (cleardrops) this.clearInventory();

		// "die" when the match isn't in progress just means a teleport
		if (!getTeam().getMatch().getCurrentState().inProgress())
		{
			player.teleport(getTeam().getSpawnLocation());
			player.setFallDistance(0.0f);
		}

		else
		{
			// if a cause of death is specified, set it
			if (cause != null) player.setLastDamageCause(cause);

			// kill the player
			player.setHealth(0);
		}
	}

	/**
	 * Clears the contents of this player's inventory.
	 */
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

	/**
	 * Heals this player, including health, hunger, saturation, and exhaustion.
	 */
	public void heal()
	{
		getPlayer().setHealth    ( 20 ); // 10 hearts
		getPlayer().setFoodLevel ( 20 ); // full food
		getPlayer().setSaturation(  5 ); // saturation depletes hunger
		getPlayer().setExhaustion(  0 ); // exhaustion depletes saturation
	}

	public void enterLane()
	{
		Player pl = getPlayer();
		if (pl == null) return;

		this.heal();
		this.clearInventory();

		// reset the player's level
		pl.setLevel(0);
		pl.setExp(0.0f);

		// remove all potion effects upon entering the lane
		for (PotionEffect effect : pl.getActivePotionEffects())
			pl.removePotionEffect(effect.getType());
	}

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
		this.saveInventoryView();

		// get the last damage cause, and mark that as the cause of one death
		AutoRefPlayer.DamageCause dc = AutoRefPlayer.DamageCause.fromDamageEvent(e.getEntity().getLastDamageCause());
		deaths.put(dc, 1 + deaths.get(dc)); ++totalDeaths;

		AutoRefMatch match = getTeam().getMatch();
		Location loc = e.getEntity().getLocation();
		if (getExitLocation() != null) loc = getExitLocation();

		match.messageReferees("player", getName(), "deathpos", BlockVector3.fromLocation(loc).toCoords());
		match.messageReferees("player", getName(), "deaths", Integer.toString(totalDeaths));

		match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_DEATH,
			e.getDeathMessage(), loc, this, dc.p));
		this.setLastDeathLocation(loc);

		// reset total kill streak
		this.resetKillStreak();
		match.messageReferees("player", getName(), "streak", Integer.toString(totalStreak));
	}

	/**
	 * Resets this player's killstreak.
	 */
	public void resetKillStreak()
	{
		// if it meets the requirements, report it
		if (totalStreak >= MIN_KILLSTREAK)
		{
			AutoRefMatch match = getTeam().getMatch();
			match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_STREAK,
				String.format("%s had a %d-kill streak!", this.getName(), totalStreak), null, this, null));
		}

		// reset to zero
		this.totalStreak = 0;
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

		if (totalStreak >= MIN_KILLSTREAK)
			match.messageReferees("player", getName(), "streak", Integer.toString(totalStreak));
		match.messageReferees("player", getName(), "kills", Integer.toString(totalKills));

		if (playerStreak.get(apl) + 1 == MIN_DOMINATE)
		{
			match.messageReferees("player", getName(), "dominate", apl.getName());
			match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_DOMINATE,
				String.format("%s is dominating %s", this.getName(), apl.getName()), loc, this, apl));
		}

		if (apl.isDominating(this))
		{
			match.messageReferees("player", getName(), "revenge", apl.getName());
			match.addEvent(new TranscriptEvent(match, TranscriptEvent.EventType.PLAYER_REVENGE,
				String.format("%s got revenge on %s", this.getName(), apl.getName()), loc, this, apl));
		}

		// reset player streaks
		playerStreak.put(apl, playerStreak.get(apl) + 1);
		apl.playerStreak.put(this, 0);
	}

	/**
	 * Checks if this player is dominating a specified player.
	 *
	 * @return true if dominating, otherwise false
	 */
	public boolean isDominating(AutoRefPlayer apl)
	{ return playerStreak.get(apl) >= MIN_DOMINATE; }

	/**
	 * Checks if this player is in the correct world.
	 *
	 * @return true if player is in correct world, otherwise false
	 */
	public boolean isPresent()
	{ return getPlayer().getWorld() == getTeam().getMatch().getWorld(); }

	/**
	 * Gets the kill-death difference.
	 *
	 * @return difference of the kills and deaths
	 */
	public int getKDD()
	{ return totalKills - totalDeaths; }

	/**
	 * Gets a string representation of this player's accuracy.
	 */
	public String getAccuracy()
	{ return shotsFired == 0 ? "N/A" : (Integer.toString(100 * shotsHit / shotsFired) + "%"); }

	public void sendAccuracyUpdate()
	{ for (Player ref : getTeam().getMatch().getReferees(false)) sendAccuracyUpdate(ref); }

	public void sendAccuracyUpdate(Player ref)
	{
		String acc = Integer.toString(shotsFired == 0 ? 0 : (100 * shotsHit / shotsFired));
		getTeam().getMatch().messageReferee(ref, "player", getName(), "accuracy", acc);
	}

	/**
	 * Gets a string representation of this player's accuracy. Includes number of shots
	 * fired and the number of hits.
	 */
	public String getExtendedAccuracyInfo()
	{
		return String.format("%s (%d/%d)", (shotsFired == 0 ? "N/A" :
			(Integer.toString(100 * shotsHit / shotsFired) + "%")), shotsHit, shotsFired);
	}

	private Location exitLocation = null;

	/**
	 * Gets the location where this player left their lane.
	 *
	 * @return exit location
	 */
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

	private void updateCarrying(Inventory inv)
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
				for (WinCondition wc : getTeam().getWinConditions())
					if (wc.getStatus() == WinCondition.GoalStatus.NONE && newCarrying.contains(wc.getBlockData()))
				{
					// generate a transcript event for seeing the box
					String m = String.format("%s is carrying %s", getName(), wc.getBlockData().getName());
					getTeam().getMatch().addEvent(new TranscriptEvent(getTeam().getMatch(),
						TranscriptEvent.EventType.OBJECTIVE_FOUND, m, getLocation(), this, wc.getBlockData()));
					this.addPoints(AchievementPoints.OBJECTIVE_FOUND);

					// store the player's location as the last objective location
					getTeam().setLastObjectiveLocation(getLocation());
				}
				getTeam().updateCarrying(this, oldCarrying, newCarrying);
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

	private void saveInventoryView()
	{
		this.lastInventoryView = getInventoryView();
		this.lastInventoryViewSavedTick = getPlayer().getWorld().getFullTime();
	}

	/**
	 * Gets the last saved copy of this player's inventory. If the inventory view
	 * is older than a certain age, this method returns null
	 *
	 * @return inventory view, or null
	 */
	public Inventory getLastInventoryView()
	{ return savedInventoryStale() ? null : this.lastInventoryView; }

	/**
	 * Gets a copy of this player's current inventory. This includes an extra row
	 * for armor and health/hunger information.
	 *
	 * @return inventory view
	 */
	public Inventory getInventoryView()
	{
		Player player = this.getPlayer();
		if (player == null) return null;

		PlayerInventory pInventory = player.getInventory();
		Inventory inventoryView = Bukkit.getServer().createInventory(null,
			pInventory.getSize() + 9, this.getDisplayName() + "'s Inventory");

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

	private boolean showInventory(Player pl, Inventory v)
	{
		AutoRefMatch match = AutoReferee.getInstance().getMatch(pl.getWorld());
		if (match == null || !match.isReferee(pl)) return false;

		// show the requested inventory view
		if (v != null) pl.openInventory(v);
		return v == null;
	}

	/**
	 * Shows this player's inventory to the specified player.
	 *
	 * @return true if the inventory can be shown, otherwise false
	 */
	public boolean showInventory(Player player)
	{ return this.showInventory(player, this.getInventoryView()); }

	/**
	 * Shows this player's last saved inventory to the specified player.
	 *
	 * @return true if the inventory can be shown, otherwise false
	 */
	public boolean showSavedInventory(Player player)
	{ return this.showInventory(player, this.getLastInventoryView()); }
}
