package com.watvision.mainapp;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import android.util.Log;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

import java.util.List;
import java.util.Vector;

import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;

// Main Activity - Created 2018-01-13
// Initiates the Camera, and other UI elements. This class deals with everything related to the
// Android App. Think of it as a main class

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    // TAG used for debugging purposes
    private static final String TAG = "MainActivity";
    private WatVision visionSystem;

    JavaCameraView javaCameraView;
    TextView visionOutputText;

    // Initialize OpenCV libraries
    static {
        System.loadLibrary("MyOpencvLibs");
    }

    BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS:
                    javaCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }

        }
    };

    // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
    Mat mRgba;
    Mat mRgbaF;
    Mat mRgbaT;

    Mat mGray;

    // Constructor
    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        javaCameraView = (JavaCameraView) findViewById(R.id.java_camera_view);
        javaCameraView.setVisibility(View.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);

        visionOutputText = (TextView) findViewById(R.id.vision_output_text);

        visionSystem = new WatVision(getApplicationContext());
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        if (visionSystem != null) visionSystem.pause();
    }

    @Override
    public void onResume()
    {
        // To do - Add visionSystem resume
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG,"OpenCV Loaded successfully");
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            Log.i(TAG,"OpenCV not loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallBack);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
    }

    public void onCameraViewStarted(int width, int height) {

        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgbaF = new Mat(height, width, CvType.CV_8UC4);
        mRgbaT = new Mat(width, width, CvType.CV_8UC4);
        mGray = new Mat(width, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }


    // This function is called when a new camera frame is received
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        // Some magic to flip the frame to the correct orientation
        mRgba = inputFrame.rgba();
        Mat mRgbaT = mRgba.t();
        Core.flip(mRgba.t(), mRgbaT, 1);
        Imgproc.resize(mRgbaT, mRgbaT, mRgba.size());

        Mat resultMat;

        // Code that can be swapped out with the below chunk of code to show just the
        // contour testing information
        /*
        ContourTesting testContours = new ContourTesting();

        testContours.getFingerInfoFromFrame(mRgbaT);

        resultMat = testContours.combinedContourInfo.clone();
        */


        // Some Native C Code as an example, makes mRgbaT become gray
        // OpenCVNative.convertGray(mRgbaT.getNativeObjAddr(), mGray.getNativeObjAddr());

        resultMat = mRgbaT;

        Log.d(TAG,"Screen width: " + resultMat.width() + " height: " + resultMat.height());

        // Get Menu Tracking Info
        MenuAndFingerTracking.menuAndFingerInfo trackingResult = visionSystem.getMenuAndFingerInfo(mRgbaT);

        if (trackingResult.menuTracked) {
            Log.i(TAG, "Some menu seen");


            if (trackingResult.fingerData.tracked) {
                Log.i(TAG, "FingerTracked");
            }

            final Mat menuGrabbedImage = visionSystem.getResultImage().clone();
            final Mat highlightedImage = visionSystem.getHighlightedImage().clone();
            final String displayText = visionSystem.lastReadText;

            // Update images on UI
            runOnUiThread(new Runnable() {
                private Mat sensordata = menuGrabbedImage;

                public void run() {
                    // convert to bitmap:
                    Bitmap bm = Bitmap.createBitmap(sensordata.cols(), sensordata.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(sensordata, bm);

                    // find the imageview and draw it!
                    ImageView iv = (ImageView) findViewById(R.id.result_image_view);
                    iv.setImageBitmap(bm);

                    bm = Bitmap.createBitmap(highlightedImage.cols(), highlightedImage.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(highlightedImage, bm);

                    // find the imageview and draw it!
                    iv = (ImageView) findViewById(R.id.highlighted_image_view);
                    iv.setImageBitmap(bm);

                    visionOutputText.setText(displayText);

                    Log.i(TAG, "Updated Views");

                }
            });
        }

        return resultMat;
    }
}
