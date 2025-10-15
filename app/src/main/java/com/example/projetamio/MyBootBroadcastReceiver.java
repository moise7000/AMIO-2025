package com.example.projetamio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class MyBootBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "BootBroadcastReceiver";
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_START_AT_BOOT = "start_at_boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null &&
                intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {

            Log.d(TAG, "BOOT_COMPLETED reçu");

            // Vérifier les préférences de l'utilisateur
            SharedPreferences sharedPreferences =
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean startAtBoot = sharedPreferences.getBoolean(KEY_START_AT_BOOT, false);

            Log.d(TAG, "Valeur de 'start_at_boot': " + startAtBoot);

            if (startAtBoot) {
                // Démarrer le service
                Intent serviceIntent = new Intent(context, MainService.class);
                context.startService(serviceIntent);
                Log.d(TAG, "Service démarré automatiquement après le boot");
            } else {
                Log.d(TAG, "Démarrage automatique non activé - service non démarré");
            }
        }
    }
}