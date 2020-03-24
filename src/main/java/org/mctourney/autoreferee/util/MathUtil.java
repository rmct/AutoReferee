package org.mctourney.autoreferee.util;

public class MathUtil {
	public static double dist( double x, double y, double z, double x0, double y0, double z0 ) {
		return Math.sqrt( Math.pow(x - x0, 2) + Math.pow(y - y0, 2) + Math.pow(z - z0, 2) );
	}
}
