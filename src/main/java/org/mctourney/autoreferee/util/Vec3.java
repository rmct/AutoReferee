package org.mctourney.autoreferee.util;

import org.bukkit.Location;
import org.bukkit.World;

//helper class, needed as keys for hashmap
public class Vec3 {
	private int x;
	private int y;
	private int z;
	private int maxX;
	private int maxY;
	private int maxZ;
	private int minX;
	private int minY;
	private int minZ;
	
	//public Vec3() { }
	public Vec3(int x, int y, int z, int maxX, int maxY, int maxZ, int minX, int minY, int minZ) 
		{ this.x = x; this.y = y; this.z = z; this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ; } 
	
	public int x() { return this.x; }
	public int y() { return this.y; }
	public int z() { return this.z; }
	
	public Vec3 x(int x) { this.x =  this.minX + ( (x - this.minX) % (this.width() + 1)); return this; }
	public Vec3 y(int y) { this.y =  this.minY + ( (y - this.minY) % (this.length() + 1)); return this; }
	public Vec3 z(int z) { this.z =  this.minZ + ( (z - this.minZ) % (this.height() + 1)); return this; }
	
	private int width() { return this.maxX - this.minX; }
	private int length() { return this.maxY - this.minY; }
	private int height() { return this.maxZ - this.minZ; }
	
	public Location loc(World w) { return new Location(w, x(), y(), z()); }
	
	@Override
	public boolean equals(Object obj) {
		if(obj == this) return true;
		if(!(obj instanceof Vec3)) return false;
		Vec3 vec = (Vec3) obj;
		
		return (this.x() == vec.x() && this.y() == vec.y() && this.z() == vec.z());
	}
	
	@Override
	public int hashCode() {
		return ( x() - this.minX ) + (( y() - this.minY ) * ( this.width() + 1 )) + ((z() - this.minZ) * ( this.width() + 1 ) * (this.length() + 1));
	}
}
