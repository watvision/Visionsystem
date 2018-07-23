package com.watvision.mainapp;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;

import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

// Screen Analyzer - Created 2018-02-05
// Identifies all screen elements for a given screen

public class ScreenAnalyzer {

    private Vision.Builder visionBuilder;
    private Vision vision;
    private Context appContext;
    // TAG used for debugging purposes
    private static final String TAG = "ScreenAnalyzer";

    // This should be read only. Maybe use a getter? For the future!
    public SparseArray<TextBlock> textBlocks;

    public Mat resultImage;

    // A previous reference to the identified screen, used to determine if we are looking at
    // a new screen or at the old one
    public Mat prevIdentifiedScreen;

    // Previously known screen width and height
    private int screenWidth;
    private int screenHeight;

    // TODO: Fix this hack!!
    private int screenWidth_OCR;
    private int screenHeight_OCR;

    // Identified screen keyPoints
    private List<KeyPoint> screenKeyPoints;

    private MatOfKeyPoint screenKeyPointsMat;

    // Previously identified menu info
    MenuInfo prevMenuInfo;

    private class MenuInfo {

        private boolean redPower;
        private boolean greenPower;
        private boolean bluePower;

        public MenuInfo() {
            redPower = false;
            greenPower = false;
            bluePower = false;
        }

        public MenuInfo(boolean inputRed, boolean inputGreen, boolean inputBlue) {
            redPower = inputRed;
            greenPower = inputGreen;
            bluePower = inputBlue;
        }

        // Input menu is always the previous menu
        public Boolean compareColours(MenuInfo inputMenu) {

            boolean redMatch = inputMenu.redPower == this.redPower;
            boolean greenMatch = inputMenu.greenPower == this.greenPower;
            boolean blueMatch = inputMenu.bluePower == this.bluePower;

            ArrayList<Boolean> trueCheckList = new ArrayList<>();

            // Only check if there's a difference on states that were true in the previous menu
            // i.e: only checking if a colour disappears not if one gets added
            if (inputMenu.redPower) {
                trueCheckList.add(redMatch);
            }
            if (inputMenu.greenPower) {
                trueCheckList.add(greenMatch);
            }
            if (inputMenu.bluePower) {
                trueCheckList.add(blueMatch);
            }

            if (trueCheckList.size() == 0) {
                return false;
            } else {
                Boolean returnValue = true;
                for (int i = 0; i < trueCheckList.size(); i++) {
                    if (trueCheckList.get(i) == false) {
                        returnValue = false;
                    }
                }
                return returnValue;
            }
        }

        public void logDifferences(MenuInfo inputMenu) {
            Log.d(TAG,"Logging differences");
            if (inputMenu.redPower != this.redPower) {
                Log.d(TAG,"Input red: " + inputMenu.redPower + " This red: " + this.redPower);
            }
            if (inputMenu.greenPower != this.greenPower) {
                Log.d(TAG,"Input green: " + inputMenu.greenPower + " This green: " + this.greenPower);
            }
            if (inputMenu.bluePower != this.bluePower) {
                Log.d(TAG,"Input blue: " + inputMenu.bluePower + " This blue: " + this.bluePower);
            }
        }

        public boolean isChangedColour(MenuInfo inputMenu) {

            return (//(!inputMenu.redPower && this.redPower) ||
                    (!inputMenu.greenPower && this.greenPower) ||
                    (!inputMenu.bluePower && this.bluePower));
        }
    }



    public ScreenAnalyzer(Context inAppContext) {

        visionBuilder = new Vision.Builder(
                new NetHttpTransport(),
                new AndroidJsonFactory(),
                null);

        visionBuilder.setVisionRequestInitializer(
                new VisionRequestInitializer("YOUR_API_KEY_HERE"));


        visionBuilder.build();

        vision = visionBuilder.build();

        appContext = inAppContext;

        resultImage = Mat.zeros(800,800,16);

        prevIdentifiedScreen = null;
        screenWidth = 0;
        screenHeight = 0;

        screenWidth_OCR = 0;
        screenHeight_OCR = 0;

        prevMenuInfo = null;
    }

    public void analyzePhoto(Mat inputMat) {
        Bitmap bm = Bitmap.createBitmap(inputMat.cols(), inputMat.rows(), Bitmap.Config.ARGB_8888);
        org.opencv.android.Utils.matToBitmap(inputMat, bm);

        analyzePhoto(bm);

        resultImage = inputMat.clone();
    }

