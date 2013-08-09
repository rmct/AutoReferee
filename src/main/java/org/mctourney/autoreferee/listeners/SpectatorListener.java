package org.mctourney.autoreferee.listeners;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.PressureSensor;
import org.bukkit.material.Redstone;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.ChatColor;

import com.google.common.collect.Maps;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefSpectator;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.goals.BlockGoal;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.util.LocationUtil;
import org.mctourney.autoreferee.util.TeleportationUtil;

public class SpectatorListener implements PluginMessageListener, Listener
{
	public static final char DELIMITER = '|';

	public static final double SPECTATOR_VISIBILITY_RADIUS = 8.0;

	AutoReferee plugin = null;

	// mapping spectators to the matches they died in
	Map<String, AutoRefMatch> deadSpectators = Maps.newHashMap();

	// convenience for changing defaults
	public enum ToolAction
	{
		TOOL_WINCOND(Material.GOLD_SPADE),
		TOOL_STARTMECH(Material.GOLD_AXE),
		TOOL_PROTECT(Material.GOLD_SWORD),

		SPECTATOR_CYCLE(Material.FEATHER),
		;

		public final Material tooltype;

		ToolAction(Material type)
		{ this.tooltype = type; }

		private static Map<Material, ToolAction> _map;
		static
		{
			_map = Maps.newHashMap();
			for (ToolAction tool : ToolAction.values())
				_map.put(tool.tooltype, tool);
		}

		public static ToolAction fromMaterial(Material material)
		{ return _map.get(material); }
	}

	public SpectatorListener(Plugin p)
	{ plugin = (AutoReferee) p; }

	public void onPluginMessageReceived(String channel, Player player, byte[] mbytes)
	{
		if (AutoReferee.REFEREE_PLUGIN_CHANNEL.equals(channel)) try
		{
			String message = new String(mbytes, AutoReferee.PLUGIN_CHANNEL_ENC);
			AutoRefMatch match = plugin.getMatch(player.getWorld());

			if (match == null || !match.isSpectator(player)) return;
			AutoRefSpectator spec = match.getSpectator(player);

			String[] parts = message.trim().split("\\|");
			if ("tp".equalsIgnoreCase(parts[0]))
			{
				Location loc = null;
				if ("player".equalsIgnoreCase(parts[1]))
				{
					AutoRefPlayer apl = match.getPlayer(parts[2]);
					if (apl != null)
					{
						// by default, go to player location
						loc = apl.getLocation();

						if ("spawn".equalsIgnoreCase(parts[3])) loc = apl.getSpawnLocation();
						else if ("bed".equalsIgnoreCase(parts[3])) loc = apl.getBedLocation();
					}
				}
				else if ("team".equalsIgnoreCase(parts[1]))
				{
					AutoRefTeam team = match.getTeam(parts[2]);
					if (team != null)
					{
						if ("vm".equalsIgnoreCase(parts[3])) loc = team.getVictoryMonumentLocation();
						else if ("spawn".equalsIgnoreCase(parts[3])) loc = team.getSpawnLocation();
					}
				}

				// teleport to the location, if any
				if (loc == null) player.sendMessage(ChatColor.DARK_GRAY +
					"You cannot teleport to this location: invalid or unsafe.");
				else 
				{
					plugin.getMatch(player.getWorld()).getSpectator(player).setPrevLocation(player.getLocation());
					player.teleport(TeleportationUtil.locationTeleport(loc));
					player.setFlying(true);
				}
			}
			else if ("inventory".equalsIgnoreCase(parts[0]))
			{
				AutoRefPlayer apl = match.getPlayer(parts[1]);
				boolean old = parts.length > 2 && "prev".equalsIgnoreCase(parts[2]);
				if (apl != null) apl.showInventory(player, old);
			}
		}
		catch (UnsupportedEncodingException e)
		{ plugin.getLogger().info("Unsupported encoding: " + AutoReferee.PLUGIN_CHANNEL_ENC); }
	}

