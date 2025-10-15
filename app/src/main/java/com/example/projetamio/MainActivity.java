package com.example.projetamio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_START_AT_BOOT = "start_at_boot";
    private static final String WEB_SERVICE_URL = "http://iotlab.telecomnancy.eu:8080/iotlab/rest/data/1/light1/last";

    private TextView tv2;
    private TextView tv4; // Pour afficher le dernier résultat
    private ToggleButton toggleButton1;
    private CheckBox checkBoxStartAtBoot;
    private Button btnFetchData; // Nouveau bouton pour la requête
    private SharedPreferences sharedPreferences;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Création de l'activité");

        // Initialisation du Handler pour les mises à jour UI
        mainHandler = new Handler(Looper.getMainLooper());

        // Initialisation des SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Récupération des composants graphiques
        tv2 = findViewById(R.id.tv2);
        tv4 = findViewById(R.id.tv4);
        toggleButton1 = findViewById(R.id.toggleButton1);
        checkBoxStartAtBoot = findViewById(R.id.checkBoxStartAtBoot);
        btnFetchData = findViewById(R.id.btnFetchData);

        // Initialisation des TextViews
        tv2.setText("arrêté");
        tv4.setText("???");

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

        // Ajout du listener sur le bouton de requête HTTP
        btnFetchData.setOnClickListener(v -> {
            Log.d(TAG, "Bouton 'Fetch Data' pressé - lancement de la requête HTTP");
            fetchLightData();
        });
    }

    /**
     * Méthode pour récupérer les données de luminosité via une requête HTTP GET
     * Exécutée dans un thread séparé pour ne pas bloquer l'UI
     */
    private void fetchLightData() {
        // Créer une TimerTask pour exécuter la requête dans un thread séparé
        TimerTask httpTask = new TimerTask() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                BufferedReader reader = null;

                try {
                    // Création de l'URL
                    URL url = new URL(WEB_SERVICE_URL);

                    // Ouverture de la connexion
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000); // Timeout de 5 secondes
                    connection.setReadTimeout(5000);

                    // Vérification du code de réponse
                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Code de réponse HTTP: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Lecture de la réponse
                        reader = new BufferedReader(
                                new InputStreamReader(connection.getInputStream())
                        );

                        StringBuilder response = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }

                        final String result = response.toString();
                        Log.d(TAG, "Réponse reçue: " + result);

                        // Mise à jour de l'UI sur le thread principal
                        mainHandler.post(() -> {
                            tv4.setText(result);
                            Toast.makeText(MainActivity.this,
                                    "Données reçues avec succès",
                                    Toast.LENGTH_SHORT).show();
                        });

                    } else {
                        Log.e(TAG, "Erreur HTTP: " + responseCode);

                        mainHandler.post(() -> {
                            tv4.setText("Erreur: " + responseCode);
                            Toast.makeText(MainActivity.this,
                                    "Erreur HTTP: " + responseCode,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Exception lors de la requête HTTP", e);

                    mainHandler.post(() -> {
                        tv4.setText("Erreur: " + e.getMessage());
                        Toast.makeText(MainActivity.this,
                                "Erreur: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });

                } finally {
                    // Fermeture des ressources
                    try {
                        if (reader != null) reader.close();
                        if (connection != null) connection.disconnect();
                    } catch (Exception e) {
                        Log.e(TAG, "Erreur lors de la fermeture des ressources", e);
                    }
                }
            }
        };

        // Exécution de la tâche dans un thread séparé
        Timer timer = new Timer();
        timer.schedule(httpTask, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy de l'activité");
    }
}