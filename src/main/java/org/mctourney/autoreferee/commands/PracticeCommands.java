package org.mctourney.autoreferee.commands;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.util.PlayerUtil;
import org.mctourney.autoreferee.util.commands.AutoRefCommand;
import org.mctourney.autoreferee.util.commands.AutoRefPermission;
import org.mctourney.autoreferee.util.commands.CommandHandler;

import org.apache.commons.cli.CommandLine;

import java.util.Map;

public class PracticeCommands implements CommandHandler, Listener
{
	AutoReferee plugin;

	private Inventory practiceMenu;
	private static final String PRACTICE_MENU_IDENTIFIER =
		"" + ChatColor.MAGIC + ChatColor.BOLD + ChatColor.MAGIC + ChatColor.MAGIC;

	// map of players to their custom warp points
	private Map<String, Location> warpPoints;

	public PracticeCommands(Plugin plugin)
	{
		this.plugin = (AutoReferee) plugin;
		this.warpPoints = Maps.newHashMap();
		setupPracticeMenu();
	}

	@AutoRefCommand(name={"autoref", "practice"}, options="t*",
			description="Switch to practice mode or activate practice mode menu.")
	@AutoRefPermission(console=false, nodes={"autoreferee.player"})

	public boolean practiceMode(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if we are not in a match in progress, quit now
		if (match == null || !match.getCurrentState().inProgress()) return true;

		// if the match is in practice mode (perhaps just activated), show menu
		if (match.isPracticeMode())
		{
			Player player = (Player) sender;
			if (options.hasOption('t'))
			{
				// player teleport target (optional)
				String target = options.getOptionValue('t');
				if (target != null && !target.isEmpty())
				{
					AutoRefPlayer apl = match.getPlayer(target);
					if (apl != null) player.teleport(apl.getLocation());
				}
				// if no player is specified, show teleport menu
				else showPlayerTeleportMenu(player);
			}
			// if no options are given, show practice menu
			else showPracticeMenu(player);
		}

		return true;
	}

	private void showPracticeMenu(Player viewer)
	{ viewer.openInventory(practiceMenu); }

	private enum PracticeMenuOption
	{
		/**
		 * Time settings. Exact and relative.
		 */
		ADVANCE_TIME
		(
			0, 0, Material.WATCH,
			ChatColor.YELLOW + "Advance time",
			"Move time forward by one hour."
		),
		SET_TIME_SUNRISE
		(
			0, 1, Material.REDSTONE_LAMP_ON,
			ChatColor.YELLOW + "Set time -- sunrise",
			"Set the world time to 7am"
		),
		SET_TIME_SUNSET
		(
			0, 2, Material.REDSTONE_LAMP_OFF,
			ChatColor.YELLOW + "Set time -- sunset",
			"Set the world time to 9pm"
		),

		/**
		 * Potion effects.
		 */
		NIGHT_VISION
		(
			0, 7, Material.EYE_OF_ENDER,
			ChatColor.DARK_PURPLE + "Night vision (8:00)",
			"Activate night vision"
		),
		CLEAR_POTION_EFFECTS
		(
			0, 8, Material.MILK_BUCKET,
			ChatColor.DARK_PURPLE + "Clear potion effects",
			"Remove all potion effects"
		),

		/**
		 * Game mode options.
		 */
		MODE_SURVIVAL
		(
			0, 4, Material.IRON_SWORD,
			ChatColor.GREEN + "Survival mode",
			"Switch to Survival mode"
		),
		MODE_CREATIVE
		(
			0, 5, Material.BOOK_AND_QUILL,
			ChatColor.GREEN + "Creative mode",
			"Switch to Creative mode"
		),

		/**
		 * Health and status controls.
		 */
		TOGGLE_GODMODE
		(
			1, 0, Material.CHAINMAIL_CHESTPLATE,
			ChatColor.RED + "Toggle invincibility"
		),
		HEAL
		(
			1, 1, Material.APPLE,
			ChatColor.RED + "Heal",
			"Restore health to full"
		),
		FEED
		(
			1, 2, Material.COOKED_CHICKEN,
			ChatColor.RED + "Feed",
			"Restore hunger to full"
		),

		BUTCHER
		(
			1, 8, Material.TNT,
			ChatColor.GRAY + "Kill all mobs",
			"Kills all (unprotected) entities"
		),

		/**
		 * Warp point controls.
		 */
		SET_WARP
		(
			1, 4, Material.BED,
			ChatColor.BLUE + "Set warp location",
			"Set a personal warp point"
		),
		GOTO_WARP
		(
			1, 5, Material.COMPASS,
			ChatColor.BLUE + "Go to warp location",
			"Teleport to personal warp point"
		),
		TELEPORT_PLAYER
		(
			1, 6, new ItemStack(Material.SKULL_ITEM, 0, (byte) 3),
			ChatColor.BLUE + "Teleport to player"
		),
		;

