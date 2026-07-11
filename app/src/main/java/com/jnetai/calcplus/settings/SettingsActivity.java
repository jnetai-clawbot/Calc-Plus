package com.jnetai.calcplus.settings;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.jnetai.calcplus.R;
import com.jnetai.calcplus.about.AboutActivity;
import com.jnetai.calcplus.util.CryptoUtils;
import com.jnetai.calcplus.util.SecurePrefs;
import com.jnetai.calcplus.util.VaultManager;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "CalcPlus_Settings";

    private SecurePrefs securePrefs;
    private VaultManager vaultManager;
    private TextView attemptsText;
    private SeekBar attemptsSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        securePrefs = SecurePrefs.getInstance(this);
        vaultManager = new VaultManager(this);

        attemptsText = findViewById(R.id.attemptsText);
        attemptsSeekBar = findViewById(R.id.attemptsSeekBar);

        int maxAttempts = securePrefs.getMaxAttempts();
        attemptsSeekBar.setProgress(maxAttempts);
        attemptsText.setText(String.valueOf(maxAttempts));

        attemptsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                attemptsText.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                securePrefs.setMaxAttempts(seekBar.getProgress());
                Toast.makeText(SettingsActivity.this, "Max attempts set to " + seekBar.getProgress(), Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnChangePin).setOnClickListener(v -> showChangePinDialog());
        findViewById(R.id.btnResetVault).setOnClickListener(v -> confirmResetVault());
        findViewById(R.id.btnAbout).setOnClickListener(v -> {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        });
    }

    private void showChangePinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set New PIN");

        final EditText input = new EditText(this);
        input.setHint("Enter new PIN (min 4 digits)");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextColor(getColor(R.color.onBackground));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        builder.setView(input);

        builder.setPositiveButton("Next", null);
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newPin = input.getText().toString().trim();
            if (newPin.length() < 4) {
                Toast.makeText(this, R.string.pin_too_short, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showConfirmPinDialog(newPin);
        });
    }

    private void showConfirmPinDialog(String newPin) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm New PIN");

        final EditText input = new EditText(this);
        input.setHint("Re-enter new PIN");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextColor(getColor(R.color.onBackground));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String confirmPin = input.getText().toString().trim();
            if (confirmPin.equals(newPin)) {
                saveNewPin(newPin);
            } else {
                Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveNewPin(String newPin) {
        try {
            String salt = CryptoUtils.generateSalt();
            String hash = CryptoUtils.hashPin(newPin, salt);
            if (hash != null) {
                securePrefs.setSalt(salt);
                securePrefs.setPinHash(hash);
                securePrefs.setIsDefaultPin(false);
                securePrefs.resetFailedAttempts();
                Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "INFO_SETTINGS_001: PIN changed successfully");
                finish();
            } else {
                Log.e(TAG, "ERR_SETTINGS_001: Failed to hash new PIN");
                Toast.makeText(this, "Failed to save PIN", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "ERR_SETTINGS_002: PIN change failed", e);
            Toast.makeText(this, "Failed to save PIN", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmResetVault() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Calc+ Storage")
                .setMessage(R.string.reset_confirm)
                .setPositiveButton("Reset", (dialog, which) -> {
                    vaultManager.destroyAllData();
                    securePrefs.clearAll();
                    Toast.makeText(this, "Vault storage has been reset", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "WARN_SETTINGS_001: Vault storage reset by user");
                    finishAffinity();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
