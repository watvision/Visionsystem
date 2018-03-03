package com.watvision.mainapp;

/**
 * Created by Jake on 3/3/2018.
 */

public class FieldElement {
    public int x1, x2, y1, y2, w;
    public FieldElement(int X1, int Y1, int X2, int Y2, int W) {
        x1 = X1;
        y1 = Y1;
        x2 = X2;
        y2 = Y2;
        w = W;
    }

    public int[][] borderFill(int[][] proxField) {
        for(int x = x1; x < x2; x++) {
            if(proxField[x][y1] < w)
                proxField[x][y1] = w;
            if(proxField[x][y2] < w)
                proxField[x][y2] = w;
        }
        for(int y = y1+1; y < y2-1; y++) {
            if(proxField[x1][y] < w)
                proxField[x1][y] = w;
            if(proxField[x2][y] < w)
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
