package com.activis.jaycee.virtualcane;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import android.opengl.Matrix;
import android.util.Log;

import org.rajawali3d.scene.ASceneFrameCallback;

public class ClassRajawaliFrameCallback extends ASceneFrameCallback
{
    private static final String TAG = ClassRajawaliFrameCallback.class.getSimpleName();

    private ActivityMain activityMain;

    public ClassRajawaliFrameCallback(ActivityMain activityMain)
    {
        this.activityMain = activityMain;
    }

    @Override
    public void onPreFrame(long sceneTime, double deltaTime)
    {
        /* Prevent multiple access to the Tango's camera callback */
        try
        {
            synchronized (activityMain)
            {
                /* Don't execute if we're not connected */
                if(!activityMain.getIsTangoConnected())
                {
                    return;
                }

                /* Set up scene camera to match camera intrinsics */
                if(!activityMain.getRenderer().getIsSceneCameraConfigured())
                {
                    TangoCameraIntrinsics cameraIntrinsics = TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(TangoCameraIntrinsics.TANGO_CAMERA_COLOR, activityMain.getDisplayRotation());
                    activityMain.getRenderer().setProjectionMatrix(projectionMatrixFromCameraIntrinsics(cameraIntrinsics));
                }
                /* Connect the camera texture to the OpenGL Texture if necessary
                   When the OpenGL context is recycled, Rajawali may regenerate the texture with a different ID.
                */
                if(activityMain.getConnectedGLThreadID() != activityMain.getRenderer().getTextureId())
                {
                    activityMain.getTango().connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR, activityMain.getRenderer().getTextureId());
                    activityMain.setConnectedGLThreadID(activityMain.getRenderer().getTextureId());
                    Log.d(TAG, "Connected to texture ID: " + activityMain.getRenderer().getTextureId());
                }

                /* Update the frame if there's a new RGB frame */
                 if (activityMain.getIsFrameAvailable().compareAndSet(true, false))
                 {
                    activityMain.setRGBTimestamp(activityMain.getTango().updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR));
                 }

                 /* If new frame was rendered, update the camera pose */
                 if(activityMain.getRGBTimestamp() > activityMain.getCameraPoseTimestamp())
                 {
                     /* Calculate the camera color pose at the camera frame update time in OpenGL engine. */
                     TangoPoseData poseData = TangoSupport.getPoseAtTime(activityMain.getRGBTimestamp(),
                             TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                             TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                             TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                             TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                             activityMain.getDisplayRotation());
                     if(poseData.statusCode == TangoPoseData.POSE_VALID)
                     {
                         activityMain.getRenderer().updateRenderCameraPose(poseData);
                         activityMain.setCameraPoseTimestamp(poseData.timestamp);
                     }
                     else
                     {
                         Log.w(TAG, "Can't get device pose at time: " + activityMain.getRGBTimestamp());
                     }
                 }
            }
        }
        catch(TangoErrorException e)
        {
            Log.e(TAG, "TangoErrorException: " + e);
        }
        catch(Throwable t)
        {
            Log.e(TAG, "Error on OpenGL thread: " + t);
        }
    }

    @Override
    public void onPreDraw(long sceneTime, double deltaTime) { }

    @Override
    public void onPostFrame(long sceneTime, double deltaTime) { }

    @Override
    public boolean callPreFrame()
    {
        return true;
    }

    /* Use Tango camera intrinsics to calculate the projection matrix for the Rajawali scene */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics)
    {
        float cx = (float) intrinsics.cx;
        float cy = (float) intrinsics.cy;
        float width = (float) intrinsics.width;
        float height = (float) intrinsics.height;
        float fx = (float) intrinsics.fx;
        float fy = (float) intrinsics.fy;

        float near = 0.1f;
        float far = 100;

        float xScale = near / fx;
        float yScale = near / fy;
        float xOffset = (cx - (width / 2.0f)) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        float yOffset = -(cy - (height / 2.0f)) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                xScale * -width / 2.0f - xOffset,
                xScale * width / 2.0f - xOffset,
                yScale * -height / 2.0f - yOffset,
                yScale * height / 2.0f - yOffset,
                near, far);
        return m;
    }
}
