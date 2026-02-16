package com.example.da_prototyp_ocr.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.da_prototyp_ocr.R;
import java.util.ArrayList;
import java.util.List;

public class AdminAreaActivity extends AppCompatActivity {

    private ListView listViewEvents;
    private EditText etEmail;
    private Button btnLockEvent, btnDeleteEvent, btnExportEvent, btnLogout;

    private final List<String> events = new ArrayList<>();
    private ArrayAdapter<String> eventsAdapter;

    private View lastSelectedView = null;
    private String selectedEvent = null;
    private boolean isSelectedEventLocked = false; // Neuer Status

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_area);

        listViewEvents = findViewById(R.id.listViewEvents);
        etEmail = findViewById(R.id.etEmail);
        btnLockEvent = findViewById(R.id.btnLockEvent);
        btnDeleteEvent = findViewById(R.id.btnDeleteEvent);
        btnExportEvent = findViewById(R.id.btnExportEvent);
        btnLogout = findViewById(R.id.btnLogout);

        // Löschen Button am Anfang deaktivieren
        btnDeleteEvent.setEnabled(false);
        btnDeleteEvent.setAlpha(0.5f); // Optisch ausgrauen

        // Dummy Daten
        events.add("Senior:innenfest");
        events.add("Ausflug Tiergarten");
        events.add("Konzert im Park");

        eventsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, events);
        listViewEvents.setAdapter(eventsAdapter);

        listViewEvents.setOnItemClickListener((parent, view, position, id) -> {
            if (lastSelectedView != null) {
                lastSelectedView.setBackgroundColor(Color.TRANSPARENT);
            }
            selectedEvent = events.get(position);
            view.setBackgroundColor(Color.parseColor("#C8E6C9")); // Grün markiert
            lastSelectedView = view;

            // Bei neuer Auswahl Sperre zurücksetzen
            isSelectedEventLocked = false;
            btnDeleteEvent.setEnabled(false);
            btnDeleteEvent.setAlpha(0.5f);
        });

        btnLockEvent.setOnClickListener(v -> {
            if (selectedEvent == null) {
                toast("Bitte zuerst ein Event auswählen.");
                return;
            }
            isSelectedEventLocked = true;
            lastSelectedView.setBackgroundColor(Color.parseColor("#FFE0B2")); // Orange
            btnDeleteEvent.setEnabled(true); // JETZT erst freischalten
            btnDeleteEvent.setAlpha(1.0f);
            toast("Event gesperrt - Löschen jetzt möglich.");
        });

        btnDeleteEvent.setOnClickListener(v -> {
            if (isSelectedEventLocked) {
                events.remove(selectedEvent);
                eventsAdapter.notifyDataSetChanged();
                toast("Event gelöscht.");
                selectedEvent = null;
                lastSelectedView = null;
                btnDeleteEvent.setEnabled(false);
                btnDeleteEvent.setAlpha(0.5f);
            }
        });

        btnExportEvent.setOnClickListener(v -> {
            if (selectedEvent != null) {
                lastSelectedView.setBackgroundColor(Color.parseColor("#BBDEFB")); // Blau
                toast("Export gestartet.");
            }
        });

        btnLogout.setOnClickListener(v -> finish());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}