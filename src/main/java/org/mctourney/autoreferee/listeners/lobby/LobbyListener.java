package org.mctourney.autoreferee.listeners.lobby;

import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.util.PlayerUtil;
import org.mctourney.autoreferee.util.SportBukkitUtil;

import com.google.common.collect.Sets;

public abstract class LobbyListener implements Listener
{
	protected AutoReferee plugin = null;

	public LobbyListener(AutoReferee plugin)
	{ this.plugin = plugin; }

	protected boolean checkAdminPrivilege(Entity e)
	{
		return (e.getType() == EntityType.PLAYER &&
			((Player) e).hasPermission("autoreferee.admin"));
	}

	// set of players to be processed who have logged into a non-existent world
	Set<String> playerLimboLogin = Sets.newHashSet();

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerPreLogin(AsyncPlayerPreLoginEvent event)
	{
		OfflinePlayer opl = Bukkit.getOfflinePlayer(event.getName());
		Location oloc = SportBukkitUtil.getOfflinePlayerLocation(opl);

		// either there is no offline player utility, or check the world like normal
		if (opl.hasPlayedBefore() && (oloc == null || oloc.getWorld() == null))
		{
			if (!opl.isOnline()) playerLimboLogin.add(opl.getName());
			// TODO deferred teleportation back on the sync server thread if online
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void playerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();

		// if this player is "in limbo" (logged into an unloaded world)
		if (player.hasPlayedBefore() && playerLimboLogin.remove(player.getName()))
		{
			World newWorld = plugin.getLobbyWorld();
			if (newWorld == null) newWorld = Bukkit.getWorlds().get(0);
			player.teleport(newWorld.getSpawnLocation());
		}

		if (player.getWorld() == plugin.getLobbyWorld())
		{
			if (!player.hasPlayedBefore())
				player.teleport(player.getWorld().getSpawnLocation());

			PlayerUtil.setGameMode(player, GameMode.ADVENTURE);
			player.setAllowFlight(true);
		}
	}

	@EventHandler
	public void worldJoin(PlayerChangedWorldEvent event)
	{
		// moving to lobby world, set player to creative
		Player player = event.getPlayer();
		if (player.getWorld() == plugin.getLobbyWorld())
		{
			PlayerUtil.setGameMode(player, GameMode.ADVENTURE);
			player.setAllowFlight(true);
		}
	}

	@EventHandler(priority= EventPriority.HIGHEST)
	public void creatureSpawn(CreatureSpawnEvent event)
	{
		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
		{ event.setCancelled(true); return; }
	}

	@EventHandler(priority= EventPriority.HIGHEST)
	public void projectileLaunch(ProjectileLaunchEvent event)
	{
		LivingEntity shooter = event.getEntity().getShooter();
		if (shooter != null && checkAdminPrivilege(shooter)) return;

		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bowShoot(EntityShootBowEvent event)
	{
		if (checkAdminPrivilege(event.getEntity())) return;
		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void itemConsume(PlayerItemConsumeEvent event)
	{
		if (checkAdminPrivilege(event.getPlayer())) return;
		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void entityDamage(EntityDamageByEntityEvent event)
	{
		if (checkAdminPrivilege(event.getDamager())) return;
		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void hangingBreak(HangingBreakEvent event)
	{
		if (event instanceof HangingBreakByEntityEvent)
		{
			HangingBreakByEntityEvent e = (HangingBreakByEntityEvent) event;
			if (checkAdminPrivilege(e.getRemover())) return;
		}
		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void dropItem(PlayerDropItemEvent event)
	{
		if (checkAdminPrivilege(event.getPlayer())) return;
		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void blockPlace(BlockPlaceEvent event)
	{
		if (checkAdminPrivilege(event.getPlayer())) return;
		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void blockBreak(BlockBreakEvent event)
	{
		if (checkAdminPrivilege(event.getPlayer())) return;
		if (event.getBlock().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bucketEmpty(PlayerBucketEmptyEvent event)
	{
		if (checkAdminPrivilege(event.getPlayer())) return;
		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void bucketFill(PlayerBucketFillEvent event)
	{
		if (checkAdminPrivilege(event.getPlayer())) return;
		if (event.getPlayer().getWorld() == plugin.getLobbyWorld())
			event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void hungerChange(FoodLevelChangeEvent event)
	{
		if (event.getEntity().getWorld() == plugin.getLobbyWorld())
		{ event.setCancelled(true); event.setFoodLevel(20); }
	}

	protected abstract void lobbyLoadMap(Player player, AutoRefMap map);

	@EventHandler(priority=EventPriority.MONITOR)
	public void signCommand(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		if (event.hasBlock() && event.getClickedBlock().getState() instanceof Sign)
		{
			Sign sign = (Sign) event.getClickedBlock().getState();
			String[] lines = sign.getLines();

			// if the first line isn't the AutoReferee tag, not our sign, not our business
			if (lines[0] == null || !"[AutoReferee]".equals(lines[0])) return;

			if (player.getWorld() == plugin.getLobbyWorld())
			{
				// load the world named on the sign
				if (event.getAction() == Action.RIGHT_CLICK_BLOCK)
				{
					// if the last line is a version string, make sure it isn't included in the name
					boolean hasVersion = lines[3].isEmpty() ||
						(lines[3].trim().startsWith("[") && lines[3].trim().endsWith("]"));

					String mapname = lines[1] + " " + lines[2];
					if (!hasVersion) mapname += " " + lines[3];

					AutoRefMap map = AutoRefMap.getMap(mapname.trim());
					if (map != null)
					{
						if (player.isSneaking()) map.install();
						else this.lobbyLoadMap(player, map);

						// if the sign is fit to have a version string listed, add/update it
						if (hasVersion && map.getVersion().length() <= 14)
						{
							sign.setLine(3, String.format("[v%s]", map.getVersion()));
							sign.update();
						}
					}
				}
			}
		}
	}
}
