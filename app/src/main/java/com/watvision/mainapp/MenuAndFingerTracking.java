package com.watvision.mainapp;


import android.util.Log;

import java.util.ArrayList;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.utils.Converters;

// Menu And Finger Tracking - Created 2018-01-13
// This class takes an inputted image and will output the menu information and the finger
// information (Where the finger is etc.)

public class MenuAndFingerTracking {

    // The edged image
	public Mat traceImage;
	// The inputted image with highlighted colours drawn over it to indicate which elements
    // are being identified by the app
	public Mat highlightedImage;
	// Outputted zoomed in image of the menu
	public Mat resultImage;

	// Tag for debugging
    private static final String TAG = "MenuAndFingerTracking";

    // Matrix that is used to unwarp the image and make the perspective of the screen flat
	private Mat perspective_warp_matrix;

	// Finger Info Class
    // Stores relevant information for the pointer or where the finger of the person is.
	public class fingerInfo {
	    // Where the pointer is with respect to the image (stored as which pixel the tip is at)
		public Point imageLocation;
		// Where the pointer is with respect to the identified screen (stored as a percentage
        // of where the tip is with respect to its width and height. Will have a value of 0 to 1
        public Point screenLocation;
        // The angle of orientation the pointer is at. If the pointer was pointing right that would
        // correspond to 0 degrees
		public double orientation;
		// Whether the pointer is tracked or not
		public Boolean tracked;
		
		fingerInfo(Point inImageLocation, Point inScreenLocation, double inputOrientation, Boolean inputTracked) {
			imageLocation = inImageLocation;
			screenLocation = inScreenLocation;
			orientation = inputOrientation;
			tracked = inputTracked;
		}
		
	}

	// The class that is returned that contains all the menu and finger information
	public class menuAndFingerInfo {
		public Boolean menuTracked;
		public fingerInfo fingerData;
	}

	// Used to make an integer pass by reference.
	private class ToyIntClass {
		public int toyNumber;
	}

	// Used to make a double and a point pass by reference
	private class ToyDoublePointClass {
		public double toyDouble;
		public Point toyPoint;
	}

	// Used to make a point pass by reference
	private class ToyPointClass {
		public Point toyPoint;
	}

	// Stores 4 corners of the rectangle. Could be replaced by rect or something similar.
	private class rectangleCornerIndices { 
		public int topLeft;
		public int topRight;
		public int bottomLeft;
		public int bottomRight;
	}

	// Constructor
	public MenuAndFingerTracking() {
		traceImage = new Mat();
		// Initialize result and highlighted image to generic sizes
		resultImage = Mat.zeros(800,800,16);
		highlightedImage = Mat.zeros(800,800,16);
	}

