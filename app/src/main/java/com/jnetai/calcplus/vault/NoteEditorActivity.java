package com.jnetai.calcplus.vault;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.jnetai.calcplus.R;
import com.jnetai.calcplus.util.CryptoUtils;
import com.jnetai.calcplus.util.SecurePrefs;
import com.jnetai.calcplus.util.VaultManager;

import javax.crypto.SecretKey;

public class NoteEditorActivity extends AppCompatActivity {
    private static final String TAG = "CalcPlus_NoteEditor";

    private EditText noteContent;
    private String noteName;
    private String currentPin;
    private VaultManager vaultManager;
    private SecretKey currentKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        noteName = getIntent().getStringExtra("noteName");
        currentPin = getIntent().getStringExtra("pin");

        if (noteName == null || currentPin == null) {
            Log.e(TAG, "ERR_NOTE_001: Missing note name or PIN");
            finish();
            return;
        }

        String salt = SecurePrefs.getInstance(this).getSalt();
        currentKey = CryptoUtils.deriveKey(currentPin, salt);
        vaultManager = new VaultManager(this);

        noteContent = findViewById(R.id.noteContent);

        setTitle(noteName);

        String existingContent = vaultManager.readNote(noteName, currentKey);
        if (existingContent != null) {
            noteContent.setText(existingContent);
        }

        findViewById(R.id.btnSaveNote).setOnClickListener(v -> saveNote());
        findViewById(R.id.btnCancelNote).setOnClickListener(v -> finish());
    }

    private void saveNote() {
        String content = noteContent.getText().toString();
        if (vaultManager.saveNote(noteName, content, currentKey)) {
            Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to save note", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "ERR_NOTE_002: Failed to save note: " + noteName);
        }
    }
}
