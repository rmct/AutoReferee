package org.mctourney.autoreferee.util.worldsearch;

import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Skeleton.SkeletonType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.entity.minecart.SpawnerMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.mctourney.autoreferee.util.BlockData;

/**
 * Consumes: ObjectiveExhaustionMasterTask.entitychunks<br>
 * Output: ObjectiveExhaustionMasterTask.found<br>
 * Cancellation: Self, on depletion
 *
 * Searches each chunk for entities with the goal objects
 *
 * @author kane
 */
public class WorkerEntitySearch extends BukkitRunnable
{
	public static final int MAX_PER_RUN = 4;
	ObjectiveExhaustionMasterTask master;

	public WorkerEntitySearch(ObjectiveExhaustionMasterTask task)
	{
		master = task;
	}

	@Override
	public void run()
	{
		World world = master.team.getMatch().getWorld();
		Set<BlockData> goals = master.searching;
		for (int i = 0; i < 15; i++) // 15 chunks per run
		{
			Vector vec = master.entitychunks.poll();
			if (vec == null) { this.cancel(); return; }
			Entity[] entities = world.getChunkAt(vec.getBlockX(), vec.getBlockZ()).getEntities(); // actually chunk-coords

			for (Entity ent : entities)
			{
				// Humans, Horses, Storage & Hopper Minecarts
				if (ent instanceof InventoryHolder)
				{
					Inventory inv = ((InventoryHolder) ent).getInventory();
					for (ItemStack item : inv.getContents())
						submitMatches(BlockData.fromItemStack(item), ent, goals);
				}

				// note: players get their armor checked here
				// the double-check on the held item doesn't really matter
				if (ent instanceof LivingEntity)
				{
					EntityEquipment eq = ((LivingEntity) ent).getEquipment();
					for (ItemStack item : eq.getArmorContents())
					{
						submitMatches(BlockData.fromItemStack(item), ent, goals);
					}
					submitMatches(BlockData.fromItemStack(eq.getItemInHand()), ent, goals);
				}

				// these 3 should be obvious
				if (ent instanceof FallingBlock)
					submitMatches(new BlockData(((FallingBlock) ent).getMaterial(), ((FallingBlock) ent).getBlockData()), ent, goals);
				if (ent instanceof Item)
					submitMatches(BlockData.fromItemStack(((Item) ent).getItemStack()), ent, goals);
				if (ent instanceof ItemFrame)
					submitMatches(BlockData.fromItemStack(((ItemFrame) ent).getItem()), ent, goals);


				// drops wood when destroyed
				if (ent instanceof Boat)
					submitMatches(new BlockData(Material.WOOD), ent, goals);
				// endermen can carry blocks...possibly into other lane? interesting, investigate later
				if (ent instanceof Enderman)
				{
					MaterialData carried = ((Enderman) ent).getCarriedMaterial();
					submitMatches(new BlockData(carried.getItemType(), carried.getData()), ent, goals);
				}
				// break minecarts to get the blocks used to craft
				if (ent instanceof Minecart)
				{
					if (ent instanceof ExplosiveMinecart)
						submitMatches(new BlockData(Material.TNT), ent, goals);
					else if (ent instanceof HopperMinecart)
						submitMatches(new BlockData(Material.HOPPER), ent, goals);
					else if (ent instanceof PoweredMinecart)
						submitMatches(new BlockData(Material.FURNACE), ent, goals);
					// spawners can spawn entities with EntityEquipment on
					else if (ent instanceof SpawnerMinecart)
					{
						// XXX todo: spawners
					}
					else if (ent instanceof StorageMinecart)
						submitMatches(new BlockData(Material.CHEST), ent, goals);
				}
				// sheep can be sheared or killed for wool blocks
				if (ent instanceof Sheep)
					submitMatches(new BlockData(Material.WOOL, ((Sheep) ent).getColor().getWoolData()), ent, goals);
				// wither skele skulls
				if (ent instanceof Skeleton)
				{
					if (((Skeleton) ent).getSkeletonType() == SkeletonType.WITHER)
						submitMatches(new BlockData(Material.SKULL, (byte) SkullType.WITHER.ordinal()), ent, goals);
				}
				// trade for goal blocks
				if (ent instanceof Villager)
				{
					// FIXME https://github.com/Bukkit/Bukkit/pull/921
					try
					{
						List<ItemStack> tradeResults = Unsafe_InspectVillagerTrades.getTradeResults((Villager) ent);
						for (ItemStack item : tradeResults)
							submitMatches(BlockData.fromItemStack(item), ent, goals);
					}
					catch (Throwable ignored) {}
				}
			}
		}
	}

	private void submitMatches(BlockData found, Entity ent, Set<BlockData> goals)
	{
		for (BlockData bd : goals)
		{
			if (bd.equals(found))
			{
				master.found.add(new _Entry<BlockData, Vector>(bd, ent.getLocation().toVector()));
			}
		}
	}
}
