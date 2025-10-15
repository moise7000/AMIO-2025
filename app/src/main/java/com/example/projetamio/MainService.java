package com.example.projetamio;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class MainService extends Service {

    private static final String TAG = "MainService";
    private Timer timer;
    private TimerTask timerTask;
    private static final long PERIOD = 30_000L; // 30 secondes

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate() du service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() - démarrage du timer");

        if (timer == null) {
            timer = new Timer();

            timerTask = new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "TimerTask exécutée — toutes les 30 secondes");
                    scheduleNextRun();
                }
            };

            // Premier lancement
            timer.schedule(timerTask, PERIOD);
        }

        return START_STICKY;
    }

    private void scheduleNextRun() {
        // ⚠️ recréer une nouvelle tâche sinon IllegalStateException
        timerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "TimerTask exécutée — toutes les 30 secondes");
                scheduleNextRun();
            }
        };
        timer.schedule(timerTask, PERIOD);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy() - arrêt du timer");
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
