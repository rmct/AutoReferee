package org.mctourney.AutoReferee;

import java.util.Set;

import org.mctourney.AutoReferee.util.CuboidRegion;
import org.mctourney.AutoReferee.util.Vector3;

import com.google.common.collect.Sets;

public class AutoRefRegion extends CuboidRegion
{
	public enum RegionFlag
	{
		NO_BUILD(1 << 0, "nobuild", 'b'),
		NO_ENTRY(1 << 1, "noentry", 'n'),
		NO_SPAWN(1 << 2, "nospawn", 'm');
		
		// value for the flag set
		private int value;
		
		public int getValue() { return value; }
		
		// name for use with commands
		private String name;
		
		public String getName() { return name; }
		
		// character marker for config files
		private char mark;
		
		public char getMark() { return mark; }
		
		RegionFlag(int val, String name, char c)
		{ this.value = val; this.name = name; this.mark = c; }
	}
	
	private int flags;
	
	public AutoRefRegion(CuboidRegion reg)
	{ this(reg.getMinimumPoint(), reg.getMaximumPoint()); }
	
	public AutoRefRegion(Vector3 a, Vector3 b)
	{ super(a, b); flags = 0; }
	
	public static AutoRefRegion fromCoords(String coords)
	{
		// split the region string (vec:vec=flags)
		String[] values = coords.split("=");

		AutoRefRegion reg = new AutoRefRegion(CuboidRegion.fromCoords(values[0]));
		if (values.length > 1 && values[1] != null)
			for (char c : values[1].toCharArray()) reg.toggle(c);

		return reg;
	}
	
	public String toCoords()
	{ return super.toCoords() + "=" + getFlagList(); }
	
	public boolean is(RegionFlag flag)
	{ return 0 != (flag.getValue() & this.flags); }
	
	public boolean canBuild()
	{ return !is(RegionFlag.NO_BUILD); }
	
	public boolean canEnter()
	{ return !is(RegionFlag.NO_ENTRY); }
	
	public boolean canMobsSpawn()
	{ return !is(RegionFlag.NO_SPAWN); }
	
	public Set<RegionFlag> getFlags()
	{
		Set<RegionFlag> fset = Sets.newHashSet();
		for (RegionFlag f : RegionFlag.values())
			if ((f.getValue() & this.flags) != 0) fset.add(f);
		return fset;
	}
	
	public String getFlagList()
	{
		String fstr = "";
		for (RegionFlag f : getFlags())
			fstr += f.getMark();
		return fstr;
	}

	public AutoRefRegion toggle(RegionFlag flag)
	{
		AutoReferee.getInstance().getLogger().info("Toggle " + flag.name());
		flags ^= flag.getValue(); return this;
	}

	public AutoRefRegion toggle(String nm)
	{
		for (RegionFlag f : RegionFlag.values())
			if (f.getName().equalsIgnoreCase(nm)) return toggle(f); 
		return this;
	}

	public AutoRefRegion toggle(char c)
	{
		for (RegionFlag f : RegionFlag.values())
			if (f.getMark() == c) return toggle(f); 
		return this;
	}
}
