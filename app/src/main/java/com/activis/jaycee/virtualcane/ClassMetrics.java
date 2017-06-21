package com.activis.jaycee.virtualcane;

import android.os.AsyncTask;
import android.util.Log;

import com.google.atap.tangoservice.TangoPoseData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;

class ClassMetrics
{
    private static final String TAG = ClassMetrics.class.getSimpleName();

    private WifiDataSend dataStreamer = null;

    private static final String DELIMITER = ",";

    private double timeStamp;
    private TangoPoseData poseData;

    private double vibrationIntensity = 0.f;
    private double distanceToObstacle = 0.0;

    ClassMetrics() { }

    /* Trigger CSV update, write to wifi */
    void writeWiFi()
    {
        /*
        time - x - y - z - roll - pitch (deg) - yaw - distToObs - vibration
         */

        try
        {
            String csvString = "";

            csvString += String.valueOf(this.timeStamp);
            csvString += DELIMITER;

            csvString += String.valueOf(poseData.translation[0]);
            csvString += DELIMITER;
            csvString += String.valueOf(poseData.translation[1]);
            csvString += DELIMITER;
            csvString += String.valueOf(poseData.translation[2]);
            csvString += DELIMITER;

            csvString += String.valueOf(poseData.rotation[0]);
            csvString += DELIMITER;
            csvString += String.valueOf(poseData.rotation[1]);
            csvString += DELIMITER;
            csvString += String.valueOf(poseData.rotation[2]);
            csvString += DELIMITER;
            csvString += String.valueOf(poseData.rotation[3]);
            csvString += DELIMITER;

            csvString += String.valueOf(distanceToObstacle);
            csvString += DELIMITER;

            csvString += String.valueOf(vibrationIntensity);
            csvString += DELIMITER;

            csvString += String.valueOf(0);
            csvString += DELIMITER;

            csvString += String.valueOf(0);
            csvString += DELIMITER;

        /* WRITE TO WIFI PORT */
            if (dataStreamer == null || dataStreamer.getStatus() != AsyncTask.Status.RUNNING)
            {
                Log.d(TAG, "wifi transmitting");
                dataStreamer = new WifiDataSend();
                dataStreamer.execute(csvString);
            }
        }
        catch(NullPointerException e)
        {
            Log.e(TAG, "NullPointerException: " + e);
        }
    }

    void updateTimestamp(double timestamp) { this.timeStamp = timestamp; }
    void updatePoseData(TangoPoseData pose) { this.poseData = pose; }
    void updateDistanceToObstacle(double distance) { this.distanceToObstacle = distance; }
    void updateVibrationIntensity(double intensity) { this.vibrationIntensity = intensity; }

    private class WifiDataSend extends AsyncTask<String, Void, Void>
    {
        private String serverIdAddress = "10.18.12.35";
        private int connectionPort = 6666;

        WifiDataSend(){ }

        @Override
        protected Void doInBackground(String... strings)
        {
            try
            {
                Socket socket = new Socket(serverIdAddress, connectionPort);
                OutputStream stream = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(stream);

                int charsRead;
                int bufferLen = 1024;
                char[] tempBuffer = new char[bufferLen];

                BufferedReader bufferedReader = new BufferedReader(new StringReader(strings[0]));

                while((charsRead = bufferedReader.read(tempBuffer, 0, bufferLen)) != -1)
                {
                    writer.print(tempBuffer);
                }
                writer.write("\n");

                writer.flush();
                writer.close();

                socket.close();
            }
            catch(IOException e)
            {
                Log.e(TAG, "Wifi write error: ", e);
            }

            return null;
        }
    }

}
