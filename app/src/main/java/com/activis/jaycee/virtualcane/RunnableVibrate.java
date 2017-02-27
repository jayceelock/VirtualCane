package com.activis.jaycee.virtualcane;

import android.util.Log;

class RunnableVibrate implements Runnable
{
    private static final String TAG = RunnableVibrate.class.getSimpleName();
    private static final int VIBRATION_DELAY = 150;

    private ActivityMain activityMain;

    private double depth = 0.0;

    RunnableVibrate(ActivityMain activityMain)
    {
        this.activityMain = activityMain;
    }

    @Override
    public void run()
    {
        Log.i(TAG, String.format("Average depth is: %f", this.depth));

        long[] pwmSignal = generatePWM(this.depth, VIBRATION_DELAY);

        activityMain.getVibrator().vibrate(pwmSignal, -1);
    }

    void setDepth(double depth)
    {
        this.depth = depth;
        this.run();
    }

    long[] generatePWM(double distance, int duration)
    {
        double intensity = -distance + 1.15f;
        intensity = intensity >= 1.f ? 1.f : intensity;

        activityMain.getClassMetrics().updateVibrationIntensity(intensity);
        activityMain.getClassMetrics().writeWiFi();

        long[] pwmSignal = {(long) ((1 - intensity) * duration), (long)(intensity * duration)};

        return pwmSignal;
    }
}
