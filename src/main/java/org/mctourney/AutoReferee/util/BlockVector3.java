package org.mctourney.AutoReferee.util;

public class BlockVector3 
{
	public int x, y, z;
	
	public BlockVector3(int x, int y, int z)
	{ this.x = x; this.y = y; this.z = z; }
	
	public BlockVector3(Vector3 v)
	{
		this.x = (int) v.x;
		this.y = (int) v.y;
		this.z = (int) v.z;
	}
}
