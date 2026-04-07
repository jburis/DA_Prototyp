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

/**
 * Admin-Bereich zur Verwaltung von Veranstaltungen.
 * Aktuell mit Mock-Daten – wird später mit der API verbunden.
 *
 * Funktionen:
 * - Veranstaltungen sperren (orange markiert)
 * - Gesperrte Events per E-Mail exportieren (blau markiert beim Auswählen)
 * - Gesperrte Events löschen
 *
 * Farblogik:
 * - Grün = aktuell ausgewählt
 * - Orange = gesperrt
 * - Blau = für Export ausgewählt (nur im Export-Modus)
 */
public class AdminAreaActivity extends AppCompatActivity {

    private ListView listViewEvents;
    private EditText etEmail, etSearch;
    private Button btnLockEvent, btnDeleteEvent, btnExportEvent, btnLogout;

    private final List<String> allEvents = new ArrayList<>();      // Alle Events
    private final List<String> displayList = new ArrayList<>();    // Aktuell angezeigte (nach Filter)
    private ArrayAdapter<String> eventsAdapter;

    private String selectedEvent = null;

    // Status-Tracking für Farben
    private final Set<String> lockedEvents = new HashSet<>();     // Gesperrte Events (orange)
    private final Set<String> exportedEvents = new HashSet<>();   // Zum Export markiert (blau)

    private boolean isExportMode = false;  // Sind wir gerade im Export-Modus?

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_area);

        // UI-Elemente verbinden
        listViewEvents = findViewById(R.id.listViewEvents);
        etEmail = findViewById(R.id.etEmail);
        etSearch = findViewById(R.id.etSearch);
        btnLockEvent = findViewById(R.id.btnLockEvent);
        btnDeleteEvent = findViewById(R.id.btnDeleteEvent);
        btnExportEvent = findViewById(R.id.btnExportEvent);
        btnLogout = findViewById(R.id.btnLogout);

        // Löschen-Button anfangs deaktiviert (nur gesperrte Events löschbar)
        btnDeleteEvent.setEnabled(false);
        btnDeleteEvent.setAlpha(0.5f);

        // Mock-Daten (später durch API-Call ersetzen)
        allEvents.add("Senior:innenfest");
        allEvents.add("Ausflug Tiergarten");
        allEvents.add("Konzert im Park");
        allEvents.add("Tanznachmittag");
        allEvents.add("Kaiser Wiesn 2024");
        allEvents.add("Weihnachtsmarkt");
        allEvents.add("Sommerfest KWP");

        displayList.addAll(allEvents);

        // Adapter mit Custom-Farben je nach Status
        eventsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayList) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String item = getItem(position);

                if (item != null) {
                    // Priorität der Farben (von oben nach unten)
                    if (isExportMode && exportedEvents.contains(item)) {
                        // Im Export-Modus und ausgewählt → Blau
                        view.setBackgroundColor(Color.parseColor("#BBDEFB"));
                    } else if (item.equals(selectedEvent)) {
                        // Aktuell angeklickt → Grün
                        view.setBackgroundColor(Color.parseColor("#C8E6C9"));
                    } else if (lockedEvents.contains(item)) {
                        // Gesperrt → Orange
                        view.setBackgroundColor(Color.parseColor("#FFE0B2"));
                    } else {
                        // Normal → Transparent
                        view.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
                return view;
            }
        };

        listViewEvents.setAdapter(eventsAdapter);

        // Suchfeld filtert die Liste live
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                eventsAdapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Klick auf Event: Unterschiedliches Verhalten je nach Modus
        listViewEvents.setOnItemClickListener((parent, view, position, id) -> {
            String clickedItem = displayList.get(position);

            if (isExportMode) {
                // Export-Modus: Mehrfachauswahl für gesperrte Events (toggle blau)
                if (lockedEvents.contains(clickedItem)) {
                    if (exportedEvents.contains(clickedItem)) {
                        exportedEvents.remove(clickedItem);
                    } else {
                        exportedEvents.add(clickedItem);
                    }
                }
            } else {
                // Normal-Modus: Einzelauswahl
                selectedEvent = clickedItem;
            }

            eventsAdapter.notifyDataSetChanged();
            updateButtonStates();
        });

        // Event sperren (wird orange)
        btnLockEvent.setOnClickListener(v -> {
            if (selectedEvent == null) {
                toast("Wähle ein Event!");
                return;
            }
            lockedEvents.add(selectedEvent);
            eventsAdapter.notifyDataSetChanged();
            updateButtonStates();
        });

        // Export-Button: Wechselt zwischen zwei Modi
        btnExportEvent.setOnClickListener(v -> {
            if (lockedEvents.isEmpty() && !isExportMode) {
                toast("Keine gesperrten Events!");
                return;
            }

            if (!isExportMode) {
                // In Export-Modus wechseln: Nur gesperrte Events anzeigen
                isExportMode = true;
                displayList.clear();
                displayList.addAll(lockedEvents);

                btnExportEvent.setText("Auswahl versenden");
                btnExportEvent.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34A853")));

                selectedEvent = null;
                toast("Wähle jetzt Events (Blau) für den Export.");
            } else {
                // Versenden: E-Mail validieren und abschicken
                String email = etEmail.getText().toString().trim();

                if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    toast("Bitte gib zuerst eine gültige E-Mail-Adresse ein!");
                    etEmail.requestFocus();
                    return;
                }

                if (exportedEvents.isEmpty()) {
                    toast("Wähle mindestens ein blaues Event aus!");
                    return;
                }

                // Erfolg (hier später echter E-Mail-Versand)
                toast(exportedEvents.size() + " Events an " + email + " versendet.");

                // Zurück zum Normal-Modus
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

        // Event löschen (nur wenn gesperrt)
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

    /**
     * Aktualisiert den Löschen-Button: Nur aktiv wenn ein gesperrtes Event ausgewählt ist.
     */
    private void updateButtonStates() {
        boolean canDelete = selectedEvent != null && lockedEvents.contains(selectedEvent);
        btnDeleteEvent.setEnabled(canDelete);
        btnDeleteEvent.setAlpha(canDelete ? 1.0f : 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}