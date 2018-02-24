package com.watvision.mainapp;

import android.content.Context;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;

import org.opencv.android.JavaCameraView;
import org.opencv.core.Mat;

import java.util.Locale;

// WatVision - Created 2018-01-13
// Identifies a screen, and its elements and then determines if a user is pointing to a screen
// element. Will then read out screen element information.
// This is the main app behind the WatVision device, and is responsible for the higher level
// functions of the app (when to input the identified screen, what to do with the finger data,
// etc.)

public class WatVision {

    private TextToSpeech textSpeaker;
    private Context applicationContext;
    private MenuAndFingerTracking tracker;
    private Screen currentScreen;

    private Boolean readingTextNoInterrupt;

    // Not a fan of this implementation... But it will do for a demo!
    private Boolean narratorSpoken;

    // Janky code as a means to not read out things multiple times
    String lastReadText;

    private ScreenAnalyzer screenAnalyzer;

    // The current state of the program, used to indicate which screen resolution is being used
    private watVisionState currentState;

    // Tag used for debugging
    final static String TAG = "WatVision";

    // Context of main app
    Context mainContext;

    // Access to the camera
    private JavaCameraView camera;

    public static int lowResMaxWidth = 1000;
    public static int lowResMaxHeight = 1000;
    public static int highResMaxWidth = 1500;
    public static int highResMaxHeight = 1500;

    // The state enum
    private enum watVisionState {
        // Lower resolution state, when we are just findiing the aruco markers
        TRACKING_MENU,
        // Higher resolution state, used to get clearer picture for OCR, etc.
        OBTAINING_OCR_SCREEN,
        // Lower resolution state used to get the differences picture
        OBTAINING_SCREEN_FEATURES
    };

