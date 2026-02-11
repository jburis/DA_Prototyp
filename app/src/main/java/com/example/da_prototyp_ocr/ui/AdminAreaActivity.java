package com.example.da_prototyp_ocr.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.da_prototyp_ocr.R;

import java.util.ArrayList;
import java.util.List;

public class AdminAreaActivity extends AppCompatActivity {

    private ListView listViewEvents;
    private TextView tvSelectedEvent;
    private EditText etEmail;

    private Button btnLockEvent, btnDeleteEvent, btnExportEvent, btnLogout;

    private final List<String> events = new ArrayList<>();
    private ArrayAdapter<String> eventsAdapter;

    private String selectedEvent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_area);

        // Views (IDs müssen im XML existieren)
        listViewEvents = findViewById(R.id.listViewEvents);
        tvSelectedEvent = findViewById(R.id.tvSelectedEvent);
        etEmail = findViewById(R.id.etEmail);

        btnLockEvent = findViewById(R.id.btnLockEvent);
        btnDeleteEvent = findViewById(R.id.btnDeleteEvent);
        btnExportEvent = findViewById(R.id.btnExportEvent);
        btnLogout = findViewById(R.id.btnLogout);

        // Dummy Events
        events.add("Senior:innenfest");
        events.add("Kaiser Wiesn 2024");
        events.add("Ausflug Tiergarten");
        events.add("Tanznachmittag");
        events.add("Konzert im Park");

        eventsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, events);
        listViewEvents.setAdapter(eventsAdapter);

        // Event auswählen
        listViewEvents.setOnItemClickListener((parent, view, position, id) -> {
            selectedEvent = events.get(position);
            tvSelectedEvent.setText(selectedEvent);
        });

        btnLockEvent.setOnClickListener(v -> {
            if (selectedEvent == null) {
                toast("Bitte zuerst ein Event auswählen.");
                return;
            }
            toast("Event gesperrt: " + selectedEvent);
            // TODO später: API call "lock event"
        });

        btnDeleteEvent.setOnClickListener(v -> {
            if (selectedEvent == null) {
                toast("Bitte zuerst ein Event auswählen.");
                return;
            }

            // Dummy: aus Liste entfernen
            events.remove(selectedEvent);
            eventsAdapter.notifyDataSetChanged();
            toast("Event gelöscht: " + selectedEvent);

            selectedEvent = null;
            tvSelectedEvent.setText("Kein Event ausgewählt");
            // TODO später: API call "delete event"
        });

        btnExportEvent.setOnClickListener(v -> {
            if (selectedEvent == null) {
                toast("Bitte zuerst ein Event auswählen.");
                return;
            }

            String email = etEmail.getText().toString().trim();
            if (email.isEmpty() || !email.contains("@")) {
                toast("Bitte gültige E-Mail eingeben.");
                return;
            }

            toast("Export gestartet für '" + selectedEvent + "' an: " + email);
            // TODO später: API call "export event list"
        });

        btnLogout.setOnClickListener(v -> finish());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}