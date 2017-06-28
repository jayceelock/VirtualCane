package com.activis.jaycee.virtualcane;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.Renderer;

import javax.microedition.khronos.opengles.GL10;

public class ClassRenderer extends Renderer
{
    private static final String TAG = ClassRenderer.class.getSimpleName();
    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;
    private static final int MAX_NUMBER_OF_POINTS = 60000;

    private float[] textureCoords0 = new float[]{0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};

    public boolean isSceneCameraConfigured;

    private ATexture tangoCameraTexture;
    private ScreenQuad screenBackgroundQuad;
    private Sphere point;
    private ClassPointcloud pointCloud;

    public ClassRenderer(Context context)
    {
        super(context);
    }

    @Override
    protected void initScene()
    {
        /* Create a material quad covering entire screen
           and assign the Tango's camera view to it
          */
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0.f);

        if(screenBackgroundQuad == null)
        {
            screenBackgroundQuad = new ScreenQuad();
            screenBackgroundQuad.getGeometry().setTextureCoords(textureCoords0);
        }

        /* Create new camera texture and add to the scene */
        tangoCameraTexture = new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try
        {
            tangoCameraMaterial.addTexture(tangoCameraTexture);
            screenBackgroundQuad.setMaterial(tangoCameraMaterial);
        }
        catch(ATexture.TextureException e)
        {
            Log.e(TAG, "Texture exception: " + e);
        }
        getCurrentScene().addChild(screenBackgroundQuad);

        /* Initialise Depth Point */
        Material pointMaterial = new Material();
        pointMaterial.setColor(Color.GREEN);
        point = new Sphere(0.01f, 24, 24);
        point.setMaterial(pointMaterial);
        point.setPosition(0, 0, 1);

        getCurrentScene().addChild(point);

        pointCloud = new ClassPointcloud(MAX_NUMBER_OF_POINTS, 4);
        getCurrentScene().addChild(pointCloud);

        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);
        getCurrentCamera().setFieldOfView(37.5);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) { }

    @Override
    public void onTouchEvent(MotionEvent event) { }

    public boolean togglePointCloud(boolean isCloudEnabled)
    {
        pointCloud.setVisible(isCloudEnabled);

        return !isCloudEnabled;
    }

    public void setDepthPoint(Vector3 position)
    {
        if(point != null)
        {
            point.setPosition(position.x, position.y, position.z);
            
            if(Math.abs(position.z) > 1.15)
            {
                point.setColor(Color.GREEN);
            }
            else if(Math.abs(position.z) < 0.3)
            {
                point.setColor(Color.RED);
            }

            else
            {
                Vector3 color = new Vector3((Math.abs(position.z) * (-255 / 0.85) + 345) / 255, (Math.abs(position.z) * 255 / 0.85 - 90) / 255, 0.0);
                point.setColor(color);
                Log.d(TAG, String.format("r: %f g: %g b:%f", color.x, color.y, color.z));
            }
        }
    }

    public Vector3 getDepthPointPosition()
    {
        return getCurrentScene().getChildrenCopy().get(1).getPosition();
    }

    /* Handle screen orientation changes.
       Run in OpenGL thread.
      */
    public void updateColorCameraTextureUvGlThread(int rotation)
    {
        if (screenBackgroundQuad == null)
        {
            screenBackgroundQuad = new ScreenQuad();
        }

        float[] textureCoords = TangoSupport.getVideoOverlayUVBasedOnDisplayRotation(textureCoords0, rotation);
        screenBackgroundQuad.getGeometry().setTextureCoords(textureCoords, true);
        screenBackgroundQuad.getGeometry().reload();
    }

    /**
     * Updates the rendered point cloud. For this, we need the point cloud data and the device pose
     * at the time the cloud data was acquired.
     * NOTE: This needs to be called from the OpenGL rendering thread.
     */
    public void updatePointCloud(TangoPointCloudData pointCloudData, float[] openGlTdepth)
    {
        pointCloud.updateCloud(pointCloudData.numPoints, pointCloudData.points);
        Matrix4 openGlTdepthMatrix = new Matrix4(openGlTdepth);
        pointCloud.setPosition(openGlTdepthMatrix.getTranslation());
        // Conjugating the Quaternion is needed because Rajawali uses left-handed convention.
        pointCloud.setOrientation(new Quaternion().fromMatrix(openGlTdepthMatrix));//.conjugate());
    }

    /* Update the scene camera based on the provided pose in Tango start of service frame.
       The camera pose should match the pose of the camera color at the time of the last rendered
       RGB frame, which can be retrieved with this.getTimestamp();
       This must be called from the OpenGL render thread
     */
    public void updateRenderCameraPose(TangoPoseData cameraPose)
    {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);

        /* Conjugating the Quaternion is needed because Rajawali uses left-handed convention for quaternions. */
        getCurrentCamera().setRotation(quaternion.conjugate());
        getCurrentCamera().setPosition(translation[0], translation[1], translation[2]);
    }

    /* Returns the Texture ID on which the camera content is rendered.
       This must be called from the OpenGL render thread
     */
    public int getTextureId()
    {
        return tangoCameraTexture == null ? -1 : tangoCameraTexture.getTextureId();
    }

    /* Override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height)
    {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        isSceneCameraConfigured = false;
    }

    public boolean getIsSceneCameraConfigured()
    {
        return this.isSceneCameraConfigured;
    }

    /* Sets the projection matrix for the scene camera to match the parameters of the color camera */
    public void setProjectionMatrix(float[] matrixFloats)
    {
        getCurrentCamera().setProjectionMatrix(new Matrix4(matrixFloats));
    }
}