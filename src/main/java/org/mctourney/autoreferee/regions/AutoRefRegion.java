package org.mctourney.autoreferee.regions;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Location;

import org.jdom2.Element;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class AutoRefRegion
{
	private Random random = new Random();

	public static enum Flag
	{
		// - no place/break blocks
		// - no fill/empty buckets
		NO_BUILD(1 << 0, "nobuild", 'b', true),

		// - negative region, used for fine tuning access controls
		NO_ENTRY(1 << 1, "noentry", 'n', true),

		// - no mob spawns
		// - mobs will not track players in these regions
		SAFE(1 << 2, "safe", 's', false),

		// - explosions will not damage blocks
		NO_EXPLOSIONS(1 << 3, "noexplosion", 'e', false);

		// generated from above values
		public static final String OPTIONS = "bnse";

		// value for the flag set
		private int value;

		public int getValue() { return value; }

		// name for use with commands
		private String name;

		public String getName() { return name; }

		public static Flag fromName(String name)
		{
			for (Flag f : values())
				if (f.name.equals(name)) return f;
			return null;
		}

		// character marker for config files
		private char mark;

		public char getMark() { return mark; }

		public boolean defaultValue;

		Flag(int val, String name, char c, boolean def)
		{ this.value = val; this.name = name; this.mark = c; this.defaultValue = def; }

		public static Flag fromChar(char c)
		{
			for (Flag f : values())
				if (f.mark == c) return f;
			return null;
		}
	}

	private int flags;

	// yaw = f * 90, SOUTH = 0
	protected Integer yaw = null;

	// -90 = up, 0 = level, 90 = down
	protected Integer pitch = null;

	private Set<AutoRefTeam> owners = Sets.newHashSet();

	public AutoRefRegion()
	{ flags = 0; }

	// these methods need to be implemented
	public abstract double distanceToRegion(Location loc);
	public abstract Location getRandomLocation(Random r);
	public abstract CuboidRegion getBoundingCuboid();

	public abstract Element toElement();

	public Location getLocation()
	{
		Location loc = getRandomLocation(random);
		if (pitch != null) loc.setPitch(pitch);
		if (yaw != null) loc.setYaw(yaw);

		return loc.add(0.0, 0.5, 0.0);
	}

	public boolean contains(Location loc)
	{ return distanceToRegion(loc) <= 0.0; }

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

	protected AutoRefRegion getRegionSettings(AutoRefMatch match, Element e)
	{
		this.addFlags(e.getChild("flags"));
		for (Element owner : e.getChildren("owner"))
			this.addOwners(match.teamNameLookup(owner.getTextTrim()));

		if (e.getAttribute("yaw") != null)
			yaw = Integer.parseInt(e.getAttributeValue("yaw"));

		if (e.getAttribute("pitch") != null)
			pitch = Integer.parseInt(e.getAttributeValue("pitch"));

		return this;
	}

	private int ANGLE_RND = 15;

	protected Element setRegionSettings(Element e)
	{
		Set<Flag> fset = getFlags();
		if (!fset.isEmpty())
		{
			Element eFlags; e.addContent(eFlags = new Element("flags"));
			for (Flag f : fset) eFlags.addContent(new Element(f.name.toLowerCase()));
		}

		for (AutoRefTeam team : getOwners())
			e.addContent(new Element("owner").setText(team.getName()));

		if (yaw != null) e.setAttribute("yaw",
			Integer.toString(Math.round((float)yaw/ANGLE_RND)*ANGLE_RND));

		if (pitch != null) e.setAttribute("pitch",
			Integer.toString(Math.round((float)pitch/ANGLE_RND)*ANGLE_RND));

		return e;
	}

	public Set<AutoRefTeam> getOwners()
	{ return owners; }

	public void addOwners(AutoRefTeam ...teams)
	{ for (AutoRefTeam team : teams) owners.add(team); }

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

	public static Map<String, Class<? extends AutoRefRegion>> elementNames = Maps.newHashMap();
	static
	{
		elementNames.put("location", PointRegion.class);
		elementNames.put("cuboid", CuboidRegion.class);
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
}
