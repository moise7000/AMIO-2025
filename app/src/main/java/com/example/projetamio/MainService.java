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
import android.preference.PreferenceManager;
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
    public static final String ACTION_RESULT = "com.example.projetamio.ACTION_RESULT";
    public static final String EXTRA_SENSOR_DATA = "sensor_data";
    public static final String EXTRA_TIMESTAMP = "timestamp";
    private static final float LUMINOSITY_THRESHOLD = 250.0f;
    private static final String CHANNEL_ID = "LuminosityNotificationChannel";

    private Timer timer;
    private TimerTask timerTask;
    private static final long PERIOD = 30_000L; // 30 secondes

    private Map<String, Boolean> lightStatus = new HashMap<>();
    private SharedPreferences preferences;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createNotificationChannel();
        Log.d(TAG, "onCreate() du service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() - démarrage du timer");

        if (timer == null) {
            timer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    fetchData();
                }
            };
            timer.schedule(timerTask, 0, PERIOD);
        }

        return START_STICKY;
    }

    private void fetchData() {
        try {
            URL url = new URL("http://iotlab.telecomnancy.eu:8080/iotlab/rest/data/1/light1/last");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                int responseCode = urlConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream in = urlConnection.getInputStream();
                    InputStreamReader reader = new InputStreamReader(in);
                    JsonReader jsonReader = new JsonReader(reader);
                    StringBuilder dataForActivity = new StringBuilder();

                    jsonReader.beginObject();
                    while (jsonReader.hasNext()) {
                        String name = jsonReader.nextName();
                        if (name.equals("data")) {
                            jsonReader.beginArray();
                            int moteId = 0;
                            while (jsonReader.hasNext()) {
                                jsonReader.beginObject();
                                String moteName = "mote_" + moteId;
                                float value = 0;
                                while (jsonReader.hasNext()) {
                                    String key = jsonReader.nextName();
                                    if (key.equals("value")) {
                                        value = (float) jsonReader.nextDouble();
                                    } else {
                                        jsonReader.skipValue();
                                    }
                                }
                                processSensorData(moteName, value);
                                dataForActivity.append(moteName).append(": ").append(value).append(" lux\n");
                                jsonReader.endObject();
                                moteId++;
                            }
                            jsonReader.endArray();
                        }
                    }
                    jsonReader.endObject();
                    sendDataToActivity(dataForActivity.toString());

                } else {
                    Log.e(TAG, "HTTP Error: " + responseCode);
                }
            } finally {
                urlConnection.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching data", e);
        }
    }

    private void processSensorData(String mote, float luminosity) {
        boolean isCurrentlyOn = luminosity > LUMINOSITY_THRESHOLD;
        Boolean wasPreviouslyOn = lightStatus.get(mote);

        if (wasPreviouslyOn == null || wasPreviouslyOn != isCurrentlyOn) {
            lightStatus.put(mote, isCurrentlyOn);
            vibratePhone();
            if (isNotificationTime()) {
                String status = isCurrentlyOn ? "allumée" : "éteinte";
                sendNotification("Changement d'état", "La lumière du " + mote + " est maintenant " + status);
            }
            if (isEmailTime()) {
                 String status = isCurrentlyOn ? "allumée" : "éteinte";
                 sendEmail(mote, status);
            }
        }
    }

    private void vibratePhone() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //deprecated in API 26
                vibrator.vibrate(500);
            }
            Log.d(TAG, "Vibration for 500ms");
        }
    }

    private boolean isNotificationTime() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
        return !isWeekend && hour >= 19 && hour < 23;
    }

    private boolean isEmailTime() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);

        if (isWeekend) {
            return hour >= 19 && hour < 23;
        } else {
            return hour >= 23 || hour < 6;
        }
    }

    private void sendDataToActivity(String data) {
        Intent intent = new Intent(ACTION_RESULT);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_SENSOR_DATA, data);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        sendBroadcast(intent);
        Log.d(TAG, "Données envoyées à l'activité: " + data);
    }

    private void sendNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void sendEmail(String mote, String status) {
        String emailAddress = preferences.getString("email_address", "votre.email@example.com");

        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailAddress});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "[AMIO] Alerte de changement d'état de lumière");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "La lumière du capteur '" + mote + "' est maintenant " + status + ".");
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(Intent.createChooser(emailIntent, "Envoyer l'email via...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.d(TAG, "Chooser d'email affiché");
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "Aucune application email trouvée sur l'appareil", ex);
        }
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Luminosity Notifications";
            String description = "Notifications for light status changes";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
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