	// Input the list of contours and their hierarchy from a frame and output the read finger info
	private fingerInfo getFingerInfoFromFrame(ArrayList<MatOfPoint> contours, Mat hierarchy) {

		ArrayList<Integer> contourIndexList = new ArrayList<Integer>();
		
		boolean objectFound = false;
		fingerInfo objectFingerInfo = null;
		
		int childContour;
		
		for( int i = 0; i < contours.size(); i++ )
		{	
	        //Find the approximated polygon of the contour we are examining
			MatOfPoint2f resultingApproximation = new MatOfPoint2f();
			MatOfPoint2f contour2fApproximation = new MatOfPoint2f();
			
			contours.get(i).convertTo(contour2fApproximation, CvType.CV_32FC2);
			Imgproc.approxPolyDP(contour2fApproximation, resultingApproximation, Imgproc.arcLength(contour2fApproximation, true)*0.02, true); 
			
			childContour = i;
	        if (resultingApproximation.size().height == 3)      // only triangles contours are examined
	        { 
	        	childContour = (int) hierarchy.get(0, childContour)[2];
	        	
	        	// If the contour has a child
	        	if (childContour != -1) { 
	        		childContour = (int) hierarchy.get(0, childContour)[2];
	        		
	        		if (childContour != -1) {
	        			MatOfPoint2f childApproximation = new MatOfPoint2f();
	        			MatOfPoint2f childContour2f = new MatOfPoint2f();
	        			
	        			contours.get(childContour).convertTo(childContour2f, CvType.CV_32FC2);
	        			Imgproc.approxPolyDP(childContour2f, childApproximation, Imgproc.arcLength(childContour2f, true)*0.02, true); 
	        			
	        			if (childApproximation.size().height == 3) {
	        				contourIndexList.add(i);
	        				contourIndexList.add(childContour);
	        				objectFound = true;
	        				objectFingerInfo = getFingerInfoFromTriangle(childApproximation);
	        			}
	        		}
	        	}
	        	
	        }
		}

		if (objectFound) {
			
			for (int i = 0; i < contourIndexList.size(); i++) {
				Imgproc.drawContours( highlightedImage, contours, contourIndexList.get(i) , new Scalar(255,200,0), 10, 8, hierarchy, 0, new Point(0,0));
			}
			
			Point pointA = new Point();
			Point pointB = new Point();
			
			pointA.x = objectFingerInfo.imageLocation.x;
			pointA.y = objectFingerInfo.imageLocation.y;
			
			pointB.x = pointA.x + 100 * Math.cos(objectFingerInfo.orientation * Math.PI / 180);
			pointB.y = pointA.y - 100 * Math.sin(objectFingerInfo.orientation * Math.PI / 180);
			
			Imgproc.line(highlightedImage, pointA, pointB, new Scalar(0,255,0), 5);
			
			return objectFingerInfo;
		} else {
			Point errorFingerPoint = new Point(-1,-1);
			fingerInfo returnInfo = new fingerInfo(errorFingerPoint, errorFingerPoint,0,false);
			
			return returnInfo;
		}
	}

	// Input in a MatOfPoint2f that has 3 points in it (defining a triangle)
    // and outputs the fingerInfo (point tip location on both the image and the screen,
    // and the orientation). Triangle needs to be isoceles!
	private fingerInfo getFingerInfoFromTriangle(MatOfPoint2f triangleContour) {
		
		java.util.List<Point> pointList = triangleContour.toList();
		
		double ABdist = Utils.cv_distance(pointList.get(0),pointList.get(1));
		double ACdist = Utils.cv_distance(pointList.get(0),pointList.get(2));
		double BCdist = Utils.cv_distance(pointList.get(1),pointList.get(2));
		
		int tip = -1;
		int baseA = -1;
		int baseB = -1;
		
		if (ABdist > ACdist && BCdist > ACdist) {
			tip = 1;
			baseA = 2;
			baseB = 0;
		} else if (ACdist > ABdist && BCdist > ABdist) {
			tip = 2;
			baseA = 1;
			baseB = 0;
		} else if (ABdist > BCdist && ACdist > BCdist) {
			tip = 0;
			baseA = 1;
			baseB = 2;
		}
		
		if (tip == -1 || baseA == -1 || baseB == -1) {
			return new fingerInfo(new Point(-1,-1), new Point(-1,-1),0,false);
		}
		
		Point shortLengthMidPoint = new Point();
		
		shortLengthMidPoint.x = (pointList.get(baseA).x + pointList.get(baseB).x) / 2;
		shortLengthMidPoint.y = (pointList.get(baseA).y + pointList.get(baseB).y) / 2;
		
		Point imageTipPoint = pointList.get(tip);
		
		double lineAngle = 0;
		double deltaX = imageTipPoint.x - shortLengthMidPoint.x;
		double deltaY = imageTipPoint.y - shortLengthMidPoint.y;
		
		lineAngle = Math.atan2(-deltaY, deltaX) * 180 / Math.PI;


		// Bullshit conversions between points and Point2f to do perspective warping

		ArrayList<Point> screenPointList = new ArrayList<Point>();

		screenPointList.add(imageTipPoint);

		Mat transformedPoint = new Mat();

        Mat imageTipPointMat = Converters.vector_Point2f_to_Mat(screenPointList);

        Core.perspectiveTransform(imageTipPointMat, transformedPoint, perspective_warp_matrix);

        ArrayList<Point> outputtedTransformPoints = new ArrayList<Point>();

        Converters.Mat_to_vector_Point2f(transformedPoint, outputtedTransformPoints);

        Point screenTipPoint = outputtedTransformPoints.get(0);
        screenTipPoint.x = screenTipPoint.x * 1.0 / resultImage.width();
        screenTipPoint.y = screenTipPoint.y * 1.0 / resultImage.height();

       // End of the bullshit

		Log.i(TAG,"screenTipPoint  x: " + screenTipPoint.x + " , y: " + screenTipPoint.y);
		
		fingerInfo returnInfo = new fingerInfo(imageTipPoint,screenTipPoint,lineAngle,true);
		return returnInfo;
	}

