package com.jnetai.calcplus.vault;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.jnetai.calcplus.R;
import com.jnetai.calcplus.about.AboutActivity;
import com.jnetai.calcplus.settings.SettingsActivity;
import com.jnetai.calcplus.util.CryptoUtils;
import com.jnetai.calcplus.util.SecurePrefs;
import com.jnetai.calcplus.util.VaultManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

public class VaultActivity extends AppCompatActivity {
    private static final String TAG = "CalcPlus_Vault";

    private VaultManager vaultManager;
    private SecretKey currentKey;
    private String currentPin;
    private ListView itemList;
    private EditText searchBox;
    private TextView currentPathText;
    private String currentPath = "";
    private List<String> currentItems = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private boolean showingNotes = false;

    private ActivityResultLauncher<Intent> importFileLauncher;
    private ActivityResultLauncher<Intent> exportFileLauncher;
    private ActivityResultLauncher<Intent> backupSaveLauncher;
    private ActivityResultLauncher<Intent> backupRestoreLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault);

        currentPin = getIntent().getStringExtra("pin");
        if (currentPin == null) {
            Log.e(TAG, "ERR_VAULT_ACT_001: No PIN provided");
            finish();
            return;
        }

        String salt = SecurePrefs.getInstance(this).getSalt();
        currentKey = CryptoUtils.deriveKey(currentPin, salt);
        if (currentKey == null) {
            Log.e(TAG, "ERR_VAULT_ACT_002: Failed to derive key");
            finish();
            return;
        }

        vaultManager = new VaultManager(this);

        itemList = findViewById(R.id.itemList);
        searchBox = findViewById(R.id.searchBox);
        currentPathText = findViewById(R.id.currentPathText);

        adapter = new ArrayAdapter<String>(this, R.layout.item_vault_entry, R.id.itemName, currentItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView nameView = view.findViewById(R.id.itemName);
                TextView typeView = view.findViewById(R.id.itemType);
                String item = getItem(position);
                if (item != null) {
                    if (item.startsWith("note:")) {
                        typeView.setText("Note");
                    } else if (vaultManager.isDirectory(item)) {
                        typeView.setText("Folder");
                    } else {
                        typeView.setText("File");
                    }
                }
                return view;
            }
        };
        itemList.setAdapter(adapter);

        itemList.setOnItemClickListener((parent, view, position, id) -> {
            String item = currentItems.get(position);
            onItemClick(item);
        });

        itemList.setOnItemLongClickListener((parent, view, position, id) -> {
            String item = currentItems.get(position);
            showItemOptions(item);
            return true;
        });

        findViewById(R.id.btnNewNote).setOnClickListener(v -> createNewNote());
        findViewById(R.id.btnNewFolder).setOnClickListener(v -> createNewFolder());
        findViewById(R.id.btnImport).setOnClickListener(v -> importFile());
        findViewById(R.id.btnBack).setOnClickListener(v -> navigateBack());
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("pin", currentPin);
            startActivity(intent);
        });
        findViewById(R.id.btnAbout).setOnClickListener(v -> {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.btnToggleView).setOnClickListener(v -> toggleView());

        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        importFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleImportResult(result.getData().getData());
                    }
                });

        exportFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleExportResult(result.getData().getData());
                    }
                });

        backupSaveLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleBackupSaveResult(result.getData().getData());
                    }
                });

        backupRestoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleBackupRestoreResult(result.getData().getData());
                    }
                });

        refreshList();
    }

    private void refreshList() {
        currentItems.clear();
        if (showingNotes) {
            List<String> notes = vaultManager.listNotes();
            for (String note : notes) {
                currentItems.add("note:" + note);
            }
        } else {
            currentItems.addAll(vaultManager.listFiles(currentPath));
        }
        adapter.notifyDataSetChanged();
        updatePathDisplay();
    }

    private void updatePathDisplay() {
        if (showingNotes) {
            currentPathText.setText("Notes");
        } else if (currentPath.isEmpty()) {
            currentPathText.setText("Files");
        } else {
            currentPathText.setText("Files/" + currentPath);
        }
    }

    private void onItemClick(String item) {
        if (item.startsWith("note:")) {
            String noteName = item.substring(5);
            openNoteEditor(noteName);
        } else {
            String fullPath = currentPath.isEmpty() ? item : currentPath + "/" + item;
            if (vaultManager.isDirectory(fullPath)) {
                currentPath = fullPath;
                refreshList();
            } else {
                showItemOptions(item);
            }
        }
    }

    private void navigateBack() {
        if (showingNotes) {
            showingNotes = false;
            currentPath = "";
            refreshList();
        } else if (!currentPath.isEmpty()) {
            int lastSlash = currentPath.lastIndexOf('/');
            currentPath = lastSlash > 0 ? currentPath.substring(0, lastSlash) : "";
            refreshList();
        } else {
            finish();
        }
    }

    private void toggleView() {
        showingNotes = !showingNotes;
        currentPath = "";
        Button toggleBtn = findViewById(R.id.btnToggleView);
        toggleBtn.setText(showingNotes ? "Files" : "Notes");
        refreshList();
    }

    private void showItemOptions(String item) {
        String[] options = {"Open/View", "Move to", "Copy to", "Rename", "Delete", "Export"};
        new AlertDialog.Builder(this)
                .setTitle(item.startsWith("note:") ? item.substring(5) : item)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: openItem(item); break;
                        case 1: showMoveDialog(item); break;
                        case 2: showCopyDialog(item); break;
                        case 3: showRenameDialog(item); break;
                        case 4: confirmDelete(item); break;
                        case 5: exportItem(item); break;
                    }
                })
                .show();
    }

    private void openItem(String item) {
        if (item.startsWith("note:")) {
            openNoteEditor(item.substring(5));
        } else {
            String fullPath = currentPath.isEmpty() ? item : currentPath + "/" + item;
            byte[] data = vaultManager.readEncryptedFile(fullPath, currentKey);
            if (data != null) {
                try {
                    File tempFile = new File(getCacheDir(), item);
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.write(data);
                    }
                    Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                            getPackageName() + ".fileprovider", tempFile);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, getMimeType(item));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Open with"));
                } catch (Exception e) {
                    Log.e(TAG, "ERR_VAULT_ACT_003: Failed to open file", e);
                    Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getMimeType(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "pdf": return "application/pdf";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "mp4": return "video/mp4";
            case "mp3": return "audio/mpeg";
            case "txt": return "text/plain";
            case "doc": case "docx": return "application/msword";
            case "xls": case "xlsx": return "application/vnd.ms-excel";
            default: return "*/*";
        }
    }

    private void showMoveDialog(String item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Move \"" + item + "\" to:");
        final EditText input = new EditText(this);
        input.setHint("Enter destination path");
        input.setTextColor(getColor(R.color.onBackground));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        builder.setView(input);
        builder.setPositiveButton("Move", (dialog, which) -> {
            String dest = input.getText().toString().trim();
            if (!dest.isEmpty()) {
                String srcPath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                String destPath = dest + "/" + item;
                if (vaultManager.moveItem(srcPath, destPath)) {
                    refreshList();
                    Toast.makeText(this, "Moved successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Move failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showCopyDialog(String item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Copy \"" + item + "\" to:");
        final EditText input = new EditText(this);
        input.setHint("Enter destination path");
        input.setTextColor(getColor(R.color.onBackground));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        builder.setView(input);
        builder.setPositiveButton("Copy", (dialog, which) -> {
            String dest = input.getText().toString().trim();
            if (!dest.isEmpty()) {
                String srcPath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                String destPath = dest + "/" + item;
                if (vaultManager.copyItem(srcPath, destPath)) {
                    refreshList();
                    Toast.makeText(this, "Copied successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Copy failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showRenameDialog(String item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename \"" + item + "\":");
        final EditText input = new EditText(this);
        input.setText(item.startsWith("note:") ? item.substring(5) : item);
        input.setTextColor(getColor(R.color.onBackground));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        builder.setView(input);
        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(item)) {
                if (item.startsWith("note:")) {
                    String oldName = item.substring(5);
                    String content = vaultManager.readNote(oldName, currentKey);
                    if (content != null) {
                        vaultManager.saveNote(newName, content, currentKey);
                        vaultManager.deleteNote(oldName);
                    }
                } else {
                    String srcPath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                    String destPath = currentPath.isEmpty() ? newName : currentPath + "/" + newName;
                    vaultManager.moveItem(srcPath, destPath);
                }
                refreshList();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void confirmDelete(String item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Are you sure you want to delete \"" + item + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (item.startsWith("note:")) {
                        vaultManager.deleteNote(item.substring(5));
                    } else {
                        String fullPath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                        if (vaultManager.isDirectory(fullPath)) {
                            vaultManager.deleteFolder(fullPath);
                        } else {
                            vaultManager.deleteFile(fullPath);
                        }
                    }
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewNote() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Note");
        final EditText input = new EditText(this);
        input.setHint("Note name");
        input.setTextColor(getColor(R.color.onBackground));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                openNoteEditor(name);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void createNewFolder() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Folder");
        final EditText input = new EditText(this);
        input.setHint("Folder name");
        input.setTextColor(getColor(R.color.onBackground));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                String folderPath = currentPath.isEmpty() ? name : currentPath + "/" + name;
                File folder = new File(vaultManager.getFilesDir(), folderPath);
                if (folder.mkdirs()) {
                    refreshList();
                } else {
                    Toast.makeText(this, "Failed to create folder", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void openNoteEditor(String noteName) {
        Intent intent = new Intent(this, NoteEditorActivity.class);
        intent.putExtra("noteName", noteName);
        intent.putExtra("pin", currentPin);
        startActivity(intent);
    }

    private void importFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        importFileLauncher.launch(intent);
    }

    private void handleImportResult(Uri uri) {
        try {
            String fileName = getFileName(uri);
            if (fileName == null) fileName = "imported_file";
            String destPath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                byte[] data = new byte[inputStream.available()];
                inputStream.read(data);
                inputStream.close();

                if (vaultManager.saveEncryptedFile(destPath, data, currentKey)) {
                    refreshList();
                    Toast.makeText(this, "File imported", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "ERR_VAULT_ACT_004: Import failed", e);
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportItem(String item) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String fileName = item.startsWith("note:") ? item.substring(5) + ".txt" : item;
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        intent.putExtra("export_item", item);
        exportFileLauncher.launch(intent);
    }

    private void handleExportResult(Uri uri) {
        try {
            String item = getIntent().getStringExtra("export_item");
            if (item == null) return;

            byte[] data = null;
            if (item.startsWith("note:")) {
                String content = vaultManager.readNote(item.substring(5), currentKey);
                if (content != null) data = content.getBytes();
            } else {
                String fullPath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                data = vaultManager.readEncryptedFile(fullPath, currentKey);
            }

            if (data != null) {
                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(data);
                        os.flush();
                    }
                }
                Toast.makeText(this, "Exported successfully", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "ERR_VAULT_ACT_005: Export failed", e);
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            refreshList();
            return;
        }
        currentItems.clear();
        currentItems.addAll(vaultManager.searchItems(query));
        adapter.notifyDataSetChanged();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "WARN_VAULT_ACT_001: Could not get filename from cursor", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void onBackupClick(View view) {
        String[] options = {"Create Backup", "Restore Backup", "Save to Google Drive", "Save to OneDrive", "Share Backup"};
        new AlertDialog.Builder(this)
                .setTitle("Backup Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: createBackup(); break;
                        case 1: restoreBackup(); break;
                        case 2: saveToGoogleDrive(); break;
                        case 3: saveToOneDrive(); break;
                        case 4: shareBackup(); break;
                    }
                })
                .show();
    }

    private void createBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "CalcPlus_Backup_" + System.currentTimeMillis() + ".cpbak");
        backupSaveLauncher.launch(intent);
    }

    private void handleBackupSaveResult(Uri uri) {
        try {
            File tempFile = new File(getCacheDir(), "temp_backup.cpbak");
            if (vaultManager.createBackup(tempFile, currentKey)) {
                try (InputStream is = new java.io.FileInputStream(tempFile);
                     java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                        os.flush();
                    }
                }
                tempFile.delete();
                Toast.makeText(this, R.string.backup_created, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "ERR_VAULT_ACT_006: Backup save failed", e);
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        backupRestoreLauncher.launch(intent);
    }

    private void handleBackupRestoreResult(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Restore Backup")
                .setMessage("This will replace all current vault data. Continue?")
                .setPositiveButton("Restore", (dialog, which) -> {
                    try {
                        File tempFile = new File(getCacheDir(), "temp_restore.cpbak");
                        try (InputStream is = getContentResolver().openInputStream(uri);
                             FileOutputStream fos = new FileOutputStream(tempFile)) {
                            if (is != null) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = is.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                        if (vaultManager.restoreBackup(tempFile, currentKey)) {
                            tempFile.delete();
                            refreshList();
                            Toast.makeText(this, R.string.backup_restored, Toast.LENGTH_SHORT).show();
                        } else {
                            tempFile.delete();
                            Toast.makeText(this, "Restore failed. Wrong PIN?", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "ERR_VAULT_ACT_007: Backup restore failed", e);
                        Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveToGoogleDrive() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, "CalcPlus_Backup_" + System.currentTimeMillis() + ".cpbak");
            intent.putExtra("android.provider.extra.INITIAL_URI",
                    Uri.parse("content://com.google.android.apps.docs.storage"));
            backupSaveLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "ERR_VAULT_ACT_008: Google Drive save failed", e);
            Toast.makeText(this, "Google Drive not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToOneDrive() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, "CalcPlus_Backup_" + System.currentTimeMillis() + ".cpbak");
            backupSaveLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "ERR_VAULT_ACT_009: OneDrive save failed", e);
            Toast.makeText(this, "OneDrive not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareBackup() {
        try {
            File tempFile = new File(getCacheDir(), "CalcPlus_Backup.cpbak");
            if (vaultManager.createBackup(tempFile, currentKey)) {
                Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", tempFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/octet-stream");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share Backup"));
            } else {
                Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "ERR_VAULT_ACT_010: Share backup failed", e);
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
