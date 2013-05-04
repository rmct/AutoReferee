package org.mctourney.autoreferee.commands;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefMatch.MatchStatus;
import org.mctourney.autoreferee.listeners.SpectatorListener;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.regions.CuboidRegion;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;
import org.mctourney.autoreferee.util.commands.AutoRefCommand;
import org.mctourney.autoreferee.util.commands.AutoRefPermission;
import org.mctourney.autoreferee.util.commands.CommandHandler;

import org.apache.commons.cli.CommandLine;

import com.google.common.collect.Sets;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;

public class ConfigurationCommands implements CommandHandler
{
	AutoReferee plugin;

	public ConfigurationCommands(Plugin plugin)
	{
		this.plugin = (AutoReferee) plugin;
	}

	@AutoRefCommand(name={"autoref", "archive"}, argmax=0, options="z",
		description="Package the map and configuration into the maps/ directory.")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean archive(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
		throws IOException
	{
		if (match == null) return false;
		World world = match.getWorld();

		// LAST MINUTE CLEANUP!!!
		match.clearEntities();
		world.setTime(match.getStartClock());

		// save the world and configuration first
		world.save();
		match.saveWorldConfiguration();

		File archiveFolder = null;
		if (options.hasOption('z'))
			archiveFolder = match.distributeMap();
		else archiveFolder = match.archiveMapData();

		String resp = String.format("%s %s", match.getVersionString(),
			archiveFolder == null ? "failed to archive." : "archived!");
		sender.sendMessage(ChatColor.GREEN + resp); plugin.getLogger().info(resp);
		return true;
	}

	@AutoRefCommand(name={"autoref", "tool", "wincond"}, argmax=0,
		description="Get the tool used to configure win conditions.")
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean toolWinCondition(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		// get the tool used to set the win conditions
		Material tooltype = SpectatorListener.ToolAction.TOOL_WINCOND.tooltype;
		ItemStack toolitem = new ItemStack(tooltype);

		// add to the inventory and switch to holding it
		((Player) sender).getInventory().addItem(toolitem);

		// let the player know what the tool is and how to use it
		sender.sendMessage("Given win condition tool: " + toolitem.getType().name());
		sender.sendMessage("Right-click on a block to set it as a win-condition.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "tool", "startmech"}, argmax=0,
		description="Get the tool used to configure start mechanisms.")
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean toolStartMechanism(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		// get the tool used to set the starting mechanisms
		Material tooltype = SpectatorListener.ToolAction.TOOL_STARTMECH.tooltype;
		ItemStack toolitem = new ItemStack(tooltype);

		// add to the inventory and switch to holding it
		((Player) sender).getInventory().addItem(toolitem);

		// let the player know what the tool is and how to use it
		sender.sendMessage("Given start mechanism tool: " + toolitem.getType().name());
		sender.sendMessage("Right-click on a device to set it as a starting mechanism.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "tool", "protect"}, argmax=0,
		description="Get the tool used to configure protected entities (will not be butchered before match).")
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean toolProtectEntities(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		// get the tool used to set the starting mechanisms
		Material tooltype = SpectatorListener.ToolAction.TOOL_PROTECT.tooltype;
		ItemStack toolitem = new ItemStack(tooltype);

		// add to the inventory and switch to holding it
		((Player) sender).getInventory().addItem(toolitem);

		// let the player know what the tool is and how to use it
		sender.sendMessage("Given entity protection tool: " + toolitem.getType().name());
		sender.sendMessage("Right-click on an entity to protect it from butchering.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "protectall"}, argmax=0,
		description="Protect all entities currently on the map.")
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean protectAll(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		for (Entity e : match.getWorld().getEntitiesByClasses(NPC.class))
		{
			match.protect(e.getUniqueId());
			match.broadcast(ChatColor.DARK_GRAY + "Protecting " + e.getType().name() +
				" @ " + LocationUtil.toBlockCoords(e.getLocation()));
		}
		return true;
	}

	@AutoRefCommand(name={"autoref", "nocraft"}, argmax=0,
		description="Prohibit the item in hand from being crafted during a match.")
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean setNoCraft(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		ItemStack item = ((Player) sender).getItemInHand();
		if (item != null) match.addIllegalCraft(BlockData.fromItemStack(item));
		return true;
	}

	@AutoRefCommand(name={"autoref", "setspawn"}, argmin=0, argmax=1,
		description="Set the current location as the global spawn. If a team name is provided, sets team spawn.")
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean setSpawn(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		Player player = (Player) sender;

		if (args.length == 0)
		{
			match.setWorldSpawn(player.getLocation());
			String coords = LocationUtil.toBlockCoords(match.getWorldSpawn());
			sender.sendMessage(ChatColor.GRAY + "Global spawn set to " + coords);

			return true;
		}

		AutoRefTeam team = match.getTeam(args[0]);
		if (team == null)
		{
			// team name is invalid. let the player know
			sender.sendMessage(ChatColor.DARK_GRAY + args[1] +
				ChatColor.RESET + " is not a valid team.");
			sender.sendMessage("Teams are " + match.getTeamList());
		}
		else
		{
			team.setSpawnLocation(player.getLocation());
			String coords = LocationUtil.toBlockCoords(player.getLocation());
			sender.sendMessage(ChatColor.GRAY + "Spawn set to " +
				coords + " for " + team.getDisplayName());
		}
		return true;
	}

	@AutoRefCommand(name={"zones"}, argmax=1,
		description="List all configured regions on the map.")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean getZones(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		Set<AutoRefTeam> teams = null;

		// if a team has been specified as an argument
		if (args.length > 0)
		{
			AutoRefTeam t = match.getTeam(args[0]);
			if (t == null)
			{
				// team name is invalid. let the player know
				sender.sendMessage(ChatColor.DARK_GRAY + args[0] +
					ChatColor.RESET + " is not a valid team.");
				return true;
			}

			teams = Sets.newHashSet();
			teams.add(t);
		}

		// otherwise, just print all the teams
		else teams = match.getTeams();

		// sanity check...
		if (teams == null) return false;

		// print all the start regions
		sender.sendMessage("Start Regions:");
		if (match.getStartRegions().size() > 0)
			for (AutoRefRegion reg : match.getStartRegions())
				sender.sendMessage("  " + reg.toString());

			// if there are no regions, print None
		else sender.sendMessage("  <None>");

		// for all the teams being looked up
		for (AutoRefTeam team : teams)
		{
			// print team-name header
			sender.sendMessage(team.getDisplayName() + "'s Regions:");

			// print all the regions owned by this team
			if (team.getRegions().size() > 0)
				for (AutoRefRegion reg : team.getRegions())
					sender.sendMessage("  " + reg.toString());

			// if there are no regions, print None
			else sender.sendMessage("  <None>");
		}

		return true;
	}

	@AutoRefCommand(name={"zone"}, argmin=0, options=AutoRefRegion.Flag.OPTIONS + "XS",
		description="Set the currently selected WorldEdit region to be a team's zone.")
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean setupZone(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		Player player = (Player) sender;

		WorldEditPlugin worldEdit = plugin.getWorldEdit();
		if (worldEdit == null)
		{
			// world edit not installed
			sender.sendMessage("This method requires WorldEdit installed and running.");
			return true;
		}

		Set<AutoRefTeam> teams = Sets.newHashSet();
		for (String arg : args)
		{
			AutoRefTeam team = match.getTeam(arg);
			if (team != null) teams.add(team);
		}

		boolean isStartRegion = options.hasOption('S');
		if (teams.isEmpty() && !isStartRegion)
		{
			// team name is invalid. let the player know
			sender.sendMessage(ChatColor.DARK_GRAY + "No valid team names given.");
			sender.sendMessage("Teams are " + match.getTeamList());
			return true;
		}

		Selection sel = worldEdit.getSelection(player);
		AutoRefRegion reg = null;

		if ((sel instanceof CuboidSelection))
		{
			CuboidSelection csel = (CuboidSelection) sel;
			reg = new CuboidRegion(csel.getMinimumPoint(), csel.getMaximumPoint());
		}

		// add the selection to the start regions
		if (isStartRegion)
		{
			match.addStartRegion(reg);
			sender.sendMessage("Region now marked as a start region!");
			return true;
		}

		for (AutoRefRegion.Flag flag : AutoRefRegion.Flag.values())
			if (options.hasOption(flag.getMark())) reg.toggle(flag);
		for (AutoRefTeam team : teams) if (team.addRegion(reg))
			sender.sendMessage(reg.toString() + " set as " + team.getDisplayName() + "'s region.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "setheight"}, argmin=1, argmax=1,
			description="Restrict all team zones to be within a given height.")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean setHeight(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		try
		{
			double newHeight = Double.parseDouble(args[0]);
			for (CuboidRegion creg : match.getRegions(CuboidRegion.class))
				if (creg.y2 > newHeight) creg.y2 = newHeight;
		}
		catch (NumberFormatException e)
		{ sender.sendMessage(ChatColor.RED + args[0] + " is not a valid height."); }
		return true;
	}

	@AutoRefCommand(name={"autoref", "cfg", "init"}, argmax=0,
		description="Initialize a blank configuration file for this map.")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean configInit(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// if there is a match object for this map
		if (match != null) sender.sendMessage(plugin.getName() +
			" already initialized for " + match.getMapName() + ".");
		else
		{
			World world = plugin.getSenderWorld(sender);
			plugin.addMatch(match = new AutoRefMatch(world, false));

			match.saveWorldConfiguration();
			match.setCurrentState(MatchStatus.NONE);

			sender.sendMessage(ChatColor.GREEN + AutoReferee.CFG_FILENAME + " generated.");
		}

		return true;
	}

	@AutoRefCommand(name={"autoref", "cfg", "save"}, argmax=0,
		description="Save the configuration file for this map.")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean configSave(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		match.saveWorldConfiguration();
		sender.sendMessage(ChatColor.GREEN + AutoReferee.CFG_FILENAME + " saved.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "cfg", "reload"}, argmax=0,
		description="Reload the configuration file from disk for this map.")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean configReload(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		match.reload();
		sender.sendMessage(ChatColor.GREEN + AutoReferee.CFG_FILENAME + " reload complete!");
		return true;
	}

	@AutoRefCommand(name={"autoref", "debug"}, argmax=0, options="c",
		description="Turn on debugging mode.")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean debug(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		if (match.isDebugMode())
		{ match.setDebug(null); return true; }

		match.setDebug(!options.hasOption('c') ? sender :
			plugin.getServer().getConsoleSender());

		return true;
	}
}
