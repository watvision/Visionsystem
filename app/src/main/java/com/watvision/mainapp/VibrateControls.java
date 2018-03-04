package com.watvision.mainapp;

import android.content.Context;
import android.os.Vibrator;
import android.os.VibrationEffect;

import org.opencv.core.Point;

import java.util.ArrayList;

public class VibrateControls {

    boolean BTEnable;
    Vibrator phoneVib;
    int iter;
    int iter2;

    // Intensity settings for local vibration
    private long[] intensity = {400, 350, 300, 250, 200, 150, 100};

    // Bluetooth services
    WatBlueToothService blueToothService;

    int[][] proxField;
    int scaleW;
    int scaleH;

    // Constructor
    public VibrateControls(Context appContext, WatBlueToothService bts) {
        BTEnable = false; //to change when bluetooth gets integrated
        phoneVib = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        blueToothService = bts;
        iter = 0;
        iter2 = 1;
    }

    // Test function for local vibrate
    public void testvibrate(boolean start) {
        if(start) {
            long[] timings = {intensity[iter], intensity[iter], intensity[iter], intensity[iter], 200};
            int[] amp = {0, 255, 0, 255, 0};
            phoneVib.vibrate(VibrationEffect.createWaveform(timings, amp, 0));
            if(blueToothService.isConnected()) {
                blueToothService.vibrate(iter2);
                iter2++;
                if(iter2 > 116)
                    iter2 = 1;
            }
        } else {
            phoneVib.cancel();
            iter++;
            if(iter > 6)
                iter = 0;
        }
    }

    // Main vibration call
    public void vibrate(Point finger) {
        int i = proxField[(int)(finger.x*scaleW)][(int)(finger.y*scaleH)];
        if(blueToothService.isConnected()) {
            blueToothService.vibrate(i);
        } else {
            long[] timings = {intensity[i], intensity[i], intensity[i], intensity[i], 200};
            int[] amp = {0, 255, 0, 255, 0};
            phoneVib.vibrate(VibrationEffect.createWaveform(timings, amp, 0));
        }
    }

    public void generateProximityField(ArrayList<ScreenElement> elements, int screenWidth, int screenHeight) {
        scaleW = screenWidth;
        scaleH = screenHeight;
        proxField = new int[screenWidth/10][screenHeight/10];
        ArrayList<FieldElement> borderExpansionList = new ArrayList<>();
        for(ScreenElement e: elements) {
            int x1 = (int)(e.getX1()*screenWidth/10);
            int y1 = (int)(e.getY1()*screenHeight/10);
            int x2 = (int)(e.getX2()*screenWidth/10);
            int y2 = (int)(e.getY2()*screenHeight/10);
            for(int i = x1; i < x2; i++) {
                for(int j = y1; j < y2; j++) {
                    proxField[i][j] = 7;
                }
            }
            borderExpansionList.add(new FieldElement(x1-1, y1-1, x2+1, y2+1, 6));
        }
        borderExpansion(borderExpansionList);
    }

    private void borderExpansion(ArrayList<FieldElement> list) {
        while(list.size() > 0) {
            for(int i = 0; i < list.size(); i++) {
                proxField = list.get(i).borderFill(proxField);
                if(list.get(i).w-1 > 0) {
                    list.get(i).expandOneStep();
                } else {
                    list.remove(i);
                }
            }
        }
    }


}