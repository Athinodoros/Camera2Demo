package com.example.athinodoros.camera2demo;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {


    private static final int REQUEST_CAMERA_PERMITION_RESULT = 0;
    private TextureView bottomTextureView;
    private TextureView topTextureView;
    private TextureView.SurfaceTextureListener bottomListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice mCameraDevice;
    private String mCameraId;
    private HandlerThread backgroundThread;
    private android.os.Handler handler;
    private Size previewSize;
    private CaptureRequest.Builder captureRequestBuilder;
    private static SparseArray<Integer> devOrientations = new SparseArray();

    static {
        devOrientations.append(Surface.ROTATION_0, 0);
        devOrientations.append(Surface.ROTATION_90, 90);
        devOrientations.append(Surface.ROTATION_180, 180);
        devOrientations.append(Surface.ROTATION_270, 270);
    }

    CameraDevice.StateCallback cameraDeviceStateCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
            Toast.makeText(MainActivity.this, "Camera connection made!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottomTextureView = (TextureView) findViewById(R.id.textureView);

    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackThread();

        if (bottomTextureView.isAvailable()) {
            setupCamera(bottomTextureView.getWidth(), bottomTextureView.getHeight());
            connectCamera();
        } else {
            bottomTextureView.setSurfaceTextureListener(bottomListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        startBackThread();
        super.onPause();

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocused) {
        super.onWindowFocusChanged(hasFocused);
        View decorView = getWindow().getDecorView();
        if (hasFocused) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    public static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long) lhs.getWidth() * lhs.getHeight());
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraid : cameraManager.getCameraIdList()) {
                CameraCharacteristics camChars = cameraManager.getCameraCharacteristics(cameraid);
                CameraCharacteristics camChars2 = cameraManager.getCameraCharacteristics(cameraid);
                if (camChars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    StreamConfigurationMap map = camChars2.get(camChars.SCALER_STREAM_CONFIGURATION_MAP);

                    continue;
                } else {
                    StreamConfigurationMap map = camChars.get(camChars.SCALER_STREAM_CONFIGURATION_MAP);
                    int devOrient = getWindowManager().getDefaultDisplay().getRotation();
                    int totalRotation = sensorRotation(camChars, devOrient);
                    boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                    int rotWidth = width;
                    int rotHeight = height;
                    if (swapRotation) {
                        rotHeight = width;
                        rotWidth = height;
                    }
                    previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotWidth, rotHeight);
                    mCameraId = cameraid;
//                    if(cameraid != CameraCharacteristics.LENS_FACING_FRONT)
//                    mCameraId2 = ;
                    return;
                }

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        ;
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  // Get permission  (new marshmallow permission checker)
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, cameraDeviceStateCallBack, handler);
                } else {
                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(MainActivity.this, "This app needs permission to use the camera", Toast.LENGTH_SHORT).show();

                    }
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMITION_RESULT);
                }
            } else {
                cameraManager.openCamera(mCameraId, cameraDeviceStateCallBack, handler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfTexture = bottomTextureView.getSurfaceTexture();

        surfTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfTexture);

        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "Something whent wromg with the Comfigurations!", Toast.LENGTH_SHORT).show();
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void startBackThread() {
        backgroundThread = new HandlerThread("CameraThread");
        backgroundThread.start();
        handler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            handler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorRotation(CameraCharacteristics characteristics, int deviceOrientatiion) {
        int sensOrientation = 0; // characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientatiion = devOrientations.get(sensOrientation);
        return (sensOrientation + deviceOrientatiion + 360) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width &&
                    option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMITION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Application will not run without camera services", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
