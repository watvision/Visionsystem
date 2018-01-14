package com.watvision.mainapp;

import java.util.ArrayList;

// Screen Class - Created 2018-01-13
// Stores a list of screen elements that composes the info of a screen

public class Screen {

    private ArrayList<ScreenElement> elements;

    public Screen() {
        elements = new ArrayList<ScreenElement>();
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
