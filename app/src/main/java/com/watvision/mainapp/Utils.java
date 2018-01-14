package com.watvision.mainapp;

import org.opencv.core.Point;



/**
 * Provide general purpose methods for handling OpenCV-JavaFX data conversion.
 * Moreover, expose some "low level" methods for matching few JavaFX behavior.
 *
 * @author <a href="mailto:luigi.derussis@polito.it">Luigi De Russis</a>
 * @author <a href="http://max-z.de">Maximilian Zuleger</a>
 * @version 1.0 (2016-09-17)
 * @since 1.0
 * 
 */

// Utils
// Used from an external source. Has distance and slope support classes. Could be replaced.

public final class Utils
{

	public static class ToyIntClass {
		public int toyNumber;
	}	
	

	
	public static double cv_distance(Point P, Point Q)
	{
		return Math.sqrt(Math.pow(Math.abs(P.x - Q.x),2) + Math.pow(Math.abs(P.y - Q.y),2)) ; 
	}
	
	public static double cv_lineSlope(Point L, Point M, ToyIntClass alignmentVal)
	{
		double dx,dy;
		dx = M.x - L.x;
		dy = M.y - L.y;
		
		if ( dx != 0)
		{	 
			alignmentVal.toyNumber = 1;
			return (dy / dx);
		}
		else				// Make sure we are not dividing by zero; so use 'alignement' flag
		{	 
			alignmentVal.toyNumber = 0;
			return 0.0;
		}
	}
}