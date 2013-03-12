package org.mctourney.autoreferee;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import org.mctourney.autoreferee.regions.AutoRefRegion;

import com.google.common.collect.Sets;

public class RegionFlagCoverageTest
{
	@Test
	public void test()
	{
		Set<Character> optionsGiven, optionsComputed;

		optionsGiven = Sets.newHashSet();
		for (char c : AutoRefRegion.Flag.OPTIONS.toCharArray())
			optionsGiven.add(c);

		optionsComputed = Sets.newHashSet();
		for (AutoRefRegion.Flag flag : AutoRefRegion.Flag.values())
			optionsComputed.add(flag.getMark());

		Assert.assertEquals(optionsGiven, optionsComputed);
	}
}
