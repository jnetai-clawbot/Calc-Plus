package com.jnetai.calcplus.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

public class VaultManager {
    private static final String TAG = "CalcPlus_VaultMgr";
    private static final String VAULT_DIR = "calcplus_vault";
    private static final String NOTES_DIR = "notes";
    private static final String FILES_DIR = "files";
    private static final String BACKUP_EXTENSION = ".cpbak";

    private final File vaultRoot;
    private final File notesDir;
    private final File filesDir;

    public VaultManager(Context context) {
        vaultRoot = new File(context.getFilesDir(), VAULT_DIR);
        notesDir = new File(vaultRoot, NOTES_DIR);
        filesDir = new File(vaultRoot, FILES_DIR);
        ensureDirectories();
    }

    private void ensureDirectories() {
        if (!vaultRoot.exists() && !vaultRoot.mkdirs()) {
            Log.e(TAG, "ERR_VAULT_001: Failed to create vault root directory");
        }
        if (!notesDir.exists() && !notesDir.mkdirs()) {
            Log.e(TAG, "ERR_VAULT_002: Failed to create notes directory");
        }
        if (!filesDir.exists() && !filesDir.mkdirs()) {
            Log.e(TAG, "ERR_VAULT_003: Failed to create files directory");
        }
    }

    public File getVaultRoot() {
        return vaultRoot;
    }

    public File getNotesDir() {
        return notesDir;
    }

    public File getFilesDir() {
        return filesDir;
    }

