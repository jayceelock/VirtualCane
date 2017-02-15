package com.activis.jaycee.virtualcane;

import android.os.Vibrator;
import android.util.Log;

public class RunnableVibrate implements Runnable
{
    private static final String TAG = RunnableVibrate.class.getSimpleName();
    private static final int VIBRATION_DELAY = 150;

    private Vibrator vibrator;

    private double depth = 0.0;

    public RunnableVibrate(Vibrator vibrator)
    {
        this.vibrator = vibrator;
    }

    @Override
    public void run()
    {
        Log.i(TAG, String.format("Average depth is: %f", this.depth));

        long[] pwmSignal = generatePWM(this.depth, VIBRATION_DELAY);

        vibrator.vibrate(pwmSignal, -1);
    }

    public void setDepth(double depth)
    {
        this.depth = depth;
        this.run();
    }

    public double getDepth()
    {
        return this.depth;
    }

    public long[] generatePWM(double distance, int duration)
    {
        double intensity = -distance + 1.15f;
        intensity = intensity >= 1.f ? 1.f : intensity;

        long[] pwmSignal = {(long) ((1 - intensity) * duration), (long) (intensity * duration)};

        return pwmSignal;
    }
}
