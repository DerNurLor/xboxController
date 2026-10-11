package dev.phonepad;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Full-screen, aspect-correct QR reader for provisioning a local companion connection. */
@SuppressWarnings("deprecation")
public final class QrScannerActivity extends Activity implements SurfaceHolder.Callback {
    public static final String EXTRA_PAYLOAD = "dev.phonepad.QR_PAYLOAD";
    public static final String EXTRA_MANUAL = "dev.phonepad.QR_MANUAL";
    private static final int CAMERA_PERMISSION = 4712;
    private static final int MINT = 0xff8ed9c5;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final AtomicBoolean decoding = new AtomicBoolean();
    private Camera camera;
    private PreviewSurfaceView preview;
    private SurfaceHolder holder;
    private TextView status;
    private volatile boolean active;
    private int previewWidth, previewHeight;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        preview = new PreviewSurfaceView();
        holder = preview.getHolder();
        holder.addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(28), dp(10), dp(28), dp(12));
        status = label("Waiting for camera…", 14, 0xffe4ecef, false);
        status.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(status, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button cancel = new Button(this);
        Button manual = new Button(this);
        manual.setText("Manually");
        manual.setAllCaps(false);
        manual.setOnClickListener(v -> {
            Intent data = new Intent().putExtra(EXTRA_MANUAL, true);
            setResult(RESULT_FIRST_USER, data);
            finish();
        });
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(dp(132), dp(48));
        manualParams.leftMargin = dp(12);
        bottom.addView(manual, manualParams);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(132), dp(48));
        cancelParams.leftMargin = dp(16);
        bottom.addView(cancel, cancelParams);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        ScanCorners corners = new ScanCorners();
        int guide = dp(264);
        root.addView(corners, new FrameLayout.LayoutParams(guide, guide, Gravity.CENTER));

        setContentView(root);
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) active = true;
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == CAMERA_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            active = true;
            startCamera();
        } else if (request == CAMERA_PERMISSION) {
            status.setText("Allow camera access to scan the connection code.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            active = true;
            startCamera();
        }
    }

    @Override protected void onPause() {
        active = false;
        stopCamera();
        super.onPause();
    }

    @Override protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    @Override public void surfaceCreated(SurfaceHolder surface) { startCamera(); }
    @Override public void surfaceChanged(SurfaceHolder surface, int format, int width, int height) { startCamera(); }
    @Override public void surfaceDestroyed(SurfaceHolder surface) { stopCamera(); }

    private synchronized void startCamera() {
        if (!active || holder == null || !holder.getSurface().isValid() || camera != null) return;
        try {
            int cameraId = backCameraId();
            if (cameraId < 0) throw new IllegalStateException("No rear camera is available.");
            Camera opened = Camera.open(cameraId);
            camera = opened;
            Camera.Parameters parameters = opened.getParameters();
            Camera.Size size = chooseSize(parameters.getSupportedPreviewSizes());
            if (size == null) throw new IllegalStateException("No camera preview size is available.");
            parameters.setPreviewSize(size.width, size.height);
            if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            opened.setParameters(parameters);
            previewWidth = size.width;
            previewHeight = size.height;

            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
            int displayOrientation = (info.orientation - degrees + 360) % 360;
            opened.setDisplayOrientation(displayOrientation);
            preview.setDisplayAspectRatio(displayOrientation == 90 || displayOrientation == 270
                    ? (float) size.height / size.width : (float) size.width / size.height);
            opened.setPreviewDisplay(holder);
            opened.setPreviewCallback((data, source) -> decodeFrame(data));
            opened.startPreview();
            status.setText("Scanning…");
        } catch (Exception error) {
            stopCamera();
            status.setText(error.getMessage() == null ? "Could not start the camera." : error.getMessage());
        }
    }

    private Camera.Size chooseSize(java.util.List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;
        Camera.Size best = sizes.get(0);
        double bestScore = Double.MAX_VALUE;
        for (Camera.Size size : sizes) {
            double ratioError = Math.abs((double) size.width / size.height - (16.0 / 9.0));
            double score = ratioError * 10000 + Math.abs(size.width - 1280) + Math.abs(size.height - 720);
            if (size.width < 640 || size.height < 360) score += 10000;
            if (score < bestScore) { best = size; bestScore = score; }
        }
        return best;
    }

    private int backCameraId() {
        int count = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return -1;
    }

    private void decodeFrame(byte[] frame) {
        if (!active || frame == null || previewWidth <= 0 || !decoding.compareAndSet(false, true)) return;
        byte[] copy = frame.clone();
        int width = previewWidth, height = previewHeight;
        decoder.execute(() -> {
            try {
                LuminanceSource source = new com.google.zxing.PlanarYUVLuminanceSource(copy, width, height, 0, 0, width, height, false);
                for (int turn = 0; turn < 4 && active; turn++) {
                    try {
                        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
                        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
                        Result result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
                        String value = result.getText();
                        runOnUiThread(() -> {
                            if (!active) return;
                            active = false;
                            Intent data = new Intent();
                            data.putExtra(EXTRA_PAYLOAD, value);
                            setResult(RESULT_OK, data);
                            finish();
                        });
                        return;
                    } catch (NotFoundException ignored) {
                        if (source.isRotateSupported()) source = source.rotateCounterClockwise();
                        else break;
                    } catch (ReaderException ignored) {
                        if (source.isRotateSupported()) source = source.rotateCounterClockwise();
                        else break;
                    } catch (RuntimeException ignored) {
                        break;
                    }
                }
            } finally {
                decoding.set(false);
            }
        });
    }

    private synchronized void stopCamera() {
        Camera current = camera;
        camera = null;
        if (current != null) {
            try { current.setPreviewCallback(null); current.stopPreview(); } catch (RuntimeException ignored) {}
            try { current.release(); } catch (RuntimeException ignored) {}
        }
    }

    /** Crops excess preview area evenly instead of stretching the camera image. */
    private final class PreviewSurfaceView extends SurfaceView {
        private float displayAspectRatio = 16f / 9f;
        PreviewSurfaceView() { super(QrScannerActivity.this); }
        void setDisplayAspectRatio(float ratio) {
            if (ratio > 0) {
                displayAspectRatio = ratio;
                requestLayout();
            }
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            if (width == 0 || height == 0) {
                super.onMeasure(widthSpec, heightSpec);
                return;
            }
            float containerRatio = (float) width / height;
            if (containerRatio > displayAspectRatio) height = Math.round(width / displayAspectRatio);
            else width = Math.round(height * displayAspectRatio);
            setMeasuredDimension(width, height);
        }
    }

    private final class ScanCorners extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ScanCorners() {
            super(QrScannerActivity.this);
            paint.setColor(MINT);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.STROKE);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(5), length = dp(32), right = getWidth() - inset, bottom = getHeight() - inset;
            canvas.drawLine(inset, inset, inset + length, inset, paint);
            canvas.drawLine(inset, inset, inset, inset + length, paint);
            canvas.drawLine(right - length, inset, right, inset, paint);
            canvas.drawLine(right, inset, right, inset + length, paint);
            canvas.drawLine(inset, bottom - length, inset, bottom, paint);
            canvas.drawLine(inset, bottom, inset + length, bottom, paint);
            canvas.drawLine(right, bottom - length, right, bottom, paint);
            canvas.drawLine(right - length, bottom, right, bottom, paint);
        }
    }
}
