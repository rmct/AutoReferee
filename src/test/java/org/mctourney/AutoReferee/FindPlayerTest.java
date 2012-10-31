package org.mctourney.AutoReferee;

import org.mctourney.AutoReferee.AutoRefTeam;
import org.mctourney.AutoReferee.AutoRefPlayer;

import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;

public class FindPlayerTest
{
	@Test
	public void testNameLookup()
	{
		AutoRefTeam team = new AutoRefTeam();

		AutoRefPlayer p1 = new AutoRefPlayer("jcll"    , null);
		AutoRefPlayer p2 = new AutoRefPlayer("nosrepa" , null);
		AutoRefPlayer p3 = new AutoRefPlayer("coestar" , null);
		AutoRefPlayer p4 = new AutoRefPlayer("dewtroid", null);
		AutoRefPlayer pX = new AutoRefPlayer("jcllpony", null);

		team.addPlayer(p1);
		team.addPlayer(p2);

		Assert.assertNull(team.getPlayer("c"));
		Assert.assertNull(team.getPlayer("jcp"));

		team.addPlayer(p3);
		team.addPlayer(p4);

		Assert.assertEquals(p1, team.getPlayer("jc"));
		Assert.assertEquals(p2, team.getPlayer("nos"));
		Assert.assertEquals(p3, team.getPlayer("c"));

		Assert.assertNull(team.getPlayer("dewtroid!"));
	}
}
