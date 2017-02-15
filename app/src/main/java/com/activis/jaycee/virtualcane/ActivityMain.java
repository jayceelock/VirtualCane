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
    private static final double CANE_RADIUS = 0.01;

    private Tango tango;
    private TangoCameraPreview tangoCameraPreview;

    private RunnableVibrate vibrateRunnable;

    private boolean tangoConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        tangoCameraPreview = new TangoCameraPreview(this);

        setContentView(tangoCameraPreview);

    }

    @Override
    protected void onResume()
    {
        super.onResume();

        vibrateRunnable = new RunnableVibrate((Vibrator)this.getSystemService(Context.VIBRATOR_SERVICE));

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

                        tango.connectListener(framePairList, new Tango.TangoUpdateCallback()
                        {
                            @Override
                            public void onFrameAvailable(int cameraId)
                            {
                                if(cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR)
                                {
                                    tangoCameraPreview.onFrameAvailable();
                                }
                            }

                            @Override
                            public void onPointCloudAvailable(TangoPointCloudData cloud)
                            {
                                double depth = 5000;
                                for(int i = 0; i < cloud.numPoints - 3; i += 3)
                                {
                                    if(cloud.points.get(i + 2) < depth
                                            && cloud.points.get(i) * cloud.points.get(i) + cloud.points.get(i + 1) * cloud.points.get(i + 1) < CANE_RADIUS * CANE_RADIUS)
                                    {
                                        depth = cloud.points.get(i + 2);
                                    }
                                }

                                vibrateRunnable.setDepth(depth);
                            }
                        });

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
        }

        super.onPause();
    }
}
