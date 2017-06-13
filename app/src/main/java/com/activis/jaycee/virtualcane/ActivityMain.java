package com.activis.jaycee.virtualcane;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoCameraPreview;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;

import java.util.ArrayList;

public class ActivityMain extends Activity
{
    private static final String TAG = ActivityMain.class.getSimpleName();

    private Tango tango;
    private TangoCameraPreview tangoCameraPreview;

    private Vibrator vibrator;

    private RunnableVibrate vibrateRunnable;
    private ClassMetrics metrics;

    private boolean tangoConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        vibrator = (Vibrator)this.getSystemService(Context.VIBRATOR_SERVICE);
        tangoCameraPreview = new TangoCameraPreview(this);

        metrics = new ClassMetrics();

        setContentView(tangoCameraPreview);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if(!tangoConnected)
        {
            /* Start Tango connection on separate thread to avoid stutter */
            tango = new Tango(ActivityMain.this, new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        tangoCameraPreview.connectToTangoCamera(tango, TangoCameraIntrinsics.TANGO_CAMERA_COLOR);

                        ArrayList<TangoCoordinateFramePair> framePairList = new ArrayList<>();
                        framePairList.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE, TangoPoseData.COORDINATE_FRAME_DEVICE));

                        tango.connectListener(framePairList, new ClassTangoUpdateCallback(ActivityMain.this));

                        TangoConfig tangoConfig = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
                        tangoConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
                        tangoConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
                        tangoConfig.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);

                        // Finally connect Tango
                        tango.connect(tangoConfig);
                        tangoConnected = true;
                    }
                    catch(TangoOutOfDateException e)
                    {
                        Log.e(TAG, "Tango core out of date, please update: " + e);
                    }
                    catch(TangoErrorException e)
                    {
                        Log.e(TAG, "Tango connection error: " + e);
                    }
                }
            });

            vibrateRunnable = new RunnableVibrate(ActivityMain.this);
        }
    }

    @Override
    protected void onPause()
    {
        if(tangoConnected)
        {
            tangoCameraPreview.disconnectFromTangoCamera();
            tango.disconnect();
            tangoConnected = false;
            vibrateRunnable.setIsRunning(false);
        }

        vibrator.cancel();

        super.onPause();
    }

    public RunnableVibrate getRunnableVibrate() { return this.vibrateRunnable; }
    public TangoCameraPreview getTangoCameraPreview() { return this.tangoCameraPreview; }
    public ClassMetrics getClassMetrics() { return this.metrics; }
    public Vibrator getVibrator() { return this.vibrator; }
    public Tango getTango() { return this.tango; }
}
