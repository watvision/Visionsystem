package com.watvision.mainapp;

import android.content.Context;
import android.os.Vibrator;
import android.os.VibrationEffect;

public class VibrateControls {

    boolean BTEnable;
    Vibrator phoneVib;
    int iter;

    private long[] intensity = {400, 350, 300, 250, 200, 150, 100};

    // Constructor
    public VibrateControls(Context appContext) {
        BTEnable = false; //to change when bluetooth gets integrated
        phoneVib = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        iter = 0;
    }

    public void testvibrate(boolean start) {
        if(start) {
            long[] timings = {intensity[iter], intensity[iter], intensity[iter], intensity[iter], 200};
            int[] amp = {0, 255, 0, 255, 0};
            phoneVib.vibrate(VibrationEffect.createWaveform(timings, amp, 0));
        } else {
            phoneVib.cancel();
            iter++;
            if(iter > 6)
                iter = 0;
        }
    }
}
