package org.mctourney.autoreferee.util.worldsearch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import com.google.common.collect.Lists;

/**
 * FIXME https://github.com/Bukkit/Bukkit/pull/921
 *
 * @author riking
 */
class Unsafe_InspectVillagerTrades
{
	public static List<ItemStack> getTradeResults(Villager vil) throws Exception, Error
	{
		Object internalVillager = vil.getClass().getDeclaredMethod("getHandle", (Class<?>) null).invoke(vil, (Object[]) null);
		Class<?> internalEntityHumanClass = Class.forName("net.minecraft.server.v1_6_R2.EntityHuman");
		ArrayList<?> merchantRecipeList = (ArrayList<?>) internalVillager.getClass().getDeclaredMethod("getOffers", internalEntityHumanClass).invoke(internalVillager, new Object[] {null});

		if (merchantRecipeList.size() == 0) return Lists.newArrayList();
		List<ItemStack> ret = Lists.newArrayListWithCapacity(merchantRecipeList.size());

		Field itemField = merchantRecipeList.get(0).getClass().getField("sellingItem");
		Class<?> internalItemClass = Class.forName("net.minecraft.server.v1_6_R2.ItemStack");
		Method getStackMethod = Class.forName("org.bukkit.craftbukkit.v1_6_R2.inventory.CraftItemStack").getDeclaredMethod("asBukkitCopy", internalItemClass);
		for (Object o : merchantRecipeList)
		{
			Object internalItemStack = itemField.get(o);
			if (!internalItemStack.getClass().equals(internalItemClass)) continue;
			Object bukkitItemStack = getStackMethod.invoke(null, internalItemStack);
			if (!ItemStack.class.isAssignableFrom(bukkitItemStack.getClass()))
			{
				new RuntimeException("asBukkitCopy() did not return a Bukkit ItemStack..? Investigate.").printStackTrace();
				continue;
			}
			ret.add((ItemStack) bukkitItemStack);
		}
		return ret;
	}
}
