package com.example.projetamio;

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
import android.util.Log;

import java.util.Calendar;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainService extends Service {

    private static final String TAG = "MainService";
    public static final String ACTION_RESULT = "com.example.projetamio.ACTION_RESULT";
    public static final String EXTRA_SENSOR_DATA = "sensor_data";
    public static final String EXTRA_TIMESTAMP = "timestamp";

    private Timer timer;
    private TimerTask timerTask;
    private static final long PERIOD = 30_000L; // 30 secondes
    private Random random;
    private SharedPreferences preferences;
    private Vibrator vibrator;

    // Valeurs par défaut (seront écrasées par les préférences)
    private static final int DEFAULT_LUMINOSITY_THRESHOLD = 300;

    @Override
    public void onCreate() {
        super.onCreate();
        random = new Random();

        // Initialisation des préférences
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Récupération de l'instance du vibreur
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

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
                    Log.d(TAG, "TimerTask exécutée — toutes les 30 secondes");

                    // Génération des données
                    String sensorData = generateFakeSensorData();

                    // Extraction de la valeur de luminosité
                    int luminosity = extractLuminosity(sensorData);

                    // Récupération du seuil depuis les préférences
                    int threshold = Integer.parseInt(preferences.getString("luminosity_threshold", String.valueOf(DEFAULT_LUMINOSITY_THRESHOLD)));

                    // Vérification si une lumière est détectée
                    if (luminosity > threshold) {
                        Log.d(TAG, "Lumière détectée: " + luminosity + " lux (seuil: " + threshold + ")");

                        // Faire vibrer le téléphone
                        vibratePhone();

                        checkAndSendEmail(sensorData, luminosity);
                    }

                    // Envoi des données à l'activité
                    sendDataToActivity(sensorData);

                    scheduleNextRun();
                }
            };

            // Premier lancement
            timer.schedule(timerTask, PERIOD);
        }

        return START_STICKY;
    }

    /**
     * Envoie des données à l'activité via un Intent broadcast
     */
    private void sendDataToActivity(String sensorData) {
        Intent resultIntent = new Intent(ACTION_RESULT);
        resultIntent.setPackage(getPackageName());

        long timestamp = System.currentTimeMillis();

        resultIntent.putExtra(EXTRA_SENSOR_DATA, sensorData);
        resultIntent.putExtra(EXTRA_TIMESTAMP, timestamp);

        sendBroadcast(resultIntent);

        Log.d(TAG, "Données envoyées à l'activité: " + sensorData);
    }

    /**
     * Fait vibrer le téléphone pendant 500ms
     */
    private void vibratePhone() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0 (API 26) et supérieur
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // Anciennes versions Android
                vibrator.vibrate(500);
            }
            Log.d(TAG, "Vibration déclenchée (500ms)");
        } else {
            Log.w(TAG, "Vibreur non disponible sur cet appareil");
        }
    }

    /**
     * Vérifie les conditions et envoie un email si nécessaire
     * MODE TEST : Envoie à toute heure pour faciliter les tests
     */
    private void checkAndSendEmail(String sensorData, int luminosity) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        // 🧪 MODE TEST : toujours envoyer l'email
        String reason = "Détection à " + hour + "h" + String.format("%02d", minute);

        Log.d(TAG, "Envoi d'email: " + reason);
        sendEmail(sensorData, luminosity, reason);

        /* 📝 VERSION PRODUCTION avec conditions horaires :
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
        boolean isWeekday = !isWeekend;

        boolean shouldSendEmail = false;
        String reason = "";

        // Récupération des paramètres depuis les préférences
        boolean emailEnabledWeekend = preferences.getBoolean("email_enabled_weekend", true);
        boolean emailEnabledWeekday = preferences.getBoolean("email_enabled_weekday", true);

        int weekendStartHour = Integer.parseInt(preferences.getString("weekend_start_hour", "19"));
        int weekendEndHour = Integer.parseInt(preferences.getString("weekend_end_hour", "23"));

        int weekdayStartHour = Integer.parseInt(preferences.getString("weekday_start_hour", "23"));
        int weekdayEndHour = Integer.parseInt(preferences.getString("weekday_end_hour", "6"));

        // Conditions pour envoyer un email
        if (isWeekend && emailEnabledWeekend && hour >= weekendStartHour && hour < weekendEndHour) {
            shouldSendEmail = true;
            reason = "Week-end entre " + weekendStartHour + "h et " + weekendEndHour + "h";
        } else if (isWeekday && emailEnabledWeekday && (hour >= weekdayStartHour || hour < weekdayEndHour)) {
            shouldSendEmail = true;
            reason = "Semaine entre " + weekdayStartHour + "h et " + weekdayEndHour + "h";
        }

        if (shouldSendEmail) {
            Log.d(TAG, "Envoi d'email: " + reason);
            sendEmail(sensorData, luminosity, reason);
        } else {
            Log.d(TAG, "Pas d'email à envoyer (heure: " + hour + "h" + String.format("%02d", minute) + ", weekend: " + isWeekend + ")");
        }
        */
    }

    /**
     * Envoie un email via une Intent ACTION_SEND
     */
    private void sendEmail(String sensorData, int luminosity, String reason) {
        // Récupération de l'adresse email depuis les préférences
        String emailAddress = preferences.getString("email_address", "votre.email@example.com");

        // Corps de l'email
        String emailBody = "Une lumière a été détectée dans les conditions suivantes:\n\n" +
                "📍 Capteur: " + sensorData + "\n" +
                "💡 Luminosité: " + luminosity + " lux\n" +
                "🕐 Détection: " + reason + "\n" +
                "📅 Date et heure: " + Calendar.getInstance().getTime() + "\n\n" +
                "Merci de vérifier et d'éteindre les lumières si nécessaire.\n\n" +
                "---\n" +
                "Message automatique du système AMIO";

        // Tentative 1 : Essayer d'ouvrir Gmail directement
        Intent gmailIntent = new Intent(Intent.ACTION_SEND);
        gmailIntent.setType("text/plain");
        gmailIntent.setPackage("com.google.android.gm"); // Package Gmail
        gmailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailAddress});
        gmailIntent.putExtra(Intent.EXTRA_SUBJECT, "⚠️ Alerte Lumière Détectée - AMIO");
        gmailIntent.putExtra(Intent.EXTRA_TEXT, emailBody);
        gmailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            // Essayer de lancer Gmail directement
            startActivity(gmailIntent);
            Log.d(TAG, "Email ouvert dans Gmail pour " + emailAddress);
            return; // Succès, on sort de la méthode
        } catch (android.content.ActivityNotFoundException ex) {
            Log.w(TAG, "Gmail non trouvé, utilisation du chooser");
        }

        // Tentative 2 : Si Gmail n'est pas installé, fallback sur le chooser
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailAddress});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "⚠️ Alerte Lumière Détectée - AMIO");
        emailIntent.putExtra(Intent.EXTRA_TEXT, emailBody);
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(Intent.createChooser(emailIntent, "Envoyer l'email via...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.d(TAG, "Chooser d'email affiché");
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "Aucune application email trouvée sur l'appareil", ex);
        }
    }

    /**
     * Génère des données de capteur factices
     */
    private String generateFakeSensorData() {
        String[] sensors = {"Bureau A101", "Bureau B203", "Salle C305", "Hall D104"};
        int sensorIndex = random.nextInt(sensors.length);
        int luminosity = random.nextInt(1000); // Valeur entre 0 et 999

        return sensors[sensorIndex] + " - Luminosité: " + luminosity + " lux";
    }

    /**
     * Extrait la valeur de luminosité depuis la chaîne de données
     */
    private int extractLuminosity(String sensorData) {
        try {
            // Format: "Bureau A101 - Luminosité: 500 lux"
            String[] parts = sensorData.split(":");
            if (parts.length > 1) {
                String luminosityStr = parts[1].trim().replace("lux", "").trim();
                return Integer.parseInt(luminosityStr);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'extraction de la luminosité", e);
        }
        return 0;
    }

    private void scheduleNextRun() {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "TimerTask exécutée — toutes les 30 secondes");

                String sensorData = generateFakeSensorData();
                int luminosity = extractLuminosity(sensorData);

                // Récupération du seuil depuis les préférences
                int threshold = Integer.parseInt(preferences.getString("luminosity_threshold", String.valueOf(DEFAULT_LUMINOSITY_THRESHOLD)));

                if (luminosity > threshold) {
                    Log.d(TAG, "Lumière détectée: " + luminosity + " lux (seuil: " + threshold + ")");

                    // Faire vibrer le téléphone
                    vibratePhone();

                    checkAndSendEmail(sensorData, luminosity);
                }

                sendDataToActivity(sensorData);
                scheduleNextRun();
            }
        };
        timer.schedule(timerTask, PERIOD);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy() - arrêt du timer");
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}