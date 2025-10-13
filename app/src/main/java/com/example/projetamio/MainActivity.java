package com.example.projetamio;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Création de l'activité");

        // Démarrer le service
        Intent serviceIntent = new Intent(this, MainService.class);
        startService(serviceIntent);
        Log.d(TAG, "Demande de démarrage du service");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Arrêter le service
        Intent serviceIntent = new Intent(this, MainService.class);
        stopService(serviceIntent);
        Log.d(TAG, "Demande d'arrêt du service");
    }
}