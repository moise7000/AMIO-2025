package com.example.projetamio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_START_AT_BOOT = "start_at_boot";

    private TextView tv2;
    private ToggleButton toggleButton1;
    private CheckBox checkBoxStartAtBoot;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Création de l'activité");

        // Initialisation des SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Récupération des composants graphiques
        tv2 = findViewById(R.id.tv2);
        toggleButton1 = findViewById(R.id.toggleButton1);
        checkBoxStartAtBoot = findViewById(R.id.checkBoxStartAtBoot);

        // Initialisation du TextView
        tv2.setText("arrêté");

        // Restaurer l'état de la checkbox depuis les préférences
        boolean startAtBoot = sharedPreferences.getBoolean(KEY_START_AT_BOOT, false);
        checkBoxStartAtBoot.setChecked(startAtBoot);
        Log.d(TAG, "État initial de 'Start at boot': " + startAtBoot);

        // Ajout du listener sur le bouton toggle
        toggleButton1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent serviceIntent = new Intent(MainActivity.this, MainService.class);

            if (isChecked) {
                // Bouton pressé => ON
                startService(serviceIntent);
                tv2.setText("en cours");
                Log.d(TAG, "Service démarré via toggle button");
            } else {
                // Bouton pressé => OFF
                stopService(serviceIntent);
                tv2.setText("arrêté");
                Log.d(TAG, "Service arrêté via toggle button");
            }
        });

        // Ajout du listener sur la checkbox "Start at boot"
        checkBoxStartAtBoot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Sauvegarder l'état dans les SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_START_AT_BOOT, isChecked);
            editor.apply();

            Log.d(TAG, "État de la checkbox 'Start at boot' changé: " + isChecked);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy de l'activité");
    }
}