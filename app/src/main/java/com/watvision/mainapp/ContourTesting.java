package com.watvision.mainapp;

import java.util.ArrayList;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;


// Contour Testing - Created 2018-01-13
// Outputs images that show contour information. Specifically contourNumImage shows the number of
// approximated point that each contour has, and ContourChildImage shows the number of children
// that each contour has. combinedContourInfo shows contours that would be positively identified
// by the MenuAndFingerTracking Class

public class ContourTesting {
	
	public Mat traceImage;
	public Mat contourNumImage;
	public Mat contourChildImage;
	public Mat resultImage;
	public Mat combinedContourInfo;
	
	public int maxContourNum;
	public int maxChildrenNum;
	
	public class contourInfo {
		public int contourIndex;
		public int contourPointSize;
		public int numChildren;
		
		contourInfo(int inputIndex, int inputPointSize, int inChildren) {
			contourIndex = inputIndex;
			contourPointSize = inputPointSize;
			numChildren = inChildren;
		}
	}

	public ContourTesting() {
		traceImage = new Mat();
		resultImage = Mat.zeros(800,800,16);
		contourNumImage = Mat.zeros(800,800,16);
		combinedContourInfo = Mat.zeros(800,800,16);
	}
	
	public void getFingerInfoFromFrame(Mat inputImage) {
		Mat greyImage = new Mat();
		Mat edgeImage = new Mat();
		
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		
		Mat hierarchy = new Mat();
		
		// Get Edged Image
		Imgproc.cvtColor(inputImage, greyImage, Imgproc.COLOR_RGB2GRAY);
		Imgproc.Canny(greyImage, edgeImage, 70, 210);
		
		traceImage = edgeImage;
		
		// Get contoured Image
		Imgproc.findContours(edgeImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
		
		ArrayList<Moments> momentList = new ArrayList<Moments>();
		ArrayList<Point> momentCenterList = new ArrayList<Point>();
		
		for (int i = 0; i < contours.size(); i++) {
			momentList.add(Imgproc.moments(contours.get(i),false));
			momentCenterList.add(new Point(momentList.get(i).m10/momentList.get(i).m00, momentList.get(i).m01 / momentList.get(i).m00));
		}
		
		ArrayList<contourInfo> contourInfoList = new ArrayList<contourInfo>();
		
		int maxContourValue = 0;
		int maxChildrenValue = 0;
		
		for( int i = 0; i < contours.size(); i++ )
		{	
	        //Find the approximated polygon of the contour we are examining
			MatOfPoint2f resultingApproximation = new MatOfPoint2f();
			MatOfPoint2f contour2fApproximation = new MatOfPoint2f();
			
			contours.get(i).convertTo(contour2fApproximation, CvType.CV_32FC2);
			Imgproc.approxPolyDP(contour2fApproximation, resultingApproximation, Imgproc.arcLength(contour2fApproximation, true)*0.02, true); 
			
			int numberOfChildren = getChildNumberFromContour(hierarchy,i);
			
			if (resultingApproximation.size().height > maxContourValue) {
				maxContourValue = (int) resultingApproximation.size().height;
			}
			
			if (numberOfChildren > maxChildrenValue) {
				maxChildrenValue = numberOfChildren;
			}
			
			contourInfoList.add(new contourInfo(i,(int)resultingApproximation.size().height,numberOfChildren));
		}
		
		contourNumImage = Mat.zeros(inputImage.height(), inputImage.width(), inputImage.type());
		
		contourChildImage = Mat.zeros(inputImage.height(), inputImage.width(), inputImage.type());

		combinedContourInfo = Mat.zeros(inputImage.height(), inputImage.width(), inputImage.type());



		for (int i = 0; i < contourInfoList.size(); i++) {

			contourInfo visitor = contourInfoList.get(i);
			int thickness = 1;

			// Draw contourNumbers
			Scalar outputColour = new Scalar(255, 255, 255);

			if (visitor.contourPointSize == 4) {
				outputColour = new Scalar(0, 0, 255);
			}

			if (visitor.contourPointSize == 3) {
				outputColour = new Scalar(0, 255, 0);
				thickness = 3;
			}

			Imgproc.drawContours(contourNumImage, contours, i, outputColour, thickness);
		}

		//Draw contour children
		for (int i = 0; i < contourInfoList.size(); i++) {

			contourInfo visitor = contourInfoList.get(i);
			Scalar outputColour = new Scalar(255,255,255);
			int thickness = 1;


			if (visitor.numChildren == 3) {
				outputColour = new Scalar(0,255,0);
				thickness = 3;
			}
			
			if (visitor.numChildren == 4) {
				outputColour = new Scalar(0,0,255);
			}
			
			if (visitor.numChildren >= 5) {
				outputColour = new Scalar(255,0,0);
			}
			
			Imgproc.drawContours(contourChildImage, contours, i, outputColour, thickness);
		}

		//Draw combined data children
		for (int i = 0; i < contourInfoList.size(); i++) {

			contourInfo visitor = contourInfoList.get(i);
			Scalar outputColour = new Scalar(255,255,255);
			int thickness = 1;


			if (visitor.numChildren == 3 && visitor.contourPointSize == 3) {
				outputColour = new Scalar(0,255,0);
				thickness = 3;
			}

			if (visitor.numChildren == 4 && visitor.contourPointSize == 4) {
				outputColour = new Scalar(0,0,255);
			}

			Imgproc.drawContours(combinedContourInfo, contours, i, outputColour, thickness);
		}
		
		maxContourNum = maxContourValue;
		maxChildrenNum = maxChildrenValue;
	}	
	
	private int getChildNumberFromContour(Mat hierarchy, int contourNumber) {
		int numberOfChildren = 0;
			
		int childValue = (int) hierarchy.get(0, contourNumber)[2];
		
		if (childValue != -1) {
			
			int firstSiblingValue = (int) hierarchy.get(0, childValue)[0];
			
			java.util.List<Integer> childrenChildNums = new ArrayList<Integer>();
			
			// Add a list of all sibling children numbers
			
			childrenChildNums.add(getChildNumberFromContour(hierarchy, childValue));
			while (firstSiblingValue != -1) {
				childrenChildNums.add(getChildNumberFromContour(hierarchy, firstSiblingValue));
				firstSiblingValue = (int) hierarchy.get(0, firstSiblingValue)[0];
			}
			
			// Get max child number from list of sibling child numbers
			for (int i = 0; i < childrenChildNums.size(); i++) {
				if (childrenChildNums.get(i) > numberOfChildren) {
					numberOfChildren = childrenChildNums.get(i);
				}
			}
			
			numberOfChildren++;
			
		}
		
		return numberOfChildren;
	}
	
}
