package com.example.projetamio;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_START_AT_BOOT = "start_at_boot";

    private TextView tv2;
    private TextView tvSensorData;
    private ToggleButton toggleButton1;
    private CheckBox checkBoxStartAtBoot;
    private SharedPreferences sharedPreferences;

    // BroadcastReceiver pour recevoir les données du service
    private BroadcastReceiver dataReceiver;

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
        tvSensorData = findViewById(R.id.tvSensorData);

        // Vérification que le TextView existe bien
        if (tvSensorData == null) {
            Log.e(TAG, "ERREUR: tvSensorData est NULL après findViewById!");
        } else {
            Log.d(TAG, "tvSensorData initialisé correctement");
        }

        // Initialisation du TextView
        tv2.setText("arrêté");
        tvSensorData.setText("En attente de données...");

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
                tvSensorData.setText("En attente de données...");
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

        // Création du BroadcastReceiver
        createDataReceiver();
        Button btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Crée le menu dans l'ActionBar
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Gère les clics sur les items du menu
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            // Ouvrir l'activité des paramètres
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Crée et configure le BroadcastReceiver pour recevoir les données du service
     */
    private void createDataReceiver() {
        Log.d(TAG, "Création du BroadcastReceiver");

        dataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "==== onReceive APPELÉ ====");
                Log.d(TAG, "Action reçue: " + intent.getAction());

                if (MainService.ACTION_RESULT.equals(intent.getAction())) {
                    Log.d(TAG, "Action correspond à ACTION_RESULT");

                    // Récupération des données de l'Intent
                    String sensorData = intent.getStringExtra(MainService.EXTRA_SENSOR_DATA);
                    long timestamp = intent.getLongExtra(MainService.EXTRA_TIMESTAMP, 0);

                    Log.d(TAG, "Données extraites - sensorData: " + sensorData + ", timestamp: " + timestamp);
                    Log.d(TAG, "tvSensorData est null ? " + (tvSensorData == null));

                    if (sensorData != null) {
                        // Formatage de la date
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE);
                        String time = sdf.format(new Date(timestamp));

                        // Affichage dans le TextView
                        String displayText = "[" + time + "]\n" + sensorData;

                        Log.d(TAG, "Texte à afficher: " + displayText);

                        // Mise à jour sur le thread UI
                        runOnUiThread(() -> {
                            if (tvSensorData != null) {
                                tvSensorData.setText(displayText);
                                Log.d(TAG, "TextView mis à jour avec succès!");
                            } else {
                                Log.e(TAG, "tvSensorData est NULL dans runOnUiThread!");
                            }
                        });
                    } else {
                        Log.e(TAG, "sensorData est NULL dans l'intent!");
                    }

                    Log.d(TAG, "Données reçues du service: " + sensorData);
                } else {
                    Log.w(TAG, "Action ne correspond pas: attendu=" + MainService.ACTION_RESULT + ", reçu=" + intent.getAction());
                }
            }
        };

        Log.d(TAG, "BroadcastReceiver créé");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "==== onResume() ====");

        if (dataReceiver == null) {
            Log.e(TAG, "ERREUR: dataReceiver est NULL dans onResume!");
            createDataReceiver();
        }

        // Enregistrement du receiver quand l'activité est visible
        IntentFilter filter = new IntentFilter(MainService.ACTION_RESULT);
        Log.d(TAG, "IntentFilter créé avec action: " + MainService.ACTION_RESULT);

        registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "BroadcastReceiver enregistré avec succès");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "==== onPause() ====");

        // Désenregistrement du receiver pour éviter les fuites mémoire
        try {
            unregisterReceiver(dataReceiver);
            Log.d(TAG, "BroadcastReceiver désenregistré");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver était déjà désenregistré");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy de l'activité");
    }
}