package com.example.projetamio;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
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
        tvSensorData.setMovementMethod(new ScrollingMovementMethod()); // Rendre le TextView scrollable

        // Initialisation du TextView
        tv2.setText("arrêté");
        tvSensorData.setText("En attente de données...");

        // Restaurer l'état de la checkbox depuis les préférences
        boolean startAtBoot = sharedPreferences.getBoolean(KEY_START_AT_BOOT, false);
        checkBoxStartAtBoot.setChecked(startAtBoot);

        // Ajout du listener sur le bouton toggle
        toggleButton1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent serviceIntent = new Intent(MainActivity.this, MainService.class);
            if (isChecked) {
                startService(serviceIntent);
                tv2.setText("en cours");
            } else {
                stopService(serviceIntent);
                tv2.setText("arrêté");
                tvSensorData.setText("En attente de données...");
            }
        });

        // Ajout du listener sur la checkbox "Start at boot"
        checkBoxStartAtBoot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_START_AT_BOOT, isChecked);
            editor.apply();
        });

        // Création du BroadcastReceiver
        createDataReceiver();
        Button btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createDataReceiver() {
        dataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MainService.ACTION_RESULT.equals(intent.getAction())) {
                    String sensorData = intent.getStringExtra(MainService.EXTRA_SENSOR_DATA);
                    long timestamp = intent.getLongExtra(MainService.EXTRA_TIMESTAMP, 0);

                    if (sensorData != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE);
                        String time = sdf.format(new Date(timestamp));
                        final String displayText = "Dernière mise à jour : " + time + "\n\n" + sensorData;
                        runOnUiThread(() -> tvSensorData.setText(displayText));
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(MainService.ACTION_RESULT);
        registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(dataReceiver);
    }
}