    public void analyzePhoto(Bitmap inputBitmap) {

        Log.i(TAG,"Starting Analyzing screen");

        if(inputBitmap != null) {

            TextRecognizer textRecognizer = new TextRecognizer.Builder(appContext).build();

            if(!textRecognizer.isOperational()) {
                // Note: The first time that an app using a Vision API is installed on a
                // device, GMS will download a native libraries to the device in order to do detection.
                // Usually this completes before the app is run for the first time.  But if that
                // download has not yet completed, then the above call will not detect any text,
                // barcodes, or faces.
                // isOperational() can be used to check if the required native libraries are currently
                // available.  The detectors will automatically become operational once the library
                // downloads complete on device.
                Log.w(TAG, "Detector dependencies are not yet available.");

                // Check for low storage.  If there is low storage, the native library will not be
                // downloaded, so detection will not become operational.
                IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
                boolean hasLowStorage = appContext.registerReceiver(null, lowstorageFilter) != null;

                if (hasLowStorage) {
                    Toast.makeText(appContext,"Low Storage", Toast.LENGTH_LONG).show();
                    Log.w(TAG, "Low Storage");
                }
            }

            Frame imageFrame = new Frame.Builder()
                    .setBitmap(inputBitmap)
                    .build();

            textBlocks = textRecognizer.detect(imageFrame);

            Log.w(TAG,"Detected " + textBlocks.size() + " text objects");

            screenWidth_OCR = inputBitmap.getWidth();
            screenHeight_OCR = inputBitmap.getHeight();

            for (int i = 0; i < textBlocks.size(); i++) {
                TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));

                Log.i(TAG, textBlock.getValue());
            }
        }

    }

    public void highlightTextOnResultImage() {

        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));
            // Do something with value

            android.graphics.Rect textBoundingRect = textBlock.getBoundingBox();

            // Always gonna give 4 points according to the API so it's okay to hard code some stuff
            Point[] textCornerPoints = textBlock.getCornerPoints();

            org.opencv.core.Point[] openCVPointList = new org.opencv.core.Point[4];

            for (int j = 0; j < 4; j++) {
                openCVPointList[j] = new org.opencv.core.Point(textCornerPoints[j].x, textCornerPoints[j].y);
            }

            for (int j = 0; j < 4; j++) {
                Imgproc.line(resultImage,openCVPointList[j],openCVPointList[(j+1)%4], new Scalar(255,0,0), 3);
            }

        }
    }

    public void highlightTextOnResultImage(ArrayList<ScreenElement> elements) {

        for (int i = 0; i < elements.size(); i++) {
            ScreenElement visitor = elements.get(i);

            org.opencv.core.Point[] openCVPointList = new org.opencv.core.Point[4];

            double leftX = visitor.getX_base() * screenWidth_OCR;
            double topY = visitor.getY_base() * screenHeight_OCR;
            double rightX = (visitor.getX_base() + visitor.getX_Width())* screenWidth_OCR;
            double bottomY = (visitor.getY_base() + visitor.getY_length()) * screenHeight_OCR;

            openCVPointList[0] = new org.opencv.core.Point(leftX,topY);
            openCVPointList[1] = new org.opencv.core.Point(rightX,topY);
            openCVPointList[2] = new org.opencv.core.Point(rightX,bottomY);
            openCVPointList[3] = new org.opencv.core.Point(leftX,bottomY);

            for (int j = 0; j < 4; j++) {
                Imgproc.line(resultImage,openCVPointList[j],openCVPointList[(j+1)%4], new Scalar(255,0,0), 3);
            }

        }
    }

    public Boolean isSameScreen(Mat inputScreen) {

        // If we don't know what the previous screen looked like then it must be different
        if (prevIdentifiedScreen == null) {
            return false;
        }

        // Number of points that are similar to the identified screen
        double similarPointPercentage = 0.2;

        // Number of points that are different to the identified screen
        double differentPointPercentage = 0.8;


        // Do some other magic to determine if screen is the same

        // If some percentage of the points are in a similar place then it's probably the same screen
        int numberOfMatchesForSuccess = (int)(screenKeyPoints.size() * similarPointPercentage);

        FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        detector.detect(inputScreen,keypoints1);

        List<KeyPoint> inputKeyPoints =  keypoints1.toList();

        int similarPointSum = 0;
        int nonSimilarPointSum = 0;

        for (int i = 0; i < inputKeyPoints.size(); i++) {
            // If the point is within 5% of the width of the screen
            if (pointIsNearPointList(inputKeyPoints.get(i),screenWidth*0.05)) {
                similarPointSum++;
            } else {
                nonSimilarPointSum++;
            }
        }

        // If we are less than the number of matches for success, or there are tons of extra
        // points that are incorrect
        if (similarPointSum < numberOfMatchesForSuccess || nonSimilarPointSum > ( screenKeyPoints.size() * differentPointPercentage) ) {
            Log.d(TAG,"Is NOT same screen," +
                    "did not find enough similar points. similar points: " + similarPointSum +
                    " total input points: " + inputKeyPoints.size());

            if (similarPointSum < numberOfMatchesForSuccess) {
                Log.d(TAG,"Failed on similar points");
            } else {
                Log.d(TAG,"Failed on different points");
            }

            return false;
        } else {
            Log.d(TAG,"Is same screen with: " + similarPointSum + " matches out of "
            + inputKeyPoints.size() + " input points. Prev screen total points: " + screenKeyPoints.size());
            return true;
        }

    }

    boolean isSameScreenColour(Mat inputScreen) {

        if (prevMenuInfo == null) {
            return false;
        }

        MenuInfo identifiedScreenInfo = getMenuInfoFromScreen(inputScreen, false);

        if (!identifiedScreenInfo.isChangedColour(prevMenuInfo)) {
            return true;
        } else {
            Log.d(TAG,"New screen detected");
            identifiedScreenInfo.logDifferences(prevMenuInfo);
            getMenuInfoFromScreen(inputScreen, true);
            return false;
        }
    }

    public void clearKnownScreen() {
        prevIdentifiedScreen = null;

        screenKeyPoints = null;

        prevMenuInfo = null;

        screenWidth = 0;
        screenHeight = 0;

    }

    public void setKnownScreen(Mat inputScreen) {
        Log.d(TAG,"Setting known screen based on screen width: " + inputScreen.width()
                + " screen height: " + inputScreen.height());
        prevIdentifiedScreen = inputScreen.clone();
        FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        detector.detect(inputScreen,keypoints1);

        screenKeyPoints = keypoints1.toList();
        screenKeyPointsMat = keypoints1;

        screenWidth = inputScreen.width();
        screenHeight = inputScreen.height();

        Mat inputRGB = new Mat();
        Imgproc.cvtColor(prevIdentifiedScreen,inputRGB,Imgproc.COLOR_RGBA2RGB);

        Features2d.drawKeypoints(inputRGB,screenKeyPointsMat,prevIdentifiedScreen);
    }

    public void setKnownScreenColour(Mat inputScreen) {
        prevMenuInfo = getMenuInfoFromScreen(inputScreen, true);
        highlightColourInfo(prevMenuInfo);
    }

    private void highlightColourInfo(MenuInfo inputInfo) {

        double point1 = prevIdentifiedScreen.width()*0.33;
        double point2 = prevIdentifiedScreen.width()*0.66;

        if (inputInfo.redPower) {
            Imgproc.line(prevIdentifiedScreen,new org.opencv.core.Point(0,0),new org.opencv.core.Point(point1,0),new Scalar(255,0,0),3);
        }
        if (inputInfo.greenPower) {
            Imgproc.line(prevIdentifiedScreen,new org.opencv.core.Point(point1,0),new org.opencv.core.Point(point2,0),new Scalar(0,255,0),3);
        }
        if (inputInfo.bluePower) {
            Imgproc.line(prevIdentifiedScreen,new org.opencv.core.Point(point2,0),new org.opencv.core.Point(prevIdentifiedScreen.width(),0),new Scalar(0,0,255),3);
        }

    }

    private MenuInfo getMenuInfoFromScreen(Mat inputScreen, boolean log) {
        Mat RGBImage = new Mat();
        Mat blurImage = new Mat();

        Imgproc.cvtColor(inputScreen,RGBImage,Imgproc.COLOR_RGBA2RGB);

        Imgproc.blur(RGBImage,blurImage,new Size(5,5));

        Boolean bluePower = false;
        Boolean redPower = false;
        Boolean greenPower = false;

        int numBluePower = 0;
        int numRedPower = 0;
        int numGreenPower = 0;

        double compareMult = 1.25;
        double minPixelValue = 100;
        int pixelTolerance = 20;

        for (int i = 0; i < blurImage.width(); i+=2) {
            for (int j = 0; j < blurImage.height(); j+=2) {
                double[] pixelValues = blurImage.get(j,i);

                if ((pixelValues[0] > minPixelValue) && (pixelValues[1] > minPixelValue) && (pixelValues[2] > minPixelValue)) {
                    if (((pixelValues[0] > pixelValues[1]*compareMult) && (pixelValues[0] > pixelValues[2]*compareMult))) {
                        numRedPower++;
                    }
                    if (((pixelValues[1] > pixelValues[0]*compareMult) && (pixelValues[1] > pixelValues[2]*compareMult))) {
                        numGreenPower++;
                    }
                    if (((pixelValues[2] > pixelValues[0]*compareMult) && (pixelValues[2] > pixelValues[1]*compareMult))) {
                        numBluePower++;
                    }
                }
            }
        }

        if (log) {
            Log.d(TAG,"Screen red power: " + numRedPower + " green power: " + numGreenPower + " blue power: " + numBluePower);
        }

        if (numRedPower > pixelTolerance) {
            redPower = true;
        }
        if (numGreenPower > pixelTolerance) {
            greenPower = true;
        }
        if (numBluePower > pixelTolerance) {
            bluePower = true;
        }

        MenuInfo returnInfo = new MenuInfo(redPower,greenPower,bluePower);
        return returnInfo;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    private Boolean pointIsNearPointList(KeyPoint a, double tolerance) {
        for (int i = 0; i < screenKeyPoints.size(); i++) {
            if ((a.pt.x) > (screenKeyPoints.get(i).pt.x - tolerance) && (a.pt.x) < (screenKeyPoints.get(i).pt.x + tolerance)
                    && (a.pt.y) > (screenKeyPoints.get(i).pt.y - tolerance) && (a.pt.y) < (screenKeyPoints.get(i).pt.y + tolerance)) {
                return true;
            }
        }

        return false;
    }

}
