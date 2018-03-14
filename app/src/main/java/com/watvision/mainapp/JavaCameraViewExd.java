package com.watvision.mainapp;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;

import org.opencv.android.JavaCameraView;

import java.util.List;

/**
 * Created by Jake on 3/13/2018.
 */

public class JavaCameraViewExd extends JavaCameraView {
    public JavaCameraViewExd(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTorch() {
        Camera.Parameters params = mCamera.getParameters();
        List<String> fm = params.getSupportedFlashModes();
        if(fm.contains(Camera.Parameters.FLASH_MODE_TORCH))
            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        mCamera.setParameters(params);
    }
}
