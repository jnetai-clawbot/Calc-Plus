package com.jnetai.calcplus.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class SecurePrefs {
    private static final String TAG = "CalcPlus_SecurePrefs";
    private static final String PREFS_NAME = "calcplus_secure_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_MAX_ATTEMPTS = "max_attempts";
    private static final String KEY_SELF_DESTRUCTED = "self_destructed";
    private static final String KEY_IS_DEFAULT_PIN = "is_default_pin";
    private static final String KEY_VAULT_VERSION = "vault_version";

    private static SecurePrefs instance;
    private SharedPreferences prefs;

    private SecurePrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "ERR_SECPREF_001: Failed to initialize secure preferences", e);
            prefs = context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE);
        }
    }

    public static synchronized SecurePrefs getInstance(Context context) {
        if (instance == null) {
            instance = new SecurePrefs(context.getApplicationContext());
        }
        return instance;
    }

    public void setPinHash(String hash) {
        prefs.edit().putString(KEY_PIN_HASH, hash).apply();
    }

    public String getPinHash() {
        return prefs.getString(KEY_PIN_HASH, null);
    }

    public void setSalt(String salt) {
        prefs.edit().putString(KEY_SALT, salt).apply();
    }

    public String getSalt() {
        return prefs.getString(KEY_SALT, null);
    }

    public int getFailedAttempts() {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0);
    }

    public void incrementFailedAttempts() {
        int attempts = getFailedAttempts() + 1;
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();
        Log.w(TAG, "WARN_AUTH_001: Failed attempt " + attempts + " of " + getMaxAttempts());
    }

    public void resetFailedAttempts() {
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply();
    }

    public int getMaxAttempts() {
        return prefs.getInt(KEY_MAX_ATTEMPTS, 10);
    }

    public void setMaxAttempts(int max) {
        prefs.edit().putInt(KEY_MAX_ATTEMPTS, max).apply();
    }

    public boolean isSelfDestructed() {
        return prefs.getBoolean(KEY_SELF_DESTRUCTED, false);
    }

    public void setSelfDestructed(boolean destructed) {
        prefs.edit().putBoolean(KEY_SELF_DESTRUCTED, destructed).apply();
    }

    public boolean isDefaultPin() {
        return prefs.getBoolean(KEY_IS_DEFAULT_PIN, true);
    }

    public void setIsDefaultPin(boolean isDefault) {
        prefs.edit().putBoolean(KEY_IS_DEFAULT_PIN, isDefault).apply();
    }

    public int getVaultVersion() {
        return prefs.getInt(KEY_VAULT_VERSION, 1);
    }

    public void setVaultVersion(int version) {
        prefs.edit().putInt(KEY_VAULT_VERSION, version).apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
        Log.w(TAG, "WARN_SECPREF_002: All secure preferences cleared");
    }
}
