package com.watvision.mainapp;

import android.content.Context;
import android.speech.tts.TextToSpeech;

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

    // Janky code as a means to not read out things multiple times
    String lastReadText;

    // Constructor
    public WatVision(Context appContext) {

        applicationContext = appContext;

        textSpeaker = new TextToSpeech(applicationContext, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    textSpeaker.setLanguage(Locale.ENGLISH);
                }
            }
        });

        tracker = new MenuAndFingerTracking();

        currentScreen = new Screen();

        // Some sample test data will be replaced later, useful now for testing.
        ScreenElement elementA = new ScreenElement(0,0,0.2,0.2,"Upper Left");
        ScreenElement elementB = new ScreenElement(0.8,0.0,0.2,0.2,"Upper Right");
        ScreenElement elementC = new ScreenElement(0.8,0.8,0.2,0.2,"Bottom Right");
        ScreenElement elementD = new ScreenElement(0,0.8,0.2,0.2,"Bottom Left");
        ScreenElement elementE = new ScreenElement(0.4,0.4,0.2,0.2,"Center");

        currentScreen.getAllElements().add(elementA);
        currentScreen.getAllElements().add(elementB);
        currentScreen.getAllElements().add(elementC);
        currentScreen.getAllElements().add(elementD);
        currentScreen.getAllElements().add(elementE);

        // End of sample test data

        lastReadText = "Initiate";

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

            if ( resultInfo.fingerData.tracked ) {

                ScreenElement selectedElement = currentScreen.GetElementAtPoint(
                        resultInfo.fingerData.screenLocation.x,
                        resultInfo.fingerData.screenLocation.y);

                if (selectedElement != null) {
                    String selectedElementText = selectedElement.GetElementDescription();

                    if (selectedElementText != lastReadText) {
                        readText(selectedElement.GetElementDescription());
                        lastReadText = selectedElement.GetElementDescription();
                    }
                } else {
                    // If I move off the element this resets the last read text.
                    lastReadText = "Null Text";
                }

            }

        }

        return resultInfo;
    }

    private void readText(String textToRead) {
        textSpeaker.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null);
    }

    public Mat getResultImage() {
        return tracker.resultImage;
    }

    public Mat getHighlightedImage() {
        return tracker.highlightedImage;
    }


}
