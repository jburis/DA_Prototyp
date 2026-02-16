package com.example.da_prototyp_ocr.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.da_prototyp_ocr.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdminAreaActivity extends AppCompatActivity {

    private ListView listViewEvents;
    private EditText etEmail, etSearch;
    private Button btnLockEvent, btnDeleteEvent, btnExportEvent, btnLogout;

    private final List<String> allEvents = new ArrayList<>();
    private final List<String> displayList = new ArrayList<>();
    private ArrayAdapter<String> eventsAdapter;

    private String selectedEvent = null;

    // Status-Listen für die Farben
    private final Set<String> lockedEvents = new HashSet<>();   // Orange (Gesperrt)
    private final Set<String> exportedEvents = new HashSet<>(); // Blau (Für Versand markiert)

    private boolean isExportMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_area);

        // UI Bindings
        listViewEvents = findViewById(R.id.listViewEvents);
        etEmail = findViewById(R.id.etEmail);
        etSearch = findViewById(R.id.etSearch);
        btnLockEvent = findViewById(R.id.btnLockEvent);
        btnDeleteEvent = findViewById(R.id.btnDeleteEvent);
        btnExportEvent = findViewById(R.id.btnExportEvent);
        btnLogout = findViewById(R.id.btnLogout);

        // Initialer Zustand
        btnDeleteEvent.setEnabled(false);
        btnDeleteEvent.setAlpha(0.5f);

        // Beispiel-Daten
        allEvents.add("Senior:innenfest");
        allEvents.add("Ausflug Tiergarten");
        allEvents.add("Konzert im Park");
        allEvents.add("Tanznachmittag");
        allEvents.add("Kaiser Wiesn 2024");
        allEvents.add("Weihnachtsmarkt");
        allEvents.add("Sommerfest KWP");

        displayList.addAll(allEvents);

        // Adapter steuert Farben SOFORT
        eventsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayList) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String item = getItem(position);

                if (item != null) {
                    // 1. Im Export-Modus & gewählt -> BLAU
                    if (isExportMode && exportedEvents.contains(item)) {
                        view.setBackgroundColor(Color.parseColor("#BBDEFB"));
                    }
                    // 2. Aktuelle Auswahl -> GRÜN
                    else if (item.equals(selectedEvent)) {
                        view.setBackgroundColor(Color.parseColor("#C8E6C9"));
                    }
                    // 3. Gesperrt -> ORANGE
                    else if (lockedEvents.contains(item)) {
                        view.setBackgroundColor(Color.parseColor("#FFE0B2"));
                    }
                    else {
                        view.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
                return view;
            }
        };

        listViewEvents.setAdapter(eventsAdapter);

        // Suche
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                eventsAdapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Klick-Logik für MEHRFACHAUSWAHL & SOFORTIGES BLAU
        listViewEvents.setOnItemClickListener((parent, view, position, id) -> {
            String clickedItem = displayList.get(position);

            if (isExportMode) {
                if (lockedEvents.contains(clickedItem)) {
                    if (exportedEvents.contains(clickedItem)) {
                        exportedEvents.remove(clickedItem);
                    } else {
                        exportedEvents.add(clickedItem);
                    }
                }
            } else {
                selectedEvent = clickedItem;
            }

            eventsAdapter.notifyDataSetChanged();
            updateButtonStates();
        });

        // Sperren
        btnLockEvent.setOnClickListener(v -> {
            if (selectedEvent == null) {
                toast("Wähle ein Event!");
                return;
            }
            lockedEvents.add(selectedEvent);
            eventsAdapter.notifyDataSetChanged();
            updateButtonStates();
        });

        // Export & E-Mail Validierung
        btnExportEvent.setOnClickListener(v -> {
            if (lockedEvents.isEmpty() && !isExportMode) {
                toast("Keine gesperrten Events!");
                return;
            }

            if (!isExportMode) {
                isExportMode = true;
                displayList.clear();
                displayList.addAll(lockedEvents);

                btnExportEvent.setText("Auswahl versenden");
                btnExportEvent.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34A853")));

                selectedEvent = null;
                toast("Wähle jetzt Events (Blau) für den Export.");
            } else {
                // PRÜFUNG: Ist eine E-Mail eingeben?
                String email = etEmail.getText().toString().trim();

                if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    toast("Bitte gib zuerst eine gültige E-Mail-Adresse ein!");
                    etEmail.requestFocus(); // Cursor ins E-Mail Feld setzen
                    return; // Abbrechen, kein Senden möglich
                }

                if (exportedEvents.isEmpty()) {
                    toast("Wähle mindestens ein blaues Event aus!");
                    return;
                }

                // Erfolg: Senden simulieren
                toast(exportedEvents.size() + " Events an " + email + " versendet.");

                // Modus beenden & zurücksetzen
                isExportMode = false;
                displayList.clear();
                displayList.addAll(allEvents);
                btnExportEvent.setText("Exportieren");
                btnExportEvent.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2F7DFF")));

                exportedEvents.clear();
                selectedEvent = null;
            }
            eventsAdapter.notifyDataSetChanged();
        });

        // Löschen
        btnDeleteEvent.setOnClickListener(v -> {
            if (selectedEvent != null && lockedEvents.contains(selectedEvent)) {
                allEvents.remove(selectedEvent);
                displayList.remove(selectedEvent);
                lockedEvents.remove(selectedEvent);
                exportedEvents.remove(selectedEvent);

                selectedEvent = null;
                eventsAdapter.notifyDataSetChanged();
                updateButtonStates();
                toast("Gelöscht.");
            }
        });

        btnLogout.setOnClickListener(v -> finish());
    }

    private void updateButtonStates() {
        boolean canDelete = selectedEvent != null && lockedEvents.contains(selectedEvent);
        btnDeleteEvent.setEnabled(canDelete);
        btnDeleteEvent.setAlpha(canDelete ? 1.0f : 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}