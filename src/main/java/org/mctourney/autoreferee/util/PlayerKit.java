package org.mctourney.autoreferee.util;

import java.util.Map;
import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoReferee;

public class PlayerKit
{
	// potion effects
	private Set<PotionEffect> potionEffects = Sets.newHashSet();

	// player gear
	private Set<ItemStack> gear = Sets.newHashSet();
	private ItemStack helmet = null;
	private ItemStack chest = null;
	private ItemStack leggings = null;
	private ItemStack boots = null;

	// colored gear (gear that needs special coloring)
	private Set<ItemStack> teamColors = Sets.newHashSet();

	// name of kit
	private String name = "Custom Kit";

	public PlayerKit() {  }

	public PlayerKit(String name)
	{ super(); this.setName(name); }

	private static Map<String, PotionEffectType> potionTypes = Maps.newHashMap();
	static
	{
		for (PotionEffectType type : PotionEffectType.values()) if (type != null)
			potionTypes.put(type.getName().toLowerCase().replaceAll("_", " "), type);

		potionTypes.put("resistance", PotionEffectType.DAMAGE_RESISTANCE);
		potionTypes.put("strength", PotionEffectType.INCREASE_DAMAGE);
		potionTypes.put("jump boost", PotionEffectType.JUMP);
		potionTypes.put("slowness", PotionEffectType.SLOW);
		potionTypes.put("haste", PotionEffectType.FAST_DIGGING);
		potionTypes.put("mining fatigue", PotionEffectType.SLOW_DIGGING);
	}

	public static PotionEffectType getPotionEffectType(String type)
	{
		try { return PotionEffectType.getById(Integer.parseInt(type)); }
		catch (NumberFormatException e) {  }

		type = type.toLowerCase();

		int bscore = Integer.MAX_VALUE; PotionEffectType best = null;
		for (Map.Entry<String, PotionEffectType> e : potionTypes.entrySet())
		{
			int score = StringUtils.getLevenshteinDistance(type, e.getKey());
			if (score < bscore) { bscore = score; best = e.getValue(); }
		}

		return best;
	}

	public static PotionEffect parsePotionEffect(Element element)
	{
		assert "effect".equalsIgnoreCase(element.getName());
		PotionEffectType type = getPotionEffectType(element.getAttributeValue("type"));
		int duration = -1, amplifier = 0;

		try { duration = Integer.parseInt(element.getAttributeValue("duration")) * 20; }
		catch (NumberFormatException e) {  }

		try { amplifier = Integer.parseInt(element.getAttributeValue("level")) - 1; }
		catch (NumberFormatException e) {  }

		return new PotionEffect(type, duration, amplifier);
	}

	public static void addParsedEnchantment(ItemStack item, Element element)
	{
		assert "enchant".equalsIgnoreCase(element.getName());
		String etype = element.getAttributeValue("type");
		Enchantment enchantment = Enchantment.getByName(etype);

		if (enchantment == null)
			try { enchantment = Enchantment.getById(Integer.parseInt(etype)); }
			catch (NumberFormatException e) {  }

		if (enchantment == null)
		{
			etype = etype.toLowerCase();
			int bscore = Integer.MAX_VALUE;
			for (Enchantment ench : Enchantment.values())
			{
				int score = StringUtils.getLevenshteinDistance(ench.getName().toLowerCase(), etype);
				if (score < bscore) { bscore = score; enchantment = ench; }
			}
		}

		int elevel = 1;
		if (element.getAttribute("level") != null)
			try { elevel = Integer.parseInt(element.getAttributeValue("level")); }
			catch (NumberFormatException e) { e.printStackTrace(); }
		item.addUnsafeEnchantment(enchantment, elevel);
	}