		public int slot;
		public ItemStack item;

		PracticeMenuOption(int row, int col, Material type, String name, String ...desc)
		{ this(row, col, new ItemStack(type), name, desc); }

		PracticeMenuOption(int row, int col, ItemStack item, String name, String ...desc)
		{ this.slot = row*9 + col; this.item = addItemMetadata(item, name, desc); }

		private ItemStack addItemMetadata(ItemStack item, String name, String ...desc)
		{
			for (int i = 0; i < desc.length; ++i)
				desc[i] = ChatColor.RESET + desc[i];

			ItemMeta meta = item.getItemMeta();
			meta.setDisplayName(name);
			meta.setLore(Lists.newArrayList(desc));

			item.setItemMeta(meta);
			return item;
		}

		private static Map<Integer, PracticeMenuOption> _map;
		static
		{
			_map = Maps.newHashMap();
			for (PracticeMenuOption option : PracticeMenuOption.values())
				_map.put(option.slot, option);
		}

		public static PracticeMenuOption fromSlot(int slot)
		{ return _map.get(slot); }
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=false)
	public void practiceMenuClick(InventoryClickEvent event)
	{
		Player player = (Player) event.getWhoClicked();
		AutoRefMatch match = plugin.getMatch(player.getWorld());

		if (match == null || !match.isPracticeMode()) return;
		if (!event.getInventory().getTitle().endsWith(PRACTICE_MENU_IDENTIFIER)
			|| !practiceMenu.getViewers().contains(player)) return;

		event.setCancelled(true);

		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null && PracticeMenuOption.fromSlot(event.getSlot()) != null)
			switch (PracticeMenuOption.fromSlot(event.getSlot()))
		{
			case ADVANCE_TIME:
				player.getWorld().setFullTime(player.getWorld().getFullTime() + 1000L);
				match.broadcast(ChatColor.DARK_GRAY + "[" + apl.getDisplayName() +
					ChatColor.DARK_GRAY + "] Advanced time by one hour");
				break;

			case SET_TIME_SUNRISE:
				player.getWorld().setTime(0L);
				match.broadcast(ChatColor.DARK_GRAY + "[" + apl.getDisplayName() +
					ChatColor.DARK_GRAY + "] Set time to 7am");
				break;

			case SET_TIME_SUNSET:
				player.getWorld().setTime(13000L);
				match.broadcast(ChatColor.DARK_GRAY + "[" + apl.getDisplayName() +
					ChatColor.DARK_GRAY + "] Set time to 9pm");
				break;

			case NIGHT_VISION:
				player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 8 * 60 * 20, 1));
				break;

			case CLEAR_POTION_EFFECTS:
				player.sendMessage(ChatColor.GRAY + "-- Potion effects cleared");
				PlayerUtil.removeStatusEffects(player);
				break;

			case BUTCHER:
				match.clearEntities();
				break;

			case MODE_SURVIVAL: PlayerUtil.setGameMode(player, GameMode.SURVIVAL); break;
			case MODE_CREATIVE: PlayerUtil.setGameMode(player, GameMode.CREATIVE); break;

			case SET_WARP:
				warpPoints.put(player.getName(), player.getLocation());
				player.sendMessage(ChatColor.GRAY + "-- Set warp point");
				break;

			case GOTO_WARP:
				Location loc = warpPoints.get(player.getName());
				if (loc != null && loc.getWorld() == player.getWorld())
					player.teleport(loc);
				break;

			case TOGGLE_GODMODE:
				boolean b = !apl.isGodMode();
				apl.setGodMode(b);

				match.broadcast(ChatColor.DARK_GRAY + "[" + apl.getDisplayName() +
					ChatColor.DARK_GRAY + "] Toggled invulnerability " + ChatColor.RED + (b ? "ON" : "OFF"));
				break;

			case HEAL: PlayerUtil.heal(player); break;
			case FEED: PlayerUtil.feed(player); break;

			case TELEPORT_PLAYER:
				player.closeInventory();
				showPlayerTeleportMenu(player);
				break;
		}
	}

	private void setupPracticeMenu()
	{
		practiceMenu = Bukkit.createInventory(null, 9 * 2,
			ChatColor.BOLD + "AutoReferee Practice" + PRACTICE_MENU_IDENTIFIER);

		for (PracticeMenuOption option : PracticeMenuOption.values())
			practiceMenu.setItem(option.slot, option.item);
	}

	private void showPlayerTeleportMenu(Player viewer)
	{
	}
}
