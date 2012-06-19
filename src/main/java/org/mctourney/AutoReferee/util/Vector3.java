package org.mctourney.AutoReferee.util;

public class Vector3
{
	public double x, y, z;
	
	public Vector3(double x, double y, double z)
	{ this.x = x; this.y = y; this.z = z; }
	
	public Vector3(BlockVector3 v)
	{ this.x = v.x; this.y = v.y; this.z = v.z; }
}
