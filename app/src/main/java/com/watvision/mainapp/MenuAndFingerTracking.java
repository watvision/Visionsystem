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

	// Determined menu Width and Height
    private double menuWidth;
    private double menuHeight;

    // If true then lock the last known corner positions in place
    private Boolean lockedCornerState;
    private Boolean prevLockedCornerState;

    // Information used to save the state of the system
    private ArrayList<Point> savedCornerList;
    private Mat savedWarpMatrix;
    private double savedResultMenuWidth;
    private double savedResultMenuHeight;

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
		public Boolean[] trackedCorners;
		public fingerInfo fingerData;
		public int numTrackedCorners;

		public menuAndFingerInfo() {
		    menuTracked = false;
		    trackedCorners = new Boolean[4];
		    for (int i = 0; i < trackedCorners.length; i++) {
		        trackedCorners[i] = false;
            }
		    fingerData = new fingerInfo();
		    numTrackedCorners = 0;
        }

		// Marks all data as invalid to indicate no menu was tracked
		public void markInvalid() {
		    menuTracked = false;
		    for (int i = 0; i < trackedCorners.length; i++) {
		        trackedCorners[i] = false;
            }
		    numTrackedCorners = 0;
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

		menuWidth = 0;
		menuHeight = 0;

		savedCornerList = null;
		lockedCornerState = false;
		savedWarpMatrix = null;
		prevLockedCornerState = false;
	}

    // Called on an incoming frame. Outputs the menu and finger information for an incoming image
    public menuAndFingerInfo grabMenuAndFingerInfo(Mat inputImage) {

	    menuAndFingerInfo returnInfo;

	    // This method isn't exactly thread safe... Really should be using a semaphore or something

        if (prevLockedCornerState != lockedCornerState) {
            if (!lockedCornerState) {
                // We are transitioning from locked to unlocked
                clearMenuKnowledge();
            } else {
                // We are transitioning from unlocked to locked
                savedCornerList = null;
                savedWarpMatrix = null;
            }
        }

	    if (lockedCornerState && savedCornerList != null) {
	        // Return info from saved corner state
            returnInfo = grabLockedMenuAndFingerInfoFromImage(inputImage);
        } else {
            returnInfo = grabMenuAndFingerInfoFromImage(inputImage);
        }

        prevLockedCornerState = lockedCornerState;

        return returnInfo;
    }

    // Getting menu and finger info from the saved corner point locations using only a subimage
    private menuAndFingerInfo grabLockedMenuAndFingerInfoFromImage(Mat inputImage) {
        Mat resizedImage;

        menuAndFingerInfo returnInfo = new menuAndFingerInfo();

        Marker fingerMarker = null;

        // The image may need some resizing. It seems to come in warped. For now leaving the image unaltered
        resizedImage = new Mat(inputImage.rows(),(int) (inputImage.cols()*0.561165),inputImage.type());
        Imgproc.resize(inputImage, resizedImage, resizedImage.size(), 0, 0, Imgproc.INTER_CUBIC);

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

        // Find the finger based on the smaller subimage
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

                if (markerID == 5) {
                    fingerMarker = marker;
                }
            }
        }

        if (fingerMarker != null && returnInfo.menuTracked) {
            // Track and output finger info
            produceFingerInfo(fingerMarker,returnInfo,camParams);
        }

        // Artificially set that the menu is tracked
        returnInfo.menuTracked = true;
        for (int i = 0; i < returnInfo.trackedCorners.length; i++) {
            returnInfo.trackedCorners[i] = true;
        }
        returnInfo.numTrackedCorners = 4;

        Imgproc.warpPerspective(resizedImage, resultImage, savedWarpMatrix, new Size(savedResultMenuWidth,savedResultMenuHeight));

        // Draw highlights on the image
        for (int i = 0; i < 4; i++) {
            Imgproc.line(highlightedImage,savedCornerList.get(i),savedCornerList.get((i+1) % 4), new Scalar(255,0,0),lineThickness);
        }

        if (fingerMarker != null && returnInfo.menuTracked) {
            // Track and output finger info
            produceFingerInfo(fingerMarker,returnInfo,camParams);
        }

        return returnInfo;
    }

	// Getting menu and finger info from a full image using the regular algorithm
	private menuAndFingerInfo grabMenuAndFingerInfoFromImage(Mat inputImage) {
		Mat resizedImage;

        menuAndFingerInfo returnInfo = new menuAndFingerInfo();

        // Storage for the corner markers
        Marker topLeftMarker = null;
        Marker topRightMarker = null;
        Marker bottomRightMarker = null;
        Marker bottomLeftMarker = null;
        Marker fingerMarker = null;

        // The unstretched image ratio of 0.561165 was found through experiment by measuring
        // the aspect ratio in both portrait and landscape mode of the resulting image
        // and then graphing these results for 3 different values of the ratio.
        // The results were parabolic, and the intersection between the two parabolas
        // (one being the portrait aspect ratio versus the stretch image ratio,
        // and the other being the landscape aspect ratio) gives the point where the image
        // is unstretched

        // The image may need some resizing. It seems to come in warped. For now leaving the image unaltered
		resizedImage = new Mat(inputImage.rows(),(int) (inputImage.cols()*0.561165),inputImage.type());
		Imgproc.resize(inputImage, resizedImage, resizedImage.size(), 0, 0, Imgproc.INTER_CUBIC);

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
                        returnInfo.trackedCorners[0] = true;
                        topLeftMarker = marker;
                        break;
                    case 2:
                        returnInfo.trackedCorners[1] = true;
                        topRightMarker = marker;
                        break;
                    case 3:
                        returnInfo.trackedCorners[2] = true;
                        bottomRightMarker = marker;
                        break;
                    case 4:
                        returnInfo.trackedCorners[3] = true;
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

        for (int i = 0; i < returnInfo.trackedCorners.length; i++) {
            if (returnInfo.trackedCorners[i]) {
                numTrackedCorners++;
            }
        }

        returnInfo.numTrackedCorners = numTrackedCorners;

        // If we do not know the menu size
        if (menuWidth == 0 || menuHeight == 0) {
            if (numTrackedCorners == 4) {
                returnInfo.menuTracked = true;
                // Calculate menu size
                calculateMenuSize(topLeftMarker,topRightMarker,bottomRightMarker,bottomLeftMarker,camParams);
                produceResultingMenu(topLeftMarker,topRightMarker,bottomLeftMarker,bottomRightMarker,
                        resizedImage, camParams, returnInfo);
            } else {
                returnInfo.menuTracked = false;
            }
        } else {
            // Here we know the menu size
            if (numTrackedCorners >= 1) {
                Log.d(TAG,"Getting screen with one or more markers");
                returnInfo.menuTracked = true;
                produceResultingMenu(topLeftMarker,topRightMarker,bottomLeftMarker,bottomRightMarker,
                        resizedImage, camParams, returnInfo);
            }
        }

        if (fingerMarker != null && returnInfo.menuTracked) {
            // Track and output finger info
            produceFingerInfo(fingerMarker,returnInfo,camParams);
        }

        return returnInfo;
	}

	// Remove any knowledge about the menu
	public void clearMenuKnowledge() {
	    menuWidth = 0;
	    menuHeight = 0;
    }

    private void calculateMenuSize(Marker topLeft, Marker topRight, Marker bottomRight, Marker bottomLeft,
                                   CameraParameters camParams) {

	    Log.d(TAG,"Calculating menu size");
        ArrayList<Point> topLeftCornerPoints = new ArrayList(topLeft.getCorners(camParams));
        ArrayList<Point> topRightCornerPoints = new ArrayList(topRight.getCorners(camParams));
        ArrayList<Point> bottomLeftCornerPoints = new ArrayList(bottomLeft.getCorners(camParams));

        double pixelTolerance = 30;

        // Calculate width in meters
        double markerPixelWidth = cv_distance(topLeftCornerPoints.get(3),topLeftCornerPoints.get(2));
        double menuPixelWidth = cv_distance(topLeftCornerPoints.get(2),topRightCornerPoints.get(3));

        menuWidth = menuPixelWidth * MARKER_SIZE / markerPixelWidth;

        // Calculate height in meters
        double markerPixelHeight = cv_distance(topLeftCornerPoints.get(1),topLeftCornerPoints.get(2));
        double menuPixelHeight = cv_distance(topLeftCornerPoints.get(2),bottomLeftCornerPoints.get(1));



        double error;
        double adjustment;

        // Figure out the width
        do {
            // Get error distance and iterate
            Vector<Point3> topRightPointList = new Vector<>();
            topRightPointList.add(new Point3(menuWidth + (MARKER_SIZE/2),-(MARKER_SIZE/2),0));
            ArrayList<Point> projectedCornerList = new ArrayList(topLeft.getProjectedPoints(topRightPointList,camParams));
            error = cv_distance(projectedCornerList.get(0),topRightCornerPoints.get(3));

            if (projectedCornerList.get(0).x > topRightCornerPoints.get(3).x) {
                adjustment = -error * MARKER_SIZE / markerPixelWidth;
            } else {
                adjustment = error * MARKER_SIZE / markerPixelWidth;
            }

            menuWidth += adjustment;

            Log.d(TAG,"Width iteration error: " + error + " adjustment: " + adjustment);

        } while (error >= pixelTolerance);

        Log.d(TAG,"Final Width iteration error: " + error + " adjustment: " + adjustment);

        // Figure out the height
        do {
            // Get error distance and iterate
            Vector<Point3> bottomLeftPointList = new Vector<>();
            bottomLeftPointList.add(new Point3(MARKER_SIZE/2,-(MARKER_SIZE/2) - menuHeight,0));
            ArrayList<Point> projectedCornerList = new ArrayList(topLeft.getProjectedPoints(bottomLeftPointList,camParams));
            error = cv_distance(projectedCornerList.get(0),bottomLeftCornerPoints.get(1));

            if (projectedCornerList.get(0).y > bottomLeftCornerPoints.get(1).y) {
                adjustment = -error * MARKER_SIZE / markerPixelHeight;
            } else {
                adjustment = error * MARKER_SIZE / markerPixelHeight;
            }

            menuHeight += adjustment;

            Log.d(TAG,"Height iteration error: " + error + " adjustment: " + adjustment);

        } while (error >= pixelTolerance);

        Log.d(TAG,"Final Height iteration error: " + error + " adjustment: " + adjustment);

        menuHeight = menuPixelHeight * MARKER_SIZE / markerPixelHeight;
        Log.d(TAG,"Estimated menu width: " + menuWidth + " height: " + menuHeight);
    }

	public void highlightMarker(Marker inputMarker, CameraParameters camParams) {

	    List<Point> cornerList = inputMarker.getCorners(camParams);

	    for (int i = 0; i < 4; i++) {
            Imgproc.line(highlightedImage, cornerList.get(i), cornerList.get((i+1) % 4), new Scalar(255, 0, 0), lineThickness);
        }

    }

	public void produceResultingMenu(Marker topLeft, Marker topRight, Marker bottomLeft, Marker bottomRight,
                                     Mat inputImage, CameraParameters camParams, menuAndFingerInfo returnInfo) {

	    // Get corner points
        // 0 is topLeft, 1 is topRight, 2 is bottomRight, 3 is bottomLeft
        ArrayList<Point> cornerList = getCornerPointList(topLeft, topRight, bottomRight, bottomLeft, camParams, returnInfo);

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

        // If we are in the locked state then save the corner point list
        if (lockedCornerState) {
            savedCornerList = cornerList;
            savedWarpMatrix = perspective_warp_matrix.clone();
            savedResultMenuWidth = width;
            savedResultMenuHeight = height;
        }

        // Draw the highlights on the image
        for (int i = 0; i < 4; i++) {
            Imgproc.line(highlightedImage,cornerList.get(i),cornerList.get((i+1) % 4), new Scalar(255,0,0),lineThickness);
        }
    }

    private ArrayList<Point> getCornerPointList(Marker topLeft, Marker topRight, Marker bottomRight, Marker bottomLeft,
                                                CameraParameters camParams, menuAndFingerInfo returnInfo) {

        Point topLeftCorner = new Point(0,0);
        Point topRightCorner = new Point(0,0);
        Point bottomRightCorner = new Point(0,0);
        Point bottomLeftCorner = new Point(0,0);

        ArrayList<Point> sourcePoints = new ArrayList<Point>();
        sourcePoints.add(topLeftCorner);
        sourcePoints.add(topRightCorner);
        sourcePoints.add(bottomRightCorner);
        sourcePoints.add(bottomLeftCorner);

        if (topLeft != null) {
            updateCornerPointList(topLeft,cornerLocation.topLeft,sourcePoints,camParams, returnInfo);
        }
        if (topRight != null) {
            updateCornerPointList(topRight,cornerLocation.topRight,sourcePoints,camParams, returnInfo);
        }
        if (bottomRight != null) {
            updateCornerPointList(bottomRight,cornerLocation.bottomRight,sourcePoints,camParams, returnInfo);
        }
        if (bottomLeft != null) {
            updateCornerPointList(bottomLeft,cornerLocation.bottomLeft,sourcePoints,camParams, returnInfo);
        }

	    return sourcePoints;
    }

    // Updates the list of known menu corner points based upon the current input marker
    public void updateCornerPointList(Marker inMarker, cornerLocation inCornerLocation, ArrayList<Point> cornerList,
                                      CameraParameters camParams, menuAndFingerInfo returnInfo) {

	    ArrayList<Point> markerCornerList = new ArrayList<Point>(inMarker.getCorners(camParams));

	    // Set the marker's corner point to the identified spot
	    cornerList.set((inCornerLocation.ordinal()), markerCornerList.get((inCornerLocation.ordinal() + 2) % 4));

        // If a corner isn't tracked then we first identify it and then update its value
        Vector<Point3> pointGrabList = new Vector<Point3>();
        ArrayList<Integer> cornerIndexList = new ArrayList<>();

        for (int i = 0; i < cornerList.size(); i++) {

            // If the corner isn't tracked then we should make a guess for it
            if (!returnInfo.trackedCorners[i]) {
                pointGrabList.add(new Point3(getRelativeMenuWidthDistance(inCornerLocation,cornerLocation.values()[i]),
                        getRelativeMenuHeightDistance(inCornerLocation,cornerLocation.values()[i]),0));
                cornerIndexList.add(i);
            }
        }

        // Update values for all the untracked points
        if (!pointGrabList.isEmpty()) {
            ArrayList<Point> projectedCornerList = new ArrayList(inMarker.getProjectedPoints(pointGrabList,camParams));

            // Update the unknown points with the projected points
            for (int i = 0; i < projectedCornerList.size(); i++) {
                // Define a new point
                Point newInputPoint = new Point();

                // Get the current value of the corner point
                Point currentCornerPoint = cornerList.get(cornerIndexList.get(i));

                // Get the new value of the corner point
                Point newInputValue = projectedCornerList.get(i);

                // Add the averaged value of the new input point to the current corner points value to create an average
                newInputPoint.x = currentCornerPoint.x + (newInputValue.x / returnInfo.numTrackedCorners);
                newInputPoint.y = currentCornerPoint.y + (newInputValue.y / returnInfo.numTrackedCorners);

                cornerList.set(cornerIndexList.get(i),newInputPoint);
            }
        }

    }

    // Returns whether to input a negative positive, or zero menu width based on the marker position
    private double getRelativeMenuWidthDistance(cornerLocation markerLocation, cornerLocation desiredLocation) {

	    if (markerLocation == cornerLocation.topLeft || markerLocation == cornerLocation.bottomLeft) {
            if (desiredLocation == cornerLocation.topRight || desiredLocation == cornerLocation.bottomRight) {
                return menuWidth + MARKER_SIZE/2;
            } else {
                return MARKER_SIZE/2;
            }
        } else {
            if (desiredLocation == cornerLocation.topRight || desiredLocation == cornerLocation.bottomRight) {
                return -MARKER_SIZE/2;
            } else {
                return -menuWidth - (MARKER_SIZE/2);
            }
        }
    }

    // Returns whether to input a negative positive, or zero menu height based on the marker position
    private double getRelativeMenuHeightDistance(cornerLocation markerLocation, cornerLocation desiredLocation) {

        if (markerLocation == cornerLocation.topLeft || markerLocation == cornerLocation.topRight) {
            if (desiredLocation == cornerLocation.bottomLeft || desiredLocation == cornerLocation.bottomRight) {
                return -menuHeight - MARKER_SIZE/2;
            } else {
                return -MARKER_SIZE/2;
            }
        } else {
            if (desiredLocation == cornerLocation.bottomLeft || desiredLocation == cornerLocation.bottomRight) {
                return MARKER_SIZE/2;
            } else {
                return menuHeight + MARKER_SIZE/2;
            }
        }
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

    public void lockCornerPoints() {
	    clearMenuKnowledge();
	    lockedCornerState = true;
    }

    public void unlockCornerPoints() {
	    lockedCornerState = false;
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
