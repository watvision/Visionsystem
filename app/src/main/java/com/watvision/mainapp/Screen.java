package com.watvision.mainapp;

import android.graphics.Rect;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.text.TextBlock;

import java.util.ArrayList;

// Screen Class - Created 2018-01-13
// Stores a list of screen elements that composes the info of a screen

public class Screen {

    // Element list. Should be read only, nothing should be removed from the list
    private ArrayList<ScreenElement> elements;

    // TAG used for debugging purposes
    private static final String TAG = "Screen";

    public Screen() {
        elements = new ArrayList<ScreenElement>();
    }

    public void GenerateScreen(SparseArray<TextBlock> textBlocks, int screenWidth, int screenHeight) {

        elements.clear();

        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));

            elements.add(generateScreenElement(textBlock, screenWidth, screenHeight, elements.size()));
        }

    }

    private ScreenElement generateScreenElement(TextBlock textBlock, int screenWidth, int screenHeight, int inId) {

        Rect boundingRect = textBlock.getBoundingBox();

        double xpos = boundingRect.left * 1.0 / screenWidth;
        double ypos = boundingRect.top * 1.0 / screenHeight;
        double width = boundingRect.width() * 1.0 / screenWidth;
        double height = boundingRect.height() * 1.0 / screenHeight;

        double w_pad = calculate_padding(width);
        double h_pad = calculate_padding(height);

        xpos -= w_pad;
        ypos -= h_pad;
        width += 2*w_pad;
        height +=2*h_pad;
        if(width > 1) width = 1;
        if(height> 1) height = 1;
        if(xpos < 0) xpos = 0;
        if(ypos < 0) ypos = 0;

        Log.d(TAG,"Increasing width and height");

        Log.w(TAG,"New Screen Element: " + xpos + " , " + ypos + " , " +
                width + " , " + height + " , " + textBlock.getValue() + " id: " + inId);

        ScreenElement returnElement = new ScreenElement(xpos, ypos, width, height,textBlock.getValue(), inId);

        return returnElement;
    }

    private double calculate_padding(double element_size)
    {
        Log.d(TAG,"Calculating padding");
        double m = -1;
        double b = 0.6; // 60%
        double percent_pad = element_size * m + b;
        if(percent_pad < 0) percent_pad = 0;
        double pad = percent_pad * element_size;
        return pad;
    }

    public ScreenElement GetElementAtPoint(double x, double y) {

        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).IsLocationWithinElement(x,y)) {
                return elements.get(i);
            }
        }

        // If nothing was found just return null

        return null;
    }

    public ArrayList<ScreenElement> getAllElements() {
        return elements;
    }

}
