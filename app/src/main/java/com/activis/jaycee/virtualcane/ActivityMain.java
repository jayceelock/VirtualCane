package com.activis.jaycee.virtualcane;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActivityMain extends Activity
{
    private static final String TAG = ActivityMain.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final String INTERNET_PERMISSION = Manifest.permission.INTERNET;
    private static final String VIBRATE_PERMISSION = Manifest.permission.VIBRATE;

    private static final int MULTIPLE_PERMISSION_CODE = 0;

    private Tango tango;
    private TangoPointCloudManager pointCloudManager;

    private SurfaceView surfaceView;
    private ClassRenderer renderer;

    private TextView textviewDepth;

    private Vibrator vibrator;

    private RunnableVibrate vibrateRunnable;
    private Handler vibrateHandler;

    private ClassMetrics metrics;

    private boolean tangoConnected = false;
    private boolean isCloudEnabled = false;

    private int displayRotation;
    private int connectedGLThreadID = INVALID_TEXTURE_ID;

    private double RGBTimestamp;
    private double cameraPoseTimestamp;

    private AtomicBoolean isFrameAvailable = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null)
        {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener()
            {
                @Override
                public void onDisplayAdded(int displayId) {  }

                @Override
                public void onDisplayChanged(final int displayId)
                {
                    synchronized (this)
                    {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) { }
            }, null);
        }

        pointCloudManager = new TangoPointCloudManager();

        renderer = setupRenderer();
        surfaceView = (SurfaceView)findViewById(R.id.camera_surfaceview);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setSurfaceRenderer(renderer);

        findViewById(R.id.button_toggle_cloud).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                isCloudEnabled = renderer.togglePointCloud(isCloudEnabled);
                Log.d(TAG, "Clicked, toggle status: " + isCloudEnabled);
            }
        });

        textviewDepth = (TextView)findViewById(R.id.textview_depth);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        if(checkAndRequestPermissions())
        {
            bindTangoService();

            vibrator = (Vibrator)this.getSystemService(Context.VIBRATOR_SERVICE);
            vibrateRunnable = new RunnableVibrate(ActivityMain.this);
            vibrateHandler = new Handler();
            vibrateHandler.post(vibrateRunnable);

            metrics = new ClassMetrics();
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        synchronized (ActivityMain.this)
        {
            if(vibrator != null)
            {
                vibrateRunnable.setIsRunning(false);
                vibrateHandler.removeCallbacks(vibrateRunnable);
                vibrator.cancel();
            }

            if (tango != null)
            {
                tango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                tango.disconnect();
                tango = null;

                connectedGLThreadID = INVALID_TEXTURE_ID;

                tangoConnected = false;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if(hasPermissions())
        {
            bindTangoService();

            vibrator = (Vibrator)this.getSystemService(Context.VIBRATOR_SERVICE);
            vibrateRunnable = new RunnableVibrate(ActivityMain.this);

            metrics = new ClassMetrics();
        }
        else
        {
            Toast.makeText(this, "Virtual Cane requires additional permissions.", Toast.LENGTH_LONG).show();
        }
    }

    private void bindTangoService()
    {
        /* Start Tango connection on separate thread to avoid stutter */
        tango = new Tango(ActivityMain.this, new Runnable()
        {
            @Override
            public void run()
            {
                synchronized (ActivityMain.this)
                {
                    try
                    {
                        // tangoCameraPreview.connectToTangoCamera(tango, TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        TangoSupport.initialize(tango);

                        ArrayList<TangoCoordinateFramePair> framePairList = new ArrayList<>();
                        framePairList.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE, TangoPoseData.COORDINATE_FRAME_DEVICE));

                        tango.connectListener(framePairList, new ClassTangoUpdateCallback(ActivityMain.this));

                        TangoConfig tangoConfig = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
                        tangoConfig.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
                        tangoConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
                        tangoConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);
                        tangoConfig.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);

                        tangoConfig.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);

                        // Finally connect Tango
                        tango.connect(tangoConfig);
                        tangoConnected = true;
                        setDisplayRotation();
                    }
                    catch (TangoOutOfDateException e)
                    {
                        Log.e(TAG, "Tango core out of date, please update: " + e);
                    }
                    catch (TangoErrorException e)
                    {
                        Log.e(TAG, "Tango connection error: " + e);
                    }
                    catch(TangoInvalidException e)
                    {
                        Log.e(TAG, "Tango invalid exception: " + e);
                    }
                }
            }
        });
    }

    public ClassRenderer setupRenderer()
    {
        ClassRenderer renderer = new ClassRenderer(this);

        /* Register renderer to Rajawali Callback to update the scene for every RGB frame */
        renderer.getCurrentScene().registerFrameCallback(new ClassRajawaliFrameCallback(this));

        return renderer;
    }

    public void setDisplayRotation()
    {
        Display display = getWindowManager().getDefaultDisplay();
        displayRotation = display.getRotation();

        /* We also need to update the camera texture UV coordinates. This must be run in the OpenGL thread */
        surfaceView.queueEvent(new Runnable()
        {
            @Override
            public void run()
            {
                if (tangoConnected)
                {
                    renderer.updateColorCameraTextureUvGlThread(displayRotation);
                }
            }
        });
    }

    private boolean checkAndRequestPermissions()
    {
        int permissionInternet = ContextCompat.checkSelfPermission(this, INTERNET_PERMISSION);
        int permissionVibrate = ContextCompat.checkSelfPermission(this, VIBRATE_PERMISSION);
        int permissionCamera = ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION);

        List<String> permissionsRequired  = new ArrayList<>();

        if(permissionCamera != PackageManager.PERMISSION_GRANTED)
        {
            permissionsRequired.add(CAMERA_PERMISSION);
        }
        if(permissionInternet != PackageManager.PERMISSION_GRANTED)
        {
            permissionsRequired.add(INTERNET_PERMISSION);
        }
        if(permissionVibrate != PackageManager.PERMISSION_GRANTED)
        {
            permissionsRequired.add(VIBRATE_PERMISSION);
        }

        if(!permissionsRequired.isEmpty())
        {
            ActivityCompat.requestPermissions(this, permissionsRequired.toArray(new String[permissionsRequired.size()]), MULTIPLE_PERMISSION_CODE);

            return false;
        }

        return true;
    }

    private boolean hasPermissions()
    {
        return ContextCompat.checkSelfPermission(this, INTERNET_PERMISSION) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, VIBRATE_PERMISSION) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, INTERNET_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public RunnableVibrate getRunnableVibrate() { return this.vibrateRunnable; }
    public Handler getVibrateHandler() { return this.vibrateHandler; }
    public ClassMetrics getClassMetrics() { return this.metrics; }
    public Vibrator getVibrator() { return this.vibrator; }
    public Tango getTango() { return this.tango; }
    public boolean getIsTangoConnected() { return this.tangoConnected; }
    public ClassRenderer getRenderer() { return this.renderer; }
    public int getDisplayRotation() { return this.displayRotation; }
    public int getConnectedGLThreadID() { return this.connectedGLThreadID; }
    public void setConnectedGLThreadID(int connectedGLThreadID) { this.connectedGLThreadID = connectedGLThreadID; }
    public double getRGBTimestamp() { return  this.RGBTimestamp; }
    public void setRGBTimestamp(double RGBTimestamp){ this.RGBTimestamp = RGBTimestamp; }
    public double getCameraPoseTimestamp() { return this.cameraPoseTimestamp; }
    public void setCameraPoseTimestamp(double cameraPoseTimestamp) { this.cameraPoseTimestamp = cameraPoseTimestamp; }
    public AtomicBoolean getIsFrameAvailable() { return  this.isFrameAvailable; }
    public SurfaceView getSurfaceView() { return this.surfaceView; }
    public TangoPointCloudManager getPointCloudManager() { return this.pointCloudManager; }
    public void setText(final double distance)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                textviewDepth.setText(String.format("%.3fm", distance));
            }
        });
    }
}
