package com.example.projetamio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Activité principale de l'application.
 * <p>
 * Gère l'interface utilisateur, l'affichage des données reçues du service,
 * et le contrôle manuel (Start/Stop) du service et des préférences de démarrage.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_START_AT_BOOT = "start_at_boot";
    private static final int LIGHT_THRESHOLD = 250; // Seuil TP2 Exercice 2

    // Éléments de l'interface TP1 Exercice 3
    private TextView tv2; // Status
    private TextView tv4; // Last Result
    private TextView tv6; // Last Alert

    private ToggleButton toggleButton1;
    private CheckBox checkBoxStartAtBoot;
    private Button fetchDataButton;
    private TextView lightValueTextView;
    private TextView motesDataTextView;
    private SharedPreferences sharedPreferences;

    private final Map<String, MoteData> motes = new HashMap<>();

    private static class MoteData {
        String value;
        boolean lightOn;
    }

    private BroadcastReceiver updateUIReciver;

    /**
     * Initialisation de l'activité.
     * Configure les vues, charge les préférences (boot), initialise le ToggleButton
     * et enregistre le BroadcastReceiver pour écouter le service.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Configuration de la Toolbar (TP3 Exercice 2)
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Log.d(TAG, "Création de l'activité"); // TP1 Exercice 1

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Initialisation des vues
        tv2 = findViewById(R.id.tv2);
        tv4 = findViewById(R.id.tv4); // Ajouté pour TP1 Ex 3
        tv6 = findViewById(R.id.tv6); // Ajouté pour TP1 Ex 3

        toggleButton1 = findViewById(R.id.toggleButton1);
        checkBoxStartAtBoot = findViewById(R.id.checkBoxStartAtBoot);
        fetchDataButton = findViewById(R.id.fetchDataButton);
        lightValueTextView = findViewById(R.id.lightValueTextView);
        motesDataTextView = findViewById(R.id.motesDataTextView);

        // État initial de l'interface
        tv2.setText("Arrêté");
        tv4.setText("Aucune donnée");
        tv6.setText("R.A.S.");

        // Gestion de la CheckBox "Start at Boot" (TP1 Exercice 4)
        boolean startAtBoot = sharedPreferences.getBoolean(KEY_START_AT_BOOT, false);
        checkBoxStartAtBoot.setChecked(startAtBoot);
        Log.d(TAG, "État initial de 'Start at boot': " + startAtBoot);

        // Gestion du ToggleButton pour le Service (TP1 Exercice 3)
        toggleButton1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent serviceIntent = new Intent(MainActivity.this, MainService.class);
            if (isChecked) {
                startService(serviceIntent);
                tv2.setText("En cours");
                Log.d(TAG, "Service démarré via toggle button");
            } else {
                stopService(serviceIntent);
                tv2.setText("Arrêté");
                Log.d(TAG, "Service arrêté via toggle button");
            }
        });

        checkBoxStartAtBoot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_START_AT_BOOT, isChecked);
            editor.apply();
            Log.d(TAG, "État de la checkbox 'Start at boot' changé: " + isChecked);
        });

        // Le bouton manuel est désactivé car le service gère tout (TP3),
        // mais on le garde visible comme demandé au TP2.
        fetchDataButton.setEnabled(false);

        // Enregistrement du BroadcastReceiver pour mettre à jour l'UI (TP2)
        IntentFilter filter = new IntentFilter(MainService.ACTION_UPDATE_UI);
        updateUIReciver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(MainService.EXTRA_DATA)) {
                    HashMap<String, String> receivedData = (HashMap<String, String>) intent.getSerializableExtra(MainService.EXTRA_DATA);
                    updateMotesData(receivedData);
                    updateUi();
                }
            }
        };
        // Note: RECEIVER_EXPORTED est requis pour Android 14+, sinon utilisez 0 ou Context.RECEIVER_NOT_EXPORTED
        registerReceiver(updateUIReciver, filter, Context.RECEIVER_EXPORTED);
    }

    /**
     * Crée le menu d'options dans la barre d'outils (Settings).
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TP3 Exercice 2 : Menu Settings
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Gère les clics sur les éléments du menu (Lancement SettingsActivity).
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Met à jour le modèle de données local avec les nouvelles valeurs reçues.
     * Parse les valeurs en float et détermine l'état (lightOn) selon le seuil.
     *
     * @param newMotesData Map contenant les données brutes.
     */
    private void updateMotesData(Map<String, String> newMotesData) {
        // TP2 Exercice 2 : Stockage et seuillage des données
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

    /**
     * Rafraîchit l'interface utilisateur.
     * Construit une chaîne HTML pour afficher l'état de chaque mote.
     * Utilise le rouge et le gras si une lumière est détectée.
     */
    private void updateUi() {
        Log.d(TAG, "Updating UI with fetched data.");
        StringBuilder motesDisplayText = new StringBuilder();
        String lastValue = "N/A";
        boolean alertDetected = false;
        String alertMote = "";

        for (Map.Entry<String, MoteData> entry : motes.entrySet()) {
            MoteData data = entry.getValue();

            // Formatage HTML pour mise en évidence
            motesDisplayText.append("<b>Mote: ").append(entry.getKey()).append("</b><br/>");

            if (data.lightOn) {
                // TP2 Exercice 2 : ROUGE et GRAS pour les lumières allumées
                motesDisplayText.append("<font color='red'><b>LUMIÈRE ALLUMÉE</b></font>");
                alertDetected = true;
                alertMote = entry.getKey();
            } else {
                motesDisplayText.append("<font color='#666666'>Lumière éteinte</font>");
            }
            motesDisplayText.append(" (valeur: ").append(data.value).append(")<br/><br/>");
            lastValue = data.value;
        }

        // Mise à jour case TV4 (Last Result) - TP1/TP2
        String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tv4.setText(lastValue + " (" + currentTime + ")");

        // Mise à jour case TV6 (Last Alert) - TP1/TP3
        if (alertDetected) {
            tv6.setText("ALERTE Mote " + alertMote);
            tv6.setTextColor(Color.RED);
        } else {
            tv6.setText("R.A.S.");
            tv6.setTextColor(Color.BLACK);
        }

        // Mise à jour de la zone de défilement principale
        lightValueTextView.setText("Dernière valeur brute: " + lastValue);
        motesDataTextView.setText(Html.fromHtml(motesDisplayText.toString(), Html.FROM_HTML_MODE_COMPACT));
    }

    /**
     * Nettoyage à la destruction de l'activité.
     * Désenregistre le BroadcastReceiver pour éviter les fuites de mémoire.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateUIReciver != null) {
            unregisterReceiver(updateUIReciver);
        }
        Log.d(TAG, "onDestroy de l'activité");
    }
}