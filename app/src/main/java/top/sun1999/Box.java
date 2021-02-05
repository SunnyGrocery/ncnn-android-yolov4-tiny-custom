package top.sun1999;

import android.graphics.Color;
import android.graphics.RectF;

import java.util.Random;

public class Box {
    public float x0, y0, x1, y1;
    private final int label;
    private final float score;
    /**
     * 检测人脸，判断是否佩戴口罩
     */
    private static final String[] labels = {"nomask", "masked"};

    public Box(float x0, float y0, float x1, float y1, int label, float score) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.label = label;
        this.score = score;
    }

    public RectF getRect() {
        return new RectF(x0, y0, x1, y1);
    }

    public String getLabel() {
        return labels[label];
    }

    public float getScore() {
        return score;
    }

    public int getColor() {
        if (label == 0) {
            return Color.argb(255, 220, 20, 60);
        }
        return Color.argb(255, 50, 205, 50);
    }
}