	// Input an image, its contours, the contours hierarchy, and the momentCenterList (the center
    // points of each contour), and output whether the image was tracked or not. Menu image information
    // is stored in resultImage and highlightedImage.
	private Boolean grabMenuFromImage(Mat inputImage, ArrayList<MatOfPoint> contours, Mat hierarchy, ArrayList<Point> momentCenterList) {
		
		MatOfPoint2f contour2f = new MatOfPoint2f();
		
		int mark = 0;
		int squareIndexA = 0;
		int squareIndexB = 0;
		int squareIndexC = 0;
		int squareIndexD = 0;
		
		ArrayList<Point> landMarkCenterList = new ArrayList<Point>();
		ArrayList<Integer> landMarkIndexList = new ArrayList<Integer>();
		
		ArrayList<double[]> hierarchyList = new ArrayList<double[]>();
		
		for (int i = 0; i < hierarchy.width(); i++) {
			hierarchyList.add(hierarchy.get(0, i));
		}

		Log.i(TAG,"Starting QR search");
		
		for( int i = 0; i < contours.size(); i++ )
		{	
	        //Find the approximated polygon of the contour we are examining
			MatOfPoint2f resultingApproximation = new MatOfPoint2f();
			
			contours.get(i).convertTo(contour2f, CvType.CV_32FC2);
			Imgproc.approxPolyDP(contour2f, resultingApproximation, Imgproc.arcLength(contour2f, true)*0.02, true); 
			
			MatOfPoint contourValues = contours.get(i);
			java.util.List<Point> testList = contourValues.toList();
			
			int childContourValue = i;
			int childCount = 0;
			
			//Outer first square
	        if (resultingApproximation.size().height == 4)      // only quadrilaterals contours are examined
	        { 
	        	landMarkIndexList.add(i);
	        	
	        	//Inner first square
				childContourValue = (int) hierarchy.get(0, childContourValue)[2];
				int contourSiblingValue = (int) hierarchy.get(0, childContourValue)[0];
				
				while (childContourValue != -1 && contourSiblingValue == -1) {
					childContourValue = (int) hierarchy.get(0, childContourValue)[2];
					contourSiblingValue = (int) hierarchy.get(0, childContourValue)[0];
					childCount++;
				}
				
						
				if (childCount == 4) {
					if (mark == 0) {
						squareIndexA = i;
                        Log.i(TAG,"Found first square");
					} else if (mark == 1) {
						squareIndexB = i;
                        Log.i(TAG,"Found second square");
					} else if (mark == 2) {
						squareIndexC = i;
                        Log.i(TAG,"Found third square");
					} else if (mark == 3) {
						squareIndexD = i;
                        Log.i(TAG,"Found fourth square");
					}
					
					mark = mark + 1;
					landMarkCenterList.add(momentCenterList.get(i));
				}
				
	        }
		}
		
		int topLeftIndex = 0;
		int topRightIndex = 0;
		int bottomLeftIndex = 0;
		int bottomRightIndex = 0;
		
		if (mark >= 4) {
			
			// Find corner squares
			rectangleCornerIndices cornerIndeces = new rectangleCornerIndices();
			cornerIndeces = getCornerIndices(momentCenterList,squareIndexA,squareIndexB,squareIndexC,squareIndexD);
			
			topLeftIndex = cornerIndeces.topLeft;
			topRightIndex = cornerIndeces.topRight;
			bottomRightIndex = cornerIndeces.bottomRight;
			bottomLeftIndex = cornerIndeces.bottomLeft;
			
			Boolean indexBoundingCheck = (topLeftIndex < contours.size() && topRightIndex < contours.size()
					&& bottomLeftIndex < contours.size() && bottomRightIndex < contours.size())
					&& (topLeftIndex >= 0 && topRightIndex >= 0 && bottomRightIndex >= 0 && bottomLeftIndex >= 0);

			Log.i(TAG,"Starting bounding check");

			if( indexBoundingCheck) {

                Log.i(TAG,"Index Bounding Check Success");
				
				Boolean cornerSizeCheck = (Imgproc.contourArea(contours.get(topLeftIndex)) > 10) &&
						(Imgproc.contourArea(contours.get(topRightIndex)) > 10) &&
						(Imgproc.contourArea(contours.get(bottomRightIndex)) > 10) &&
						(Imgproc.contourArea(contours.get(bottomLeftIndex)) > 10);

                Log.i(TAG,"Starting corner size check");

				if (cornerSizeCheck) {

                    Log.i(TAG,"Corner Size Check Success");

					ToyIntClass alignVal = new ToyIntClass();
					double slope = cv_lineSlope(momentCenterList.get(topLeftIndex),momentCenterList.get(topRightIndex), alignVal);
					
					MatOfPoint2f L = new MatOfPoint2f();
					MatOfPoint2f M = new MatOfPoint2f();
					MatOfPoint2f O = new MatOfPoint2f();
					MatOfPoint2f N = new MatOfPoint2f();
					
					cv_getVertices(contours,topLeftIndex,slope,L);
					cv_getVertices(contours,topRightIndex,slope,M);
					cv_getVertices(contours,bottomRightIndex,slope,N);
					cv_getVertices(contours,bottomLeftIndex,slope,O);
					
					ArrayList<Point> testListL = new ArrayList<Point>();
					ArrayList<Point> testListM = new ArrayList<Point>();
					ArrayList<Point> testListN = new ArrayList<Point>();
					ArrayList<Point> testListO = new ArrayList<Point>();
					
					Converters.Mat_to_vector_Point2f(L, testListL);
					Converters.Mat_to_vector_Point2f(M, testListM);
					Converters.Mat_to_vector_Point2f(N, testListN);
					Converters.Mat_to_vector_Point2f(O, testListO);
					
					highlightedImage = inputImage.clone();
					
					Imgproc.drawContours( highlightedImage, contours, topLeftIndex , new Scalar(255,200,0), 2, 8, hierarchy, 0, new Point(0,0));
					Imgproc.drawContours( highlightedImage, contours, topRightIndex , new Scalar(255,200,0), 2, 8, hierarchy, 0, new Point(0,0));
					Imgproc.drawContours( highlightedImage, contours, bottomRightIndex , new Scalar(255,200,0), 2, 8, hierarchy, 0, new Point(0,0));
					Imgproc.drawContours( highlightedImage, contours, bottomLeftIndex , new Scalar(255,200,0), 2, 8, hierarchy, 0, new Point(0,0));
					
					ArrayList<Point> sourcePoints = new ArrayList<Point>();
					sourcePoints.add(new Point(L.get(2, 0)[0],L.get(2, 0)[1]));
					sourcePoints.add(new Point(M.get(3, 0)[0],M.get(3, 0)[1]));
					sourcePoints.add(new Point(N.get(0, 0)[0],N.get(0, 0)[1]));
					sourcePoints.add(new Point(O.get(1, 0)[0],O.get(1, 0)[1]));

					// Top left is 0, top right is 1, bottom right is 2, bottom left is 3

					MatOfPoint sourcePointsMat = new MatOfPoint();
					sourcePointsMat.fromList(sourcePoints);
					
					Rect sourceBoundingBox = Imgproc.boundingRect(sourcePointsMat);
					
					
					ArrayList<Point> destinationPoints = new ArrayList<Point>();
					destinationPoints.add(new Point(0,0));
					destinationPoints.add(new Point(sourceBoundingBox.width,0));
					destinationPoints.add(new Point(sourceBoundingBox.width,sourceBoundingBox.height));
					destinationPoints.add(new Point(0,sourceBoundingBox.height));
					
					Mat warp_matrix;
					Mat perspectiveSource = Converters.vector_Point2f_to_Mat(sourcePoints);
					Mat perspectiveDestination = Converters.vector_Point2f_to_Mat(destinationPoints);
					
					warp_matrix = Imgproc.getPerspectiveTransform(perspectiveSource, perspectiveDestination);

					// Store the warp matrix to the class variable
                    perspective_warp_matrix = warp_matrix;
					
					Imgproc.warpPerspective(inputImage, resultImage, warp_matrix, new Size(sourceBoundingBox.width,sourceBoundingBox.height));
					
					Imgproc.line(highlightedImage, sourcePoints.get(0), sourcePoints.get(1), new Scalar(0, 255, 0), 4);
					Imgproc.line(highlightedImage, sourcePoints.get(1), sourcePoints.get(2), new Scalar(0, 255, 0), 4);
					Imgproc.line(highlightedImage, sourcePoints.get(2), sourcePoints.get(3), new Scalar(0, 255, 0), 4);
					Imgproc.line(highlightedImage, sourcePoints.get(3), sourcePoints.get(0), new Scalar(0, 255, 0), 4);
					return true;
				}		
			}
		}

		return false;
	}

