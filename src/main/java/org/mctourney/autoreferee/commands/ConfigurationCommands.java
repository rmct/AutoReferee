package org.mctourney.autoreferee.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;

import org.mctourney.autoreferee.AutoRefMap;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefMatch.MatchStatus;
import org.mctourney.autoreferee.goals.CoreGoal;
import org.mctourney.autoreferee.listeners.SpectatorListener;
import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.regions.CuboidRegion;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.LocationUtil;
import org.mctourney.autoreferee.util.Vec3;
import org.mctourney.autoreferee.util.commands.AutoRefCommand;
import org.mctourney.autoreferee.util.commands.AutoRefPermission;
import org.mctourney.autoreferee.util.commands.CommandHandler;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;
import com.google.gson.Gson;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import com.sk89q.worldedit.IncompleteRegionException;
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

	@AutoRefCommand(name={"autoref", "archive"}, argmax=0,
		description="Package the map and configuration into the maps/ directory.")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean archive(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
		throws IOException
	{
		if (match == null) return false;
		File zipfile = match.distributeMap();

		String resp = String.format("%s %s", match.getVersionString(),
			zipfile == null ? "failed to archive." : "archived!");
		sender.sendMessage(ChatColor.GREEN + resp); AutoReferee.log(resp);
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

	@AutoRefCommand(name={"autoref", "setspawn"}, argmin=0, argmax=1, options="as",
		description="Set the current location as the global spawn. If a team name is provided, sets team spawn.",
		usage="<command> [<team name>]",
		opthelp=
		{
			"a", "add an additional spawn location",
			"s", "set spectator spawn",
		})
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean setSpawn(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		Player player = (Player) sender;

		if (args.length == 0)
		{
			if (options.hasOption('s'))
			{
				match.setSpectatorSpawn(player.getLocation());
				sender.sendMessage(ChatColor.GRAY + "Spectator spawn set!");
			}
			else
			{
				match.setWorldSpawn(player.getLocation());
				String coords = LocationUtil.toBlockCoords(match.getWorldSpawn());
				sender.sendMessage(ChatColor.GRAY + "Global spawn set to " + coords);
			}

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
			boolean append = options.hasOption('a');
			if (!append) team.clearSpawnRegions();
			team.addSpawnRegion(player.getLocation());

			sender.sendMessage(ChatColor.GRAY + String.format("%s set as spawn for %s",
				LocationUtil.toBlockCoords(player.getLocation()), team.getDisplayName()));
			if (append) sender.sendMessage(ChatColor.GRAY + "Appended.");
		}
		return true;
	}

	@AutoRefCommand(name={"zones"}, argmax=1,
		description="List all configured regions on the map.",
		usage="<command> [<team name>]")
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean getZones(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;

		Set<AutoRefTeam> teams = null;

		// if a team has been specified as an argument
		if (args.length > 0)
		{
			AutoRefTeam t = match.getTeam(args[0]);
			teams = Sets.newHashSet(t);

			if (t == null)
			{
				// team name is invalid. let the player know
				sender.sendMessage(ChatColor.DARK_GRAY + args[0] +
					ChatColor.RESET + " is not a valid team.");
				return true;
			}
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

	@AutoRefCommand(name={"zone"}, argmin=0, options=AutoRefRegion.Flag.OPTIONS + "XSN+",
		description="Set the currently selected WorldEdit region to be a team's zone.",
		usage="<command> [<team names...>]",
		opthelp=
		{
			// TODO Need data automatically from AutoRefRegion.Flag
		})
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean setupZone(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		Player player = (Player) sender;

		WorldEditPlugin worldEdit = AutoReferee.getWorldEdit();
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

		// if we couldn't get a region from WorldEdit
		if (reg == null) return false;

		// add the selection to the start regions
		if (isStartRegion)
		{
			match.addStartRegion(reg);
			sender.sendMessage("Region now marked as a start region!");
			return true;
		}

		// name this region if it has a name
		if (options.hasOption('N'))
			reg.setName(options.getOptionValue('N', null));

		for (AutoRefRegion.Flag flag : AutoRefRegion.Flag.values())
			if (options.hasOption(flag.getMark())) reg.toggle(flag);
		for (AutoRefTeam team : teams) if (team.addRegion(reg))
			sender.sendMessage(reg.toString() + " set as " + team.getDisplayName() + "'s region.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "core"}, argmin=1, argmax=1, options="r+",
		description="Set the currently selected WorldEdit region to be a team's core.",
		usage="<command> <team name>",
		opthelp=
		{
			"r", "specify the range of the core",
		})
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})

	public boolean setupCore(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null) return false;
		Player player = (Player) sender;

		WorldEditPlugin worldEdit = AutoReferee.getWorldEdit();
		if (worldEdit == null)
		{
			// world edit not installed
			sender.sendMessage("This method requires WorldEdit installed and running.");
			return true;
		}

		AutoRefTeam team = match.getTeam(args[0]);
		Selection sel = worldEdit.getSelection(player);
		AutoRefRegion reg = null;

		if ((sel instanceof CuboidSelection))
		{
			CuboidSelection csel = (CuboidSelection) sel;
			reg = new CuboidRegion(csel.getMinimumPoint(), csel.getMaximumPoint());
		}
		else
		{
			sender.sendMessage("You must have a selection with WorldEdit already to run this method.");
			return true;
		}

		CoreGoal core = new CoreGoal(team, reg);
		if (options.hasOption('r'))
			try { core.setRange(Long.parseLong(options.getOptionValue('r'))); }
			catch (NumberFormatException e)
			{ sender.sendMessage(ChatColor.RED + options.getOptionValue('r') + " is not a valid range."); }

		team.addGoal(core);
		sender.sendMessage(reg.toString() + " set as " + team.getDisplayName() + "'s TARGET core.");
		sender.sendMessage(team.getDisplayName() + " will be attacking this core, not defending it.");
		return true;
	}

	@AutoRefCommand(name={"autoref", "setheight"}, argmin=1, argmax=1,
		description="Restrict all team zones to be within a given height.",
		usage="<command> <height>")
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

	@AutoRefCommand(name={"autoref", "cfg", "apply"}, argmin=1, options="v+",
		description="Apply a known map configuration file to the current map.",
		usage="<command> <map name>",
		opthelp=
		{
			"v", "new map version number",
		})
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean configApply(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		// generate a map name from the args
		String mapName = StringUtils.join(args, " ");
		AutoRefMap map = AutoRefMap.getMap(mapName);
		World world = plugin.getSenderWorld(sender);

		if (map == null)
		{
			sender.sendMessage(ChatColor.RED + "Unknown map: " + mapName);
			return true;
		}
		else sender.sendMessage(ChatColor.RED + "Applying " + map.getVersionString());

		Element worldConfig = null;
		try { worldConfig = AutoRefMap.getConfigFileData(map.getZip()); }
		catch (IOException e) { e.printStackTrace(); return true; }
		catch (JDOMException e) { e.printStackTrace(); return true; }

		// set a new version string if one is specified
		if (options.hasOption('v'))
		{
			Element version = worldConfig.getChild("meta").getChild("version");
			version.setText(options.getOptionValue('v'));
		}

		try
		{
			XMLOutputter xmlout = new XMLOutputter(Format.getPrettyFormat());
			File localconfig = new File(world.getWorldFolder(), AutoReferee.CFG_FILENAME);
			xmlout.output(worldConfig, new FileOutputStream(localconfig));
		}
		catch (java.io.IOException e)
		{ AutoReferee.log("Could not save world config: " + world.getName()); return true; }

		AutoRefMatch.setupWorld(world, false);
		return true;
	}

	@AutoRefCommand(name={"autoref", "scoreboard", "save"}, argmax=0, options="m",
		description="Save the current world's scoreboard to file.",
		opthelp=
		{
			"m", "save the main server scoreboard",
		})
	@AutoRefPermission(console=true, nodes={"autoreferee.configure"})

	public boolean scoreboardSave(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		if (match == null || !match.getCurrentState().isBeforeMatch()) return false;

		Scoreboard scoreboard = match.getScoreboard();
		if (options.hasOption('m')) scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

		match.saveScoreboardData(scoreboard);
		sender.sendMessage(ChatColor.GREEN + "Scoreboard saved to file.");

		return true;
	}
	
	@AutoRefCommand(name= {"autoref", "regions"}, argmin=1, argmax=1, options="flprs")
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})
	public boolean arRegions(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options) {
		if(!AutoReferee.getInstance().isExperimentalMode()) return false;
		if ( match == null ) return false;
		Player player = (Player) sender;

		WorldEditPlugin worldEdit = AutoReferee.getWorldEdit();
		if (worldEdit == null)
		{
			// world edit not installed
			sender.sendMessage("This method requires WorldEdit installed and running.");
			return true;
		}
		
		AutoRefTeam team = match.getTeam(args[0]);

		if(options.hasOption('r')) { 
			//match.cancelGraphTask();
			team.initRegionGraph();
			team.computeRegionGraph(); 
			player.sendMessage( "Found " + team.getRegGraph().connectedRegions().size() + " regions.");
		}
		
		if(options.hasOption('p')) {
			Selection sel = worldEdit.getSelection(player);
			if(sel == null || !(sel instanceof CuboidSelection))  return true;
			
			CuboidSelection csel = (CuboidSelection) sel;
			if(!sel.getRegionSelector().isDefined()) return true;
			
			com.sk89q.worldedit.regions.CuboidRegion reg;
			
			try {
				reg 
					= (com.sk89q.worldedit.regions.CuboidRegion) csel.getRegionSelector().getRegion();
			} catch (IncompleteRegionException e) {
				e.printStackTrace();
				return false;
			}
			
			Location l0 = new Location(player.getWorld(), reg.getPos1().getBlockX(), reg.getPos1().getBlockY(), reg.getPos1().getBlockZ());
			Location l1 = new Location(player.getWorld(), reg.getPos2().getBlockX(), reg.getPos2().getBlockY(), reg.getPos2().getBlockZ());
			
			Set<Block> path = team.getRegGraph().shortestPath(l0, l1);
			
			if(path == null) {
				player.sendMessage("No path found");
				return true;
			}
			
			path.forEach(b -> b.setType(Material.WOOL));
		}
		
		if(options.hasOption('f')) {
			byte data = 1;
			
			for( Set<Vec3> reg : team.getRegGraph().regionsWithoutPointsLoc( team.unrestrictedPts() ) ) {
				for( Vec3 v : reg ) {
					Block b = v.loc(player.getWorld()).getBlock();
					b.setType(Material.WOOL);
					b.setData(data);
				}
				
				data = (byte)(data + 1);
			}
		}
		
		if(options.hasOption('s')) {
			//System.out.println(team.getRegGraph().toJSON( team.unrestrictedPts() ));
			
			HashMap<String, Object> json = new HashMap<String, Object>();
			
			for(AutoRefTeam t : match.getTeams()) {
				json.put( t.getName() , t.getRegGraph().toJSON( t.unrestrictedPts() ) );
			}
			
			Gson gson = new Gson();
					
			File f = new File( match.getWorld().getWorldFolder(), AutoRefMatch.REGION_CFG_FILENAME );
			
			try (FileWriter writer = new FileWriter( f )) {
	            gson.toJson(json, writer);
	        } catch(IOException e) {
	        	player.sendMessage("Error writing file");
	        	e.printStackTrace();
	        }
			
			player.sendMessage("Successfully wrote " + AutoRefMatch.REGION_CFG_FILENAME + "!");
		}
		
		if(options.hasOption('l')) {
			try {
				match.loadRegionJSON();
				player.sendMessage("Successfully loaded regions file");
			} catch (FileNotFoundException | ClassCastException e) {
				player.sendMessage("Failed loading regions file");
				e.printStackTrace();
			}
		}
		
		return true;
	}
	
	@AutoRefCommand(name= {"autoref", "test"}, argmin=1, argmax=1)
	@AutoRefPermission(console=false, nodes={"autoreferee.configure"})
	public boolean bruh(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options) {
		if ( match == null ) return false;
		Player p = (Player) sender;
		
		if(!p.isOp()) return false;
		
		WorldEditPlugin worldEdit = AutoReferee.getWorldEdit();
		if (worldEdit == null)
		{
			// world edit not installed
			sender.sendMessage("This method requires WorldEdit installed and running.");
			return true;
		}
		
		Selection sel = worldEdit.getSelection(p);
		if(sel == null || !(sel instanceof CuboidSelection))  return true;
		CuboidSelection csel = (CuboidSelection) sel;
		
		AutoRefTeam team = match.getTeam(args[0]);
		
		Location pos = p.getLocation();
		pos.getBlock().setType(Material.WOOL);
		
		
		
		return true;
	}
}
