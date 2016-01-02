package org.mctourney.autoreferee.regions;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefPlayer;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.util.TeleportationUtil;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class AutoRefRegion
{
	private static final long NOTIFICATION_THRESHOLD_MS = 5 * 60 * 1000L;
	private static final Random random = new Random();

	public static enum Flag
	{
		NO_BUILD           (1 << 0, true,  'b', "nobuild"),
		NO_ENTRY           (1 << 1, true,  'n', "noentry"),
		SAFE               (1 << 2, false, 's', "safe"),
		NO_EXPLOSIONS      (1 << 3, false, 'e', "noexplosion"),
		NO_ACCESS          (1 << 4, false, 'a', "noaccess"),
		NO_TELEPORT        (1 << 5, false, 't', "noteleport"),
		SPAWNERS_ONLY      (1 << 6, false, 'w', "spawnersonly"),
		NO_FLOW            (1 << 7, true,  'f', "noflow");

		// generated from above values
		public static final String OPTIONS = "abenstwf";

		private int value;
		private String name;
		private char mark;

		public boolean defaultValue;

		public int getValue() { return value; }
		public String getName() { return name; }
		public char getMark() { return mark; }

		public static Flag fromName(String name)
		{
			for (Flag f : values())
				if (f.name.equals(name)) return f;
			return null;
		}

		Flag(int val, boolean def, char c, String name)
		{ this.value = val; this.name = name; this.mark = c; this.defaultValue = def; }
	}

	private int flags;

	protected Integer yaw = null; // yaw = f * 90, SOUTH = 0
	protected Integer pitch = null; // -90 = up, 0 = level, 90 = down

	private Set<AutoRefTeam> owners = Sets.newHashSet();

	private String regionName = null;
	private Map<AutoRefTeam, Long> lastNotification = Maps.newHashMap();

	public AutoRefRegion()
	{ flags = 0; }

	public String getName()
	{ return this.regionName; }

	public void setName(String name)
	{ this.regionName = name; }

	public void announceRegion(AutoRefPlayer apl)
	{
		AutoRefTeam team = apl.getTeam();
		if (this.regionName == null || !owners.contains(team)) return;

		Long last = lastNotification.get(team);
		long ctime = ManagementFactory.getRuntimeMXBean().getUptime();

		if (last == null || ctime - last > NOTIFICATION_THRESHOLD_MS)
		{
			String msg = apl.getDisplayName() + " has entered " +
				team.getColor() + ChatColor.BOLD + this.getName();
			AutoReferee.log(msg);
			for (Player ref : apl.getMatch().getReferees(false)) ref.sendMessage(msg);
			lastNotification.put(team, ctime);
		}
	}

	// these methods need to be implemented
	public abstract double distanceToRegion(Location loc);
	public abstract Location getRandomLocation(Random r);
	public abstract CuboidRegion getBoundingCuboid();

	public abstract Element toElement();

	private int ANGLE_RND = 15;

	public Integer getFixedYaw()
	{
		if (yaw == null) return null;
		return Math.round(((float)yaw/ANGLE_RND)*ANGLE_RND);
	}

	public Integer getFixedPitch()
	{
		if (pitch == null) return null;
		return Math.round(((float)pitch/ANGLE_RND)*ANGLE_RND);
	}

	public Location getLocation()
	{
		Location loc = getRandomLocation(random);
		if (pitch != null) loc.setPitch(getFixedPitch());
		if (yaw != null) loc.setYaw(getFixedYaw());

		return loc.add(0.0, 0.5, 0.0);
	}

	public Location getCenter()
	{
		CuboidRegion cuboid = this.getBoundingCuboid();

		// Get points of the region
		Location min = cuboid.getMinimumPoint();
		Location max = cuboid.getMaximumPoint();
		World world = cuboid.world;

		double pointX = (min.getBlockX() + max.getBlockX()) / 2.0;
		double pointY = (min.getBlockY() + max.getBlockY()) / 2.0;
		double pointZ = (min.getBlockZ() + max.getBlockZ()) / 2.0;

		Location loc = new Location(world, pointX, pointY, pointZ);
		if (pitch != null) loc.setPitch(getFixedPitch());
		if (yaw != null) loc.setYaw(getFixedYaw());

		return loc;
	}

	public Location getGroundedCenter()
	{
		// keep searching down until we hit ground
		Location loc = getCenter();
		while (loc.getBlockY() > 0)
		{
			Location next = loc.clone().add(0,-1,0);
			if (!TeleportationUtil.isBlockPassable(next.getBlock())) break;
			loc = next;
		}
		return loc;
	}

	public boolean contains(Location loc)
	{ return distanceToRegion(loc) <= 0.0; }

	public boolean containsBlock(Block block)
	{ return this.contains(block.getLocation().clone().add(0.5, 0.5, 0.5)); }

	public boolean is(Flag flag)
	{ return 0 != (flag.getValue() & this.flags); }

	public Set<Flag> getFlags()
	{
		Set<Flag> fset = Sets.newHashSet();
		for (Flag f : Flag.values())
			if ((f.getValue() & this.flags) != 0) fset.add(f);
		return fset;
	}

	public AutoRefRegion toggle(Flag flag)
	{ if (flag != null) flags ^= flag.getValue(); return this; }

	public AutoRefRegion addFlags(Element elt)
	{
		if (elt != null) for (Element c : elt.getChildren())
			try { flags |= Flag.fromName(c.getName()).getValue(); }
			catch (Exception e) { AutoReferee.log("Unrecognized flag: " + c.getName()); }
		return this;
	}

	public boolean isEnterEvent(PlayerMoveEvent event)
	{ return !contains(event.getFrom()) && contains(event.getTo()); }

	protected AutoRefRegion getRegionSettings(AutoRefMatch match, Element e)
	{
		for (Element owner : e.getChildren("owner"))
			this.addOwners(match.getTeam(owner.getTextTrim()));
		return this.getRegionSettings(match.getWorld(), e);
	}

	protected AutoRefRegion getRegionSettings(World world, Element e)
	{
		if (e.getAttribute("yaw") != null)
			yaw = Integer.parseInt(e.getAttributeValue("yaw"));

		if (e.getAttribute("pitch") != null)
			pitch = Integer.parseInt(e.getAttributeValue("pitch"));

		if (e.getAttribute("name") != null)
			this.setName(e.getAttributeValue("name"));

		this.addFlags(e.getChild("flags"));
		return this;
	}

	protected Element setRegionSettings(Element e)
	{
		Set<Flag> fset = getFlags();
		if (!fset.isEmpty())
		{
			Element eFlags; e.addContent(eFlags = new Element("flags"));
			for (Flag f : fset) eFlags.addContent(new Element(f.name.toLowerCase()));
		}

		if (getOwners() != null) for (AutoRefTeam team : getOwners())
			e.addContent(new Element("owner").setText(team.getDefaultName()));

		if (yaw != null) e.setAttribute("yaw", Integer.toString(getFixedYaw()));
		if (pitch != null) e.setAttribute("pitch", Integer.toString(getFixedPitch()));

		// set the custom region name if one has been specified
		if (regionName != null) e.setAttribute("name", this.getName());

		return e;
	}

	public Set<AutoRefTeam> getOwners()
	{ return owners; }

	public void addOwners(AutoRefTeam ...teams)
	{ for (AutoRefTeam team : teams) if (team != null) owners.add(team); }

	public boolean isOwner(AutoRefTeam team)
	{ return owners.contains(team); }

	public static CuboidRegion combine(AutoRefRegion reg1, AutoRefRegion reg2)
	{
		// handle nulls gracefully
		if (reg1 == null && reg2 == null) return null;
		if (reg1 == null) return reg2.getBoundingCuboid();
		if (reg2 == null) return reg1.getBoundingCuboid();

		return CuboidRegion.combine(reg1.getBoundingCuboid(), reg2.getBoundingCuboid());
	}

	// wrote this dumb helper function because `distanceToRegion` was looking ugly...
	protected static double multimax(double base, double ... more )
	{ for ( double x : more ) base = Math.max(base, x); return base; }

	public static Map<String, Class<? extends AutoRefRegion>> elementNames = Maps.newHashMap();
	static
	{
		elementNames.put("location", PointRegion.class);
		elementNames.put("cuboid", CuboidRegion.class);
		elementNames.put("cylinder", CylinderRegion.class);
	}

	public static void addRegionType(String tag, Class<? extends AutoRefRegion> cls)
	{ elementNames.put(tag, cls); }

	public static AutoRefRegion fromElement(AutoRefMatch match, Element elt)
	{
		Class<? extends AutoRefRegion> cls = elementNames.get(elt.getName());
		if (cls == null) return null;

		try
		{
			Constructor<? extends AutoRefRegion> cons = cls.getConstructor(AutoRefMatch.class, Element.class);
			return cons.newInstance(match, elt).getRegionSettings(match, elt);
		}
		catch (Exception e) { e.printStackTrace(); return null; }
	}

	public static AutoRefRegion fromElement(World world, Element elt)
	{
		Class<? extends AutoRefRegion> cls = elementNames.get(elt.getName());
		if (cls == null) return null;

		try
		{
			Constructor<? extends AutoRefRegion> cons = cls.getConstructor(World.class, Element.class);
			return cons.newInstance(world, elt).getRegionSettings(world, elt);
		}
		catch (Exception e) { e.printStackTrace(); return null; }
	}
}
