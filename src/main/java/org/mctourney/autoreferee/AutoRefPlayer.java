package org.mctourney.autoreferee;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import org.mctourney.autoreferee.AutoRefMatch.RespawnMode;
import org.mctourney.autoreferee.goals.AutoRefGoal;
import org.mctourney.autoreferee.goals.BlockGoal;
import org.mctourney.autoreferee.listeners.GoalsInventorySnapshot;
import org.mctourney.autoreferee.util.AchievementPoints;
import org.mctourney.autoreferee.util.ArmorPoints;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;
import org.mctourney.autoreferee.util.Metadatable;
import org.mctourney.autoreferee.util.PlayerKit;
import org.mctourney.autoreferee.util.PlayerUtil;

import org.apache.commons.collections.map.DefaultedMap;

import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Sets;

/**
 * Represents a player participating in a match.
 *
 * @author authorblues
 */
public class AutoRefPlayer implements Metadatable, Comparable<AutoRefPlayer>
{
	public class JSONPlayerData
	{
		public String name, team;
		public int kills, assists, deaths;
		public int streak;

		public int arrowfired, arrowhit;
		public int teamkills;
	}

	public static final EntityDamageEvent VOID_DEATH =
		new EntityDamageEvent(null, EntityDamageEvent.DamageCause.VOID, 0);

	private static final int MIN_KILLSTREAK = 5;
	private static final int MIN_DOMINATE = 3;

	private static final long DAMAGE_COOLDOWN_TICKS = 3 * 20L;
	public static final int SLOT_HELMET = 0;
	public static final int SLOT_CHEST = 1;
	public static final int SLOT_LEGS = 2;
	public static final int SLOT_FEET = 3;

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
	{
		return AutoReferee.getInstance().getServer().getOfflinePlayer(pname).isOnline()
			&& getPlayer().getWorld() == getMatch().getWorld();
	}

	/**
	 * Checks if the player is dead.
	 *
	 * @return true if player is dead, otherwise false
	 */
	public boolean isDead()
	{ return currentHealth <= 0; }

	/**
	 * Gets raw player name.
	 *
	 * @return player name
	 */
	public String getName()
	{ return pname; }

	@Override public String toString()
	{ return "AutoRefPlayer[" + getName() + ", team=" + getTeam() + "]"; }

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

	protected Map<String, Object> metadata = Maps.newHashMap();

	public void addMetadata(String key, Object value)
	{ this.metadata.put(key, value); }

	public Object getMetadata(String key)
	{ return this.metadata.get(key); }

	public boolean hasMetadata(String key)
	{ return this.metadata.containsKey(key); }

	public Object removeMetadata(String key)
	{ return this.metadata.remove(key); }

	public void clearMetadata()
	{ this.metadata.clear(); }

	/**
	 * Gets the match this player is in.
	 *
	 * @return match object
	 */
	public AutoRefMatch getMatch()
	{ return this.team == null ? null : this.team.getMatch(); }