	// Called on an incoming frame. Outputs the menu and finger information for an incoming image
	public menuAndFingerInfo grabMenuAndFingerInfo(Mat inputImage) {
		Mat greyImage = new Mat();
		Mat edgeImage = new Mat();
		Mat hierarchy = new Mat();
		Mat resizedImage;
		
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		
		resizedImage = new Mat(inputImage.rows(),(int) (inputImage.cols()*0.8),inputImage.type());
		Imgproc.resize(inputImage, resizedImage, resizedImage.size(), 0, 0, Imgproc.INTER_CUBIC);
		
		// Get Edged Image
		Imgproc.cvtColor(resizedImage, greyImage, Imgproc.COLOR_RGB2GRAY);
		Imgproc.Canny(greyImage, edgeImage, 70, 210);
		
		traceImage = edgeImage;
		
		// Get contours
		Imgproc.findContours(edgeImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
		
		highlightedImage = resizedImage.clone();
		
		resultImage = Mat.zeros(new Size(100,100), resizedImage.type());
		
		ArrayList<Moments> momentList = new ArrayList<Moments>();
		ArrayList<Point> momentCenterList = new ArrayList<Point>();
		
		for (int i = 0; i < contours.size(); i++) {
			momentList.add(Imgproc.moments(contours.get(i),false));
			momentCenterList.add(new Point(momentList.get(i).m10/momentList.get(i).m00, momentList.get(i).m01 / momentList.get(i).m00));
		}


		Boolean menuTracked = grabMenuFromImage(resizedImage, contours, hierarchy, momentCenterList);
		
		if (menuTracked) {
			fingerInfo resultFingerInfo = getFingerInfoFromFrame(contours, hierarchy);
			
			menuAndFingerInfo returnInfo = new menuAndFingerInfo();
			returnInfo.fingerData = resultFingerInfo;
			returnInfo.menuTracked = true;
			return returnInfo;
		} else {
			menuAndFingerInfo returnInfo = new menuAndFingerInfo();
			returnInfo.fingerData = null;
			returnInfo.menuTracked = false;
			return returnInfo;
		}
	}

	// Distance between two points
	private double cv_distance(Point P, Point Q)
	{
		return Math.sqrt(Math.pow(Math.abs(P.x - Q.x),2) + Math.pow(Math.abs(P.y - Q.y),2)) ; 
	}

	// Gets distance between the line formed between L and M, and the point J
	private double cv_lineEquation(Point L, Point M, Point J)
	{
		double a,b,c,pdist;
	
		a = -((M.y - L.y) / (M.x - L.x));
		b = 1.0;
		c = (((M.y - L.y) /(M.x - L.x)) * L.x) - L.y;
		
		// Now that we have a, b, c from the equation ax + by + c, time to substitute (x,y) by
        // values from the Point J
	
		pdist = (a * J.x + (b * J.y) + c) / Math.sqrt((a * a) + (b * b));
		return pdist;
	}

	// Gets the slope of the line L and M. AlignmentVal is used to check whether we are dividing by
    // 0
	private double cv_lineSlope(Point L, Point M, ToyIntClass alignmentVal)
	{
		double dx,dy;
		dx = M.x - L.x;
		dy = M.y - L.y;
		
		if ( dy != 0)
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

	// Input the 4 corners of a rectangle and it will organize them based on the rectangles center
	private rectangleCornerIndices getCornerIndices(ArrayList<Point> momentList, int cornerA, int cornerB, int cornerC, int cornerD) {
		rectangleCornerIndices returnValue = new rectangleCornerIndices();
		returnValue.topLeft = -1;
		returnValue.topRight = -1;
		returnValue.bottomRight = -1;
		returnValue.bottomLeft = -1;
		
		double avgX; 
		double avgY; 
		
		avgX = ( momentList.get(cornerA).x + momentList.get(cornerB).x 
				+ momentList.get(cornerC).x + momentList.get(cornerD).x ) / 4;
		avgY = ( momentList.get(cornerA).y + momentList.get(cornerB).y 
				+ momentList.get(cornerC).y + momentList.get(cornerD).y ) / 4;
		
		int[] cornerList = new int[4];
		cornerList[0] = cornerA;
		cornerList[1] = cornerB;
		cornerList[2] = cornerC;
		cornerList[3] = cornerD;
		
		for (int i = 0; i < cornerList.length; i++) {
			int cornerResult = getQuadrantLocation(momentList.get(cornerList[i]),avgX,avgY);
			if (cornerResult == 0) {
				returnValue.topLeft = cornerList[i];
			} else if (cornerResult == 1) {
				returnValue.topRight = cornerList[i];
			} else if (cornerResult == 2) {
				returnValue.bottomRight = cornerList[i];
			} else if (cornerResult == 3) {
				returnValue.bottomLeft = cornerList[i];
			}
		}
		
		
		
		return returnValue;
	}
	

	// Outputs the quadrant that the inputted point is with respect to the point made by avgX
    // and avgY.
	// 0 is topLeft, 1 is topRight, 2 is BottomRight, 3 is BottomLeft
	private int getQuadrantLocation(Point evalPoint, double avgX, double avgY) {
		if (evalPoint.x < avgX) {
			if (evalPoint.y < avgY) {
				return 0;
			} else {
				return 3;
			}
		} else {
			if (evalPoint.y < avgY) {
				return 1;
			} else {
				return 2;
			}
		}
	}
	
	
	// Function: Routine to calculate 4 Corners of the Marker in Image Space using Region partitioning
	// Theory: OpenCV Contours stores all points that describe it and these points lie the perimeter of the polygon.
//		The below function chooses the farthest points of the polygon since they form the vertices of that polygon,
//		exactly the points we are looking for. To choose the farthest point, the polygon is divided/partitioned into
//		4 regions equal regions using bounding box. Distance algorithm is applied between the centre of bounding box
//		every contour point in that region, the farthest point is deemed as the vertex of that region. Calculating
//		for all 4 regions we obtain the 4 corners of the polygon ( - quadrilateral).

    // Function taken from online. See description above.
    // Gets the corner vertices based upon on 4 inputted points
	private void cv_getVertices(ArrayList<MatOfPoint> contours, int c_id, double slope, MatOfPoint2f quad)
	{
		Rect box;
		box = Imgproc.boundingRect( contours.get(c_id));
		
		Point M0 = new Point();
		Point M1 = new Point();
		Point M2 = new Point();
		Point M3 = new Point();
		Point A, C;
		Point B = new Point();
		Point D = new Point();
		Point W = new Point();
		Point X = new Point();
		Point Y = new Point();
		Point Z = new Point();
		

		A =  box.tl();
		B.x = box.br().x;
		B.y = box.tl().y;
		C = box.br();
		D.x = box.tl().x;
		D.y = box.br().y;


		W.x = (A.x + B.x) / 2;
		W.y = A.y;

		X.x = B.x;
		X.y = (B.y + C.y) / 2;

		Y.x = (C.x + D.x) / 2;
		Y.y = C.y;

		Z.x = D.x;
		Z.y = (D.y + A.y) / 2;


		double[] dmax = new double[4];
		dmax[0]=0.0;
		dmax[1]=0.0;
		dmax[2]=0.0;
		dmax[3]=0.0;

		double pd1 = 0.0;
		double pd2 = 0.0;
		
		if (slope > 5 || slope < -5 ) {
			ArrayList<Point> testPointList = new ArrayList<Point>();
			ArrayList<Point> topQuadrant = new ArrayList<Point>();
			ArrayList<Point> rightQuadrant = new ArrayList<Point>();
			ArrayList<Point> leftQuadrant = new ArrayList<Point>();
			ArrayList<Point> bottomQuadrant = new ArrayList<Point>();
			
			Converters.Mat_to_vector_Point2f(contours.get(c_id), testPointList);
		    for( int i = 0; i < contours.get(c_id).size().height; i++ ) {
		    	
		    	Point contourPoint = new Point();
		    	contourPoint.x = contours.get(c_id).get(i, 0)[0];
		    	contourPoint.y = contours.get(c_id).get(i, 0)[1];
		    	
		    	pd1 = cv_lineEquation(C,A,contourPoint);
		    	pd2 = cv_lineEquation(B,D,contourPoint);
		    	
		    	ToyDoublePointClass toyDoubleValue = new ToyDoublePointClass();
		    	
		    	if((pd1 >= 0.0) && (pd2 > 0.0)) {
		    		toyDoubleValue.toyDouble = dmax[1];
		    		toyDoubleValue.toyPoint = M1;
				    cv_updateCorner(contourPoint,W,toyDoubleValue);
				    dmax[1] = toyDoubleValue.toyDouble;
				    M1 = toyDoubleValue.toyPoint;
				    
				    topQuadrant.add(contourPoint);
				}
				else if((pd1 > 0.0) && (pd2 <= 0.0)) {
					toyDoubleValue.toyDouble = dmax[2];
					toyDoubleValue.toyPoint = M2;
				    cv_updateCorner(contourPoint,X,toyDoubleValue);
				    dmax[2] = toyDoubleValue.toyDouble;
				    M2 = toyDoubleValue.toyPoint;
				    
				    rightQuadrant.add(contourPoint);
				}
				else if((pd1 <= 0.0) && (pd2 < 0.0)) {
					toyDoubleValue.toyDouble = dmax[3];
					toyDoubleValue.toyPoint = M3;
				    cv_updateCorner(contourPoint,Y,toyDoubleValue);
				    dmax[3] = toyDoubleValue.toyDouble;
				    M3 = toyDoubleValue.toyPoint;
				    
				    bottomQuadrant.add(contourPoint);
				}
				else if((pd1 < 0.0) && (pd2 >= 0.0)) {
					toyDoubleValue.toyDouble = dmax[0];
					toyDoubleValue.toyPoint = M0;
				    cv_updateCorner(contourPoint,Z,toyDoubleValue);
				    dmax[0] = toyDoubleValue.toyDouble;
				    M0 = toyDoubleValue.toyPoint;
				    
				    leftQuadrant.add(contourPoint);
				} else {
					continue;
				}
		    	
		    	if (i == testPointList.size() - 1) {
		    		bottomQuadrant.size();
		    	}
		    	
		    }
		} else {
			double halfx = (A.x + B.x) / 2;
			double halfy = (A.y + D.y) / 2;
			
			for( int i = 0; i < contours.get(c_id).size().height; i++ ){
				
				Point contourPoint = new Point();
		    	contourPoint.x = contours.get(c_id).get(i, 0)[0];
		    	contourPoint.y = contours.get(c_id).get(i, 0)[1];
				
		    	ToyDoublePointClass toyDoubleValue = new ToyDoublePointClass();
		    	
				if((contourPoint.x < halfx) && (contourPoint.y <= halfy))
				{
				    toyDoubleValue.toyDouble = dmax[2];
		    		toyDoubleValue.toyPoint = M0;
				    cv_updateCorner(contourPoint,C,toyDoubleValue);
				    dmax[2] = toyDoubleValue.toyDouble;
				    M0 = toyDoubleValue.toyPoint;
				}
				else if((contourPoint.x >= halfx) && (contourPoint.y < halfy))
				{
				    toyDoubleValue.toyDouble = dmax[3];
		    		toyDoubleValue.toyPoint = M1;
				    cv_updateCorner(contourPoint,D,toyDoubleValue);
				    dmax[3] = toyDoubleValue.toyDouble;
				    M1 = toyDoubleValue.toyPoint;
				}
				else if((contourPoint.x > halfx) && (contourPoint.y >= halfy))
				{
				    toyDoubleValue.toyDouble = dmax[0];
		    		toyDoubleValue.toyPoint = M2;
				    cv_updateCorner(contourPoint,A,toyDoubleValue);
				    dmax[0] = toyDoubleValue.toyDouble;
				    M2 = toyDoubleValue.toyPoint;
				}
				else if((contourPoint.x <= halfx) && (contourPoint.y > halfy))
				{
				    toyDoubleValue.toyDouble = dmax[1];
		    		toyDoubleValue.toyPoint = M3;
				    cv_updateCorner(contourPoint,B,toyDoubleValue);
				    dmax[1] = toyDoubleValue.toyDouble;
				    M3 = toyDoubleValue.toyPoint;
				}
		    }
		}
		
		ArrayList<Point> pointsToAdd = new ArrayList<Point>();
		pointsToAdd.add(M0);
		pointsToAdd.add(M1);
		pointsToAdd.add(M2);
		pointsToAdd.add(M3);
		
		quad.fromList(pointsToAdd);
	}
	
	// Function: Compare a point if it more far than previously recorded farthest distance
	// Description: Farthest Point detection using reference point and baseline distance

    // Taken from online. See description above.
	private void cv_updateCorner(Point P, Point ref , ToyDoublePointClass baselineAndPoint)
	{
	    double temp_dist;
	    temp_dist = cv_distance(P,ref);

	    if(temp_dist > baselineAndPoint.toyDouble)
	    {
	    	baselineAndPoint.toyDouble = temp_dist;			// The farthest distance is the new baseline
	        Point corner = new Point();
	        corner.x = P.x;
	        corner.y = P.y;					// P is now the farthest point
	        baselineAndPoint.toyPoint = corner;
	    }
		
	}
	
	// Function: Get the Intersection Point of the lines formed by sets of two points

    // Taken from online. See description above.
	private Boolean getIntersectionPoint(Point a1, Point a2, Point b1, Point b2, ToyPointClass intersection)
	{
	    Point p = a1.clone();
	    Point q = b1.clone();
	    Point r = new Point();
	    r.x = a2.x - a1.x;
	    r.y = a2.y - a1.y;
	    Point s = new Point();
	    s.x = b2.x - b1.x;
	    s.y = b2.y - b1.y;

	    if(cross(r,s) == 0) {return false;}

	    Point qMinusP = new Point();
	    qMinusP.x = q.x - p.x;
	    qMinusP.y = q.y - p.y;
	    
	    double t = cross(qMinusP,s) / cross(r,s);

	    r.x = t * r.x;
	    r.y = t * r.y;
	    
	    intersection.toyPoint.x = p.x + r.x;
	    intersection.toyPoint.y = p.y + r.y;
	    return true;
	}

	// Taken from online
    // Returns the cross product of two points
	private double cross(Point v1,Point v2)
	{
	    return v1.x*v2.y - v1.y*v2.x;
	}
	
	
	
}
