package com.watvision.mainapp;

import android.app.Activity;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;

import org.opencv.android.JavaCameraView;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

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
    private JavaCameraViewExd camera;

    // VibrateController
    public VibrateControls Vibrate;

    public static int lowResMaxWidth = 800;
    public static int lowResMaxHeight = 800;
    public static int highResMaxWidth = 1500;
    public static int highResMaxHeight = 1500;

    // Bluetooth services
    WatBlueToothService blueToothService;

    // A request for a new screen has been raised
    private boolean newScreenRequestFlag;

    // A request for a screen readout has been raised
    private boolean screenReadoutFlag;

    // The parent activity
    MainActivity parentActivity;

    // Text to read out corresponding to element IDs
    private static String[] numberToPositionInList = {"First","Second","Third","Fourth","Fifth","Sixth"};

    // The state enum
    private enum watVisionState {
        // Lower resolution state, when we are just findiing the aruco markers
        TRACKING_MENU,
        // Higher resolution state, used to get clearer picture for OCR, etc.
        OBTAINING_OCR_SCREEN,
        // Lower resolution state used to get the differences picture
        OBTAINING_SCREEN_FEATURES,
        // Waiting State, not doing any outputs during this state
        WAITING_TO_CAPTURE_SCREEN,
        // Paused state before obtaining screen features to adjust aperture
        PAUSE_BEFORE_SCREEN_FEATURES
    };

    // Constructor
    public WatVision(Context appContext, BluetoothLeScanner inputScanner, Handler mainLoopHandler) {

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
                    readText("Please point your camera at the screen. Directions will be given when a corner is seen.");
                }
            }
        });

        // Increase text read rate
        textSpeaker.setSpeechRate((float)2.0);

        narratorSpoken = false;

        screenAnalyzer = new ScreenAnalyzer(appContext);

        currentState = watVisionState.TRACKING_MENU;

        camera = null;

        mainContext = appContext;

        blueToothService = new WatBlueToothService(inputScanner, mainContext, mainLoopHandler,this);

        blueToothService.InitiateConnection();

        Vibrate = new VibrateControls(appContext, blueToothService);

        newScreenRequestFlag = false;
        screenReadoutFlag = false;

        // Create repeating read task
        final Timer readTimer = new Timer();
        final TimerTask readTask = new TimerTask() {
            @Override
            public void run() {
                blueToothService.readButton();
            }
        };
        readTimer.scheduleAtFixedRate(readTask,2000,1000);
    }

    // textSpeaker needs to be paused when the app is paused
    public void pause(){
        if(textSpeaker !=null){
            textSpeaker.stop();
            textSpeaker.shutdown();
        }
        if (blueToothService != null) {
            blueToothService.pause();
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
                screenAnalyzer.analyzePhoto(tracker.resultImage);
                currentScreen.GenerateScreen(screenAnalyzer.textBlocks, tracker.resultImage.width(),
                        tracker.resultImage.height());
                screenAnalyzer.highlightTextOnResultImage(currentScreen.getAllElements());
                switchStates(watVisionState.PAUSE_BEFORE_SCREEN_FEATURES);
            // What happens when we need to obtain the screen for feature purposes,
            // This is separate since the screen resolution is different!
            } else if (currentState == watVisionState.OBTAINING_SCREEN_FEATURES) {
                screenAnalyzer.setKnownScreen(tracker.resultImage);
                screenAnalyzer.setKnownScreenColour(tracker.resultImage);

                Vibrate.generateProximityField(currentScreen.getAllElements(), tracker.resultImage.width(), tracker.resultImage.height());

                switchStates(watVisionState.TRACKING_MENU);
            // What happens if we are just doing normal tracking
            } else if (currentState == watVisionState.TRACKING_MENU) {
                Boolean isSameMenu = screenAnalyzer.isSameScreenColour(tracker.resultImage);

                // If the screen changed or we have a request to change then we should capture the new screen
                if (!isSameMenu || newScreenRequestFlag) {
                        switchStates(watVisionState.WAITING_TO_CAPTURE_SCREEN);
                }

                if (screenReadoutFlag) {
                    readOutAllScreenElements();
                    screenReadoutFlag = false;
                }

                if ( resultInfo.fingerData.tracked ) {
                    ScreenElement selectedElement = currentScreen.GetElementAtPoint(
                            resultInfo.fingerData.screenLocation.x,
                            resultInfo.fingerData.screenLocation.y);

                    Boolean onElement = false;

                    if (selectedElement != null) {
                        String selectedElementText = selectedElement.GetElementDescription();
                        onElement = true;

                        // If it is a new element
                        if (!selectedElementText.equals(lastReadText)) {
                            // Only if we are in the tracking state should we read out. (If we have switched states don't read!)
                            if (currentState == watVisionState.TRACKING_MENU) {
                                //blueToothService.Buzz();
                                readText(selectedElementText);
                            }
                        }

                    } else {
                        // If I move off the element this resets the last read text.
                        lastReadText = "No Element Present";
                    }

                    Vibrate.vibrate(resultInfo.fingerData.screenLocation, onElement);

                } else {
                    // If finger data is not tracked stop vibrating
                    Vibrate.stopVibrating();
                }
            } else if (currentState == watVisionState.WAITING_TO_CAPTURE_SCREEN
                    || currentState == watVisionState.PAUSE_BEFORE_SCREEN_FEATURES) {
                Vibrate.stopVibrating();
                // Do nothing during the waiting state
            }

        } else {
            // Narrate screen isn't in use anymore
            //narrateScreenLocation(resultInfo);
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

    // I've deprecated this function since the code doesn't apply anymore to changes
    /*
    // Narrate the screen based off the known corner locations
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
    */

    public void setJavaCameraViewRef(JavaCameraViewExd inputCamera) {
        camera = inputCamera;
    }

    public void setParentActivity(MainActivity inActivity) {
        parentActivity = inActivity;
    }

    private void switchStates(watVisionState inputState) {

        Handler mainHandler;
        Runnable myRunnable;

        Log.d(TAG,"Entering state: " + inputState.toString());

        Vibrate.stopVibrating();

        switch (inputState) {
            case TRACKING_MENU:

                // Get a handler that can be used to post to the main thread
                mainHandler = new Handler(mainContext.getMainLooper());

                myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        parentActivity.setTorch();
                    } // This is your code
                };
                mainHandler.post(myRunnable);

                readText("Menu analysis finished, please explore the menu");
                break;
            case OBTAINING_OCR_SCREEN:

                readText("Processing");

                tracker.clearMenuKnowledge();
                break;
            case OBTAINING_SCREEN_FEATURES:

                break;
            case WAITING_TO_CAPTURE_SCREEN:

                final int secondsToCountdown = 3;
                final int initialReadDelay = 3500;
                final int numberReadDelay = 500;

                readText("Please move hand off screen so new screen can be captured. Capturing in: ");
                newScreenRequestFlag = false;

                // Initiate the countdown
                for (int i = 0; i < secondsToCountdown; i++) {
                    final int countdownNumber = i;
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Log.d(TAG,"Reading some number");
                            readText(Integer.toString(secondsToCountdown - countdownNumber));
                        }
                    }, i*1000 + initialReadDelay);
                }


                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        switchStates(watVisionState.OBTAINING_OCR_SCREEN);
                    }
                }, secondsToCountdown*1000 + initialReadDelay + numberReadDelay);

                // Get a handler that can be used to post to the main thread
                mainHandler = new Handler(mainContext.getMainLooper());

                myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        camera.disableView();
                        camera.setMaxFrameSize(highResMaxWidth,highResMaxHeight);
                        camera.enableView();
                        parentActivity.unSetTorch();
                    } // This is your code
                };
                mainHandler.post(myRunnable);

                break;
            case PAUSE_BEFORE_SCREEN_FEATURES:
                // Get a handler that can be used to post to the main thread
                mainHandler = new Handler(mainContext.getMainLooper());

                myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        camera.disableView();
                        camera.setMaxFrameSize(lowResMaxWidth,lowResMaxHeight);
                        camera.enableView();
                    } // This is your code
                };
                mainHandler.post(myRunnable);
                tracker.clearMenuKnowledge();

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        switchStates(watVisionState.OBTAINING_SCREEN_FEATURES);
                    }
                }, 2000);
                break;
            default:
                break;
        }

        currentState = inputState;

    }

    public void requestNewScreen() {
        newScreenRequestFlag = true;
    }

    public void requestScreenReadout() {
        screenReadoutFlag = true;
    }

    public void destroy() {
        if (blueToothService != null) {
            blueToothService.destroy();
        }
    }

    public void resume() {
        if (blueToothService != null) {
            blueToothService.resume();
        }
    }

    public void lockCornerPoints() {
        tracker.lockCornerPoints();
    }

    public void unlockCornerPoints() {
        tracker.unlockCornerPoints();
    }

    public void readOutAllScreenElements() {
        ArrayList<ScreenElement> screenElements = currentScreen.getAllElements();
        Collections.sort(screenElements);
        Log.d(TAG,"Reading out all screen elements");

        for (int i = 0; i < screenElements.size(); i++) {
            String textToRead = numberToPositionInList[i] + " Text ";
            if (i == 0) {
                textSpeaker.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null);
            } else {
                textSpeaker.speak(textToRead, TextToSpeech.QUEUE_ADD, null);
            }
            textSpeaker.playSilentUtterance(200,TextToSpeech.QUEUE_ADD,null);
            textSpeaker.speak(screenElements.get(i).GetElementDescription(),TextToSpeech.QUEUE_ADD,null);
        }
    }

    public Mat getResultImage() {
        return screenAnalyzer.resultImage;
    }

    public Mat getHighlightedImage() {
        return tracker.highlightedImage;
    }

    public Mat getScreenSimilarityImage() { return screenAnalyzer.prevIdentifiedScreen; }


}
