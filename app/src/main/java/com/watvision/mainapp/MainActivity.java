package com.watvision.mainapp;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

import java.util.List;

// Main Activity - Created 2018-01-13
// Initiates the Camera, and other UI elements. This class deals with everything related to the
// Android App. Think of it as a main class

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    // TAG used for debugging purposes
    private static final String TAG = "MainActivity";
    private WatVision visionSystem;

    JavaCameraViewExd javaCameraView;
    TextView visionOutputText;
    TextView bluetoothText;

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

    // Used for requesting location for bluetooth
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private final static int REQUEST_ENABLE_BT = 1;

    // Variables used to control bluetooth
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothLeScanner btScanner;

    // Variable to control torch on camera
    Button torchSwitch;

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

        javaCameraView = (JavaCameraViewExd) findViewById(R.id.java_camera_view);
        javaCameraView.setVisibility(View.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);

        javaCameraView.setMaxFrameSize(visionSystem.lowResMaxWidth,visionSystem.lowResMaxHeight);

        javaCameraView.enableFpsMeter();

        visionOutputText = (TextView) findViewById(R.id.vision_output_text);
        bluetoothText = (TextView) findViewById(R.id.bluetooth_status);

        //Check we have location permission
        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }

        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();

        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            while(btManager.getAdapter() == null);
            btAdapter = btManager.getAdapter();
            while(btAdapter.getBluetoothLeScanner() == null);
            btScanner = btAdapter.getBluetoothLeScanner();
            SystemClock.sleep(500);
        }

        // The bluetooth message handler
        Handler visionSystemHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                // Gets the image task from the incoming Message object.
                Log.d(TAG,"Updating bluetooth text " + inputMessage.arg1 + " " + inputMessage.arg2);

                if (inputMessage.arg1 == WatBlueToothService.bluetoothStates.CONNECTED.ordinal()) {
                    bluetoothText.setText("Device connected");
                } else if (inputMessage.arg1 == WatBlueToothService.bluetoothStates.FAILED_TO_CONNECT.ordinal()) {
                    bluetoothText.setText("Device failed to connect");
                } else if (inputMessage.arg1 == WatBlueToothService.bluetoothStates.DISCONNECTED.ordinal()) {
                    bluetoothText.setText("Device disconnected");
                } else if (inputMessage.arg1 == WatBlueToothService.bluetoothStates.READY.ordinal()) {
                    bluetoothText.setText("Device ready");
                } else if (inputMessage.arg1 == -1) {
                    bluetoothText.setText("Initialized");
                }
            }
        };

        visionSystem = new WatVision(getApplicationContext(), btScanner, visionSystemHandler);

        visionSystem.setJavaCameraViewRef(javaCameraView);
        visionSystem.setParentActivity(this);

        // Enable the code below to allow for testvibrate testing
        // Touch and hold screen anywhere to call testvibrate
        LinearLayout rlayout = (LinearLayout) findViewById(R.id.mainscreen);
        rlayout.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    visionSystem.readOutAllScreenElements();
                }
                return true;
            }

        });

        final Button lockMenuButton = (Button) findViewById(R.id.lock_camera_button);

        lockMenuButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (lockMenuButton.getText().equals("X")) {
                    Log.d(TAG,"Locking menu");
                    visionSystem.lockCornerPoints();

                    runOnUiThread(new Runnable() {
                        public void run() {
                            lockMenuButton.setText("O");
                        }
                    });
                } else {
                    Log.d(TAG,"Unlocking menu");
                    visionSystem.unlockCornerPoints();

                    runOnUiThread(new Runnable() {
                        public void run() {
                            lockMenuButton.setText("X");
                        }
                    });
                }
            }
        });

        torchSwitch = (Button) findViewById(R.id.torch_on);

        torchSwitch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (torchSwitch.getText().equals("O")) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            unSetTorch();
                            torchSwitch.setText("X");
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            torchSwitch.setText("O");
                            setTorch();
                        }
                    });
                }
            }
        });
    }

    public void setTorch() {
        if (torchSwitch.getText().equals("O")) {
            Log.d(TAG,"Turning on torch");
            javaCameraView.setTorch();
        }
    }

    public void unSetTorch() {
        if (torchSwitch.getText().equals("O")) {
            Log.d(TAG,"Turning off torch");
            javaCameraView.unsetTorch();
        }
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
        visionSystem.resume();
    }

    public void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        visionSystem.destroy();
    }

    public void onCameraViewStarted(int width, int height) {

        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgbaF = new Mat(height, width, CvType.CV_8UC4);
        mRgbaT = new Mat(width, width, CvType.CV_8UC4);
        mGray = new Mat(width, width, CvType.CV_8UC1);

        if(javaCameraView.getTorchStatus())
            javaCameraView.setTorch();
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
            final Mat screenSimilarityImage;
            if (visionSystem.getScreenSimilarityImage() != null) {
                screenSimilarityImage = visionSystem.getScreenSimilarityImage().clone();
            } else {
                screenSimilarityImage = Mat.zeros(100,100,menuGrabbedImage.type());
            }
            final String displayText = visionSystem.lastReadText;

            Bitmap initial_bm;

            initial_bm = Bitmap.createBitmap(highlightedImage.cols(), highlightedImage.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(highlightedImage, initial_bm);

            final Bitmap highlight_bm = initial_bm.copy(initial_bm.getConfig(),true);

            initial_bm = Bitmap.createBitmap(menuGrabbedImage.cols(), menuGrabbedImage.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(menuGrabbedImage, initial_bm);

            final Bitmap result_image_bm = initial_bm.copy(initial_bm.getConfig(),true);

            initial_bm = Bitmap.createBitmap(screenSimilarityImage.cols(), screenSimilarityImage.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(screenSimilarityImage, initial_bm);

            final Bitmap screen_similarity_bm = initial_bm.copy(initial_bm.getConfig(),true);

            // Update images on UI
            runOnUiThread(new Runnable() {

                public void run() {
                    // find the imageview and draw it!
                    ImageView iv = (ImageView) findViewById(R.id.result_image_view);
                    iv.setImageBitmap(result_image_bm);

                    // find the imageview and draw it!
                    iv = (ImageView) findViewById(R.id.highlighted_image_view);
                    iv.setImageBitmap(highlight_bm);

                    iv = (ImageView) findViewById(R.id.screen_similarity_view);
                    iv.setImageBitmap(screen_similarity_bm);

                    visionOutputText.setText(displayText);

                    Log.i(TAG, "Updated Views");
                }
            });
        } else {
            Bitmap initial_bm;

            Mat resizedImage = new Mat(resultMat.rows(),(int) (resultMat.cols()*0.561165),resultMat.type());
            Imgproc.resize(resultMat, resizedImage, resizedImage.size(), 0, 0, Imgproc.INTER_CUBIC);

            initial_bm = Bitmap.createBitmap(resizedImage.cols(), resizedImage.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(resizedImage, initial_bm);

            final Bitmap highlight_bm = initial_bm.copy(initial_bm.getConfig(),true);

            // Update images on UI
            runOnUiThread(new Runnable() {

                public void run() {

                    // find the imageview and draw it!
                    ImageView iv = (ImageView) findViewById(R.id.highlighted_image_view);
                    iv.setImageBitmap(highlight_bm);

                }
            });
        }

        return resultMat;
    }
}
