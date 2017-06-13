package com.activis.jaycee.virtualcane;

import android.util.Log;

class RunnableVibrate implements Runnable
{
    private static final String TAG = RunnableVibrate.class.getSimpleName();
    private static final int VIBRATION_DELAY = 150;

    private ActivityMain activityMain;

    private float depth = 10.f;

    private boolean isRunning = false;

    RunnableVibrate(ActivityMain activityMain)
    {
        this.activityMain = activityMain;
    }

    @Override
    public void run()
    {
        isRunning = true;
        Log.i(TAG, String.format("Average depth is: %f", this.depth));

        long[] pwmSignal = generatePWM(this.depth, VIBRATION_DELAY);

        activityMain.getVibrator().vibrate(pwmSignal, -1);
    }

    void setDepth(float depth)
    {
        this.depth = depth;
        this.run();
    }

    public boolean getIsRunning()
    {
        return this.isRunning;
    }

    public void setIsRunning(boolean isRunning)
    {
        this.isRunning = isRunning;
    }

    long[] generatePWM(float distance, int duration)
    {
        double intensity = -distance + 1.15f;
        intensity = intensity >= 1.f ? 1.f : intensity;

        activityMain.getClassMetrics().updateVibrationIntensity(intensity);
        activityMain.getClassMetrics().writeWiFi();

        long[] pwmSignal = {(long) ((1 - intensity) * duration), (long)(intensity * duration)};

        return pwmSignal;
    }
}