    public boolean saveEncryptedFile(String relativePath, byte[] data, SecretKey key) {
        try {
            File targetFile = new File(filesDir, relativePath);
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.e(TAG, "ERR_VAULT_004: Failed to create parent directory: " + parent.getAbsolutePath());
                return false;
            }
            byte[] encrypted = CryptoUtils.encrypt(data, key);
            if (encrypted == null) {
                Log.e(TAG, "ERR_VAULT_005: Encryption returned null for: " + relativePath);
                return false;
            }
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(encrypted);
                fos.flush();
            }
            Log.i(TAG, "INFO_VAULT_001: File saved: " + relativePath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_006: Failed to save encrypted file: " + relativePath, e);
            return false;
        }
    }

    public byte[] readEncryptedFile(String relativePath, SecretKey key) {
        try {
            File file = new File(filesDir, relativePath);
            if (!file.exists()) {
                Log.w(TAG, "WARN_VAULT_001: File not found: " + relativePath);
                return null;
            }
            byte[] encrypted;
            try (FileInputStream fis = new FileInputStream(file)) {
                encrypted = new byte[(int) file.length()];
                int bytesRead = fis.read(encrypted);
                if (bytesRead != encrypted.length) {
                    Log.e(TAG, "ERR_VAULT_007: Incomplete read for: " + relativePath);
                    return null;
                }
            }
            byte[] decrypted = CryptoUtils.decrypt(encrypted, key);
            if (decrypted == null) {
                Log.e(TAG, "ERR_VAULT_008: Decryption returned null for: " + relativePath);
            }
            return decrypted;
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_009: Failed to read encrypted file: " + relativePath, e);
            return null;
        }
    }

    public boolean saveNote(String noteName, String content, SecretKey key) {
        try {
            String encrypted = CryptoUtils.encryptString(content, key);
            if (encrypted == null) {
                Log.e(TAG, "ERR_VAULT_010: Note encryption failed: " + noteName);
                return false;
            }
            File noteFile = new File(notesDir, noteName + ".cpn");
            try (FileOutputStream fos = new FileOutputStream(noteFile)) {
                fos.write(encrypted.getBytes());
                fos.flush();
            }
            Log.i(TAG, "INFO_VAULT_002: Note saved: " + noteName);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_011: Failed to save note: " + noteName, e);
            return false;
        }
    }

    public String readNote(String noteName, SecretKey key) {
        try {
            File noteFile = new File(notesDir, noteName + ".cpn");
            if (!noteFile.exists()) {
                Log.w(TAG, "WARN_VAULT_002: Note not found: " + noteName);
                return null;
            }
            byte[] encrypted;
            try (FileInputStream fis = new FileInputStream(noteFile)) {
                encrypted = new byte[(int) noteFile.length()];
                fis.read(encrypted);
            }
            String encryptedStr = new String(encrypted);
            String decrypted = CryptoUtils.decryptString(encryptedStr, key);
            if (decrypted == null) {
                Log.e(TAG, "ERR_VAULT_012: Note decryption failed: " + noteName);
            }
            return decrypted;
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_013: Failed to read note: " + noteName, e);
            return null;
        }
    }

    public boolean deleteNote(String noteName) {
        File noteFile = new File(notesDir, noteName + ".cpn");
        boolean deleted = noteFile.delete();
        if (!deleted) {
            Log.w(TAG, "WARN_VAULT_003: Failed to delete note: " + noteName);
        }
        return deleted;
    }

    public boolean deleteFile(String relativePath) {
        File file = new File(filesDir, relativePath);
        boolean deleted = file.delete();
        if (!deleted) {
            Log.w(TAG, "WARN_VAULT_004: Failed to delete file: " + relativePath);
        }
        return deleted;
    }

    public boolean deleteFolder(String relativePath) {
        File folder = new File(filesDir, relativePath);
        if (!folder.isDirectory()) return false;
        return deleteRecursive(folder);
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        Log.w(TAG, "WARN_VAULT_005: Failed to delete: " + child.getAbsolutePath());
                    }
                }
            }
        }
        return file.delete();
    }

    public List<String> listNotes() {
        List<String> notes = new ArrayList<>();
        File[] files = notesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".cpn")) {
                    notes.add(name.substring(0, name.length() - 4));
                }
            }
        }
        return notes;
    }

    public List<String> listFiles(String relativePath) {
        List<String> items = new ArrayList<>();
        File dir = relativePath.isEmpty() ? filesDir : new File(filesDir, relativePath);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                items.add(file.getName());
            }
        }
        return items;
    }

    public boolean moveItem(String fromPath, String toPath) {
        File from = new File(filesDir, fromPath);
        File to = new File(filesDir, toPath);
        File parent = to.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.e(TAG, "ERR_VAULT_014: Failed to create target directory");
            return false;
        }
        boolean result = from.renameTo(to);
        if (!result) {
            Log.e(TAG, "ERR_VAULT_015: Move failed from " + fromPath + " to " + toPath);
        }
        return result;
    }

    public boolean copyItem(String fromPath, String toPath) {
        try {
            File from = new File(filesDir, fromPath);
            File to = new File(filesDir, toPath);
            File parent = to.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.e(TAG, "ERR_VAULT_016: Failed to create target directory");
                return false;
            }
            if (from.isDirectory()) {
                copyDirectory(from, to);
            } else {
                copyFile(from, to);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_017: Copy failed from " + fromPath + " to " + toPath, e);
            return false;
        }
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
    }

    private void copyDirectory(File source, File dest) throws IOException {
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("Failed to create directory: " + dest.getAbsolutePath());
        }
        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(dest, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, targetFile);
                } else {
                    copyFile(file, targetFile);
                }
            }
        }
    }

    public boolean importFile(InputStream inputStream, String relativePath, SecretKey key) {
        try {
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            return saveEncryptedFile(relativePath, data, key);
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_018: Import failed: " + relativePath, e);
            return false;
        }
    }

    public byte[] exportFile(String relativePath, SecretKey key) {
        return readEncryptedFile(relativePath, key);
    }

    public boolean createBackup(File outputFile, SecretKey key) {
        try {
            byte[] vaultData = serializeVault();
            if (vaultData == null) {
                Log.e(TAG, "ERR_VAULT_019: Vault serialization failed");
                return false;
            }
            byte[] encrypted = CryptoUtils.encrypt(vaultData, key);
            if (encrypted == null) {
                Log.e(TAG, "ERR_VAULT_020: Backup encryption failed");
                return false;
            }
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(encrypted);
                fos.flush();
            }
            Log.i(TAG, "INFO_VAULT_003: Backup created: " + outputFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_021: Backup creation failed", e);
            return false;
        }
    }

    public boolean restoreBackup(File inputFile, SecretKey key) {
        try {
            byte[] encrypted;
            try (FileInputStream fis = new FileInputStream(inputFile)) {
                encrypted = new byte[(int) inputFile.length()];
                fis.read(encrypted);
            }
            byte[] decrypted = CryptoUtils.decrypt(encrypted, key);
            if (decrypted == null) {
                Log.e(TAG, "ERR_VAULT_022: Backup decryption failed - wrong PIN?");
                return false;
            }
            if (!deserializeVault(decrypted)) {
                Log.e(TAG, "ERR_VAULT_023: Vault deserialization failed");
                return false;
            }
            Log.i(TAG, "INFO_VAULT_004: Backup restored from: " + inputFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_024: Backup restore failed", e);
            return false;
        }
    }

    private byte[] serializeVault() {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos);

            addDirToZip(notesDir, "notes/", zos);
            addDirToZip(filesDir, "files/", zos);

            zos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_025: Vault serialization failed", e);
            return null;
        }
    }

    private void addDirToZip(File dir, String prefix, java.util.zip.ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                addDirToZip(file, prefix + file.getName() + "/", zos);
            } else {
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(prefix + file.getName());
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private boolean deserializeVault(byte[] data) {
        try {
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(bais);

            deleteRecursive(notesDir);
            deleteRecursive(filesDir);
            ensureDirectories();

            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                File target;
                if (name.startsWith("notes/")) {
                    target = new File(notesDir, name.substring(6));
                } else if (name.startsWith("files/")) {
                    target = new File(filesDir, name.substring(6));
                } else {
                    continue;
                }

                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    target.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
            zis.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "ERR_VAULT_026: Vault deserialization failed", e);
            return false;
        }
    }

    public void destroyAllData() {
        Log.w(TAG, "WARN_VAULT_006: SELF-DESTRUCT initiated - destroying all vault data");
        deleteRecursive(vaultRoot);
        ensureDirectories();
    }

    public boolean itemExists(String relativePath) {
        return new File(filesDir, relativePath).exists();
    }

    public boolean isDirectory(String relativePath) {
        return new File(filesDir, relativePath).isDirectory();
    }

    public long getItemSize(String relativePath) {
        File file = new File(filesDir, relativePath);
        if (file.isDirectory()) {
            return getDirectorySize(file);
        }
        return file.length();
    }

    private long getDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += getDirectorySize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    public List<String> searchItems(String query) {
        List<String> results = new ArrayList<>();
        searchInDirectory(filesDir, "", query.toLowerCase(), results);
        for (String note : listNotes()) {
            if (note.toLowerCase().contains(query.toLowerCase())) {
                results.add("note:" + note);
            }
        }
        return results;
    }

    private void searchInDirectory(File dir, String prefix, String query, List<String> results) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String relativePath = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();
            if (file.getName().toLowerCase().contains(query)) {
                results.add(relativePath);
            }
            if (file.isDirectory()) {
                searchInDirectory(file, relativePath, query, results);
            }
        }
    }
}
