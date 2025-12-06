
package com.example.projetamio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_START_AT_BOOT = "start_at_boot";

    private TextView tv2;
    private ToggleButton toggleButton1;
    private CheckBox checkBoxStartAtBoot;
    private SharedPreferences sharedPreferences;

    private Button fetchDataButton;
    private TextView lightValueTextView;
    private TextView motesDataTextView;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        fetchDataButton.setOnClickListener(v -> fetchData());
    }

    private void fetchData() {
        Log.d(TAG, "fetchData() called");
        executorService.execute(() -> {
            try {
                URL url = new URL("http://iotlab.telecomnancy.eu:8080/iotlab/rest/data/1/light1/last");
                Log.d(TAG, "Requesting URL: " + url.toString());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Request successful. Parsing response...");
                    InputStream responseBody = connection.getInputStream();
                    InputStreamReader responseBodyReader = new InputStreamReader(responseBody, "UTF-8");
                    JsonReader jsonReader = new JsonReader(responseBodyReader);

                    StringBuilder motesData = new StringBuilder();
                    String lastValue = "N/A";
                    String lastTimestamp = "N/A";

                    jsonReader.beginObject(); // Start JSON object
                    while (jsonReader.hasNext()) {
                        String name = jsonReader.nextName();
                        if (name.equals("data")) {
                            jsonReader.beginArray(); // Start "data" array
                            while (jsonReader.hasNext()) {
                                jsonReader.beginObject(); // Start mote object
                                String moteId = "";
                                String value = "";
                                String timestamp = "";
                                while(jsonReader.hasNext()){
                                    String key = jsonReader.nextName();
                                    if(key.equals("mote")){
                                        moteId = jsonReader.nextString();
                                    } else if (key.equals("value")){
                                        value = jsonReader.nextString();
                                    } else if(key.equals("timestamp")){
                                        timestamp = jsonReader.nextString();
                                    } else {
                                        jsonReader.skipValue();
                                    }
                                }
                                motesData.append("Mote: ").append(moteId).append("\n");
                                motesData.append("Value: ").append(value).append("\n");
                                motesData.append("Timestamp: ").append(timestamp).append("\n\n");
                                lastValue = value;
                                lastTimestamp = timestamp;
                                jsonReader.endObject(); // End mote object
                            }
                            jsonReader.endArray(); // End "data" array
                        } else {
                            jsonReader.skipValue();
                        }
                    }
                    jsonReader.endObject(); // End JSON object

                    final String finalLastValue = lastValue;
                    final String finalLastTimestamp = lastTimestamp;
                    final String finalMotesData = motesData.toString();

                    handler.post(() -> {
                        Log.d(TAG, "Updating UI with fetched data.");
                        lightValueTextView.setText("Light Value: " + finalLastValue + " at " + finalLastTimestamp);
                        motesDataTextView.setText(finalMotesData);
                    });

                } else {
                    Log.e(TAG, "Request failed. Response code: " + responseCode);
                    handler.post(() -> {
                        Toast.makeText(MainActivity.this, "Error: " + responseCode, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during data fetch", e);
                handler.post(() -> {
                    Toast.makeText(MainActivity.this, "Error fetching data", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        Log.d(TAG, "onDestroy de l'activité");
    }
}
