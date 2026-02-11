package com.example.da_prototyp_ocr.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.da_prototyp_ocr.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.LinkedHashSet;
import java.util.Set;

public class ManualAddParticipantActivity extends AppCompatActivity {

    private EditText etName;
    private EditText etOrder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_add_participant);

        etName = findViewById(R.id.etName);
        etOrder = findViewById(R.id.etOrder);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        fabAdd.setOnClickListener(v -> addParticipant());
    }

    private void addParticipant() {
        String name = etName.getText().toString().trim();
        String order = etOrder.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Bitte Namen eingeben.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (order.isEmpty()) order = "-";

        String row = name + " – " + order;

        SharedPreferences prefs = getSharedPreferences("manual_participants", MODE_PRIVATE);
        Set<String> set = prefs.getStringSet("items", new LinkedHashSet<>());
        // Achtung: returned Set kann immutable sein -> kopieren
        Set<String> copy = new LinkedHashSet<>(set);
        copy.add(row);

        prefs.edit().putStringSet("items", copy).apply();

        Toast.makeText(this, "Hinzugefügt: " + row, Toast.LENGTH_SHORT).show();
        finish(); // zurück zum Check-in Screen
    }
}