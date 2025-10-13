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
        Log.d(TAG, "Service créé");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service démarré");
        return START_STICKY; // Mode sticky pour redémarrage automatique
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service détruit");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Service non lié
    }
}