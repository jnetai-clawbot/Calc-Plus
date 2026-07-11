package com.jnetai.calcplus.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.jnetai.calcplus.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AboutActivity extends AppCompatActivity {
    private static final String TAG = "CalcPlus_About";
    private static final String CURRENT_VERSION = "1.0.1";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/jnetai-clawbot/Calc-Plus/releases/latest";
    private static final String GITHUB_RELEASES_URL = "https://github.com/jnetai-clawbot/Calc-Plus/releases";

    private TextView versionText;
    private TextView updateStatusText;
    private String latestVersion = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        versionText = findViewById(R.id.versionText);
        updateStatusText = findViewById(R.id.updateStatusText);

        versionText.setText("Version " + CURRENT_VERSION);

        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> checkForUpdate());
        findViewById(R.id.btnShareApp).setOnClickListener(v -> shareApp());
        findViewById(R.id.btnOpenUpdate).setOnClickListener(v -> openReleases());
    }

    private void checkForUpdate() {
        updateStatusText.setText("Checking...");
        findViewById(R.id.btnOpenUpdate).setVisibility(View.GONE);

        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    latestVersion = json.getString("tag_name");
                    if (latestVersion.startsWith("v")) {
                        latestVersion = latestVersion.substring(1);
                    }

                    String currentVersion = CURRENT_VERSION;
                    runOnUiThread(() -> {
                        if (!currentVersion.equals(latestVersion)) {
                            updateStatusText.setText("Update Available: v" + latestVersion);
                            findViewById(R.id.btnOpenUpdate).setVisibility(View.VISIBLE);
                        } else {
                            updateStatusText.setText(R.string.up_to_date);
                        }
                    });
                } else {
                    runOnUiThread(() -> updateStatusText.setText("Could not check for updates"));
                    Log.w(TAG, "WARN_ABOUT_001: GitHub API returned " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "ERR_ABOUT_001: Update check failed", e);
                runOnUiThread(() -> updateStatusText.setText("Could not check for updates"));
            }
        }).start();
    }

    private void openReleases() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "ERR_ABOUT_002: Failed to open releases URL", e);
            Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Calc+ - Secure Calculator Vault");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Check out Calc+ - A secure calculator with hidden encrypted vault storage!\n\n" +
                        "Download: " + GITHUB_RELEASES_URL + "\n\n" +
                        "Made by jnetai.com");
        startActivity(Intent.createChooser(shareIntent, "Share Calc+"));
    }
}