    // Constructor
    public WatVision(Context appContext) {

        applicationContext = appContext;

        tracker = new MenuAndFingerTracking();

        currentScreen = new Screen();

        lastReadText = "Initiate";

        readingTextNoInterrupt = false;

        textSpeaker = new TextToSpeech(applicationContext, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    textSpeaker.setLanguage(Locale.ENGLISH);
                    readTextNoInterrupt("Please point your camera at the screen. Directions will be given when a corner is seen.");
                }
            }
        });

        narratorSpoken = false;

        screenAnalyzer = new ScreenAnalyzer(appContext);

        currentState = watVisionState.TRACKING_MENU;

        camera = null;

        mainContext = appContext;
    }

    // textSpeaker needs to be paused when the app is paused
    public void pause(){
        if(textSpeaker !=null){
            textSpeaker.stop();
            textSpeaker.shutdown();
        }
    }

    // Called when a frame is inputted into the system. Outputs tracking info, and takes in a frame
    // Also handles narrator events (detecting when to speak)
    public MenuAndFingerTracking.menuAndFingerInfo getMenuAndFingerInfo(Mat inputtedFrame) {

        MenuAndFingerTracking.menuAndFingerInfo resultInfo = tracker.grabMenuAndFingerInfo(inputtedFrame);

        if ( resultInfo.menuTracked ) {

            Log.d(TAG,"Menu is tracked");

            // What happens when we need to obtain the screen for OCR purposes
            if (currentState == watVisionState.OBTAINING_OCR_SCREEN) {
                readTextNoInterrupt("Menu Found!");
                screenAnalyzer.analyzePhoto(tracker.resultImage);
                currentScreen.GenerateScreen(screenAnalyzer.textBlocks, tracker.resultImage.width(),
                        tracker.resultImage.height());
                switchStates(watVisionState.OBTAINING_SCREEN_FEATURES);
            // What happens when we need to obtain the screen for feature purposes,
            // This is separate since the screen resolution is different!
            } else if (currentState == watVisionState.OBTAINING_SCREEN_FEATURES) {
                screenAnalyzer.setKnownScreen(tracker.resultImage);
                switchStates(watVisionState.TRACKING_MENU);
            // What happens if we are just doing normal tracking
            } else if (currentState == watVisionState.TRACKING_MENU) {
                Boolean isSameMenu = screenAnalyzer.isSameScreen(tracker.resultImage);

                // If the screen changed then we should capture the new screen
                if (!isSameMenu) {
                        switchStates(watVisionState.OBTAINING_OCR_SCREEN);
                }

                if ( resultInfo.fingerData.tracked ) {

                    ScreenElement selectedElement = currentScreen.GetElementAtPoint(
                            resultInfo.fingerData.screenLocation.x,
                            resultInfo.fingerData.screenLocation.y);

                    if (selectedElement != null) {
                        String selectedElementText = selectedElement.GetElementDescription();

                        readText(selectedElementText);

                    } else {
                        // If I move off the element this resets the last read text.
                        lastReadText = "No Element Present";
                    }
                }
            }

        } else {
            narrateScreenLocation(resultInfo);
        }

        return resultInfo;
    }

    private void readText(String textToRead) {
        if (!readingTextNoInterrupt) {
            if (!textToRead.equals(lastReadText)) {
                textSpeaker.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null);
                lastReadText = textToRead;
            }
        } else {
                if (!textSpeaker.isSpeaking()) {
                    // This is ultimately messy and should be replaced. A task to do so is in Jira.
                    if (narratorSpoken) {
                        readingTextNoInterrupt = false;
                        readText(textToRead);
                    }
                } else {
                    narratorSpoken = true;
                }
        }
    }

    private void readTextNoInterrupt(String textToRead) {

        if (!textToRead.equals(lastReadText) && !readingTextNoInterrupt) {
            readingTextNoInterrupt = true;
            textSpeaker.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null);
            lastReadText = textToRead;
        }
    }

    private void narrateScreenLocation(MenuAndFingerTracking.menuAndFingerInfo menuInfo) {

        String locateString = "";

        if (menuInfo.topLeftTracked) {
            if (menuInfo.bottomLeftTracked) {
                locateString = "Right";
            } else if (menuInfo.topRightTracked) {
                locateString = "Down";
            } else {
                locateString = "Down and Right";
            }
        } else if (menuInfo.bottomRightTracked) {
            if (menuInfo.bottomLeftTracked) {
                locateString = "Up";
            } else if (menuInfo.topRightTracked) {
                locateString = "Left";
            } else {
                locateString = "Up and Left";
            }
        } else if (menuInfo.topRightTracked) {
            locateString = "Down and Left";
        } else if (menuInfo.bottomLeftTracked) {
            locateString = "Up and Right";
        } else {
            locateString = "No screen found";
        }

        readText(locateString);
    }

    public void setJavaCameraViewRef(JavaCameraView inputCamera) {
        camera = inputCamera;
    }

    private void switchStates(watVisionState inputState) {

        Handler mainHandler;
        Runnable myRunnable;

        Log.d(TAG,"Entering state: " + inputState.toString());

        switch (inputState) {
            case TRACKING_MENU:

                break;
            case OBTAINING_OCR_SCREEN:

                // Get a handler that can be used to post to the main thread
                mainHandler = new Handler(mainContext.getMainLooper());

                myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        camera.disableView();
                        camera.setMaxFrameSize(highResMaxWidth,highResMaxHeight);
                        camera.enableView();
                    } // This is your code
                };
                mainHandler.post(myRunnable);
                tracker.clearMenuKnowledge();
                break;
            case OBTAINING_SCREEN_FEATURES:

                // Get a handler that can be used to post to the main thread
                mainHandler = new Handler(mainContext.getMainLooper());

                myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        camera.disableView();
                        camera.setMaxFrameSize(highResMaxWidth,highResMaxHeight);
                        camera.enableView();
                    } // This is your code
                };
                mainHandler.post(myRunnable);
                tracker.clearMenuKnowledge();
                break;
            default:
                break;
        }

        currentState = inputState;

    }

    public Mat getResultImage() {
        return screenAnalyzer.resultImage;
    }

    public Mat getHighlightedImage() {
        return tracker.highlightedImage;
    }

    public Mat getScreenSimilarityImage() { return screenAnalyzer.prevIdentifiedScreen; }


}
