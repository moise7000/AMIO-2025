package com.example.projetamio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.JsonReader;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainService extends Service {

    private static final String TAG = "MainService";
    private Timer timer;
    private static final long PERIOD = 30_000L; // 30 secondes
    private static final int LIGHT_THRESHOLD = 250;
    private static final String CHANNEL_ID = "MoteStateChangeChannel";
    public static final String ACTION_UPDATE_UI = "com.example.projetamio.UPDATE_UI";
    public static final String EXTRA_DATA = "extra_data";

    private final Map<String, Boolean> previousMoteStates = new HashMap<>();
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        Log.d(TAG, "onCreate() du service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() - démarrage du timer");
        if (timer == null) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    fetchData();
                }
            }, 0, PERIOD);
        }
        return START_STICKY;
    }

    private void fetchData() {
        Log.d(TAG, "Fetching data from webservice");
        try {
            URL url = new URL("http://iotlab.telecomnancy.eu:8080/iotlab/rest/data/1/light1/last");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream responseBody = connection.getInputStream();
                InputStreamReader responseBodyReader = new InputStreamReader(responseBody, "UTF-8");
                JsonReader jsonReader = new JsonReader(responseBodyReader);
                Map<String, String> newMotesData = new HashMap<>();

                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if (name.equals("data")) {
                        jsonReader.beginArray();
                        while (jsonReader.hasNext()) {
                            jsonReader.beginObject();
                            String moteId = "";
                            String value = "";
                            while (jsonReader.hasNext()) {
                                String key = jsonReader.nextName();
                                if (key.equals("mote")) {
                                    moteId = jsonReader.nextString();
                                } else if (key.equals("value")) {
                                    value = jsonReader.nextString();
                                } else {
                                    jsonReader.skipValue();
                                }
                            }
                            processMoteData(moteId, value);
                            newMotesData.put(moteId, value);
                            jsonReader.endObject();
                        }
                        jsonReader.endArray();
                    } else {
                        jsonReader.skipValue();
                    }
                }
                jsonReader.endObject();
                broadcastUpdate(newMotesData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during data fetch", e);
        }
    }

    private void processMoteData(String moteId, String value) {
        try {
            float lightValue = Float.parseFloat(value);
            boolean isLightOn = lightValue > LIGHT_THRESHOLD;

            Boolean previousState = previousMoteStates.get(moteId);

            // TP3: On détecte une "nouvelle lumière qui vient d'être allumée" (FALSE -> TRUE)
            if (previousState != null && !previousState && isLightOn) {
                Log.d(TAG, "State change detected (OFF->ON) for mote " + moteId);

                // TP3 Exercice 3: Action matériel (Vibreur)
                vibrate();

                // TP3: Logique conditionnelle pour Email vs Notification
                checkConditionsAndAlert(moteId, isLightOn);
            }
            previousMoteStates.put(moteId, isLightOn);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Could not parse light value", e);
        }
    }

    private void checkConditionsAndAlert(String moteId, boolean isLightOn) {
        // 1. Récupération des préférences (TP3 Exercice 2)
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String emailDest = prefs.getString("pref_email_dest", "destinataire@example.com");

        // Valeurs par défaut selon l'énoncé si non configuré
        int startHour = 19;
        int endHour = 23;
        try {
            startHour = Integer.parseInt(prefs.getString("pref_hour_start", "19"));
            endHour = Integer.parseInt(prefs.getString("pref_hour_end", "23"));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Erreur parsing heures préférences", e);
        }

        // 2. Vérification Temporelle (Calendar)
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Dimanche, 7=Samedi

        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);

        // Plage "Soirée" (ex: 19h-23h)
        boolean isEveningRange = (hour >= startHour && hour < endHour);

        // Plage "Nuit" (ex: 23h-06h) - NB: énoncé TP3 dit "semaine entre 23h et 6h"
        boolean isNightRange = (hour >= endHour || hour < 6);

        // 3. Application des règles du TP3

        // Règle 1: Notification -> Semaine entre 19h et 23h
        if (!isWeekend && isEveningRange) {
            Log.d(TAG, "Condition Notification remplie (Semaine soirée)");
            sendNotification(moteId, isLightOn);
        }

        // Règle 2: Email -> Week-end (19h-23h) OU Semaine (23h-06h)
        else if ((isWeekend && isEveningRange) || (!isWeekend && isNightRange)) {
            Log.d(TAG, "Condition Email remplie (WE ou Nuit semaine)");
            sendEmail(moteId, isLightOn, emailDest);
        } else {
            Log.d(TAG, "Aucune condition d'alerte remplie pour ce changement.");
        }
    }

    private void vibrate() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
            Log.d(TAG, "Vibrating for 500ms");
        }
    }

    private void sendNotification(String moteId, boolean isLightOn) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String notificationText = "Lumière ALLUMÉE détectée sur le mote " + moteId;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Alerte Lumière")
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(moteId.hashCode(), builder.build());
        Log.d(TAG, "Notification sent for mote " + moteId);
    }

    private void sendEmail(String moteId, boolean isLightOn, String emailDest) {
        Log.d(TAG, "Creating email intent for " + emailDest);
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        // Configuration spécifique pour les clients mail
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.setType("text/plain"); // ou "message/rfc822"

        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailDest});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Alerte: Changement d'état mote " + moteId);
        String body = "La lumière pour le mote " + moteId + " vient d'être allumée (Détection hors horaires ouvrés).";
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            // Utilisation du chooser pour laisser le choix à l'utilisateur (TP3 Exercice 1)
            Intent chooserIntent = Intent.createChooser(emailIntent, "Envoyer alerte email...");
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooserIntent);
            Log.d(TAG, "Email intent chooser started.");
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "No email clients installed.");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Mote State Change Channel";
            String description = "Channel for mote light state change notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void broadcastUpdate(Map<String, String> motesData) {
        Intent intent = new Intent(ACTION_UPDATE_UI);
        intent.putExtra(EXTRA_DATA, new HashMap<>(motesData));
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy() - arrêt du timer");
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}