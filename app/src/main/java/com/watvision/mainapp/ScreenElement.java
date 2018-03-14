package com.watvision.mainapp;

// Screen Element - Created 2018-01-13
// Stores the information of an element of a screen (button, textbox, etc.)

import android.support.annotation.NonNull;

public class ScreenElement implements Comparable<ScreenElement> {

    // Doubles are chosen since elements must be expressed as percentages of total screen Width
    // and total screen height.
    private double x_base;
    private double y_base;
    private double x_width;
    private double y_length;
    private int id;

    private String elementDescription;

    public ScreenElement(double inx, double iny, double inWidth, double inLength, String inText, int inId) {
        x_base = inx;
        y_base = iny;
        x_width = inWidth;
        y_length = inLength;
        elementDescription = inText;
        id = inId;
    }

    public Boolean IsLocationWithinElement(double x, double y) {
        Boolean isInX = ( x < x_base + x_width) && (x > x_base);
        Boolean isInY = ( y < y_base + y_length) && (y > y_base);

        return (isInX && isInY);

    }

    public String GetElementDescription() {
        return elementDescription;
    }

    public int getId() {
        return id;
    }

    public double getX_base() { return x_base; }
    public double getX_Width() { return x_width; }
    public double getY_base() { return y_base; }
    public double getY_length() { return y_length; }

    @Override
    public int compareTo(ScreenElement compareScreenElement) {
        return (int)(((this.y_base - compareScreenElement.y_base) + (this.x_base - compareScreenElement.x_base)/5)*100);
        /* For Descending order do like this */
        //return compareage-this.studentage;
    }

}
