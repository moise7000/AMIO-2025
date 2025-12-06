package com.example.projetamio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.JsonReader;
import android.util.Log;

import androidx.core.app.NotificationCompat;

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

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
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
            if (previousState != null && previousState != isLightOn) {
                Log.d(TAG, "State change detected for mote " + moteId);
                if (isTimeForNotification()) {
                    sendNotification(moteId, isLightOn);
                }
                sendEmail(moteId, isLightOn);
            }
            previousMoteStates.put(moteId, isLightOn);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Could not parse light value", e);
        }
    }

    private boolean isTimeForNotification() {
        Calendar cal = Calendar.getInstance();
        int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        return hourOfDay >= 18 && hourOfDay < 23;
    }

    private void sendNotification(String moteId, boolean isLightOn) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String notificationText = "Lumière " + (isLightOn ? "allumée" : "éteinte") + " pour le mote " + moteId;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Remplacez par votre icône
                .setContentTitle("Changement d'état de la lumière")
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(moteId.hashCode(), builder.build());
        Log.d(TAG, "Notification sent for mote " + moteId);
    }

    private void sendEmail(String moteId, boolean isLightOn) {
        Log.d(TAG, "Creating email intent for mote " + moteId);
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"destinataire@example.com"});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Changement d'état pour le mote " + moteId);
        String body = "La lumière pour le mote " + moteId + " est maintenant " + (isLightOn ? "allumée." : "éteinte.");
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

        // Required to start an activity from a service
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
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
