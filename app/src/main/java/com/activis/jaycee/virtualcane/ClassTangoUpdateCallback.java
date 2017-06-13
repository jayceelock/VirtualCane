package com.activis.jaycee.virtualcane;

import android.opengl.GLSurfaceView;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

class ClassTangoUpdateCallback extends Tango.TangoUpdateCallback
{
    private static final String TAG = ClassTangoUpdateCallback.class.getSimpleName();
    private static final double CANE_RADIUS = 0.01;

    private ActivityMain activityMain;

    private float depth = 10.f;
    private Vector3 depthPointPosition = new Vector3(0, 0, 0);

    ClassTangoUpdateCallback(ActivityMain activityMain)
    {
        this.activityMain = activityMain;
    }

    @Override
    public void onFrameAvailable(int cameraId)
    {
        if(cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR)
        {
            if(activityMain.getSurfaceView().getRenderMode() != GLSurfaceView.RENDERMODE_WHEN_DIRTY)
            {
                activityMain.getSurfaceView().setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            }
            activityMain.getIsFrameAvailable().set(true);
            activityMain.getSurfaceView().requestRender();
        }
    }

    @Override
    public void onPointCloudAvailable(TangoPointCloudData cloud)
    {
        /* for(int i = 0; i < cloud.numPoints - 4; i += 4)
        {
            if(cloud.points.get(i) * cloud.points.get(i) + cloud.points.get(i + 1) * cloud.points.get(i + 1) < CANE_RADIUS * CANE_RADIUS)
            {
                depth = cloud.points.get(i + 2);
                depthPointPosition.setAll((double)cloud.points.get(i), (double)cloud.points.get(i + 1), -(double)cloud.points.get(i + 2));
            }
        }*/
        try
        {
            TangoPoseData oglTdepthPose = TangoSupport.getPoseAtTime(
                    cloud.timestamp,
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                    TangoSupport.ROTATION_IGNORED);

            TangoPoseData oglTcolorPose = TangoSupport.getPoseAtTime(
                    activityMain.getRGBTimestamp(),
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                    TangoSupport.ROTATION_IGNORED);

            float[] point = TangoSupport.getDepthAtPointNearestNeighbor(cloud,
                    oglTdepthPose.translation,
                    oglTdepthPose.rotation,
                    0.5f, 0.5f,
                    activityMain.getDisplayRotation(),
                    oglTcolorPose.translation,
                    oglTcolorPose.rotation);
            if(point != null)
            {
                depthPointPosition.setAll((double) point[0], (double) point[1], (double) point[2]);
                depth = -point[2];
            }
        }
        catch(TangoInvalidException e)
        {
            Log.e(TAG, "Tango invalid: could not detect point. " + e);
        }
        double timestamp = activityMain.getTango().getPoseAtTime(0.0, new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE, TangoPoseData.COORDINATE_FRAME_DEVICE)).timestamp;

        activityMain.getRunnableVibrate().setDepth(depth);

        if(!activityMain.getRunnableVibrate().getIsRunning())
        {
            //activityMain.getRunnableVibrate().run();
        }

        activityMain.getClassMetrics().updateDistanceToObstacle(depth);
        activityMain.getClassMetrics().updateTimestamp(timestamp);
    }

    @Override
    public void onPoseAvailable(TangoPoseData pose)
    {
        activityMain.getRenderer().setDepthPoint(depthPointPosition);

        activityMain.getClassMetrics().updatePoseData(pose);
        Log.d(TAG, String.format("Z: %f", -activityMain.getRenderer().getDepthPointPosition().z));
    }
}
