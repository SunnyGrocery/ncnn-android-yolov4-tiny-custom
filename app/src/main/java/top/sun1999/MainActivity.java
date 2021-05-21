package top.sun1999;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.UseCase;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import android.util.Size;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.pow;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };
    private ImageView resultImageView;

    private TextView thresholdTextview;
    private TextView tvInfo;
    private final double threshold = 0.35;
    private final double nms_threshold = 0.7;

    private final AtomicBoolean detecting = new AtomicBoolean(false);
    private final AtomicBoolean detectPhoto = new AtomicBoolean(false);

    private long startTime = 0;
    private long endTime = 0;
    private int width;
    private int height;
    private static final Paint boxPaint = new Paint();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, PERMISSIONS, PackageManager.PERMISSION_GRANTED);
        while ((ContextCompat.checkSelfPermission(this.getApplicationContext(), PERMISSIONS[0]) == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(this.getApplicationContext(), PERMISSIONS[1]) == PackageManager.PERMISSION_DENIED)) {
            try {
                wait(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        setContentView(R.layout.activity_main);
        YOLOv4.init(getAssets());
        resultImageView = findViewById(R.id.imageView);
        thresholdTextview = findViewById(R.id.valTxtView);
        tvInfo = findViewById(R.id.tv_info);

        final String format = "Thresh: %.2f, NMS: %.2f";
        thresholdTextview.setText(String.format(Locale.ENGLISH, format, threshold, nms_threshold));

        Button inference = findViewById(R.id.button);
        inference.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        });

        resultImageView.setOnClickListener(v -> detectPhoto.set(false));
        startCamera();

    }


    private void startCamera() {
        CameraX.unbindAll();

        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setLensFacing(CameraX.LensFacing.BACK)
                .setTargetResolution(new Size(416, 416))  // 分辨率
                .build();

        Preview preview = new Preview(previewConfig);
        DetectAnalyzer detectAnalyzer = new DetectAnalyzer();
        CameraX.bindToLifecycle(this, preview, gainAnalyzer(detectAnalyzer));

    }


    private UseCase gainAnalyzer(DetectAnalyzer detectAnalyzer) {
        ImageAnalysisConfig.Builder analysisConfigBuilder = new ImageAnalysisConfig.Builder();
        analysisConfigBuilder.setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE);
        analysisConfigBuilder.setTargetResolution(new Size(416, 416));  // 输出预览图像尺寸
        ImageAnalysisConfig config = analysisConfigBuilder.build();
        ImageAnalysis analysis = new ImageAnalysis(config);
        analysis.setAnalyzer(detectAnalyzer);
        return analysis;
    }

    private class DetectAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(ImageProxy image, final int rotationDegrees) {
            if (detecting.get() || detectPhoto.get()) {
                return;
            }
            detecting.set(true);
            final Bitmap bitmapsrc = imageToBitmap(image);  // 格式转换
            Thread detectThread = new Thread(() -> {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                width = bitmapsrc.getWidth();
                height = bitmapsrc.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(bitmapsrc, 0, 0, width, height, matrix, false);

                startTime = System.currentTimeMillis();
                Box[] result = YOLOv4.detect(bitmap, threshold, nms_threshold);
                endTime = System.currentTimeMillis();

                final Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                float strokeWidth = 4 * (float) mutableBitmap.getWidth() / 800;
                float textSize = 30 * (float) mutableBitmap.getWidth() / 800;

                Canvas canvas = new Canvas(mutableBitmap);
                boxPaint.setAlpha(255);
                boxPaint.setTypeface(Typeface.SANS_SERIF);
                boxPaint.setStyle(Paint.Style.STROKE);
                boxPaint.setStrokeWidth(strokeWidth);
                boxPaint.setTextSize(textSize);
                List<Box> nomaskBox = new ArrayList<>();
                float imageArea = width * height;
                for (Box box : result) {
                    boxPaint.setColor(box.getColor());
                    boxPaint.setStyle(Paint.Style.FILL);
                    String score = Integer.toString((int) (box.getScore() * 100));
                    canvas.drawText(box.getLabel() + " [" + score + "%]",
                            box.x0 - strokeWidth, box.y0 - strokeWidth
                            , boxPaint);
                    boxPaint.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(box.getRect(), boxPaint);
                    if ("masked".equals(box.getLabel())) {
                        continue;
                    }
                    float boxArea = (box.x1 - box.x0) * (box.y1 - box.y0);
                    if (boxArea / imageArea > 0.3) {
                        continue;
                    }
                    nomaskBox.add(box);
                }

                List<Float[]> dangerousLineMatrix = calDistance(nomaskBox);
                boxPaint.setColor(Color.argb(255, 255, 165, 0));
                for (Float[] location : dangerousLineMatrix) {
                    canvas.drawLine(location[0], location[1], location[2], location[3], boxPaint);
                }

                runOnUiThread(() -> {
                    resultImageView.setImageBitmap(mutableBitmap);
                    detecting.set(false);
                    long dur = endTime - startTime;
                    float fps = (float) (1000.0 / dur);
                    tvInfo.setText(String.format(Locale.CHINESE,
                            "ImgSize: %dx%d\nUseTime: %d ms\nDetectFPS: %.2f",
                            height, width, dur, fps));
                });
            }, "detect");
            detectThread.start();
        }

        private Bitmap imageToBitmap(ImageProxy image) {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ImageProxy.PlaneProxy y = planes[0];
            ImageProxy.PlaneProxy u = planes[1];
            ImageProxy.PlaneProxy v = planes[2];
            ByteBuffer yBuffer = y.getBuffer();
            ByteBuffer uBuffer = u.getBuffer();
            ByteBuffer vBuffer = v.getBuffer();
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            // U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
            byte[] imageBytes = out.toByteArray();

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }

    }


    @Override
    protected void onDestroy() {
        CameraX.unbindAll();
        super.onDestroy();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                this.finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            return;
        }
        detectPhoto.set(true);
        Bitmap image = getPicture(data.getData());
        startTime = System.currentTimeMillis();
        Box[] result = YOLOv4.detect(image, threshold, nms_threshold);
        endTime = System.currentTimeMillis();

        final Bitmap mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true);
        float strokeWidth = 4 * (float) mutableBitmap.getWidth() / 800;
        float textSize = 30 * (float) mutableBitmap.getWidth() / 800;
        Canvas canvas = new Canvas(mutableBitmap);
        boxPaint.setAlpha(255);
        boxPaint.setTypeface(Typeface.SANS_SERIF);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(strokeWidth);
        boxPaint.setTextSize(textSize);


        int scoreAvg = 0;
        for (Box box : result) {
            int score = (int) (box.getScore() * 100);
            scoreAvg += score;

            boxPaint.setColor(box.getColor());
            boxPaint.setStyle(Paint.Style.FILL);
            canvas.drawText(box.getLabel() + " [" + score + "%]",
                    box.x0 - strokeWidth, box.y0 - strokeWidth
                    , boxPaint);
            boxPaint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(box.getRect(), boxPaint);
        }

        //距离计算
        List<Box> nomaskBox = new ArrayList<>();
        float imageArea = image.getWidth() * image.getHeight();
        for (Box box : result) {
            if ("masked".equals(box.getLabel())) {
                continue;
            }
            float boxArea = (box.x1 - box.x0) * (box.y1 - box.y0);
            if (boxArea / imageArea > 0.3) {
                continue;
            }
            nomaskBox.add(box);
        }
        List<Float[]> dangerousLineMatrix = calDistance(nomaskBox);
        boxPaint.setColor(Color.argb(255, 255, 165, 0));
        for (Float[] location : dangerousLineMatrix) {
            canvas.drawLine(location[0], location[1], location[2], location[3], boxPaint);
        }

        if (result.length != 0) {
            scoreAvg = scoreAvg / result.length;
        }
        resultImageView.setImageBitmap(mutableBitmap);
        detecting.set(false);
        long dur = endTime - startTime;
        tvInfo.setText(String.format(Locale.CHINESE,
                "ImgSize: %dx%d\nUseTime: %d ms\nAvgMatchScore: %d%%",
                height, width, dur, scoreAvg));

    }

    static double DisRatioBox = 3;

    private ArrayList<Float[]> calDistance(List<Box> element) {
        ArrayList<Float[]> dangerousLineMatrix = new ArrayList<>();

        for (int i = 0; i < element.size(); i++) {
            Box box = element.get(i);
            float centerX = (box.x0 + box.x1) / 2;
            float centerY = (box.y0 + box.y1) / 2;
            float boxArea = (box.x1 - box.x0) * (box.y1 - box.y0);
            for (int j = i + 1; j < element.size(); j++) {
                Box nowBox = element.get(j);
                float nowBoxArea = (nowBox.x1 - nowBox.x0) * (nowBox.y1 - nowBox.y0);
                float maxArea = Math.max(nowBoxArea, boxArea);
                if (Math.abs(nowBoxArea - boxArea) / maxArea > 0.3) {
                    continue;
                }
                float nowCenterX = (nowBox.x0 + nowBox.x1) / 2;
                float nowCenterY = (nowBox.y0 + nowBox.y1) / 2;
                double pixelDistance = Math.sqrt(pow(nowCenterX - centerX, 2) + pow(nowCenterY - centerY, 2));
                double realRatio = pixelDistance / (nowBox.y1 - nowBox.y0);
                //safe distance
                if (realRatio > DisRatioBox) {
                    continue;
                }
                //dangerous
                dangerousLineMatrix.add(new Float[]{centerX, centerY, nowCenterX, nowCenterY,});
            }
        }
        return dangerousLineMatrix;
    }

    public Bitmap getPicture(Uri selectedImage) {
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String picturePath = cursor.getString(columnIndex);
        cursor.close();
        Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
        int rotate = readPictureDegree(picturePath);
        return rotateBitmapByDegree(bitmap, rotate);
    }

    public int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    public Bitmap rotateBitmapByDegree(Bitmap bm, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);

        Bitmap returnBm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);

        if (returnBm == null) {
            returnBm = bm;
        }
        if (bm != returnBm) {
            bm.recycle();
        }
        return returnBm;
    }


}