	public PlayerKit(Element elt)
	{
		assert "kit".equalsIgnoreCase(elt.getName());
		this.setName(elt.getAttributeValue("name").trim());

		Element effectsElement = elt.getChild("effects");
		if (effectsElement != null)
			for (Element effect : effectsElement.getChildren("effect"))
				this.addPotionEffect(parsePotionEffect(effect));

		Element gearElement = elt.getChild("gear");
		if (gearElement != null)
			for (Element gear : gearElement.getChildren())
			{
				String typename = gear.getAttributeValue("type");
				Material type = Material.getMaterial(typename);

				if (type == null)
					try { type = Material.getMaterial(Integer.parseInt(typename)); }
					catch (NumberFormatException e) {  }

				if (type == null)
				{
					typename = typename.toLowerCase();
					int bscore = Integer.MAX_VALUE;
					for (Material mat : Material.values())
					{
						int score = StringUtils.getLevenshteinDistance(mat.name().toLowerCase(), typename);
						if (score < bscore) { bscore = score; type = mat; }
					}
				}

				if (type == null) continue;
				int count = 1, damage = 0;

				// cheating here a bit. Exception covers both NumberFormatException and NullPointerException
				try { count = Integer.parseInt(gear.getAttributeValue("count")); } catch (Exception ignored) {  }
				try { damage = Integer.parseInt(gear.getAttributeValue("damage")); } catch (Exception ignored) {  }

				// get item stack locally so we can inspect it a bit first
				ItemStack item = new ItemStack(type, count, (short) damage);

				Color color = null;
				if (item.getItemMeta() instanceof LeatherArmorMeta)
				{
					// add this to the list of items that need to be team colored
					if (gear.getChild("teamcolor") != null)
						this.teamColors.add(item);

					// add a specific coloring
					else if (gear.getChild("color") != null)
					{
						Element colorElt = gear.getChild("color");
						if (colorElt != null)
						{
							LeatherArmorMeta armorMeta = (LeatherArmorMeta) item.getItemMeta();
							if (colorElt.getAttributeValue("hex") != null && color == null)
								color = ColorConverter.hexToColor(colorElt.getAttributeValue("hex"));
							if (colorElt.getAttributeValue("rgb") != null && color == null)
								color = ColorConverter.rgbToColor(colorElt.getAttributeValue("rgb"));

							if (color != null) armorMeta.setColor(color);
							item.setItemMeta(armorMeta);
						}
					}
				}

				if (item.getItemMeta() instanceof PotionMeta)
				{
					PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
					for (Element effect : gear.getChildren("effect"))
						potionMeta.addCustomEffect(parsePotionEffect(effect), true);
					item.setItemMeta(potionMeta);
				}

				for (Element enchant : gear.getChildren("enchant"))
					addParsedEnchantment(item, enchant);

				if (gear.getChild("name") != null)
				{
					ItemMeta itemMeta = item.getItemMeta();
					itemMeta.setDisplayName(gear.getChildTextTrim("name"));
					item.setItemMeta(itemMeta);
				}

				// TODO Books

				String gearmeta = gear.getName();

				// if the gear is any of the armor slots, set those
				if (gearmeta.startsWith("helm")) this.setHelmet(item);
				else if (gearmeta.startsWith("chest")) this.setChest(item);
				else if (gearmeta.startsWith("leg")) this.setLeggings(item);
				else if (gearmeta.startsWith("boot")) this.setBoots(item);

				// otherwise, default to adding the gear to the inventory
				else this.addGear(item);
			}
	}

	private ItemStack cloneItem(AutoRefPlayer apl, ItemStack item)
	{
		ItemStack clone = item.clone();
		if (teamColors.contains(item) && apl.getTeam() != null
			&& clone.getItemMeta() instanceof LeatherArmorMeta)
		{
			// get the team color
			LeatherArmorMeta armorMeta = (LeatherArmorMeta) clone.getItemMeta();
			String hex = ColorConverter.chatToHex(apl.getTeam().getColor());

			// color the armor with the team color
			armorMeta.setColor(ColorConverter.hexToColor(hex));
			clone.setItemMeta(armorMeta);
		}

		return clone;
	}

	public void giveTo(AutoRefPlayer apl)
	{
		Player pl = apl.getPlayer();
		if (pl == null) return;

		PlayerInventory pinv = pl.getInventory();
		pinv.clear();

		// set armor slots
		pinv.setHelmet(getHelmet() == null ? null : cloneItem(apl, getHelmet()));
		pinv.setChestplate(getChest() == null ? null : cloneItem(apl, getChest()));
		pinv.setLeggings(getLeggings() == null ? null : cloneItem(apl, getLeggings()));
		pinv.setBoots(getBoots() == null ? null : cloneItem(apl, getBoots()));

		// give items (cloned, adjusted)
		for (ItemStack item : getGear())
			pinv.addItem(cloneItem(apl, item));

		// assign potion effects
		for (PotionEffect effect : getPotionEffects())
			pl.addPotionEffect(effect, true);
	}

	public Set<PotionEffect> getPotionEffects()
	{ return potionEffects; }

	public void addPotionEffect(PotionEffect effect)
	{ potionEffects.add(effect); }

	public Set<ItemStack> getGear()
	{ return gear; }

	public void addGear(ItemStack item)
	{ gear.add(item); }

	public ItemStack getHelmet()
	{ return helmet; }

	public void setHelmet(ItemStack helmet)
	{ this.helmet = helmet; }

	public ItemStack getChest()
	{ return chest; }

	public void setChest(ItemStack chest)
	{ this.chest = chest; }

	public ItemStack getLeggings()
	{ return leggings; }

	public void setLeggings(ItemStack leggings)
	{ this.leggings = leggings; }

	public ItemStack getBoots()
	{ return boots; }

	public void setBoots(ItemStack boots)
	{ this.boots = boots; }

	public String getName()
	{ return name; }

	public void setName(String name)
	{ this.name = name; }
}
