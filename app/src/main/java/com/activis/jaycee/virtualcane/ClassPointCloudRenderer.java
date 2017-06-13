package com.activis.jaycee.virtualcane;

import android.graphics.Color;
import android.util.Log;

import com.google.atap.tangoservice.TangoPointCloudData;

import org.rajawali3d.materials.Material;

import java.nio.FloatBuffer;

public class ClassPointCloudRenderer extends Points
{
    public static final String TAG = ClassPointCloudRenderer.class.getSimpleName();

    public static final float CLOUD_MAX_Z = 5;

    private float[] mColorArray;
    private final int[] mPalette;
    public static final int PALETTE_SIZE = 360;
    public static final float HUE_BEGIN = 0;
    public static final float HUE_END = 320;

    public ClassPointCloudRenderer(int maxPoints, int pointsPerSample)
    {
        super(maxPoints, true);
        mPalette = createPalette();
        mColorArray = new float[maxPoints * pointsPerSample];
        Material m = new Material();
        m.useVertexColors(true);
        setMaterial(m);
    }

    /**
     * Pre-calculate a palette to be used to translate between point distance and RGB color.
     */
    private int[] createPalette()
    {
        int[] palette = new int[PALETTE_SIZE];
        float[] hsv = new float[3];
        hsv[1] = hsv[2] = 1;
        for (int i = 0; i < PALETTE_SIZE; i++)
        {
            hsv[0] = (HUE_END - HUE_BEGIN) * i / PALETTE_SIZE + HUE_BEGIN;
            palette[i] = Color.HSVToColor(hsv);
        }
        return palette;
    }

    /**
     * Calculate the right color for each point in the point cloud.
     */
    private void calculateColors(int pointCount, FloatBuffer pointCloudBuffer)
    {
        float[] points = new float[pointCount * 4];
        //pointCloudBuffer.rewind();
        pointCloudBuffer.get(points);
        //pointCloudBuffer.rewind();

        int color;
        int colorIndex;
        float z;
        for (int i = 0; i < pointCount; i++)
        {
            z = points[i * 3 + 2];
            colorIndex = (int) Math.min(z / CLOUD_MAX_Z * mPalette.length, mPalette.length - 1);
            colorIndex = Math.max(colorIndex, 0);
            color = mPalette[colorIndex];
            mColorArray[i * 4] = Color.red(color) / 255.f;
            mColorArray[i * 4 + 1] = Color.green(color) / 255.f;
            mColorArray[i * 4 + 2] = Color.blue(color) / 255.f;
            mColorArray[i * 4 + 3] = Color.alpha(color) / 255.f;
        }
    }

    /*
       Update the points and colors in the point cloud.
     */
    public void updateCloud(TangoPointCloudData cloud)
    {
        if(cloud != null)
        {
            Log.d(TAG, "Updating point cloud");
            calculateColors(cloud.numPoints, cloud.points);
            updatePoints(cloud.numPoints, cloud.points, mColorArray);
        }
    }
}
