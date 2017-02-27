package com.activis.jaycee.virtualcane;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;

class ClassTangoUpdateCallback extends Tango.TangoUpdateCallback
{
    private static final String TAG = ClassTangoUpdateCallback.class.getSimpleName();
    private static final double CANE_RADIUS = 0.01;

    private ActivityMain activityMain;

    private double depth = 10;

    ClassTangoUpdateCallback(ActivityMain activityMain)
    {
        this.activityMain = activityMain;
    }
    @Override
    public void onFrameAvailable(int cameraId)
    {
        if(cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR)
        {
            activityMain.getTangoCameraPreview().onFrameAvailable();
        }
    }

    @Override
    public void onPointCloudAvailable(TangoPointCloudData cloud)
    {
        for(int i = 0; i < cloud.numPoints - 3; i += 3)
        {
            if(cloud.points.get(i) * cloud.points.get(i) + cloud.points.get(i + 1) * cloud.points.get(i + 1) < CANE_RADIUS * CANE_RADIUS)
            {
                depth = cloud.points.get(i + 2);
            }
        }
        double timestamp = activityMain.getTango().getPoseAtTime(0.0, new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE, TangoPoseData.COORDINATE_FRAME_DEVICE)).timestamp;

        activityMain.getRunnableVibrate().setDepth(depth);
        activityMain.getClassMetrics().updateDistanceToObstacle(depth);
        activityMain.getClassMetrics().updateTimestamp(timestamp);
    }

    @Override
    public void onPoseAvailable(TangoPoseData pose)
    {
        activityMain.getClassMetrics().updatePoseData(pose);
    }
}
