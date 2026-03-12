package com.example.da_prototyp_ocr.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.da_prototyp_ocr.R;
import com.example.da_prototyp_ocr.camera.CameraController;
import com.example.da_prototyp_ocr.camera.CombinedAnalyzer;
import com.example.da_prototyp_ocr.dto.CheckinByBestellnummerRequest;
import com.example.da_prototyp_ocr.logic.BuchungMatcher;
import com.example.da_prototyp_ocr.logic.CheckInManager;
import com.example.da_prototyp_ocr.model.Anwesenheit;
import com.example.da_prototyp_ocr.model.Buchung;
import com.example.da_prototyp_ocr.network.ApiClient;
import com.example.da_prototyp_ocr.network.ApiService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttendanceCheckInActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 100;
    private static final String TAG = "AttendanceCheckIn";
    private static final String ADMIN_TOKEN = "supersecret-token";

    // ===== UI =====
    private PreviewView previewView;
    private View cameraCard;
    private View scannedCard;
    private TextView textResult;

    private Button btnStartScan;  // NEU: Ein Button statt zwei
    private Button btnToggleCamera;

    private ImageButton btnAddManual;
    private EditText searchField;
    private ListView teilnehmerList;
    private ParticipantAdapter teilnehmerAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Bottom overlay popup
    private View dimView;
    private LinearLayout confirmationLayout;

    // Popup texts
    private TextView confirmationTitleText;
    private TextView tvInfoBestellnr, tvInfoPlaetze, tvInfoEingecheckt, tvInfoFrei;

    private Button btnMinus, btnPlus;
    private TextView checkInCountText;
    private Button confirmCheckInBtn;

    // ===== State =====
    private int veranstaltungId = -1;
    private int checkInAmount = 1;
    private boolean isScanning = false;  // NEU: Scanning-Status

    private List<Buchung> allBuchungen = new ArrayList<>();
    private List<Buchung> filteredBuchungen = new ArrayList<>();
    private Buchung currentScannedBuchung;

    // Helpers
    private final BuchungMatcher matcher = new BuchungMatcher();
    private final CheckInManager checkInManager = new CheckInManager();

    // Camera
    private final CameraController cameraController = new CameraController();
    private ExecutorService analyzerExecutor;
    private boolean isCameraOn = false;
    private CombinedAnalyzer currentAnalyzer;  // NEU: Referenz auf Analyzer

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ===== Bind =====
        previewView = findViewById(R.id.previewView);
        cameraCard = findViewById(R.id.cameraCard);
        scannedCard = findViewById(R.id.scannedCard);
        textResult = findViewById(R.id.textResult);

        // NEU: Ein Button statt zwei
        btnStartScan = findViewById(R.id.btnStartScan);
        btnToggleCamera = findViewById(R.id.btnToggleCamera);

        btnAddManual = findViewById(R.id.btnAddManual);
        searchField = findViewById(R.id.searchField);
        teilnehmerList = findViewById(R.id.scannedList);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        teilnehmerAdapter = new ParticipantAdapter(this, filteredBuchungen);
        teilnehmerList.setAdapter(teilnehmerAdapter);

        dimView = findViewById(R.id.dimView);
        confirmationLayout = findViewById(R.id.confirmationLayout);

        confirmationTitleText = findViewById(R.id.confirmationTitleText);
        tvInfoBestellnr = findViewById(R.id.tvInfoBestellnr);
        tvInfoPlaetze = findViewById(R.id.tvInfoPlaetze);
        tvInfoEingecheckt = findViewById(R.id.tvInfoEingecheckt);
        tvInfoFrei = findViewById(R.id.tvInfoFrei);

        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        checkInCountText = findViewById(R.id.checkInCountText);
        confirmCheckInBtn = findViewById(R.id.confirmCheckInBtn);

        analyzerExecutor = Executors.newSingleThreadExecutor();

        // Inset padding (damit Button nicht unter System-Navigation verschwindet)
        applyBottomInsetPaddingToPopup();

        veranstaltungId = getIntent().getIntExtra("VERANSTALTUNG_ID", -1);
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Fehler: Keine veranstaltung_id übergeben.", Toast.LENGTH_LONG).show();
        }

        // Kamera startet NICHT automatisch
        setCameraUi(false);
        hideConfirmation();

        // Teilnehmerliste beim Start laden
        loadBuchungenAndAnwesenheiten(null);

        // ===== Listeners =====
        btnToggleCamera.setOnClickListener(v -> toggleCamera());

        // NEU: Ein Button für automatisches Scannen
        btnStartScan.setOnClickListener(v -> {
            if (!isCameraOn) {
                Toast.makeText(this, "Bitte zuerst Kamera einschalten", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isScanning) {
                stopScanning();
            } else {
                // Buchungen laden und dann Scannen starten
                loadBuchungenAndAnwesenheiten(this::startContinuousScanning);
            }
        });

        // Plus Button: Neue Buchung hinzufügen
        btnAddManual.setOnClickListener(v -> showAddParticipantDialog());

        // Suchfunktion
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTeilnehmer(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Teilnehmer anklicken öffnet Confirmation Popup
        teilnehmerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Buchung selectedBuchung = filteredBuchungen.get(position);
                showConfirmation(selectedBuchung);
            }
        });

        // Pull-to-Refresh
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadBuchungenAndAnwesenheiten(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(AttendanceCheckInActivity.this, "Liste aktualisiert", Toast.LENGTH_SHORT).show();
                });
            });
        }

        // Dim klick schließt Popup
        if (dimView != null) dimView.setOnClickListener(v -> hideConfirmation());

        setupPlusMinus();

        confirmCheckInBtn.setOnClickListener(v -> {
            if (currentScannedBuchung == null) return;

            int amount = checkInManager.clampAmount(currentScannedBuchung, checkInAmount);
            if (amount <= 0) {
                Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
                return;
            }

            performCheckInByBestellnummer(currentScannedBuchung.getBestellnummer(), amount);
            hideConfirmation();
            currentScannedBuchung = null;
        });
    }

    private void applyBottomInsetPaddingToPopup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && confirmationLayout != null) {
            confirmationLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = insets.getSystemWindowInsetBottom();
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        Math.max(v.getPaddingBottom(), bottom + dp(10))
                );
                return insets;
            });
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    // ===== Filter Funktion =====
    private void filterTeilnehmer(String query) {
        filteredBuchungen.clear();

        if (query == null || query.trim().isEmpty()) {
            filteredBuchungen.addAll(allBuchungen);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (Buchung buchung : allBuchungen) {
                String displayName = safeStr(buchung.getDisplayName()).toLowerCase();
                String bestellnr = safeStr(buchung.getBestellnummer()).toLowerCase();
                String vorname = safeStr(buchung.getVorname()).toLowerCase();
                String nachname = safeStr(buchung.getNachname()).toLowerCase();

                if (displayName.contains(lowerQuery) ||
                        bestellnr.contains(lowerQuery) ||
                        vorname.contains(lowerQuery) ||
                        nachname.contains(lowerQuery)) {
                    filteredBuchungen.add(buchung);
                }
            }
        }

        // Alphabetisch sortieren nach Namen
        Collections.sort(filteredBuchungen, new Comparator<Buchung>() {
            @Override
            public int compare(Buchung b1, Buchung b2) {
                String name1 = b1.getDisplayName();
                if (name1 == null || name1.trim().isEmpty()) {
                    name1 = b1.getVorname() + " " + b1.getNachname();
                }

                String name2 = b2.getDisplayName();
                if (name2 == null || name2.trim().isEmpty()) {
                    name2 = b2.getVorname() + " " + b2.getNachname();
                }

                return name1.compareToIgnoreCase(name2);
            }
        });

        teilnehmerAdapter.notifyDataSetChanged();
    }

    // ===== Kamera UI =====
    private void setCameraUi(boolean on) {
        if (cameraCard != null) cameraCard.setVisibility(on ? View.VISIBLE : View.GONE);

        // weniger Ablenkung: Liste ausblenden wenn Kamera an
        if (scannedCard != null) scannedCard.setVisibility(on ? View.GONE : View.VISIBLE);

        // NEU: Nur einen Button anzeigen
        if (btnStartScan != null) {
            btnStartScan.setVisibility(on ? View.VISIBLE : View.GONE);
            btnStartScan.setText("Scan starten");
        }

        btnToggleCamera.setText(on ? "Kamera AUS" : "Kamera AN");
        textResult.setText(on ? "Drücke 'Scan starten' um zu beginnen"
                : "Kamera manuell starten");

        // Scanning zurücksetzen wenn Kamera aus
        if (!on) {
            isScanning = false;
        }
    }

    private void toggleCamera() {
        if (isCameraOn) stopCamera();
        else startCameraSafe();
    }

    private void startCameraSafe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        startCamera();
    }

    private void startCamera() {
        cameraController.start(
                this,
                previewView,
                CameraSelector.LENS_FACING_BACK,
                new Size(720, 720)
        );
        isCameraOn = true;
        setCameraUi(true);
    }

    private void stopCamera() {
        // NEU: Zuerst Scanning stoppen
        if (isScanning) {
            stopScanning();
        }

        try {
            ImageAnalysis analysis = cameraController.getImageAnalysis();
            if (analysis != null) analysis.clearAnalyzer();
            cameraController.stop();
        } catch (Exception e) {
            Log.e(TAG, "stopCamera failed", e);
        }
        isCameraOn = false;
        currentAnalyzer = null;
        hideConfirmation();
        setCameraUi(false);
    }

    // Permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Berechtigung OK. Du kannst Kamera einschalten.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Kamera-Berechtigung verweigert", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ===== NEU: Kontinuierliches Scannen =====
    private void startContinuousScanning() {
        ImageAnalysis analysis = cameraController.getImageAnalysis();
        if (analysis == null) return;

        isScanning = true;
        btnStartScan.setText("Scan stoppen");
        textResult.setText("Scanne... Halte QR-Code oder Buchungsbestätigung vor die Kamera");

        currentAnalyzer = new CombinedAnalyzer(new CombinedAnalyzer.Callback() {
            @Override
            public void onResult(@NonNull CombinedAnalyzer.ResultType type, @NonNull String value) {
                runOnUiThread(() -> {
                    Buchung found = null;

                    if (type == CombinedAnalyzer.ResultType.QR_CODE) {
                        // QR-Code: value ist der extrahierte Name
                        found = matcher.findByDisplayName(allBuchungen, value);
                        if (found == null) {
                            textResult.setText("QR erkannt, aber nicht gefunden: " + value);
                        }
                    } else {
                        // OCR: value ist die Bestellnummer
                        found = matcher.findByBestellnummer(allBuchungen, value);
                        if (found == null) {
                            textResult.setText("Bestellnr. erkannt, aber nicht gefunden: " + value);
                        }
                    }

                    if (found != null) {
                        // Erfolg! Popup anzeigen
                        String typeStr = (type == CombinedAnalyzer.ResultType.QR_CODE) ? "QR" : "OCR";
                        textResult.setText(typeStr + " erkannt: " + found.getDisplayName());
                        showConfirmation(found);
                    }
                });
            }

            @Override
            public void onError(@NonNull Exception e) {
                runOnUiThread(() -> {
                    // Fehler nur loggen, nicht bei jedem Frame anzeigen
                    Log.e(TAG, "Scan-Fehler: " + e.getMessage());
                });
            }
        });

        analysis.setAnalyzer(analyzerExecutor, currentAnalyzer);
    }

    private void stopScanning() {
        ImageAnalysis analysis = cameraController.getImageAnalysis();
        if (analysis != null) {
            analysis.clearAnalyzer();
        }
        isScanning = false;
        currentAnalyzer = null;
        btnStartScan.setText("Scan starten");
        textResult.setText("Scan gestoppt");
    }

    // ===== Bottom Confirmation Overlay =====
    private void showConfirmation(@NonNull Buchung buchung) {
        currentScannedBuchung = buchung;

        int free = checkInManager.freeSeats(buchung);
        if (free < 1) {
            hideConfirmation();
            Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
            return;
        }

        checkInAmount = 1;
        updateCheckInAmountUI();

        // Titel groß
        confirmationTitleText.setText("Teilnehmer: " + safeStr(buchung.getDisplayName()));

        // Infos gestapelt
        tvInfoBestellnr.setText("Bestellnr.: " + safeStr(buchung.getBestellnummer()));
        tvInfoPlaetze.setText("Plätze: " + buchung.getAnzahlPlaetze());
        tvInfoEingecheckt.setText("Eingecheckt: " + buchung.getCheckedInCount());
        tvInfoFrei.setText("Frei: " + free);

        if (dimView != null) dimView.setVisibility(View.VISIBLE);
        if (confirmationLayout != null) confirmationLayout.setVisibility(View.VISIBLE);
    }

    private void hideConfirmation() {
        if (dimView != null) dimView.setVisibility(View.GONE);
        if (confirmationLayout != null) confirmationLayout.setVisibility(View.GONE);
    }

    private void setupPlusMinus() {
        btnMinus.setText("−");
        btnPlus.setText("+");

        btnMinus.setOnClickListener(v -> {
            if (checkInManager.canDecrease(checkInAmount)) {
                checkInAmount--;
                updateCheckInAmountUI();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (currentScannedBuchung == null) return;

            if (checkInManager.canIncrease(currentScannedBuchung, checkInAmount)) {
                checkInAmount++;
                updateCheckInAmountUI();
            } else {
                Toast.makeText(this, "Nicht mehr freie Plätze verfügbar.", Toast.LENGTH_SHORT).show();
            }
        });

        updateCheckInAmountUI();
    }

    private void updateCheckInAmountUI() {
        checkInCountText.setText(String.valueOf(checkInAmount));
    }

    // ===== API Load =====
    private void loadBuchungenAndAnwesenheiten(Runnable onFinished) {
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getBuchungenByVeranstaltung(veranstaltungId)
                .enqueue(new retrofit2.Callback<List<Buchung>>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<List<Buchung>> call,
                                           @NonNull retrofit2.Response<List<Buchung>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            runOnUiThread(() -> Toast.makeText(AttendanceCheckInActivity.this,
                                    "Fehler beim Laden der Buchungen: " + response.code(),
                                    Toast.LENGTH_LONG).show());
                            return;
                        }

                        List<Buchung> buchungen = response.body();

                        apiService.getAnwesenheitenByVeranstaltung(veranstaltungId)
                                .enqueue(new retrofit2.Callback<List<Anwesenheit>>() {
                                    @Override
                                    public void onResponse(@NonNull retrofit2.Call<List<Anwesenheit>> call2,
                                                           @NonNull retrofit2.Response<List<Anwesenheit>> response2) {
                                        runOnUiThread(() -> {
                                            if (response2.isSuccessful() && response2.body() != null) {
                                                HashMap<Integer, Integer> sums = new HashMap<>();
                                                for (Anwesenheit a : response2.body()) {
                                                    int key = a.getBuchungId();
                                                    int cur = sums.containsKey(key) ? sums.get(key) : 0;
                                                    sums.put(key, cur + a.getAnzahlEingecheckt());
                                                }
                                                for (Buchung b : buchungen) {
                                                    int checked = sums.containsKey(b.getBuchungId()) ? sums.get(b.getBuchungId()) : 0;
                                                    b.setCheckedInCount(checked);
                                                }
                                            } else {
                                                for (Buchung b : buchungen) b.setCheckedInCount(0);
                                            }

                                            allBuchungen = buchungen;
                                            filterTeilnehmer(searchField.getText().toString());
                                            if (onFinished != null) onFinished.run();
                                        });
                                    }

                                    @Override
                                    public void onFailure(@NonNull retrofit2.Call<List<Anwesenheit>> call2,
                                                          @NonNull Throwable t) {
                                        runOnUiThread(() -> {
                                            for (Buchung b : buchungen) b.setCheckedInCount(0);
                                            allBuchungen = buchungen;
                                            filterTeilnehmer(searchField.getText().toString());
                                            if (onFinished != null) onFinished.run();
                                            Toast.makeText(AttendanceCheckInActivity.this,
                                                    "Anwesenheiten konnten nicht geladen werden (checkedIn=0).",
                                                    Toast.LENGTH_LONG).show();
                                        });
                                    }
                                });
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<List<Buchung>> call, @NonNull Throwable t) {
                        runOnUiThread(() -> Toast.makeText(AttendanceCheckInActivity.this,
                                "Netzwerkfehler beim Laden der Buchungen: " + t.getMessage(),
                                Toast.LENGTH_LONG).show());
                    }
                });
    }

    private void performCheckInByBestellnummer(String bestellnummer, int anzahl) {
        if (bestellnummer == null || bestellnummer.trim().isEmpty()) {
            Toast.makeText(this, "Keine Bestellnummer für Check-in gefunden", Toast.LENGTH_SHORT).show();
            return;
        }
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (anzahl < 1) anzahl = 1;

        final int finalAnzahl = anzahl;

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        CheckinByBestellnummerRequest body = new CheckinByBestellnummerRequest(bestellnummer, finalAnzahl);

        apiService.checkInByBestellnummer(ADMIN_TOKEN, veranstaltungId, body)
                .enqueue(new retrofit2.Callback<Anwesenheit>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<Anwesenheit> call,
                                           @NonNull retrofit2.Response<Anwesenheit> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                String msg = "Check-in (" + finalAnzahl + ") für " + bestellnummer + " erfolgreich!";
                                Toast.makeText(AttendanceCheckInActivity.this, msg, Toast.LENGTH_LONG).show();
                                textResult.setText(msg);

                                // Cooldown zurücksetzen damit sofort nächste Person gescannt werden kann
                                if (currentAnalyzer != null) {
                                    currentAnalyzer.resetCooldown();
                                }

                                // Liste neu laden nach erfolgreichem Check-in
                                loadBuchungenAndAnwesenheiten(null);
                            } else {
                                String errorMessage = "API-Fehler beim Check-in. Code: " + response.code();
                                Toast.makeText(AttendanceCheckInActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                textResult.setText(errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<Anwesenheit> call, @NonNull Throwable t) {
                        runOnUiThread(() -> {
                            Toast.makeText(AttendanceCheckInActivity.this, "Netzwerkfehler: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            textResult.setText("Check-in fehlgeschlagen.\nBitte Netzwerkverbindung prüfen.");
                        });
                    }
                });
    }

    private void showAddParticipantDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Neuen Teilnehmer hinzufügen");

        // Custom layout for the dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Vorname
        final EditText inputVorname = new EditText(this);
        inputVorname.setHint("Vorname");
        layout.addView(inputVorname);

        // Nachname
        final EditText inputNachname = new EditText(this);
        inputNachname.setHint("Nachname");
        layout.addView(inputNachname);

        // Anzahl Plätze
        final EditText inputPlaetze = new EditText(this);
        inputPlaetze.setHint("Anzahl Plätze");
        inputPlaetze.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputPlaetze.setText("1");
        layout.addView(inputPlaetze);

        builder.setView(layout);

        builder.setPositiveButton("Hinzufügen", (dialog, which) -> {
            String vorname = inputVorname.getText().toString().trim();
            String nachname = inputNachname.getText().toString().trim();
            String plaetzeStr = inputPlaetze.getText().toString().trim();

            // Validierung
            if (vorname.isEmpty() || nachname.isEmpty()) {
                Toast.makeText(this, "Vor- und Nachname sind erforderlich", Toast.LENGTH_SHORT).show();
                return;
            }

            int anzahlPlaetze = 1;
            try {
                anzahlPlaetze = Integer.parseInt(plaetzeStr);
                if (anzahlPlaetze < 1) anzahlPlaetze = 1;
            } catch (NumberFormatException e) {
                anzahlPlaetze = 1;
            }

            // Buchung erstellen via API
            createNewBuchung(vorname, nachname, anzahlPlaetze);
        });

        builder.setNegativeButton("Abbrechen", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void createNewBuchung(String vorname, String nachname, int anzahlPlaetze) {
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Bestellnummer automatisch generieren (6-stellige Zufallszahl)
        int randomNumber = 100000 + new java.util.Random().nextInt(900000);
        String generatedBestellnummer = "#" + randomNumber;

        // E-Mail automatisch generieren
        String generatedEmail = vorname.toLowerCase() + "." + nachname.toLowerCase() + "@generated.local";

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        // DTO für neue Buchung erstellen
        Buchung newBuchung = new Buchung();
        newBuchung.setVorname(vorname);
        newBuchung.setNachname(nachname);
        newBuchung.setBestellnummer(generatedBestellnummer);
        newBuchung.setKontakt(generatedEmail);
        newBuchung.setAnzahlPlaetze(anzahlPlaetze);
        newBuchung.setVeranstaltungId(veranstaltungId);

        // API Call
        apiService.createBuchung(ADMIN_TOKEN, newBuchung)
                .enqueue(new retrofit2.Callback<Buchung>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<Buchung> call,
                                           @NonNull retrofit2.Response<Buchung> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                Toast.makeText(AttendanceCheckInActivity.this,
                                        "Teilnehmer erfolgreich hinzugefügt!\nBestellnr.: " + generatedBestellnummer,
                                        Toast.LENGTH_LONG).show();

                                // Liste neu laden
                                loadBuchungenAndAnwesenheiten(null);
                            } else {
                                Toast.makeText(AttendanceCheckInActivity.this,
                                        "Fehler beim Hinzufügen: " + response.code(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<Buchung> call, @NonNull Throwable t) {
                        runOnUiThread(() -> {
                            Toast.makeText(AttendanceCheckInActivity.this,
                                    "Netzwerkfehler: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private String safeStr(String s) {
        return (s == null) ? "" : s;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isCameraOn) stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (analyzerExecutor != null) analyzerExecutor.shutdown(); } catch (Exception ignored) {}
    }
}