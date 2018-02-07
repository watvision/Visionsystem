package com.watvision.mainapp;


import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;

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

    // AruCo Marker size constant
    private static final float MARKER_SIZE = (float) 0.017;

    // General Line Thickness
    private static final int lineThickness = 4;

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

		fingerInfo() {
		    tracked = false;
		    orientation = 0;
		    screenLocation = null;
		    imageLocation = null;
        }
		
	}

	// The class that is returned that contains all the menu and finger information
	public class menuAndFingerInfo {
		public Boolean menuTracked;
		public Boolean topLeftTracked;
		public Boolean topRightTracked;
		public Boolean bottomLeftTracked;
		public Boolean bottomRightTracked;
		public fingerInfo fingerData;

		public menuAndFingerInfo() {
		    menuTracked = false;
		    topLeftTracked = false;
		    topRightTracked = false;
		    bottomRightTracked = false;
		    bottomLeftTracked = false;
		    fingerData = new fingerInfo();
        }

		// Marks all data as invalid to indicate no menu was tracked
		public void markInvalid() {
		    menuTracked = false;
		    topLeftTracked = false;
		    topRightTracked = false;
		    bottomLeftTracked = false;
		    bottomRightTracked = false;
        }
	}

	// An enum used to denote which corner the markerCorner is
    private enum cornerLocation {
        topLeft, topRight, bottomRight, bottomLeft
    };

	private class markerCorner {
	    public Marker mainMarker;
        // the counter-clockwise marker
	    public Marker markerA;
	    // the clockwise marker
	    public Marker markerB;
	    public cornerLocation cornerLoc;

	    private ArrayList<Point> markerACornerList;
        private ArrayList<Point> markerBCornerList;
        private ArrayList<Point> mainMarkerCornerList;

        public markerCorner(Marker inMarkerA, Marker inMarker, Marker inMarkerB, cornerLocation inCornerLoc,
                            ArrayList<Point> inMarkerACornerList,
                            ArrayList<Point> inMainMarkerCornerList,
                            ArrayList<Point> inMarkerBCornerList) {
            mainMarker = inMarker;
            markerA = inMarkerA;
            markerB = inMarkerB;
            cornerLoc = inCornerLoc;

            markerACornerList = inMarkerACornerList;
            mainMarkerCornerList = inMainMarkerCornerList;
            markerBCornerList = inMarkerBCornerList;
        }

        // Get the correct corner point for the corner
        public Point getCornerPoint(CameraParameters camParams) {

            // If main marker is defined
            if (mainMarker != null) {

                if (cornerLoc == cornerLocation.topLeft) {
                    return mainMarkerCornerList.get(2);
                } else if (cornerLoc == cornerLocation.topRight) {
                    return mainMarkerCornerList.get(3);
                } else if (cornerLoc == cornerLocation.bottomRight) {
                    return mainMarkerCornerList.get(0);
                } else if (cornerLoc == cornerLocation.bottomLeft) {
                    return mainMarkerCornerList.get(1);
                }

            // if Main Marker is not defined use the other two corners
            } else {

                Point intersectionPoint = new Point();
                Boolean intersectionExists = true;

                if (cornerLoc == cornerLocation.topLeft) {
                    intersectionExists = getIntersectionPoint(markerACornerList.get(1),markerACornerList.get(2),
                            markerBCornerList.get(3),markerBCornerList.get(2),intersectionPoint);
                } else if (cornerLoc == cornerLocation.topRight) {
                    intersectionExists = getIntersectionPoint(markerACornerList.get(3),markerACornerList.get(2),
                            markerBCornerList.get(0),markerBCornerList.get(3),intersectionPoint);
                } else if (cornerLoc == cornerLocation.bottomRight) {
                    intersectionExists = getIntersectionPoint(markerACornerList.get(0),markerACornerList.get(3),
                            markerBCornerList.get(0),markerBCornerList.get(1),intersectionPoint);
                } else if (cornerLoc == cornerLocation.bottomLeft) {
                    intersectionExists = getIntersectionPoint(markerACornerList.get(0),markerACornerList.get(1),
                            markerBCornerList.get(1),markerBCornerList.get(2),intersectionPoint);
                }

                if (intersectionExists) {
                    Log.i(TAG,"Returning intersection point");
                    return intersectionPoint;
                } else {
                    Log.i(TAG,"Reached error state. Detected parallel corner intersections");
                }

            }

            Log.i(TAG,"Code has reached an error state while detecting Marker Corners");
            return new Point();
        }
    }

	// Constructor
	public MenuAndFingerTracking() {
		traceImage = new Mat();
		// Initialize result and highlighted image to generic sizes
		resultImage = Mat.zeros(800,800,16);
		highlightedImage = Mat.zeros(800,800,16);
	}

	// Called on an incoming frame. Outputs the menu and finger information for an incoming image
	public menuAndFingerInfo grabMenuAndFingerInfo(Mat inputImage) {
		Mat resizedImage;

        menuAndFingerInfo returnInfo = new menuAndFingerInfo();

        // Storage for the corner markers
        Marker topLeftMarker = null;
        Marker topRightMarker = null;
        Marker bottomRightMarker = null;
        Marker bottomLeftMarker = null;
        Marker fingerMarker = null;

        // The image may need some resizing. It seems to come in warped. For now leaving the image unaltered
		//resizedImage = new Mat(inputImage.rows(),(int) (inputImage.cols()*0.8),inputImage.type());
		//Imgproc.resize(inputImage, resizedImage, resizedImage.size(), 0, 0, Imgproc.INTER_CUBIC);
        resizedImage = inputImage.clone();

        // Setup required parameters for detect method
        MarkerDetector mDetector = new MarkerDetector();
        Vector<Marker> detectedMarkers = new Vector<>();
        CameraParameters camParams = new CameraParameters();

        camParams.loadConstandCalibration(resizedImage.cols()/2,resizedImage.rows()/2);

        // Validate camera Parameters
        if (camParams.isValid()) {
            Log.i(TAG,"VALID cam Params");
        } else {
            // Invalid camera params return incorrect
            Log.i(TAG,"INVALID Cam Params");
            returnInfo.markInvalid();
            return returnInfo;
        }

        // Populate detectedMarkers
        mDetector.detect(resizedImage, detectedMarkers, camParams, MARKER_SIZE);

        // Detect markers and provide info
        if (detectedMarkers.size() != 0) {

            Log.i(TAG, "Detected markers!");

            Imgproc.cvtColor(resizedImage, highlightedImage, Imgproc.COLOR_RGBA2RGB);

            for (int i = 0; i < detectedMarkers.size(); i++) {
                Marker marker = detectedMarkers.get(i);

                int markerID = marker.getMarkerId();
                Log.i(TAG,"Marker id:" + markerID);

                if (markerID >= 0) {
                    highlightMarker(marker,camParams);
                }

                switch (markerID) {
                    case 1:
                        returnInfo.topLeftTracked = true;
                        topLeftMarker = marker;
                        break;
                    case 2:
                        returnInfo.topRightTracked = true;
                        topRightMarker = marker;
                        break;
                    case 3:
                        returnInfo.bottomRightTracked = true;
                        bottomRightMarker = marker;
                        break;
                    case 4:
                        returnInfo.bottomLeftTracked = true;
                        bottomLeftMarker = marker;
                        break;
                    case 5:
                        fingerMarker = marker;
                        break;
                    default:
                        break;
                }
            }
        }

        // Get number of tracked corners
        int numTrackedCorners = 0;

        if (returnInfo.topLeftTracked) numTrackedCorners++;
        if (returnInfo.topRightTracked) numTrackedCorners++;
        if (returnInfo.bottomRightTracked) numTrackedCorners++;
        if (returnInfo.bottomLeftTracked) numTrackedCorners++;

        if (numTrackedCorners >= 3) {
            returnInfo.menuTracked = true;
            // Track and produce resulting menu
            produceResultingMenu(topLeftMarker,topRightMarker,bottomLeftMarker,bottomRightMarker,
                    resizedImage, camParams);
        }

        if (fingerMarker != null && returnInfo.menuTracked) {
            // Track and output finger info
            produceFingerInfo(fingerMarker,returnInfo,camParams);
        }

        return returnInfo;
	}

	public void highlightMarker(Marker inputMarker, CameraParameters camParams) {

	    List<Point> cornerList = inputMarker.getCorners(camParams);

	    for (int i = 0; i < 4; i++) {
            Imgproc.line(highlightedImage, cornerList.get(i), cornerList.get((i+1) % 4), new Scalar(255, 0, 0), lineThickness);
        }

    }

	public void produceResultingMenu(Marker topLeft, Marker topRight, Marker bottomLeft, Marker bottomRight,
                                     Mat inputImage, CameraParameters camParams) {

	    // Get corner points
        // 0 is topLeft, 1 is topRight, 2 is bottomRight, 3 is bottomLeft
        ArrayList<Point> cornerList = getCornerPointList(topLeft, topRight, bottomRight, bottomLeft, camParams);

        // Final menu image width and height
        double width = cv_distance(cornerList.get(0),cornerList.get(1));
        double height = cv_distance(cornerList.get(0),cornerList.get(3));

        // Create resulting image dimensions
        ArrayList<Point> destinationPoints = new ArrayList<Point>();
        destinationPoints.add(new Point(0,0));
        destinationPoints.add(new Point(width,0));
        destinationPoints.add(new Point(width,height));
        destinationPoints.add(new Point(0,height));

        // Warp the matrix
        Mat warp_matrix;
        Mat perspectiveSource = Converters.vector_Point2f_to_Mat(cornerList);
        Mat perspectiveDestination = Converters.vector_Point2f_to_Mat(destinationPoints);

        warp_matrix = Imgproc.getPerspectiveTransform(perspectiveSource, perspectiveDestination);

        // Store the warp matrix to the class variable
        perspective_warp_matrix = warp_matrix;

        Imgproc.warpPerspective(inputImage, resultImage, warp_matrix, new Size(width,height));

        // Draw the highlights on the image
        for (int i = 0; i < 4; i++) {
            Imgproc.line(highlightedImage,cornerList.get(i),cornerList.get((i+1) % 4), new Scalar(255,0,0),lineThickness);
        }
    }

    private ArrayList<Point> getCornerPointList(Marker topLeft, Marker topRight, Marker bottomRight, Marker bottomLeft,
                                                CameraParameters camParams) {

        ArrayList<Point> topLeftCornerList = null;
        ArrayList<Point> topRightCornerList = null;
        ArrayList<Point> bottomLeftCornerList = null;
        ArrayList<Point> bottomRightCornerList = null;

        if (topLeft != null) {
            topLeftCornerList = new ArrayList<Point>(topLeft.getCorners(camParams));
        }
        if (topRight != null) {
            topRightCornerList = new ArrayList<Point>(topRight.getCorners(camParams));
        }
        if (bottomRight != null) {
           bottomRightCornerList = new ArrayList<Point>(bottomRight.getCorners(camParams));
        }
        if (bottomLeft != null) {
           bottomLeftCornerList = new ArrayList<Point>(bottomLeft.getCorners(camParams));
        }

        // Create marker corners. Marker input order is clockwise with the main marker being in the middle
        markerCorner topLeftMarkerCorner = new markerCorner(bottomLeft, topLeft, topRight, cornerLocation.topLeft,
                bottomLeftCornerList, topLeftCornerList, topRightCornerList);
        markerCorner topRightMarkerCorner = new markerCorner(topLeft, topRight, bottomRight, cornerLocation.topRight,
                topLeftCornerList, topRightCornerList, bottomRightCornerList);
        markerCorner bottomRightMarkerCorner = new markerCorner(topRight, bottomRight, bottomLeft, cornerLocation.bottomRight,
                topRightCornerList, bottomRightCornerList, bottomLeftCornerList);
        markerCorner bottomLeftMarkerCorner = new markerCorner(bottomRight, bottomLeft, topLeft, cornerLocation.bottomLeft,
                bottomRightCornerList, bottomLeftCornerList, topLeftCornerList);

        Point topLeftCorner = topLeftMarkerCorner.getCornerPoint(camParams);
        Point topRightCorner = topRightMarkerCorner.getCornerPoint(camParams);
        Point bottomRightCorner = bottomRightMarkerCorner.getCornerPoint(camParams);
        Point bottomLeftCorner = bottomLeftMarkerCorner.getCornerPoint(camParams);

        ArrayList<Point> sourcePoints = new ArrayList<Point>();
        sourcePoints.add(topLeftCorner);
        sourcePoints.add(topRightCorner);
        sourcePoints.add(bottomRightCorner);
        sourcePoints.add(bottomLeftCorner);

	    return sourcePoints;
    }

    public void produceFingerInfo(Marker fingerMarker, menuAndFingerInfo returnInfo,
                                  CameraParameters camParams) {

	    List<Point> fingerMarkerCorners = fingerMarker.getCorners(camParams);

	    // Calculate orientation of the code
        double lineAngle;
        // Get deltaX and Y of the top left corner to the bottom left corner
        double deltaX = fingerMarkerCorners.get(0).x - fingerMarkerCorners.get(3).x;
        double deltaY = fingerMarkerCorners.get(0).y - fingerMarkerCorners.get(3).y;

        lineAngle = Math.atan2(-deltaY, deltaX) * 180 / Math.PI;

        // Get marker center by getting the midpoint between the top left and bottom right corner
        Point markerCenterImage = new Point();
        markerCenterImage.x = (fingerMarkerCorners.get(0).x + fingerMarkerCorners.get(2).x) / 2;
        markerCenterImage.y = (fingerMarkerCorners.get(0).y + fingerMarkerCorners.get(2).y) / 2;

        // Bullshit conversions between points and Point2f to do perspective warping

        ArrayList<Point> screenPointList = new ArrayList<Point>();

        screenPointList.add(markerCenterImage);

        Mat transformedPoint = new Mat();

        Mat imageTipPointMat = Converters.vector_Point2f_to_Mat(screenPointList);

        Core.perspectiveTransform(imageTipPointMat, transformedPoint, perspective_warp_matrix);

        ArrayList<Point> outputtedTransformPoints = new ArrayList<Point>();

        Converters.Mat_to_vector_Point2f(transformedPoint, outputtedTransformPoints);

        Point screenTipPoint = outputtedTransformPoints.get(0);
        screenTipPoint.x = screenTipPoint.x * 1.0 / resultImage.width();
        screenTipPoint.y = screenTipPoint.y * 1.0 / resultImage.height();

        // End of the bullshit

        // Draw the marker onto the highlighted image
        Point pointA = new Point();
        Point pointB = new Point();

        pointA.x = markerCenterImage.x;
        pointA.y = markerCenterImage.y;

        pointB.x = pointA.x + 100 * Math.cos(lineAngle * Math.PI / 180);
        pointB.y = pointA.y - 100 * Math.sin(lineAngle * Math.PI / 180);

        Imgproc.line(highlightedImage, pointA, pointB, new Scalar(0,0,50), lineThickness);

        // Modify the returnInfo
        returnInfo.fingerData.orientation = lineAngle;
        returnInfo.fingerData.imageLocation = markerCenterImage;
        returnInfo.fingerData.screenLocation = screenTipPoint;
        returnInfo.fingerData.tracked = true;
    }

    // Distance between two points
	private double cv_distance(Point P, Point Q)
	{
		return Math.sqrt(Math.pow(Math.abs(P.x - Q.x),2) + Math.pow(Math.abs(P.y - Q.y),2)) ; 
	}

    // Taken from online. See description above.
    private Boolean getIntersectionPoint(Point a1, Point a2, Point b1, Point b2, Point intersection)
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

        intersection.x = p.x + r.x;
        intersection.y = p.y + r.y;
        return true;
    }

    // Taken from online
    // Returns the cross product of two points
    private double cross(Point v1,Point v2)
    {
        return v1.x*v2.y - v1.y*v2.x;
    }


}
