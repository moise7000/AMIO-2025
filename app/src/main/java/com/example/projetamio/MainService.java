package com.example.projetamio;

import android.app.Service;
import android.util.Log;
import android.content.Intent;
import android.os.IBinder;



public class MainService extends Service {

    private static final String TAG = "MainService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service créé - onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service démarré - onStartCommand()");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service détruit - onDestroy()");

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
