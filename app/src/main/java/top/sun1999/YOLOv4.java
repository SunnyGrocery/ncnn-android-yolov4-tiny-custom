package top.sun1999;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class YOLOv4 {
    static {
        System.loadLibrary("yolov4");
    }

    public static native void init(AssetManager manager);
    public static native Box[] detect(Bitmap bitmap, double threshold, double nms_threshold);
}
