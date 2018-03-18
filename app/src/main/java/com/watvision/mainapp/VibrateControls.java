package com.watvision.mainapp;

import android.content.Context;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;

import org.opencv.core.Point;

import java.util.ArrayList;

public class VibrateControls {

    boolean BTEnable;
    Vibrator phoneVib;
    int iter;
    int iter2;

    // Tag for debugging
    private static final String TAG = "VibrateControls";

    // Intensity settings for local vibration
    private int[] phoneInt = {0, 350, 300, 250, 200, 150, 100};
    private int[] ringInt = {0, 63, 62, 61, 60, 59, 58};
    private int currInt = 0;

    // Bluetooth services
    WatBlueToothService blueToothService;

    int[][] proxField;
    int scaleW;
    int scaleH;

    public class FieldElement {
        public int x1, x2, y1, y2, w;
        public FieldElement(int X1, int Y1, int X2, int Y2, int W) {
            x1 = X1;
            y1 = Y1;
            x2 = X2;
            y2 = Y2;
            w = W;
        }

        public int[][] borderFill(int[][] proxField, int maxWidth, int maxHeight) {
            for(int x = x1; x <= x2; x++) {
                if(x < 0 || x >= maxWidth)
                    continue;
                if(y1 >= 0 && proxField[x][y1] < w)
                    proxField[x][y1] = w;
                if(y2 < maxHeight && proxField[x][y2] < w)
                    proxField[x][y2] = w;
            }
            for(int y = y1+1; y <= y2-1; y++) {
                if(y < 0 || y >= maxHeight)
                    continue;
                if(x1 >= 0 && proxField[x1][y] < w)
                    proxField[x1][y] = w;
                if(x2 < maxWidth && proxField[x2][y] < w)
                    proxField[x2][y] = w;
            }
            return proxField;
        }

        public void expandOneStep() {
            x1--;
            y1--;
            x2++;
            y2++;
            w--;
        }
    }

    // Constructor
    public VibrateControls(Context appContext, WatBlueToothService bts) {
        BTEnable = false; //to change when bluetooth gets integrated
        phoneVib = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        blueToothService = bts;
        iter = 0;
        iter2 = 0;
        Log.i(TAG, "Vibrate Controls setup successfully");
    }

    // Test function for local vibrate
    public void testvibrate(boolean start) {
        if (blueToothService.isConnected() && start) {
            blueToothService.vibrate(ringInt[iter2]);
            iter2++;
            if (iter2 > 6)
                iter2 = 0;
            phoneVib.cancel();
        } else {
            if (start) {
                long[] timings = {phoneInt[iter], phoneInt[iter], phoneInt[iter], phoneInt[iter], 200};
                int[] amp = {0, 255, 0, 255, 0};
                phoneVib.vibrate(VibrationEffect.createWaveform(timings, amp, 0));
            } else {
                phoneVib.cancel();
                iter++;
                if (iter > 6)
                    iter = 0;
            }
        }
    }

    // Main vibration call
    public void vibrate(Point finger) {
        int i = 0;
        if(finger.x >= 0 && finger.x < 1 && finger.y >= 0 && finger.y < 1) {
            int x = (int)(finger.x*scaleW);
            int y = (int)(finger.y*scaleH);
            i = proxField[x][y]%7;
        }

        if(i != currInt) {
            currInt = i;
            if (blueToothService.isConnected()) {
                blueToothService.vibrate(currInt);
            } else {
                long[] timings = {phoneInt[currInt], phoneInt[currInt], phoneInt[currInt], phoneInt[currInt], 200};
                int[] amp = {0, 255, 0, 255, 0};
                phoneVib.vibrate(VibrationEffect.createWaveform(timings, amp, 0));
            }
        }
    }

    public void generateProximityField(ArrayList<ScreenElement> elements, int screenWidth, int screenHeight) {
        Log.i(TAG, "Generating proximity field");
        Log.i(TAG, "Screen height: " + screenHeight + " and screen width: " + screenWidth);
        scaleW = screenWidth/10;
        scaleH = screenHeight/10;
        proxField = new int[scaleW][scaleH];
        ArrayList<FieldElement> borderExpansionList = new ArrayList<>();
        for(ScreenElement e: elements) {
            int x1 = (int) (e.getX_base()*scaleW);
            int y1 = (int) (e.getY_base()*scaleH);
            int x2 = (int) (x1 + e.getX_Width()*scaleW);
            int y2 = (int) (y1 + e.getY_length()*scaleH);

            // Verify that the element isn't hanging off the screen in either direction
            if (x2 >= scaleW) {
                x2 = (scaleW - 1);
            }
            if (y2 >= scaleH) {
                y2 = (scaleH - 1);
            }
            if (x1 < 0) {
                x1 = 0;
            }
            if (y1 < 0) {
                y1 = 0;
            }

            for(int i = x1; i <= x2; i++) {
                for(int j = y1; j <= y2; j++) {
                    proxField[i][j] = 7;
                }
            }
            borderExpansionList.add(new FieldElement(x1-1, y1-1, x2+1, y2+1, 6));
        }
        borderExpansion(borderExpansionList, scaleW, scaleH);
        Log.i(TAG, "Field generated");
    }

    private void borderExpansion(ArrayList<FieldElement> list, int maxWidth, int maxHeight) {
        while(list.size() > 0) {
            for(int i = 0; i < list.size(); i++) {
                proxField = list.get(i).borderFill(proxField, maxWidth, maxHeight);
                if(list.get(i).w-1 > 0) {
                    list.get(i).expandOneStep();
                } else {
                    list.remove(i);
                }
            }
        }
        Log.i(TAG, "Field expansion complete");
    }


}