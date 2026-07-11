package com.jnetai.calcplus.util;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    private static final String TAG = "CalcPlus_Crypto";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int KEY_LENGTH = 256;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    public static String hashPin(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = pin + salt;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Log.e(TAG, "ERR_CRYPTO_001: Failed to hash PIN", e);
            return null;
        }
    }

    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[32];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static SecretKey deriveKey(String pin, String salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt.getBytes(StandardCharsets.UTF_8),
                    PBKDF2_ITERATIONS, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            Log.e(TAG, "ERR_CRYPTO_002: Failed to derive key", e);
            return null;
        }
    }

    public static byte[] encrypt(byte[] data, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] encrypted = cipher.doFinal(data);
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return combined;
        } catch (Exception e) {
            Log.e(TAG, "ERR_CRYPTO_003: Encryption failed", e);
            return null;
        }
    }

    public static byte[] decrypt(byte[] data, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(data, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            Log.e(TAG, "ERR_CRYPTO_004: Decryption failed", e);
            return null;
        }
    }

    public static String encryptString(String plaintext, SecretKey key) {
        try {
            byte[] encrypted = encrypt(plaintext.getBytes(StandardCharsets.UTF_8), key);
            return encrypted != null ? Base64.getEncoder().encodeToString(encrypted) : null;
        } catch (Exception e) {
            Log.e(TAG, "ERR_CRYPTO_005: String encryption failed", e);
            return null;
        }
    }

    public static String decryptString(String ciphertext, SecretKey key) {
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext);
            byte[] decrypted = decrypt(data, key);
            return decrypted != null ? new String(decrypted, StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            Log.e(TAG, "ERR_CRYPTO_006: String decryption failed", e);
            return null;
        }
    }
}
