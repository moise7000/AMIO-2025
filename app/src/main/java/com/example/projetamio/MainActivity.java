package com.example.projetamio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_START_AT_BOOT = "start_at_boot";
    private static final int LIGHT_THRESHOLD = 250;

    private TextView tv2;
    private ToggleButton toggleButton1;
    private CheckBox checkBoxStartAtBoot;
    private SharedPreferences sharedPreferences;

    private Button fetchDataButton;
    private TextView lightValueTextView;
    private TextView motesDataTextView;

    private final Map<String, MoteData> motes = new HashMap<>();

    private static class MoteData {
        String value;
        boolean lightOn;
    }

    private BroadcastReceiver updateUIReciver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Log.d(TAG, "Création de l'activité");

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        tv2 = findViewById(R.id.tv2);
        toggleButton1 = findViewById(R.id.toggleButton1);
        checkBoxStartAtBoot = findViewById(R.id.checkBoxStartAtBoot);
        fetchDataButton = findViewById(R.id.fetchDataButton);
        lightValueTextView = findViewById(R.id.lightValueTextView);
        motesDataTextView = findViewById(R.id.motesDataTextView);

        tv2.setText("arrêté");

        boolean startAtBoot = sharedPreferences.getBoolean(KEY_START_AT_BOOT, false);
        checkBoxStartAtBoot.setChecked(startAtBoot);
        Log.d(TAG, "État initial de 'Start at boot': " + startAtBoot);

        toggleButton1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent serviceIntent = new Intent(MainActivity.this, MainService.class);
            if (isChecked) {
                startService(serviceIntent);
                tv2.setText("en cours");
                Log.d(TAG, "Service démarré via toggle button");
            } else {
                stopService(serviceIntent);
                tv2.setText("arrêté");
                Log.d(TAG, "Service arrêté via toggle button");
            }
        });

        checkBoxStartAtBoot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_START_AT_BOOT, isChecked);
            editor.apply();
            Log.d(TAG, "État de la checkbox 'Start at boot' changé: " + isChecked);
        });

        fetchDataButton.setEnabled(false);

        IntentFilter filter = new IntentFilter(MainService.ACTION_UPDATE_UI);
        updateUIReciver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                HashMap<String, String> receivedData = (HashMap<String, String>) intent.getSerializableExtra(MainService.EXTRA_DATA);
                updateMotesData(receivedData);
                updateUi();
            }
        };
        registerReceiver(updateUIReciver, filter, RECEIVER_EXPORTED);

        Button btnTempSettings = findViewById(R.id.btn_temp_settings);
        btnTempSettings.setOnClickListener(v -> {
            // Lancement explicite de l'activité des paramètres
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
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

    private void updateMotesData(Map<String, String> newMotesData) {
        for (Map.Entry<String, String> entry : newMotesData.entrySet()) {
            MoteData moteData = motes.get(entry.getKey());
            if (moteData == null) {
                moteData = new MoteData();
            }
            moteData.value = entry.getValue();
            try {
                float lightValue = Float.parseFloat(moteData.value);
                moteData.lightOn = lightValue > LIGHT_THRESHOLD;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Could not parse light value", e);
                moteData.lightOn = false;
            }
            motes.put(entry.getKey(), moteData);
        }
    }

    private void updateUi() {
        Log.d(TAG, "Updating UI with fetched data.");
        StringBuilder motesDisplayText = new StringBuilder();
        String lastValue = "N/A";

        for (Map.Entry<String, MoteData> entry : motes.entrySet()) {
            MoteData data = entry.getValue();

            // Formatage HTML pour mise en évidence
            motesDisplayText.append("<b>Mote: ").append(entry.getKey()).append("</b><br/>");

            if (data.lightOn) {
                // ROUGE et GRAS pour les lumières allumées (Mise en évidence TP3)
                motesDisplayText.append("<font color='red'><b>LUMIÈRE ALLUMÉE</b></font>");
            } else {
                motesDisplayText.append("<font color='#666666'>Lumière éteinte</font>");
            }
            motesDisplayText.append(" (valeur: ").append(data.value).append(")<br/><br/>");
            lastValue = data.value;
        }

        lightValueTextView.setText("Dernière valeur brute: " + lastValue);

        // Interprétation du HTML
        motesDataTextView.setText(Html.fromHtml(motesDisplayText.toString(), Html.FROM_HTML_MODE_COMPACT));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(updateUIReciver);
        Log.d(TAG, "onDestroy de l'activité");
    }
}