	/**
	 * Gets the URL for this player's cape.
	 *
	 * @return cape URL
	 */
	public String getCape()
	{
		String cape = getMatch().playerCapes.get(this.getName());
		return cape == null ? "" : cape.replaceFirst("^https?://", "");
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

	// number of times this player has killed other players
	@SuppressWarnings("unchecked")
	private Map<AutoRefPlayer, Integer> kills = new DefaultedMap(0);
	private int totalKills = 0;
	private int teamKills = 0;

	// number of times this player has assisted in killing other players
	private int totalAssists = 0;

	private double furthestShot = 0.0;

	/**
	 * Sets the furthest accurate bow shot.
	 */
	public void setFurthestShot(double distance)
	{ if (distance > furthestShot) furthestShot = distance; }

	private int livesRemaining = -1;

	/**
	 * Checks if this player has lives remaining.
	 *
	 * @return whether player has lives remaining, true if infinite lives
	 */
	public boolean hasLives()
	{ return getMatch().getRespawnMode() != RespawnMode.DISALLOW && livesRemaining != 0; }

	/**
	 * Sets the number of lives remaining for this player.
	 *
	 * @param lives number of lives remaining before player is eliminated
	 */
	public void setLivesRemaining(int lives)
	{ this.livesRemaining = lives; }

	/**
	 * Gets the number of times this player has killed a specific player.
	 *
	 * @param apl target player
	 * @return number of kills
	 */
	public int getKills(AutoRefPlayer apl)
	{ return this.kills.get(apl); }

	public int getAssists()
	{ return this.totalAssists; }

	/**
	 * Gets the total number of players killed by this player.
	 *
	 * @return number of kills
	 */
	public int getKills()
	{ return totalKills - teamKills; }

	@SuppressWarnings("unchecked")
	private Map<AutoRefPlayer, Long> lastPlayerDamageMillis = new DefaultedMap((Long) 0L);
	private static final long KILLER_MS = 1000L * 3;
	private static final long ASSIST_MS = 1000L * 5;

	/**
	 * Gets the player responsible for killing this player.
	 *
	 * @return killer
	 */
	public AutoRefPlayer getKiller()
	{
		// if the player is not dead, no killer
		if (this.isOnline() && !getPlayer().isDead()) return null;

		// the cutoff before which the player will not be credited with the kill
		long threshold = ManagementFactory.getRuntimeMXBean().getUptime() - KILLER_MS;

		AutoRefPlayer killer = null;
		for (Map.Entry<AutoRefPlayer, Long> e : lastPlayerDamageMillis.entrySet())
			if (e.getValue() > threshold) { threshold = e.getValue(); killer = e.getKey(); }
		return killer;
	}

	/**
	 * Gets the players responsible for killing this player.
	 *
	 * @return killers
	 */
	public Set<AutoRefPlayer> getKillAssists()
	{
		// if the player is not dead, no killer
		if (this.isOnline() && !getPlayer().isDead()) return Sets.newHashSet();

		// the cutoff before which the player will not be credited with the kill
		long threshold = ManagementFactory.getRuntimeMXBean().getUptime() - ASSIST_MS;

		Set<AutoRefPlayer> killers = Sets.newHashSet();
		for (Map.Entry<AutoRefPlayer, Long> e : lastPlayerDamageMillis.entrySet())
			if (e.getValue() > threshold) killers.add(e.getKey());
		return killers;
	}

	// number of times player has died and damage taken
	@SuppressWarnings("unchecked")
	private Map<AutoRefPlayer, Integer> deaths = new DefaultedMap((Integer) 0);
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
	public int getDeathCount()
	{ return totalDeaths; }

	// tracking objective items
	private GoalsInventorySnapshot carrying;

	private int currentHealth = 20;
	private int currentArmor = 0;

	/**
	 * Gets the objectives carried by this player.
	 *
	 * @return set of objectives
	 */
	public GoalsInventorySnapshot getCarrying()
	{
		Player p = getPlayer();
		AutoRefTeam t = getTeam();
		if (p == null || t == null)
		{ carrying = new GoalsInventorySnapshot(); }
		else
		{ carrying = new GoalsInventorySnapshot(p.getInventory(), t.getObjectives()); }

		return carrying;
	}

	private GoalsInventorySnapshot beforeOpeningInventory;
	private String inventoryDescription;
	private Location inventoryLocation;

	public GoalsInventorySnapshot getBeforeOpeningInventorySnapshot()
	{ return beforeOpeningInventory; }

	public String getInventoryDescription()
	{ return ChatColor.GOLD + StringUtils.capitalize(inventoryDescription.toLowerCase()); }

	public Location getInventoryLocation()
	{ return inventoryLocation; }

	public void setActiveInventoryInfo(GoalsInventorySnapshot mySnap, Location loc, String description)
	{
		beforeOpeningInventory = mySnap;
		inventoryDescription = description;
		inventoryLocation = loc;
	}

	public void clearActiveInventoryInfo()
	{
		beforeOpeningInventory = null;
		inventoryLocation = null;
		inventoryDescription = null;
	}

	// streak information - kill streak, domination, revenge
	private int totalStreak = 0;
	@SuppressWarnings("unchecked")
	private final Map<AutoRefPlayer, Integer> playerStreak = new DefaultedMap(0);

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
	private long lastDamageMillis;

	/**
	 * Gets the number of world ticks since this player was last damaged.
	 *
	 * @return ticks since last damage
	 */
	public long damageCooldownLength()
	{ return ManagementFactory.getRuntimeMXBean().getUptime() - lastDamageMillis; }

	/**
	 * Checks if the player has been damaged recently.
	 *
	 * @return true if player was recently damaged, otherwise false
	 */
	public boolean wasDamagedRecently()
	{ return damageCooldownLength() < DAMAGE_COOLDOWN_TICKS; }

	// amount of time the saved inventory is valid
	private static final long SAVED_INVENTORY_LIFESPAN = 1000L * 60 * 3;

	private Inventory lastInventoryView = null;
	private long lastInventoryViewSavedMillis = -1L;

	private boolean isSavedInventoryStale()
	{
		return ManagementFactory.getRuntimeMXBean().getUptime() >
			lastInventoryViewSavedMillis + SAVED_INVENTORY_LIFESPAN;
	}

	// TODO never used
	// private int points = 0;

	/**
	 * Adds achievement points for this player.
	 *
	 * TODO value is never read
	 */
	public void addPoints(AchievementPoints ach, int count)
	{
		// if (ach == null) return;
		// this.addPoints(ach.getValue() * count);
	}

	/**
	 * Adds achievement points for this player.
	 */
	public void addPoints(AchievementPoints ach)
	// { this.addPoints(ach, 1); }
	{ }

	/**
	 * Adds achievement points for this player. This method can be used to add
	 * custom point values, if necessary.
	 */
	public void addPoints(int points)
	// { this.points += points; }
	{ }

	/**
	 * Gets the number of achievement points this player has earned.
	 *
	 * @return achievement points
	 */
	// public int getPoints()
	// { return points; }

	/**
	 * Gets the normalized number of achievement points this player has earned.
	 *
	 * @return normalized achievement points
	 */
	// public int getDisplayPoints()
	// { return AchievementPoints.ticksToPoints(points); }

	/**
	 * Returns whether or not the player has the AutoReferee client mod installed.
	 *
	 * @return true if using client mod, otherwise false
	 */
	public boolean hasClientMod()
	{ return PlayerUtil.hasClientMod(getPlayer()); }

	/**
	 * Gets location of this player's bed.
	 */
	public Location getBedLocation()
	{
		Player pl = getPlayer();
		return pl == null ? null : pl.getBedSpawnLocation();
	}

	/**
	 * Gets whether or not the player has a bed spawn set.
	 */
	public boolean hasBed()
	{ return this.getBedLocation() != null; }

	private Location lastDeathLocation = null;

	/**
	 * Gets location of this player's most recent death.
	 */
	public Location getLastDeathLocation()
	{ return lastDeathLocation; }

	public void setLastDeathLocation(Location loc)
	{
		lastDeathLocation = loc;
		this.getMatch().setLastDeathLocation(loc);
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
		this.getMatch().setLastLogoutLocation(loc);
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
		this.getMatch().setLastTeleportLocation(loc);
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
		// accuracy information
		this.resetArrowFire();

		// save the player and team as references
		this.setName(name);
		this.setTeam(team);
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
		return o instanceof AutoRefPlayer && getName().equals(((AutoRefPlayer) o).getName());
	}

	@Override
	public int compareTo(AutoRefPlayer other)
	{
		if (other == null) return 1;
		return this.pname.compareTo(other.pname);
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
		if (!getMatch().getCurrentState().inProgress())
		{
			player.teleport(getMatch().getPlayerSpawn(player));
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

	private boolean godmode = false;

	public void setGodMode(boolean godmode)
	{ this.godmode = godmode; }

	public boolean isGodMode()
	{ return this.godmode && getMatch().isPracticeMode(); }

	private boolean active = false;

	public boolean isActive()
	{ return active; }

	public void setActive()
	{ active = true; }

	/**
	 * Clears the contents of this player's inventory.
	 */
	public void clearInventory()
	{
		Player p = getPlayer();
		if (p != null) PlayerUtil.clearInventory(p);
	}

	public void respawn()
	{
		this.setExitLocation(null);
		this.active = false;
	}

	/**
	 * Heals this player, including health, hunger, saturation, and exhaustion.
	 */
	public void heal()
	{
		Player p = getPlayer();
		if (p != null) PlayerUtil.restore(p);
	}

	public void reset()
	{
		Player p = getPlayer();
		if (p != null) PlayerUtil.reset(p);

		if (getTeam() != null)
		{
			PlayerKit kit = getTeam().getKit();
			if (kit != null) kit.giveTo(this);
		}
	}

	public void registerDamage(EntityDamageEvent e, Player damager)
	{
		// sanity check...
		if (e.getEntity() != getPlayer()) return;

		// reset the damage tick
		lastDamageMillis = ManagementFactory.getRuntimeMXBean().getUptime();

		// if the damage was caused by a player, set their last damage tick
		AutoRefPlayer apl = getMatch().getPlayer(damager);
		if (apl != null) lastPlayerDamageMillis.put(apl, lastDamageMillis);
	}

	// register that we just died
	public void registerDeath(PlayerDeathEvent e, Location from)
	{
		// sanity check...
		if (e.getEntity() != getPlayer()) return;
		this.saveInventoryView();

		// if this player has a number of lives, reduce by one
		if (hasLives()) --livesRemaining;

		AutoRefMatch match = getMatch();
		AutoRefPlayer killer = match.getPlayer(e.getEntity().getKiller());
		deaths.put(killer, getDeaths(killer) + 1);
		totalDeaths++;

		for (AutoRefPlayer apl : this.getKillAssists())
			if (apl != killer && apl.getTeam() != match.getPlayerTeam(e.getEntity())) ++apl.totalAssists;

		Location loc = e.getEntity().getLocation();
		if (getExitLocation() != null) loc = getExitLocation();

		match.messageReferees("player", getName(), "deathpos", LocationUtil.toBlockCoords(loc));
		match.messageReferees("player", getName(), "deaths", Integer.toString(totalDeaths));

		TranscriptEvent entry = new TranscriptEvent(match, TranscriptEventType.PLAYER_DEATH,
			e.getDeathMessage(), loc, this, killer);

		match.addEvent(entry);

		String m = entry.getColoredMessage();
		for (Player pl : match.getWorld().getPlayers())
			pl.sendMessage(pl != e.getEntity().getKiller() || from == null ? m
				: String.format("%s " + ChatColor.DARK_GRAY + "(@ %.2f)", m, getLocation().distance(from)));

		this.setLastDeathLocation(loc);
		this.addPoints(AchievementPoints.DEATH);

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
			getMatch().addEvent(new TranscriptEvent(getMatch(), TranscriptEventType.PLAYER_STREAK,
				String.format("%s had a %d-kill streak!", this.getName(), totalStreak), null, this));

		// reset to zero
		this.totalStreak = 0;
	}

	// register that we killed the Player who fired this event
	public void registerKill(PlayerDeathEvent e)
	{
		// get the name of the player who died, record one kill against them
		AutoRefPlayer apl = getMatch().getPlayer(e.getEntity());
		kills.put(apl, 1 + kills.get(apl)); ++totalKills;

		// if the player is on our team, register a team kill
		if (apl.getTeam() == this.getTeam()) ++teamKills;

		AutoRefMatch match = getMatch();
		Location loc = e.getEntity().getLocation();

		// one more kill for kill streak
		++totalStreak;

		if (totalStreak >= MIN_KILLSTREAK)
			match.messageReferees("player", getName(), "streak", Integer.toString(totalStreak));
		match.messageReferees("player", getName(), "kills", Integer.toString(totalKills));

		if (getStreak(apl) + 1 == MIN_DOMINATE)
		{
			match.messageReferees("player", getName(), "dominate", apl.getName());
			match.addEvent(new TranscriptEvent(match, TranscriptEventType.PLAYER_DOMINATE,
				String.format("%s is dominating %s", this.getName(), apl.getName()), apl.getLocation(), apl, this));
		}

		if (apl.isDominating(this))
		{
			match.messageReferees("player", getName(), "revenge", apl.getName());
			match.addEvent(new TranscriptEvent(match, TranscriptEventType.PLAYER_REVENGE,
				String.format("%s got revenge on %s", this.getName(), apl.getName()), loc, this, apl));
			this.addPoints(AchievementPoints.REVENGE);
		}

		// reset player streaks
		playerStreak.put(apl, getStreak(apl) + 1);
		apl.playerStreak.put(this, 0);
	}

	/**
	 * Checks if this player is dominating a specified player.
	 *
	 * @return true if dominating, otherwise false
	 */
	public boolean isDominating(AutoRefPlayer apl)
	{ return getStreak(apl) >= MIN_DOMINATE; }

	/**
	 * Checks if this player is in the correct world.
	 *
	 * @return true if player is in correct world, otherwise false
	 */
	public boolean isPresent()
	{ return getPlayer().getWorld() == getMatch().getWorld(); }

	/**
	 * Gets the kill-death difference.
	 *
	 * @return difference of the kills and deaths
	 */
	public int getKDD()
	{ return totalKills - totalDeaths; }

	public void sendAccuracyUpdate()
	{ for (Player ref : getMatch().getReferees(false)) sendAccuracyUpdate(ref); }

	public void sendAccuracyUpdate(Player ref)
	{
		String acc = Integer.toString(shotsFired == 0 ? 0 : (100 * shotsHit / shotsFired));
		AutoRefMatch.messageReferee(ref, "player", getName(), "accuracy", acc);
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

	public boolean isInsideLane()
	{ return getExitLocation() == null; }

	public void updateCarrying()
	{
		Player player = getPlayer();
		if (player == null) return;

		if (getTeam() != null)
		{
			GoalsInventorySnapshot oldCarrying = carrying;
			carrying = null; // invalidate old value
			carrying = getCarrying();

			if (oldCarrying == null)
			{ return; }

			MapDifference<BlockData, Integer> diff = oldCarrying.getDiff(carrying);

			if (!diff.areEqual())
			{
				for (BlockGoal goal : getTeam().getTeamGoals(BlockGoal.class))
					if (goal.getItemStatus() == AutoRefGoal.ItemStatus.NONE && carrying.containsKey(goal.getItem()))
					{
						// generate a transcript event for being the first
						String m = String.format("%s is carrying the first %s!", getDisplayName(), goal.getItem().getDisplayName());
						getMatch().addEvent(new TranscriptEvent(getMatch(),
							TranscriptEventType.OBJECTIVE_FOUND, m, getLocation(), this, goal.getItem()));
						this.addPoints(AchievementPoints.OBJECTIVE_FOUND);

						// store the player's location as the last objective location
						getTeam().setLastObjectiveLocation(getLocation());
					}
				getTeam().updateCarrying(this, oldCarrying, carrying);
			}
		}
	}

	public void updateHealthArmor()
	{
		Player player = this.getPlayer();
		if (player == null) return;

		int newHealth = Math.max(0, player.getHealth());
		int newArmor = ArmorPoints.fromPlayerInventory(player.getInventory());

		if (getTeam() != null) getTeam().updateHealthArmor(this,
			currentHealth, currentArmor, newHealth, newArmor);

		currentHealth = newHealth;
		currentArmor = newArmor;
	}

	private void saveInventoryView()
	{
		String invname = this.getDisplayName() + " @ " + getMatch().getTimestamp();
		this.lastInventoryView = getInventoryView(invname);
		this.lastInventoryViewSavedMillis = ManagementFactory.getRuntimeMXBean().getUptime();
	}

	/**
	 * Gets the last saved copy of this player's inventory. If the inventory view
	 * is older than a certain age, this method returns null
	 *
	 * @return inventory view, or null
	 */
	public Inventory getLastInventoryView()
	{ return isSavedInventoryStale() ? null : this.lastInventoryView; }

	/**
	 * Gets a copy of this player's current inventory. This includes an extra row
	 * for armor and health/hunger information.
	 *
	 * @return inventory view
	 */
	public Inventory getInventoryView()
	{ return getInventoryView(this.getDisplayName() + "'s Inventory"); }

	/**
	 * Gets a copy of this player's current inventory. This includes an extra row
	 * for armor and health/hunger information.
	 *
	 * @return inventory view
	 */
	public Inventory getInventoryView(String name)
	{
		Player player = this.getPlayer();
		if (player == null) return null;

		PlayerInventory pInventory = player.getInventory();
		String inventoryName = name.length() > 32 ? name.substring(0, 32) : name;
		Inventory inventoryView = Bukkit.getServer().createInventory(null,
			pInventory.getSize() + 9, inventoryName);

		ItemStack[] oldContents = pInventory.getContents();
		ItemStack[] newContents = inventoryView.getContents();

		newContents[oldContents.length + SLOT_HELMET] = copyItem(pInventory.getHelmet());
		newContents[oldContents.length + SLOT_CHEST] = copyItem(pInventory.getChestplate());
		newContents[oldContents.length + SLOT_LEGS] = copyItem(pInventory.getLeggings());
		newContents[oldContents.length + SLOT_FEET] = copyItem(pInventory.getBoots());

		// SLOT 6: ACTIVE POTION EFFECTS
		if (player.getActivePotionEffects().size() > 0)
		{
			ItemStack potion = new Potion(PotionType.POISON).toItemStack(1);
			ItemMeta meta = potion.getItemMeta();

			List<String> effects = Lists.newLinkedList();
			for (PotionEffect effect : player.getActivePotionEffects())
				effects.add(ChatColor.RESET + "" + ChatColor.GRAY +
					PlayerUtil.getStatusEffectName(effect));

			meta.setDisplayName(ChatColor.BLUE + "" + ChatColor.ITALIC + "Status Effects");
			meta.setLore(effects);

			potion.setItemMeta(meta);
			newContents[oldContents.length + 5] = potion;
		}

		// SLOT 7: CURRENT PLAYER LEVEL
		if (player.getLevel() > 0)
		{
			ItemStack level = new ItemStack(Material.EXP_BOTTLE, player.getLevel());
			ItemMeta meta = level.getItemMeta();

			meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.ITALIC + "Player XP Level");
			meta.setLore(Lists.newArrayList(ChatColor.GRAY + "" + ChatColor.ITALIC +
				String.format("%d %s", player.getLevel(), player.getLevel() == 1 ? "level" : "levels")));

			level.setItemMeta(meta);
			newContents[oldContents.length + 6] = level;
		}

		// SLOT 8: PLAYER HEALTH
		{
			ItemStack health = new ItemStack(Material.APPLE, player.getHealth());
			ItemMeta meta = health.getItemMeta();

			meta.setDisplayName(ChatColor.RED + "" + ChatColor.ITALIC + "Player Health");
			meta.setLore(Lists.newArrayList(ChatColor.GRAY + "" + ChatColor.ITALIC +
				String.format("%2.1f hearts", player.getHealth() / 2.0)));

			health.setItemMeta(meta);
			newContents[oldContents.length + 7] = health;
		}

		// SLOT 9: PLAYER HUNGER
		{
			ItemStack hunger = new ItemStack(Material.COOKED_BEEF, player.getFoodLevel());
			ItemMeta meta = hunger.getItemMeta();

			meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.ITALIC + "Player Hunger");
			meta.setLore(Lists.newArrayList(ChatColor.GRAY + "" + ChatColor.ITALIC +
				String.format("%2.1f food", player.getFoodLevel() / 2.0)));

			hunger.setItemMeta(meta);
			newContents[oldContents.length + 8] = hunger;
		}

		for (int i = 0; i < oldContents.length; ++i)
			newContents[i] = copyItem(oldContents[i]);

		inventoryView.setContents(newContents);
		return inventoryView;
	}

	private ItemStack copyItem(ItemStack item)
	{ return item == null ? null : item.clone(); }

	private boolean showInventory(Player pl, Inventory v)
	{
		AutoRefMatch match = AutoReferee.getInstance().getMatch(pl.getWorld());
		if (match == null || !match.isSpectator(pl) ||
			!match.getSpectator(pl).canViewInventory()) return false;

		// show the requested inventory view
		if (v != null) pl.openInventory(v);
		return v != null;
	}

	/**
	 * Shows this player's inventory to the specified player.
	 *
	 * @param old show saved inventory if true, otherwise show current inventory
	 * @return true if the inventory can be shown, otherwise false
	 */
	public boolean showInventory(Player player, boolean old)
	{
		Inventory which = old ? this.getLastInventoryView() : this.getInventoryView();
		return this.showInventory(player, which);
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

	public JSONPlayerData getJSONPlayer()
	{
		JSONPlayerData data = new JSONPlayerData();
		data.name = this.getName();
		data.team = this.getTeam().getDefaultName();

		data.kills = this.getKills();
		data.assists = this.getAssists();
		data.deaths = this.getDeathCount();

		data.teamkills = this.teamKills;
		data.assists = this.getAssists();

		data.arrowfired = this.shotsFired;
		data.arrowhit = this.shotsHit;

		return data;
	}
}
