package com.activis.jaycee.virtualcane;

import android.opengl.GLSurfaceView;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

class ClassTangoUpdateCallback extends Tango.TangoUpdateCallback
{
    private static final String TAG = ClassTangoUpdateCallback.class.getSimpleName();
    private static final double CANE_RADIUS = 0.01;

    private ActivityMain activityMain;

    private float depth = 10.f;

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
        for(int i = 0; i < cloud.numPoints - 4; i += 4)
        {
            if(cloud.points.get(i) * cloud.points.get(i) + cloud.points.get(i + 1) * cloud.points.get(i + 1) < CANE_RADIUS * CANE_RADIUS)
            {
                depth = cloud.points.get(i + 2);
            }
        }
        double timestamp = activityMain.getTango().getPoseAtTime(0.0, new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE, TangoPoseData.COORDINATE_FRAME_DEVICE)).timestamp;

        /*TangoSupport.TangoMatrixTransformData transform = TangoSupport.getMatrixTransformAtTime(cloud.timestamp,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.ROTATION_IGNORED); */
        //activityMain.getRenderer().updatePointCloud(cloud, transform.matrix);

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
        activityMain.getClassMetrics().updatePoseData(pose);
        // Log.wtf(TAG, String.format("x: %f y: %f z: %f qx: %f qy: %f qz: %f qw: %f", pose.translation[0], pose.translation[1], pose.translation[2], pose.rotation[0], pose.rotation[1], pose.rotation[2], pose.rotation[3]));
    }
}