	@EventHandler
	public void channelRegistration(PlayerRegisterChannelEvent event)
	{
		Player pl = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(pl.getWorld());

		if (AutoReferee.REFEREE_PLUGIN_CHANNEL.equals(event.getChannel()) && match != null)
		{
			// if this is a player, complain and force them to quit their team!
			if (match.isPlayer(pl))
			{
				AutoRefPlayer apl = match.getPlayer(pl);
				for (Player ref : match.getReferees(true)) ref.sendMessage(apl.getDisplayName() +
					ChatColor.DARK_GRAY + " attempted to log in with a modified client!");
				match.leaveTeam(pl, true);
			}

			// update a referee with the latest information regarding the match
			if (match.isReferee(pl)) match.updateReferee(pl);
		}
	}

	@EventHandler(priority= EventPriority.MONITOR, ignoreCancelled=true)
	public void entityInteract(PlayerInteractEntityEvent event)
	{
		Player pl = event.getPlayer();
		Entity entity = event.getRightClicked();

		AutoRefMatch match = plugin.getMatch(pl.getWorld());
		if (match != null) match.checkWinConditions();

		if (entity.getType() == EntityType.PLAYER && match != null
			&& match.isSpectator(pl) && match.isPlayer((Player) entity))
		{
			AutoRefPlayer a = match.getPlayer((Player) entity);
			a.showInventory(pl);
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void potionSplash(PotionSplashEvent event)
	{
		World world = event.getEntity().getWorld();
		AutoRefMatch match = plugin.getMatch(world);

		for (LivingEntity living : event.getAffectedEntities())
			if (living.getType() == EntityType.PLAYER)
				if (match != null && !match.isPlayer((Player) living))
					event.setIntensity(living, 0.0);
	}

	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event)
	{
		String name = event.getPlayer().getName();
		if (deadSpectators.containsKey(name))
		{
			AutoRefMatch match = deadSpectators.get(name);
			if (match != null) event.setRespawnLocation(match.getWorldSpawn());
			deadSpectators.remove(name); return;
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void spectatorMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		World world = player.getWorld();

		AutoRefMatch match = plugin.getMatch(world);
		if (match == null || !match.isSpectator(player)) return;

		AutoRefSpectator spec = match.getSpectator(player);
		boolean pvis = spec.isInvisible();

		double bdistance = Double.MAX_VALUE;
		for (AutoRefPlayer apl : match.getPlayers()) if (apl.isOnline())
		{
			Location ploc = apl.getPlayer().getLocation();
			double dist = ploc.distanceSquared(player.getLocation());
			if (dist < bdistance) { bdistance = dist; }
		}

		// if the spectator should be invisible, change visibility and then redo visibility
		boolean vis = bdistance <= SPECTATOR_VISIBILITY_RADIUS * SPECTATOR_VISIBILITY_RADIUS;
		spec.setInvisible(vis);

		// if the visibility status has changed, reconfigure
		if (pvis != vis) match.setupVisibility(player);
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void spectatorDeath(PlayerDeathEvent event)
	{
		World world = event.getEntity().getWorld();
		AutoRefMatch match = plugin.getMatch(world);

		if (match != null && !match.isPlayer(event.getEntity()))
		{
			deadSpectators.put(event.getEntity().getName(), match);
			event.getDrops().clear();
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void spectatorDeath(EntityDamageEvent event)
	{
		World world = event.getEntity().getWorld();
		AutoRefMatch match = plugin.getMatch(world);

		if (event.getEntityType() != EntityType.PLAYER) return;
		Player player = (Player) event.getEntity();

		if (match != null && match.isSpectator(player))
		{
			Location loc = player.getLocation();
			event.setCancelled(true);

			if (loc.getY() < -64 && event.getCause() == DamageCause.VOID)
				player.teleport(match.getPlayerSpawn(player));
			player.setFallDistance(0);
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void spectatorInfo(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		AutoRefMatch match = plugin.getMatch(player.getWorld());

		// if this is a match and the person is just a spectator
		if (match == null || !match.isSpectator(player)) return;

		// spawners
		if (event.hasBlock() && event.getClickedBlock().getState() instanceof CreatureSpawner)
		{
			CreatureSpawner spawner = (CreatureSpawner) event.getClickedBlock().getState();
			String spawnerType = spawner.getCreatureTypeName();

			switch (spawner.getSpawnedType())
			{
				case DROPPED_ITEM:
					// TODO - Not implemented in CraftBukkit:
					// a method to determine the data for the dropped item
					break;
			}

			player.sendMessage(ChatColor.DARK_GRAY + String.format("%s Spawner", spawnerType));
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void foreignInventoryEvent(InventoryClickEvent event)
	{
		Player player = (Player) event.getWhoClicked();
		AutoRefMatch match = plugin.getMatch(player.getWorld());

		if (match != null && !match.isPlayer(player))
			switch (event.getInventory().getType())
			{
				case PLAYER:
				case CREATIVE:
					break;

				default: event.setCancelled(true);
			}
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void projectileLaunch(ProjectileLaunchEvent event)
	{
		LivingEntity entity = event.getEntity().getShooter();
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (!(entity instanceof Player) || match == null) return;

		if (!match.isPlayer((Player) entity))
		{ event.setCancelled(true); return; }
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void projectileLaunch(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		ItemStack itemInHand = player.getItemInHand();

		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match != null && itemInHand != null)
			switch (itemInHand.getType())
			{
				case EYE_OF_ENDER:
					event.setCancelled(true);
					break;

				default:
					break;
			}
	}

	// restrict item pickup by referees
	@EventHandler(priority=EventPriority.HIGHEST)
	public void refereePickup(PlayerPickupItemEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null && match.getCurrentState().inProgress()
			&& !match.isPlayer(event.getPlayer())) event.setCancelled(true);
	}

	// restrict item pickup by referees
	@EventHandler(priority=EventPriority.HIGHEST)
	public void refereeDrop(PlayerDropItemEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match != null && match.getCurrentState().inProgress()
			&& !match.isPlayer(event.getPlayer())) event.setCancelled(true);

		if (event.getPlayer().getListeningPluginChannels().contains(
			AutoReferee.REFEREE_PLUGIN_CHANNEL)) event.setCancelled(true);
	}

	@EventHandler
	public void toolUsage(PlayerInteractEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match == null) return;

		Block block;
		BlockState blockState;

		// this event is not an "item" event
		if (!event.hasItem()) return;

		// get type id of the event and check if its one of our tools
		ToolAction action = ToolAction.fromMaterial(event.getMaterial());
		if (action == null) return;

		// get which action to perform
		switch (action)
		{
			// this is the tool built for setting win conditions
			case TOOL_WINCOND:

				if (match.getCurrentState().inProgress()) return;

				// if there is no block involved in this event, nothing
				if (!event.hasBlock()) return;
				block = event.getClickedBlock();

				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;

				for (AutoRefTeam team : match.getTeams())
				{
					boolean canBuild = !team.hasFlag(block.getLocation(), AutoRefRegion.Flag.NO_BUILD);
					boolean canEnter = !team.hasFlag(block.getLocation(), AutoRefRegion.Flag.NO_ENTRY);
					if (canBuild && canEnter) team.addGoal(new BlockGoal(team, block));
				}

				break;

			// this is the tool built for setting start mechanisms
			case TOOL_STARTMECH:

				if (match.getCurrentState().inProgress()) return;

				// if there is no block involved in this event, nothing
				if (!event.hasBlock()) return;

				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;

				// determine who owns the region that the clicked block is in
				block = event.getClickedBlock();
				blockState = block.getState();

				if (blockState.getData() instanceof Redstone)
				{
					// get the start mechanism
					AutoRefMatch.StartMechanism sm = match.toggleStartMech(block,
						((Redstone) blockState.getData()).isPowered());

					if (sm != null) event.getPlayer().sendMessage(ChatColor.RED +
						"" + sm + ChatColor.RESET + " is a start mechanism.");
					else
					{
						String coords = LocationUtil.toBlockCoords(block.getLocation());
						event.getPlayer().sendMessage(ChatColor.RED + "" +
							coords + ChatColor.RESET + " is NOT a start mechanism.");
					}
				}

				break;

			case SPECTATOR_CYCLE:

				// this tool only valid for spectators
				if (!match.isSpectator(event.getPlayer())) return;

				switch (event.getAction())
				{
					case LEFT_CLICK_AIR:
					case LEFT_CLICK_BLOCK:
						match.getSpectator(event.getPlayer()).cyclePrevPlayer();
						break;

					case RIGHT_CLICK_AIR:
					case RIGHT_CLICK_BLOCK:
						match.getSpectator(event.getPlayer()).cycleNextPlayer();
						break;

					default: break;
				}
				break;

			// this isn't one of our tools...
			default: return;
		}

		// cancel the event, since it was one of our tools being used properly
		event.setCancelled(true);
	}

	@EventHandler
	public void toolUsage(PlayerInteractEntityEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getPlayer().getWorld());
		if (match == null) return;

		// this event is not an "item" event
		ItemStack item = event.getPlayer().getItemInHand();
		if (item == null) return;

		// get type id of the event and check if its one of our tools
		ToolAction action = ToolAction.fromMaterial(item.getType());
		if (action == null) return;

		// get which action to perform
		switch (action)
		{
			// this is the tool built for protecting entities
			case TOOL_PROTECT:

				if (match.getCurrentState().inProgress()) return;

				// if there is no entity involved in this event, nothing
				if (event.getRightClicked() == null) return;

				// if the player doesn't have configure permissions, nothing
				if (!event.getPlayer().hasPermission("autoreferee.configure")) return;

				// entity name
				String ename = String.format("%s @ %s", event.getRightClicked().getType().getName(),
					LocationUtil.toBlockCoords(event.getRightClicked().getLocation()));

				// save the entity's unique id
				UUID uid; match.toggleProtection(uid = event.getRightClicked().getUniqueId());
				match.broadcast(ChatColor.RED + ename + ChatColor.RESET + " is " +
					(match.isProtected(uid) ? "" : "not ") + "a protected entity");


				break;

			// this isn't one of our tools...
			default: return;
		}

		// cancel the event, since it was one of our tools being used properly
		event.setCancelled(true);
	}

	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void blockInteract(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		Location loc = event.getClickedBlock().getLocation();

		AutoRefMatch match = plugin.getMatch(loc.getWorld());
		if (match == null) return;

		if (match.isSpectator(player))
		{
			if (!match.isReferee(player) && match.getCurrentState().inProgress())
				event.setCancelled(true);

			Material type = event.getClickedBlock().getType();
			if (event.getClickedBlock().getState() instanceof PressureSensor
				&& match.getCurrentState().inProgress()) { event.setCancelled(true); return; }

			if (event.getClickedBlock().getState() instanceof InventoryHolder
				&& event.getAction() == Action.RIGHT_CLICK_BLOCK && match.getCurrentState().inProgress()
				&& !event.getPlayer().isSneaking())
			{
				InventoryHolder invh = (InventoryHolder) event.getClickedBlock().getState();
				Inventory inv = invh.getInventory(), newinv;

				if (inv instanceof DoubleChestInventory)
					newinv = Bukkit.getServer().createInventory(player, 54, "Large Chest");
				else newinv = Bukkit.getServer().createInventory(player, inv.getType());
				newinv.setContents(inv.getContents());

				player.openInventory(newinv);
				event.setCancelled(true); return;
			}
		}
	}
}
